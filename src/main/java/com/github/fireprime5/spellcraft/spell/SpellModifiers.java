package com.github.fireprime5.spellcraft.spell;

import java.util.List;

/**
 * Все модификаторы модулей.
 *
 * Группа "A": удача и шёлковое касание — взаимоисключающие.
 * Группа "B": по области и мгновенная добыча — взаимоисключающие.
 * Можно взять по одному из каждой группы: например «шёлк + по области».
 */
public final class SpellModifiers {

    private SpellModifiers() {
    }

    /** Добыча: дополнительные варианты одного модуля. */
    public static final SpellModifier FORTUNE = SpellModifier.of("fortune",
            "modifier.spellcraft.fortune", 180, 0.25, 12, "A", "modifier.spellcraft.fortune.desc");

    public static final SpellModifier SILK = SpellModifier.of("silk",
            "modifier.spellcraft.silk", 120, 0.35, 10, "A", "modifier.spellcraft.silk.desc");

    public static final SpellModifier AREA = SpellModifier.of("area",
            "modifier.spellcraft.area", 60, 0.50, 8, "B", "modifier.spellcraft.area.desc");

    public static final SpellModifier INSTANT = SpellModifier.of("instant",
            "modifier.spellcraft.instant", 240, 0.20, 20, "B", "modifier.spellcraft.instant.desc");

    // --- взрыв ---
    // группа "E1": что взрыв делает с блоками
    public static final SpellModifier NO_BLOCKS = SpellModifier.of("no_blocks",
            "modifier.spellcraft.no_blocks", 150, 0.30, 10, "E1", "modifier.spellcraft.no_blocks.desc");

    public static final SpellModifier WIDE = SpellModifier.of("wide",
            "modifier.spellcraft.wide", 160, 0.25, 18, "E1", "modifier.spellcraft.wide.desc");

    // группа "E2": что взрыв делает с сущностями
    public static final SpellModifier FIRE = SpellModifier.of("fire",
            "modifier.spellcraft.fire", 90, 0.45, 12, "E2", "modifier.spellcraft.fire.desc");

    public static final SpellModifier SHOVE = SpellModifier.of("shove",
            "modifier.spellcraft.shove", 80, 0.50, 8, "E2", "modifier.spellcraft.shove.desc");

    // независимый: усиление
    public static final SpellModifier POWERFUL = SpellModifier.of("powerful",
            "modifier.spellcraft.powerful", 220, 0.20, 30, null, "modifier.spellcraft.powerful.desc");

    public static final List<SpellModifier> MINING = List.of(SILK, FORTUNE, AREA, INSTANT);
    public static final List<SpellModifier> EXPLOSION =
            List.of(NO_BLOCKS, WIDE, FIRE, SHOVE, POWERFUL);

    public static final List<SpellModifier> ALL =
            java.util.stream.Stream.concat(MINING.stream(), EXPLOSION.stream()).toList();

    /** Все модификаторы для добычи. */
    public static List<SpellModifier> mining() {
        return MINING;
    }

    /** Все модификаторы для взрыва. */
    public static List<SpellModifier> explosion() {
        return EXPLOSION;
    }


    /** Насколько дорожает каждый следующий модификатор. */
    public static final int LEARN_STEP = 70;

    /**
     * Цена изучения модификатора для игрока.
     *
     * Чем больше модификаторов уже изучено, тем дороже каждый следующий:
     * это не даёт собрать весь набор в первые минуты игры.
     * Сервер и клиент считают цену одинаково.
     *
     * @param knownCount сколько модификаторов уже изучено
     */
    public static int learnCost(SpellModifier modifier, int knownCount) {
        int raw = modifier.learnCost() + LEARN_STEP * Math.max(0, knownCount);
        return Math.round(raw / 10f) * 10;
    }

    /** Сколько модификаторов уже изучено (для расчёта цены). */
    public static int knownCount(net.minecraft.world.entity.player.Player player) {
        return com.github.fireprime5.spellcraft.data.PlayerKnowledge.knownModifiers(player).size();
    }

    public static SpellModifier byId(String id) {
        for (SpellModifier modifier : ALL) {
            if (modifier.id().equals(id)) {
                return modifier;
            }
        }
        return null;
    }

    /** Можно ли включить этот модификатор вместе с уже включёнными. */
    public static boolean canEnable(SpellModifier modifier, java.util.Set<String> enabled) {
        for (String id : enabled) {
            SpellModifier other = byId(id);
            if (other != null && modifier.conflictsWith(other)) {
                return false;
            }
        }
        return true;
    }

    /** Суммарная цена каста всех включённых модификаторов. */
    public static int totalCastCost(java.util.Set<String> enabled) {
        int total = 0;
        for (String id : enabled) {
            SpellModifier modifier = byId(id);
            if (modifier != null) {
                total += modifier.castCost();
            }
        }
        return total;
    }
}