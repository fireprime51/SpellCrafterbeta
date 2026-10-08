package com.github.fireprime5.spellcraft.palette;

import com.github.fireprime5.spellcraft.spell.PortType;
import com.github.fireprime5.spellcraft.spell.SpellModuleType;

/**
 * Палитра мода — единственный набор цветов для текстур, блоков и интерфейса.
 *
 * <p>Источник правды — {@code tools/models/palette.json}; эти константы его
 * зеркало, а {@code PaletteTest} падает, если они разошлись. Править нужно json,
 * потом запускать {@code tools/models/generate_models.py}.
 *
 * <p>Логика выбора:
 *
 * <ul>
 *   <li><b>Каркас — холодный.</b> Панели, рамки, сетка и порты сидят в
 *       диапазоне 190–240 по hue и низкой насыщенности, поэтому интерфейс
 *       не спорит сам с собой.</li>
 *   <li><b>Категории модулей — тёплая шкала.</b> Пять оттенков от бледного
 *       янтаря к медному. Это ровно то, что делает референс: тёплым помечено
 *       содержимое, холодным — оболочка.</li>
 *   <li><b>Ошибка — пурпур.</b> {@link #ERROR} специально вне тёплой шкалы:
 *       медный {@link #CATEGORY_NETWORK} и оранжевый {@link #CATEGORY_LOGIC}
 *       слишком близки к красному, и сломанный узел нельзя спутать с рабочим.</li>
 * </ul>
 *
 * <p>Шкала категорий и цвет ошибки меняются в игре клавишей, поэтому это
 * единственная изменяемая часть палитры. Константы ниже -- значения по
 * умолчанию (вариант {@code current}), и именно их сверяет {@code PaletteTest}
 * с json.
 *
 * <p>Класс намеренно без единого импорта net.minecraft: его читает и клиент, и
 * генератор текстур. Загрузку вариантов из json делает клиентский
 * {@code PaletteCatalog}, чтобы на сервере файл не читался.
 */
public final class Palette {

    private Palette() {
    }

    // каркас
    public static final int BG = 0xFF0A0D13;
    public static final int PANEL = 0xFF161B24;
    public static final int PANEL_DARK = 0xFF0F131A;
    public static final int PANEL_LIGHT = 0xFF222834;
    public static final int BORDER = 0xFF38424F;
    public static final int BORDER_LIGHT = 0xFF66748A;
    public static final int GRID = 0xFF232B36;
    /** Холодный основной акцент: подсветка, активный порт, свечение рамок. */
    public static final int ACCENT = 0xFF6FE3FF;
    public static final int ACCENT_DIM = 0xFF2C7A93;
    public static final int TEXT = 0xFFE6EEF6;
    public static final int TEXT_DIM = 0xFF9DAEC0;
    public static final int WARN = 0xFFFFC266;
    /** Пурпурный, намеренно вне тёплой шкалы категорий. */
    public static final int ERROR = 0xFFFF5C9E;
    public static final int OK = 0xFF6BE3A8;
    public static final int SLOT_BG = 0xFF1B202A;

    // категории модулей: тёплая шкала
    public static final int CATEGORY_NUMBERS = 0xFFFFDC96;
    public static final int CATEGORY_ENTITIES = 0xFFFFC266;
    public static final int CATEGORY_ACTIONS = 0xFFFFA352;
    public static final int CATEGORY_LOGIC = 0xFFF8834C;
    public static final int CATEGORY_NETWORK = 0xFFE86A52;

    // порты: холодная шкала, смысл несёт форма маркера, а не цвет
    public static final int PORT_NUMBER = 0xFF7FD4F5;
    public static final int PORT_ENTITY = 0xFF6FE3C0;
    public static final int PORT_POINT = 0xFF5FB8F0;
    public static final int PORT_VECTOR = 0xFF8C9BF0;
    public static final int PORT_ITEM = 0xFFA8D8E8;
    public static final int PORT_ANY = 0xFFB4C4D4;


    /**
     * Текущая шкала категорий и цвет ошибки.
     *
     * <p>Порядок совпадает с {@link SpellModuleType.Category#ordinal()}.
     * Значения меняются только через {@link #setCategories(int[], int)}.
     */
    private static volatile int[] categories = {
            CATEGORY_NUMBERS, CATEGORY_ENTITIES, CATEGORY_ACTIONS,
            CATEGORY_LOGIC, CATEGORY_NETWORK,
    };

    private static volatile int errorColor = ERROR;

    /** Цвет категории модуля. */
    public static int category(SpellModuleType.Category category) {
        int[] ramp = categories;
        return ramp[category.ordinal() % ramp.length];
    }

    /** Цвет ошибки: часть шкалы, поэтому меняется вместе с ней. */
    public static int error() {
        return errorColor;
    }

    /** Текущая шкала, копия. */
    public static int[] categories() {
        return categories.clone();
    }

    /**
     * Ставит шкалу категорий и цвет ошибки.
     *
     * @param ramp по одному цвету на {@link SpellModuleType.Category}, в порядке
     *             {@code ordinal()}; лишние игнорируются, недостающие добираются
     *             последним, чтобы кривой json не ронял отрисовку
     */
    public static void setCategories(int[] ramp, int error) {
        int count = SpellModuleType.Category.values().length;
        int[] copy = new int[count];
        int fill = ramp.length == 0 ? ERROR : ramp[Math.min(ramp.length, count) - 1];
        for (int i = 0; i < count; i++) {
            copy[i] = i < ramp.length ? ramp[i] : fill;
        }
        categories = copy;
        errorColor = error;
    }

    /** Возвращает шкалу по умолчанию: значение из json, вариант current. */
    public static void reset() {
        setCategories(categories(), ERROR);
        categories = new int[]{CATEGORY_NUMBERS, CATEGORY_ENTITIES, CATEGORY_ACTIONS,
                CATEGORY_LOGIC, CATEGORY_NETWORK};
        errorColor = ERROR;
    }

    /**
     * Строит шкалу из одного оттенка.
     *
     * <p>То, о чём просил мин: задать один цвет -- получить всю шкалу. Оттенок
     * гуляет на пару градусов, светлота падает монотонно, насыщенность
     * постоянная: иначе пять категорий получатся пятью разными цветами и
     * перестанут читаться как одна система.
     *
     * <p>Ошибка берётся на 150 градусов от базы, поэтому зазор до любой
     * категории не меньше 140 градусов -- с запасом больше, чем требует
     * проверка в {@code PaletteTest}.
     *
     * @param hue        базовый оттенок, градусы 0..360 (0 = красный)
     * @param saturation насыщенность 0..1
     * @param lightness  светлота середины шкалы 0..1
     */
    public static int[] rampFromHue(float hue, float saturation, float lightness) {
        int count = SpellModuleType.Category.values().length;
        int[] ramp = new int[count];
        float spread = 6f;
        for (int i = 0; i < count; i++) {
            float t = count == 1 ? 0.5f : i / (float) (count - 1);
            float step = hue + (t - 0.5f) * 2f * spread;
            // от светлого к тёмному, но не в чёрный: на почти чёрном теле
            // модуля самый тёмный кадр не читается
            float light = Math.clamp(lightness + (0.5f - t) * 0.34f, 0.42f, 0.9f);
            ramp[i] = 0xFF000000 | hsl(step, saturation, light);
        }
        return ramp;
    }

    /** Цвет ошибки, согласованный с {@link #rampFromHue}. */
    public static int errorFromHue(float hue) {
        return 0xFF000000 | hsl(hue + 150f, 0.82f, 0.62f);
    }

    /** hue 0..360, s 0..1, l 0..1 -> 0xAARRGGBB. */
    public static int argb(float hue, float saturation, float lightness) {
        return 0xFF000000 | hsl(hue, saturation, lightness);
    }

    private static int hsl(float hue, float saturation, float lightness) {
        float h = ((hue % 360f) + 360f) % 360f / 60f;
        float c = (1f - Math.abs(2f * lightness - 1f)) * saturation;
        float x = c * (1f - Math.abs(h % 2f - 1f));
        int r = 0;
        int g = 0;
        int b = 0;
        if (h < 1) {
            r = Math.round(c * 255);
            g = Math.round(x * 255);
        } else if (h < 2) {
            r = Math.round(x * 255);
            g = Math.round(c * 255);
        } else if (h < 3) {
            g = Math.round(c * 255);
            b = Math.round(x * 255);
        } else if (h < 4) {
            g = Math.round(x * 255);
            b = Math.round(c * 255);
        } else if (h < 5) {
            r = Math.round(x * 255);
            b = Math.round(c * 255);
        } else {
            r = Math.round(c * 255);
            b = Math.round(x * 255);
        }
        float m = lightness - c / 2f;
        int rr = Math.clamp(Math.round((r / 255f + m) * 255f), 0, 255);
        int gg = Math.clamp(Math.round((g / 255f + m) * 255f), 0, 255);
        int bb = Math.clamp(Math.round((b / 255f + m) * 255f), 0, 255);
        return rr << 16 | gg << 8 | bb;
    }

    /** Цвет типа порта. */
    public static int port(PortType type) {
        return switch (type) {
            case NUMBER -> PORT_NUMBER;
            case ENTITY -> PORT_ENTITY;
            case POINT -> PORT_POINT;
            case VECTOR -> PORT_VECTOR;
            case ITEM -> PORT_ITEM;
            case ANY -> PORT_ANY;
        };
    }

    /** RGB без альфы — так палитра и хранится в json. */
    public static int rgb(int argb) {
        return argb & 0x00FFFFFF;
    }
}