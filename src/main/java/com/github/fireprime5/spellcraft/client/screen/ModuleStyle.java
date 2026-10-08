package com.github.fireprime5.spellcraft.client.screen;

import com.github.fireprime5.spellcraft.palette.Palette;
import com.github.fireprime5.spellcraft.spell.PortType;
import com.github.fireprime5.spellcraft.spell.SpellModuleType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Внешний вид модулей на холсте и в палитре.
 *
 * <p>Вынесено отдельно, чтобы холст в игре и превью в PNG рисовали одно и то
 * же: раньше вид модуля был размазан по {@code ProgramCanvas}, и «приукрасить»
 * его можно было только запустив игру.
 */
public final class ModuleStyle {

    /** Радиус скругления модуля. */
    public static final int RADIUS = 4;
    /** Высота шапки с цветом категории. */
    public static final int HEADER = 7;

    /** Значок категории — рисуется в углу модуля и в списке палитры. */
    private static final Map<SpellModuleType.Category, String> GLYPHS =
            new EnumMap<>(SpellModuleType.Category.class);

    static {
        GLYPHS.put(SpellModuleType.Category.NUMBERS, "#");
        GLYPHS.put(SpellModuleType.Category.ENTITIES, "@");
        GLYPHS.put(SpellModuleType.Category.ACTIONS, "!");
        GLYPHS.put(SpellModuleType.Category.LOGIC, "?");
        GLYPHS.put(SpellModuleType.Category.NETWORK, "⇄");
    }

    private ModuleStyle() {
    }

    public static String glyph(SpellModuleType.Category category) {
        return GLYPHS.getOrDefault(category, "*");
    }

    public static String glyph(SpellModuleType type) {
        return glyph(type.category());
    }

    /**
     * Цвет тела модуля.
     *
     * <p>Почти чёрный с лёгким тонированием категорией: держать 28% цвета
     * нельзя, тело становится грязно-красным и тонкая яркая рамка в нём
     * тонет. Референс сделан наоборот -- чёрный фон, светятся только рамка,
     * вырез под шапку и штриховка. Поэтому здесь максимум 14% цвета, а
     * читаемость категории несёт рамка.
     */
    public static int bodyTop(int categoryColor) {
        return mix(categoryColor, Palette.BG, 0.88f);
    }

    public static int bodyBottom(int categoryColor) {
        return mix(categoryColor, Palette.BG, 0.94f);
    }

    /** Цвет шапки: категория остаётся читаемой, но не кричит. */
    public static int header(int categoryColor) {
        return mix(categoryColor, 0xFF1B1B24, 0.25f);
    }

    /** Рамка модуля: полная яркость категории, приглушать её нельзя. */
    public static int border(int categoryColor) {
        return categoryColor;
    }

    /** Смешивание ARGB. */
    public static int mix(int argb, int other, float shareOfOther) {
        float t = Math.clamp(shareOfOther, 0f, 1f);
        int a = Math.round(((argb >>> 24) & 0xFF) * (1 - t) + ((other >>> 24) & 0xFF) * t);
        int r = Math.round(((argb >> 16) & 0xFF) * (1 - t) + ((other >> 16) & 0xFF) * t);
        int g = Math.round(((argb >> 8) & 0xFF) * (1 - t) + ((other >> 8) & 0xFF) * t);
        int b = Math.round((argb & 0xFF) * (1 - t) + (other & 0xFF) * t);
        return a << 24 | r << 16 | g << 8 | b;
    }

    /** Затемнение цвета порта для обводки: иначе маркер теряется на любом фоне. */
    public static int portOutline(PortType type) {
        return mix(type.color(), 0xFF000000, 0.55f);
    }
}