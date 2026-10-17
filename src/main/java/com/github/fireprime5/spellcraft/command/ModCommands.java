package com.github.fireprime5.spellcraft.command;

import com.github.fireprime5.spellcraft.data.PlayerKnowledge;
import com.github.fireprime5.spellcraft.data.PlayerSpellStore;
import com.github.fireprime5.spellcraft.energy.AbyssEnergy;
import com.github.fireprime5.spellcraft.energy.AbyssPool;
import com.github.fireprime5.spellcraft.spell.SpellExecutor;
import com.github.fireprime5.spellcraft.spell.SpellGraph;
import com.github.fireprime5.spellcraft.spell.SpellIssue;
import com.github.fireprime5.spellcraft.spell.SpellModules;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Команды для отладки заклинаний:
 * /spellcraft cast|info|clear|abyss|infmana|modules|give
 *
 * <p>{@code clear}, {@code abyss}, {@code infmana} и {@code modules} меняют
 * состояние игрока, поэтому доступны только оператору (уровень прав 2). Иначе
 * любой игрок мог бы сам себе выдать энергию бездны, модули или обнулить заклинание.
 *
 * <p>{@code cast} тоже кастует по-настоящему — он меняет мир, а не читает
 * состояние — поэтому кулдаун гримуара он соблюдает и сам. Раньше команда была
 * доступна всем и не имела никакой паузы: заклинание можно было «закликать»
 * столько раз в секунду, сколько успевал клиент, в обход
 * {@code SpellbookItem.CAST_COOLDOWN_TICKS}.
 *
 * <p>{@code info} только читает своё состояние, поэтому доступен всем.
 */
public final class ModCommands {

    private ModCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spellcraft")
                .then(Commands.literal("cast").executes(ctx -> cast(ctx.getSource())))
                .then(Commands.literal("info").executes(ctx -> info(ctx.getSource())))
                .then(Commands.literal("clear")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> clear(ctx.getSource())))
                .then(Commands.literal("abyss")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> abyss(ctx.getSource(), 1000)))
                .then(Commands.literal("infmana")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> infiniteAbyss(ctx.getSource())))
                .then(Commands.literal("modules")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> modules(ctx.getSource())))
                .then(Commands.literal("give")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("module",
                                        com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                .executes(ctx -> give(ctx.getSource(),
                                        com.mojang.brigadier.arguments.StringArgumentType
                                                .getString(ctx, "module"))))));
    }

    /**
     * Выдаёт один модуль — по названию или по идентификатору.
     *
     * <p>Личные модули выдаются тоже, иначе их нельзя было бы проверить в игре:
     * выпавший «Резонанс» проверяется три минуты, а «Вечность» — один шанс на шесть.
     * Выдача личного идёт через {@link PlayerKnowledge#withUnique}, а не через
     * обычное изучение, потому что у игрока ровно один личный модуль: новая выдача
     * заменяет прежнего, иначе «Анализ себя» перестал бы иметь смысл.
     *
     * <p>После выдачи клиенту уходит синхронизация — иначе модуль открылся бы на
     * сервере, но в палитре не появился бы до перезахода: именно так выглядел баг с
     * личными модулями, записанными без пространства имён.
     */
    private static int give(CommandSourceStack source, String query)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ModuleLookup.Result found = ModuleLookup.resolve(query);
        if (found.found()) {
            var type = found.exact();
            boolean personal = type.isUnique();
            var knowledge = com.github.fireprime5.spellcraft.data.PlayerKnowledge.get(player);
            if (personal) {
                // личный модуль не в наборе личных — а это значит, что он не был
                // выпадан этому игроку, и «Анализ себя» ему нечего открывать
                com.github.fireprime5.spellcraft.data.PlayerKnowledge
                        .set(player, knowledge.withUnique(type.id().getPath()));
            }
            boolean learned = com.github.fireprime5.spellcraft.data.PlayerKnowledge
                    .learn(player, type.id());
            com.github.fireprime5.spellcraft.network.AbyssEnergySyncPayload.sendTo(player);
            source.sendSuccess(() -> Component.translatable(
                    learned ? "message.spellcraft.module_granted"
                            : "message.spellcraft.module_already_known",
                    Component.translatable(type.nameKey())), false);
            if (personal) {
                source.sendSuccess(() -> Component.translatable(
                        "message.spellcraft.unique_assigned",
                        Component.translatable(type.nameKey())), false);
            }
            return learned ? 1 : 0;
        }
        if (found.ambiguous()) {
            source.sendFailure(Component.translatable("message.spellcraft.module_ambiguous"));
            for (var type : found.candidates()) {
                source.sendFailure(Component.literal("  " + type.id().getPath() + " — "
                        + Component.translatable(type.nameKey()).getString()));
            }
            return 0;
        }
        source.sendFailure(Component.translatable("message.spellcraft.module_unknown", query));
        return 0;
    }

    /** Выдать игроку все зарегистрированные модули (для тестов и отладки). */
    private static int modules(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        int granted = com.github.fireprime5.spellcraft.data.PlayerKnowledge.learnAll(player);
        source.sendSuccess(() -> Component.translatable("message.spellcraft.modules_granted", granted),
                false);
        return granted;
    }

    private static int cast(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (player.getCooldowns().isOnCooldown(
                com.github.fireprime5.spellcraft.registry.ModItems.SPELLBOOK.get())) {
            source.sendFailure(net.minecraft.network.chat.Component.translatable(
                    "message.spellcraft.cast_cooldown"));
            return 0;
        }
        player.getCooldowns().addCooldown(
                com.github.fireprime5.spellcraft.registry.ModItems.SPELLBOOK.get(),
                com.github.fireprime5.spellcraft.item.SpellbookItem.CAST_COOLDOWN_TICKS);
        SpellGraph graph = PlayerSpellStore.getCopy(player);
        var result = SpellExecutor.execute(graph, player.level(), player, player.getEyePosition());
        source.sendSuccess(() -> Component.literal("modules=" + graph.nodes.size()
                + ", cost=" + graph.totalAbyssCost(AbyssEnergy.capacity())
                + ", success=" + result.success()), false);
        for (String line : result.trace()) {
            source.sendSuccess(() -> Component.literal("  " + line), false);
        }
        for (SpellIssue issue : result.issues()) {
            source.sendSuccess(() -> Component.literal("  ! " + issue.text().getString()), false);
        }
        return 1;
    }

    private static int clear(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerSpellStore.set(player, new SpellGraph());
        source.sendSuccess(() -> Component.translatable("message.spellcraft.cleared"), false);
        return 1;
    }

    private static int info(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SpellGraph graph = PlayerSpellStore.getCopy(player);
        source.sendSuccess(() -> Component.literal("Модулей: " + graph.nodes.size()
                + ", стоимость: " + graph.totalAbyssCost(AbyssEnergy.capacity())
                + ", энергия бездны: " + AbyssPool.total(player) + "/" + AbyssEnergy.capacityOf(player)), false);
        for (int idx = 0; idx < graph.nodes.size(); idx++) {
            final int i = idx;
            var node = graph.nodes.get(i);
            source.sendSuccess(() -> Component.literal("  #" + i + " "
                    + (SpellModules.get(node.moduleId) != null
                    ? SpellModules.shortName(SpellModules.get(node.moduleId)) : "unknown")
                    + " @ " + node.gridX + "," + node.gridY
                    + " входы=" + node.inputConnections), false);
        }
        for (SpellIssue issue : graph.validate()) {
            source.sendSuccess(() -> Component.literal("  ! " + issue.text().getString()), false);
        }
        return 1;
    }

    private static int abyss(CommandSourceStack source, int amount)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AbyssEnergy.set(player, amount);
        source.sendSuccess(() -> Component.translatable("message.spellcraft.mana_set",
                AbyssEnergy.get(player), AbyssEnergy.capacity()), false);
        return 1;
    }

    /**
     * Переключает бесконечную энергию бездны: повторный вызов выключает её обратно.
     *
     * <p>Стоимость заклинаний перестаёт списываться совсем, поэтому режим
     * годится для отладки схем, но не для обычной игры. Проверка и списание
     * идут через {@link AbyssEnergy#has} и
     * {@link AbyssEnergy#consume}, поэтому модули, которые берут энергию бездны
     * сами (мгновенная добыча по блокам, изучение модификаторов), тоже
     * становятся бесплатными — иначе режим обходился бы стороной.
     *
     * <p>Сама энергия бездны при этом не меняется: «Извлечение» продолжает работать как
     * раньше, а полоска показывает настоящее число.
     */
    private static int infiniteAbyss(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean enabled = AbyssEnergy.toggleInfinite(player);
        source.sendSuccess(() -> Component.translatable(enabled
                ? "message.spellcraft.infinite_mana_on"
                : "message.spellcraft.infinite_mana_off", AbyssEnergy.get(player)), true);
        // клиент рисует полоску по данным пакета, поэтому после переключения
        // он должен получить их заново, иначе полоса обновится только кастом
        com.github.fireprime5.spellcraft.network.AbyssEnergySyncPayload.sendTo(player);
        return enabled ? 1 : 0;
    }
}