package com.github.fireprime5.spellcraft.network;

import java.util.Locale;

/**
 * Вид линии: по виду она ищет соседей и берёт ресурс.
 *
 * <p>Энергия и бездна — разные валюты, и путать их в одном блоке нельзя: иначе
 * труба бездны начнёт тянуть Forge Energy, а провод — бездну, и игрок получит
 * сеть, которая работает через раз.
 *
 * <p>Цвет у энергетической линии свой (см. {@link NetworkLine}), а у бездны его
 * нет: две линии бездны в одном блоке — это одна и та же линия, поэтому лишних
 * слотов под неё не заводим.
 */
public enum LineKind {

    /** Провод: Forge Energy, цвет разделяет сети. */
    ENERGY,

    /** Труба бездны: энергия бездны, цветом не делится. */
    ABYSS;

    /** Имя для NBT и переводов: всегда строчное, всегда латиницей. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Линия по имени из NBT. Битое имя читается как {@link #ENERGY}. */
    public static LineKind byId(String id) {
        for (LineKind kind : values()) {
            if (kind.id().equals(id)) {
                return kind;
            }
        }
        return ENERGY;
    }
}
