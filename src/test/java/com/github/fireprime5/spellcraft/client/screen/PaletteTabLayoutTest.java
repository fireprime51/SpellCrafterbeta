package com.github.fireprime5.spellcraft.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Полоса вкладок палитры не должна вылезать за панель.
 *
 * <p>Ширина вкладок зависит от шрифта и языка, поэтому проверить её в
 * {@code ModulePalette} нельзя: там нужен живой {@code Font}. Здесь считается
 * та же арифметика на подставных метриках шрифта — этого достаточно, чтобы
 * поймать возврат бага с целочисленными константами в коде.
 *
 * <p>Сам баг был в том, что ширина считалась в двух местах — при отрисовке и при
 * hit-тесте — и обе копии складывали подписи без ограничения. На английском пять
 * вкладок влезали впритык, на русском последняя уезжала за правый край, и клик
 * по её хвосту закрывал палитру.
 */
class PaletteTabLayoutTest {

    /** Ширина панели и раскладки — как в {@code ModulePalette}. */
    private static final int WIDTH = 244;
    private static final int TAB_PAD = 5;
    private static final int TAB_GAP = 2;
    private static final int TAB_MIN_LABEL = 14;

    private static final String[] RU = {"Числа", "Сущности", "Действия", "Логика", "Эффекты"};
    private static final String[] EN = {"Numbers", "Entities", "Actions", "Logic", "Effects"};
    private static final String[] RU_SIX = {"Числа", "Сущности", "Действия", "Логика", "Эффекты",
            "Броня"};

    @Test
    void русскиеВкладкиПомещаютсяВПанель() {
        assertFits(RU, 6, "русские вкладки");
    }

    @Test
    void английскиеВкладкиПомещаютсяВПанель() {
        assertFits(EN, 5, "английские вкладки");
    }

    /** Задел на будущее: шестая категория не должна увести полосу за край. */
    @Test
    void шестьВкладокЕщёПомещаются() {
        assertFits(RU_SIX, 6, "шесть вкладок");
    }

    /**
     * Даже невозможный шрифт не должен оставить вкладку нулевой ширины.
     *
     * <p>Если ужимание съест подпись целиком, по вкладке станет нечем кликнуть,
     * а игрок решит, что категория сломалась.
     */
    @Test
    void подписиНеСжимаютсяВНичто() {
        FakeFont font = new FakeFont(40);
        int per = (WIDTH - TAB_PAD * 2 - TAB_GAP * (RU.length - 1)) / RU.length;
        for (String tab : RU) {
            String fitted = fit(font, tab, Math.max(1, per - 4));
            assertTrue(!fitted.isEmpty(), "подпись вкладки исчезла: " + tab);
        }
    }

    private static void assertFits(String[] labels, int charWidth, String what) {
        int used = widthOfTabs(labels, new FakeFont(charWidth));
        int available = WIDTH - TAB_PAD * 2;
        assertTrue(used <= available,
                what + " не помещаются: " + used + " > " + available);
    }

    /** Суммарная ширина полосы при раскладке из {@code ModulePalette}. */
    private static int widthOfTabs(String[] labels, FakeFont font) {
        int count = labels.length;
        int inner = WIDTH - TAB_PAD * 2 - TAB_GAP * (count - 1);
        int per = Math.max(1, inner / count);
        boolean tight = per < 40;

        int totalText = 0;
        for (String label : labels) {
            int limit = tight ? Math.max(TAB_MIN_LABEL, per - 4) : per - 8;
            totalText += font.width(fit(font, label, limit));
        }

        int extra = inner - totalText;
        int pad = Math.max(2, Math.min(6, extra / (2 * count)));
        int used = TAB_GAP * (count - 1);
        for (String label : labels) {
            int limit = tight ? Math.max(TAB_MIN_LABEL, per - 4) : per - 8;
            used += font.width(fit(font, label, limit)) + pad * 2;
        }
        return used;
    }

    /** Усечение подписи многоточием — как в {@code UiKit.fit}. */
    private static String fit(FakeFont font, String text, int limit) {
        if (limit <= 0) {
            return "";
        }
        if (font.width(text) <= limit) {
            return text;
        }
        int ellipsis = font.width("…");
        StringBuilder sb = new StringBuilder();
        int used = 0;
        for (char c : text.toCharArray()) {
            int step = font.width(String.valueOf(c));
            if (used + step > limit - ellipsis) {
                break;
            }
            sb.append(c);
            used += step;
        }
        return sb.append('…').toString();
    }

    /**
     * Подставной шрифт: все символы одной ширины.
     *
     * <p>Кириллица в ванильном шрифте шире латиницы, поэтому для проверки
     * берётся максимальная из двух — так тест верен и для русского, и для
     * английского, без ветки на язык.
     */
    private static final class FakeFont {
        private final int charWidth;

        FakeFont(int charWidth) {
            this.charWidth = charWidth;
        }

        int width(String text) {
            return text.length() * charWidth;
        }
    }
}
