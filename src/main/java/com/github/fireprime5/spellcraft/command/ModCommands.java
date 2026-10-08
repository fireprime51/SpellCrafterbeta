package com.github.fireprime5.spellcraft.command;

import com.github.fireprime5.spellcraft.data.PlayerSpellStore;
import com.github.fireprime5.spellcraft.energy.ManaCapability;
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
 * /spellcraft cast|info|clear|mana|infmana|modules
 *
 * <p>{@code clear}, {@code mana}, {@code infmana} и {@code modules} меняют
 * состояние игрока, поэтому доступны только оператору (уровень прав 2). Иначе
 * любой игрок мог бы сам себе выдать ману, модули или обнулить заклинание.
 *
 * <p>{@code info} и {@code cast} только читают своё состояние, поэтому
 * доступны всем.
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
                .then(Commands.literal("mana")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> mana(ctx.getSource(), 1000)))
                .then(Commands.literal("infmana")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> infiniteMana(ctx.getSource())))
                .then(Commands.literal("modules")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> modules(ctx.getSource()))));
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
        SpellGraph graph = PlayerSpellStore.getCopy(player);
        var result = SpellExecutor.execute(graph, player.level(), player, player.getEyePosition());
        source.sendSuccess(() -> Component.literal("modules=" + graph.nodes.size()
                + ", cost=" + graph.totalManaCost()
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
        source.sendSuccess(() -> Component.literal("Заклинание очищено"), false);
        return 1;
    }

    private static int info(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SpellGraph graph = PlayerSpellStore.getCopy(player);
        source.sendSuccess(() -> Component.literal("Модулей: " + graph.nodes.size()
                + ", стоимость: " + graph.totalManaCost()
                + ", мана: " + ManaCapability.getMana(player)), false);
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

    private static int mana(CommandSourceStack source, int amount)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ManaCapability.setMana(player, amount);
        source.sendSuccess(() -> Component.literal("Мана: "
                + ManaCapability.getMana(player) + "/" + ManaCapability.capacity()), false);
        return 1;
    }

    /**
     * Переключает бесконечную ману: повторный вызов выключает её обратно.
     *
     * <p>Стоимость заклинаний перестаёт списываться совсем, поэтому режим
     * годится для отладки схем, но не для обычной игры. Проверка и списание
     * идут через {@link ManaCapability#has} и
     * {@link ManaCapability#consumeMana}, поэтому модули, которые берут ману
     * сами (мгновенная добыча по блокам, изучение модификаторов), тоже
     * становятся бесплатными — иначе режим обходился бы стороной.
     *
     * <p>Сама мана при этом не меняется: «Извлечение» продолжает работать как
     * раньше, а полоска показывает настоящее число.
     */
    private static int infiniteMana(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean enabled = ManaCapability.toggleInfinite(player);
        source.sendSuccess(() -> Component.translatable(enabled
                ? "message.spellcraft.infinite_mana_on"
                : "message.spellcraft.infinite_mana_off", ManaCapability.getMana(player)), true);
        // клиент рисует полоску по данным пакета, поэтому после переключения
        // он должен получить их заново, иначе полоса обновится только кастом
        com.github.fireprime5.spellcraft.network.ManaSyncPayload.sendTo(player);
        return enabled ? 1 : 0;
    }
}