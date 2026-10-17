package com.github.fireprime5.spellcraft.armor;

import com.github.fireprime5.spellcraft.spell.SpellModules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Модульный нагрудник: броня со схемой модулей внутри.
 *
 * <p>Схема живёт в компоненте данных предмета, а не в сущности брони: нагрудник
 * можно положить в сундук, передать другому игроку и снять — схема едет вместе
 * с вещью. Хранение в сущности сломало бы всё из этого.
 *
 * <p>Защита обычная, ванильная. Модули не заменяют броню, а добавляют
 * поведение поверх: «когда ударили» — это про триггер, а не про прочность.
 */
public class ModularChestplateItem extends ArmorItem {

    /** Слот: нагрудник — только грудь. */
    public ModularChestplateItem(Properties properties) {
        super(net.minecraft.world.item.ArmorMaterials.IRON, Type.CHESTPLATE,
                properties.stacksTo(1));
    }

    /**
     * Правый клик открывает конструктор модулей.
     *
     * <p>Клиент только просит: схему читает сервер из настоящего стека, а не
     * из присланного пакета. Иначе игрок мог бы нарисовать в чужих данных.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            com.github.fireprime5.spellcraft.network.RequestArmorGraphPayload.send(hand);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),
                level.isClientSide);
    }

    /** Схема модулей, записанная в предмет. */
    public static ModularSchema schemaOf(ItemStack stack) {
        // SCHEMA — отложенный holder: сам тип компонента появляется только
        // после регистрации, поэтому берём его через get()
        return stack.getOrDefault(ModArmorComponents.SCHEMA.get(), ModularSchema.EMPTY);
    }

    public static void setSchema(ItemStack stack, ModularSchema schema) {
        stack.set(ModArmorComponents.SCHEMA.get(), schema);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        ModularSchema schema = schemaOf(stack);
        if (schema.isEmpty()) {
            // Никакой подсказки «ПКМ чтобы открыть конструктор»: правый клик по
            // предмету в инвентаре и так открывает конструктор, отдельная строка
            // только занимает место и обещает то, что видно и без неё.
            return;
        }
        tooltip.add(Component.translatable("gui.spellcraft.armor_modules", schema.size())
                .withStyle(ChatFormatting.AQUA));
        // список модулей виден сразу: игрок должен понимать, что у него на груди
        // и какое поведение он за это получил. Подпись — переводом, а не сырым
        // id: те же модули в конструкторе называются по-русски
        for (String id : schema.modules()) {
            var type = SpellModules.get(SpellModules.id(id));
            if (type == null) {
                // модуль из другой версии мода: не молчим, а говорим прямо
                tooltip.add(Component.literal(" · " + id).withStyle(ChatFormatting.DARK_RED));
                continue;
            }
            tooltip.add(Component.translatable(type.nameKey()).withStyle(ChatFormatting.GRAY));
        }
    }
}