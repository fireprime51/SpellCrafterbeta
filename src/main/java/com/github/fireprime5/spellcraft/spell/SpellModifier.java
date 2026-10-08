package com.github.fireprime5.spellcraft.spell;

import net.minecraft.network.chat.Component;

/**
 * Модификатор модуля — дополнительное действие, которое можно включить галочкой
 * в меню модуля (ПКМ по модулю).
 *
 * Модификаторы делятся на конфликтные группы: нельзя включить два из одной
 * группы (шёлк и удача, по области и мгновенная добыча), но можно комбинировать
 * по одному из каждой группы. Каждый включённый модификатор стоит дополнительной
 * маны при касте.
 */
public record SpellModifier(String id, String labelKey, int learnCost, double learnChance,
                            int castCost, String conflictGroup, String descriptionKey) {

    public Component label() {
        return Component.translatable(labelKey);
    }

    public Component description() {
        return Component.translatable(descriptionKey);
    }

    public static SpellModifier of(String id, String labelKey, int learnCost, double learnChance,
                                   int castCost, String group, String descriptionKey) {
        return new SpellModifier(id, labelKey, learnCost, learnChance, castCost, group,
                descriptionKey);
    }

    /** Конфликтует ли с другим модификатором. */
    public boolean conflictsWith(SpellModifier other) {
        return conflictGroup != null && conflictGroup.equals(other.conflictGroup);
    }
}