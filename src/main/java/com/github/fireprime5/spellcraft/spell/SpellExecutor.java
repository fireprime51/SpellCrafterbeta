package com.github.fireprime5.spellcraft.spell;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Исполнитель графа заклинания. Возвращает результат (стек, ошибки, трасса)
 * и отправляет его игроку в GUI.
 */
public final class SpellExecutor {

    private SpellExecutor() {
    }

    public record Result(List<String> trace, List<SpellValue> stack, List<SpellIssue> issues,
                         boolean success, int manaSpent, List<Integer> errorNodes) {
    }

    public static Result execute(SpellGraph graph, Level level, LivingEntity caster, Vec3 origin) {
        ServerPlayer player = caster instanceof ServerPlayer sp ? sp : null;

        List<SpellIssue> issues = graph.validate(player);
        if (!issues.isEmpty()) {
            Result result = new Result(List.of(), List.of(), List.copyOf(issues), false, 0,
                    List.of());
            report(player, result, "message.spellcraft.cast.invalid");
            return result;
        }

        SpellContext ctx = new SpellContext(level, caster, origin);

        // потолок энергии бездны нужен модулям с плавающей ценой (например «Расщепление»
        // стоит ровно весь кошелёк)
        int cost = graph.totalAbyssCost(
                player == null ? 0
                        : com.github.fireprime5.spellcraft.energy.AbyssEnergy.capacity());
        if (!ctx.hasMana(cost)) {
            Result result = new Result(List.of(), List.of(),
                    List.of(SpellIssue.of("abyss.insufficient", cost)), false, 0, List.of());
            report(player, result, "message.spellcraft.cast.no_mana");
            return result;
        }

        List<Integer> order = graph.topologicalOrder(ctx.errors());
        if (order == null) {
            Result result = new Result(List.copyOf(ctx.trace()), List.of(),
                    List.copyOf(ctx.errors()), false, cost, List.of());
            report(player, result, "message.spellcraft.cast.failed");
            return result;
        }

        ctx.spend(cost);
        ctx.spendMana(cost);
        ctx.markPrepaid(cost);

        List<Integer> errorNodes = interpret(graph, ctx, order, Mode.CAST);

        boolean success = !ctx.failed();
        if (ctx.halted()) {
            ctx.trace("cast stopped by exit");
        }
        // модули могут добирать энергию бездны прямо во время каста (например, мгновенная
        // добыча по блокам) — эти траты тоже нужно списать с игрока
        int extra = ctx.manaSpent() - cost;
        if (extra > 0) {
            ctx.spend(extra);
        }
        // личный модуль «Счёт» читает, сколько бездны игрок потратил за жизнь.
        // Считаем здесь, а не в энергосистеме: тот же кошелёк тратят ещё и
        // броня, и покупка модификатора, а «потрачено за каст» — единственное
        // число, которое честно совпадает с тем, что показано в панели каста
        if (player != null && success && ctx.manaSpent() > 0) {
            com.github.fireprime5.spellcraft.data.PlayerKnowledge
                    .addAbyssSpent(player, ctx.manaSpent());
        }
        // в панели показываем то, что реально осталось в стеке
        List<SpellValue> leftOnStack = ctx.stackSnapshot();
        Result result = new Result(List.copyOf(ctx.trace()), leftOnStack,
                List.copyOf(ctx.errors()), success, ctx.manaSpent(), List.copyOf(errorNodes));
        report(player, result, "message.spellcraft.cast.failed");

        if (success) {
            castEffects(player);
        }
        return result;
    }

    /** Небольшой эффект при успешном касте: частицы и звук. */
    private static void castEffects(ServerPlayer player) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        var pos = player.position();
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                pos.x, pos.y + 1.0, pos.z, 24, 0.5, 0.6, 0.5, 0.02);
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.EFFECT,
                pos.x, pos.y + 1.2, pos.z, 8, 0.3, 0.4, 0.3, 0.01);
        server.playSound(null, pos.x, pos.y + 1, pos.z,
                net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    /**
     * Прогон графа без списания, отчёта и эффектов каста — для брони.
     *
     * <p>Броня не «кастуется»: её схему читают каждый тик, и модули-модификаторы
     * через {@link SpellContext#putArmorModifier} кладут свои множители в контекст,
     * откуда их потом забирает {@code ArmorEvents}. Отдельный метод, а не
     * {@link #execute}: тот списывает бездну, шлёт игроку пакет результата и
     * играет эффекты — для постоянного тика брони всё это лишнее и вредно.
     *
     * <p>Битый узел не роняет сервер: он просто пропускается, ведь этот прогон
     * идёт каждый тик и не должен прерываться из-за одной ошибки в схеме.
     */
    public static void runInto(SpellGraph graph, SpellContext ctx) {
        List<Integer> order = graph.topologicalOrder(ctx.errors());
        if (order == null) {
            return;
        }
        interpret(graph, ctx, order, Mode.PER_TICK);
    }

    /**
     * Как строго исполнять граф.
     *
     * <p>Разница между кастом и тиком брони только в диагностике: каст пишет
     * трассу, собирает ошибки и останавливается, как только контекст провалился;
     * тик брони молча пропускает битые узлы и всегда доходит до конца.
     */
    private enum Mode {
        /** Каст игрока: трасса, отчёт об ошибках, остановка на провале. */
        CAST,
        /** Тик модульной брони: без трассы, ошибки узлов молча игнорируются. */
        PER_TICK
    }

    /**
     * Общий цикл исполнения: идёт по {@code order} и для каждого узла
     * собирает входы из результатов уже исполненных узлов.
     *
     * <p>Входы приходят по соединениям из {@code results}, а не из стека:
     * стек трогают только модули {@code stack_*}. Поэтому исполнитель
     * ничего не кладёт и не снимает — значение само уходит дальше по графу
     * через {@code results.put(idx, outputs)}, а «Кладёт» и «Взять» делают
     * ровно то, что написано в их описании.
     *
     * @return индексы узлов, которые упали (в {@link Mode#PER_TICK} всегда пусто)
     */
    private static List<Integer> interpret(SpellGraph graph, SpellContext ctx, List<Integer> order,
                                           Mode mode) {
        Map<Integer, List<SpellValue>> results = new HashMap<>();
        List<Integer> errorNodes = new ArrayList<>();

        for (int idx : order) {
            // В режиме брони цикл не прерывается: он идёт каждый тик и должен
            // дочитать схему до конца, даже если часть узлов уже отвалилась.
            if (mode == Mode.CAST && (ctx.failed() || ctx.halted())) {
                break;
            }
            SpellNode node = graph.nodes.get(idx);
            SpellModuleType type = SpellModules.get(node.moduleId);
            if (type == null) {
                if (mode == Mode.CAST) {
                    ctx.error(SpellIssue.of("module.unknown", node.moduleId.toString()));
                }
                continue;
            }
            boolean traced = mode == Mode.CAST;
            try {
                List<SpellValue> inputs = new ArrayList<>();
                StringBuilder desc = traced ? new StringBuilder() : null;
                for (int port = 0; port < type.inputs(); port++) {
                    int conn = port < node.inputConnections.size()
                            ? node.inputConnections.get(port) : -1;
                    List<SpellValue> srcOutputs = conn >= 0 ? results.get(conn) : null;
                    SpellValue value = null;
                    if (srcOutputs != null && !srcOutputs.isEmpty()) {
                        // Порт приёмника выбирает выход источника: у модуля с
                        // одним выходом (а таких большинство) он читается в
                        // любой подключённый вход. Схема хранит только индекс
                        // узла-источника, а пару (узел, порт) формат сохранения
                        // не предусматривает — см. заметку в SpellGraph.validate.
                        value = srcOutputs.get(Math.min(port, srcOutputs.size() - 1));
                    } else if (!type.isOptional(port)) {
                        throw new SpellContext.SpellException("graph.missing_input",
                                SpellModules.shortName(type) + " вход " + (port + 1));
                    }
                    // необязательный вход без связи = null, модуль сам решает
                    inputs.add(value);
                    if (traced && value != null) {
                        if (desc.length() > 0) {
                            desc.append(" + ");
                        }
                        desc.append(value.describe().getString());
                    }
                }

                ctx.setModifiers(node.modifiers);
                ctx.setCurrentNode(node);
                List<SpellValue> outputs = new ArrayList<>();
                type.execute(ctx, inputs, outputs);
                results.put(idx, outputs);

                if (traced) {
                    ctx.trace(SpellModules.shortName(type)
                            + (desc.length() > 0 ? "(" + desc + ")" : "()")
                            + " = " + formatOutputs(outputs));
                }
            } catch (SpellContext.SpellException e) {
                // В тике брони битый узел просто пропускается: ошибки не
                // собираются, трасса не пишется, каст прерван не будет.
                if (traced) {
                    ctx.trace(SpellModules.shortName(type) + " ошибка: " + e.getKey());
                    ctx.error(e.toIssue());
                    errorNodes.add(idx);
                }
            } catch (RuntimeException e) {
                if (traced) {
                    ctx.trace(SpellModules.shortName(type) + " краш: " + e);
                    ctx.error(SpellIssue.of("cast.internal_error", e.getClass().getSimpleName()));
                    errorNodes.add(idx);
                }
            }
        }
        return errorNodes;
    }

    private static String formatOutputs(List<SpellValue> outputs) {
        if (outputs.isEmpty()) {
            return "∅";
        }
        StringBuilder sb = new StringBuilder();
        for (SpellValue v : outputs) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(v.describe().getString());
        }
        return sb.toString();
    }

    private static void report(ServerPlayer player, Result result, String messageKey) {
        if (player == null) {
            return;
        }

        // подробный лог в консоль — чтобы можно было понять, почему каст не сработал.
        // Успешные касты пишем в debug, иначе INFO засоряется каждый клик
        if (result.success()) {
            com.github.fireprime5.spellcraft.Spellcraft.LOGGER.debug(
                    "Cast by {}: success, abyss={}, nodes={}, trace={}",
                    player.getName().getString(), result.manaSpent(),
                    result.trace().size(), result.trace());
        } else {
            com.github.fireprime5.spellcraft.Spellcraft.LOGGER.info(
                    "Cast by {}: success={}, abyss={}, nodes={}, trace={}, issues={}",
                    player.getName().getString(), result.success(), result.manaSpent(),
                    result.trace().size(), result.trace(), result.issues());
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new com.github.fireprime5.spellcraft.network.SpellResultPayload(
                        result.trace(), result.stack(), result.issues(), result.success(),
                        result.manaSpent(), result.errorNodes()));

        com.github.fireprime5.spellcraft.network.AbyssEnergySyncPayload.sendTo(player);

        // в чат пишем только об ошибках и нехватке энергии бездны — успех ничем не шумит
        if (!result.success()) {
            var msg = net.minecraft.network.chat.Component.translatable(messageKey);
            player.sendSystemMessage(msg);
            player.displayClientMessage(msg, true);
            for (SpellIssue issue : result.issues()) {
                player.sendSystemMessage(issue.text());
                com.github.fireprime5.spellcraft.Spellcraft.LOGGER.warn("  cast issue: {} / {}",
                        issue.key(), issue.arg());
            }
        }
    }
}