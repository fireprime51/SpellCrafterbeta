package com.github.fireprime5.spellcraft.item;

import com.github.fireprime5.spellcraft.data.PlayerSpellStore;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Основной инструмент каста заклинаний — гримуар.
 * Shift + ПКМ: открыть построитель заклинаний.
 * ПКМ: применить сохранённое заклинание.
 */
public class SpellbookItem extends Item {

    /**
     * Задержка между кастами (в тиках). Без неё заклинание можно было бы
     * «закликать» десятки раз в секунду и ломать мир пакетами урона.
     */
    public static final int CAST_COOLDOWN_TICKS = 10;

    public SpellbookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.isShiftKeyDown()) {
            if (level.isClientSide) {
                // просто просим сервер прислать состояние и открыть билдер:
                // клиентский код не упоминается в общих классах
                com.github.fireprime5.spellcraft.network.RequestSpellGraphPayload.send();
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        if (!level.isClientSide) {
            if (player.getCooldowns().isOnCooldown(this)) {
                return InteractionResultHolder.fail(stack);
            }
            var graph = PlayerSpellStore.getCopy(player);
            com.github.fireprime5.spellcraft.spell.SpellExecutor.execute(graph, level, player,
                    player.getEyePosition());
            player.getCooldowns().addCooldown(this, CAST_COOLDOWN_TICKS);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<net.minecraft.network.chat.Component> tooltip,
                                net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(net.minecraft.network.chat.Component.translatable("spellbook.spellcraft.open")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(net.minecraft.network.chat.Component.translatable("spellbook.spellcraft.cast")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}