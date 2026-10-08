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

        int cost = graph.totalManaCost();
        if (!ctx.hasMana(cost)) {
            Result result = new Result(List.of(), List.of(),
                    List.of(SpellIssue.of("mana.insufficient", cost)), false, 0, List.of());
            report(player, result, "message.spellcraft.cast.no_mana");
            return result;
        }
        ctx.consumeMana(cost);
        ctx.spendMana(cost);

        List<Integer> order = graph.topologicalOrder(ctx.errors());
        if (order == null) {
            Result result = new Result(List.copyOf(ctx.trace()), List.of(),
                    List.copyOf(ctx.errors()), false, cost, List.of());
            report(player, result, "message.spellcraft.cast.failed");
            return result;
        }

        Map<Integer, List<SpellValue>> results = new HashMap<>();
        List<Integer> errorNodes = new ArrayList<>();

        for (int idx : order) {
            if (ctx.failed()) {
                break;
            }
            // модуль вроде «Выход» попросил прекратить каст: это не ошибка,
            // поэтому успех ниже считается по errors, а не по этому флагу
            if (ctx.halted()) {
                break;
            }
            SpellNode node = graph.nodes.get(idx);
            SpellModuleType type = SpellModules.get(node.moduleId);
            if (type == null) {
                ctx.error(SpellIssue.of("module.unknown", node.moduleId.toString()));
                continue;
            }
            try {
                List<SpellValue> inputs = new ArrayList<>();
                StringBuilder desc = new StringBuilder();
                for (int port = 0; port < type.inputs(); port++) {
                    int conn = port < node.inputConnections.size()
                            ? node.inputConnections.get(port) : -1;
                    List<SpellValue> srcOutputs = conn >= 0 ? results.get(conn) : null;
                    SpellValue value = null;
                    if (srcOutputs != null && !srcOutputs.isEmpty()) {
                        value = srcOutputs.get(Math.min(port, srcOutputs.size() - 1));
                    } else if (!type.isOptional(port)) {
                        throw new SpellContext.SpellException("graph.missing_input",
                                SpellModules.shortName(type) + " вход " + (port + 1));
                    }
                    // необязательный вход без связи = null, модуль сам решает
                    inputs.add(value);
                    if (value != null) {
                        if (desc.length() > 0) {
                            desc.append(" + ");
                        }
                        desc.append(value.describe().getString());
                    }
                }

                // значения, которые модуль берёт из входов, потребляются из стека:
                // в стеке остаётся только то, что никто не использовал
                for (SpellValue value : inputs) {
                    if (value != null && !ctx.stackIsEmpty()) {
                        ctx.pop();
                    }
                }

                ctx.setModifiers(node.modifiers);
                ctx.setCurrentNode(node);
                List<SpellValue> outputs = new ArrayList<>();
                type.execute(ctx, inputs, outputs);
                results.put(idx, outputs);

                ctx.trace(SpellModules.shortName(type)
                        + (desc.length() > 0 ? "(" + desc + ")" : "()")
                        + " = " + formatOutputs(outputs));

                // значения идут дальше по соединениям; в стек попадают только
                // явно через модули stack_*
                for (SpellValue v : outputs) {
                    ctx.push(v);
                }
            } catch (SpellContext.SpellException e) {
                ctx.trace(SpellModules.shortName(type) + " ошибка: " + e.getKey());
                ctx.error(e.toIssue());
                errorNodes.add(idx);
            } catch (RuntimeException e) {
                ctx.trace(SpellModules.shortName(type) + " краш: " + e);
                ctx.error(SpellIssue.of("cast.internal_error", e.getClass().getSimpleName()));
                errorNodes.add(idx);
            }
        }

        boolean success = !ctx.failed();
        if (ctx.halted()) {
            ctx.trace("cast stopped by exit");
        }
        // модули могут добирать ману прямо во время каста (например, мгновенная
        // добыча по блокам) — эти траты тоже нужно списать с игрока
        int extra = ctx.manaSpent() - cost;
        if (extra > 0) {
            ctx.consumeMana(extra);
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
                    "Cast by {}: success, mana={}, nodes={}, trace={}",
                    player.getName().getString(), result.manaSpent(),
                    result.trace().size(), result.trace());
        } else {
            com.github.fireprime5.spellcraft.Spellcraft.LOGGER.info(
                    "Cast by {}: success={}, mana={}, nodes={}, trace={}, issues={}",
                    player.getName().getString(), result.success(), result.manaSpent(),
                    result.trace().size(), result.trace(), result.issues());
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new com.github.fireprime5.spellcraft.network.SpellResultPayload(
                        result.trace(), result.stack(), result.issues(), result.success(),
                        result.manaSpent(), result.errorNodes()));

        com.github.fireprime5.spellcraft.network.ManaSyncPayload.sendTo(player);

        // в чат пишем только об ошибках и нехватке маны — успех ничем не шумит
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