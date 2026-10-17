package com.github.fireprime5.spellcraft.client.screen;

import com.github.fireprime5.spellcraft.config.SpellConfig;
import com.github.fireprime5.spellcraft.palette.Palette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Набор примитивов отрисовки в стиле Upload Labs / Psi.
 *
 * Прозрачность панелей настраивается конфигом (uiOpacity): 0 — видно игру
 * насквозь, 1 — полностью непрозрачные панели. Цвета хранятся в mutable
 * полях и пересчитываются через applyOpacity(), поэтому все виджеты
 * используют их без единой правки.
 */
public final class UiKit {

    private UiKit() {
    }

    // базовые цвета берутся из палитры мода, а не живут здесь своими числами:
    // раньше нейтрали UiKit, цвета портов и цвета блоков расходились, и мод
    // выглядел разноцветным при совершенно правильном коде
    private static final int BG_BASE = Palette.rgb(Palette.BG);
    private static final int PANEL_BASE = Palette.rgb(Palette.PANEL);
    private static final int PANEL_DARK_BASE = Palette.rgb(Palette.PANEL_DARK);
    private static final int PANEL_LIGHT_BASE = Palette.rgb(Palette.PANEL_LIGHT);
    private static final int BORDER_BASE = Palette.rgb(Palette.BORDER);
    private static final int BORDER_LIGHT_BASE = Palette.rgb(Palette.BORDER_LIGHT);
    private static final int GRID_BASE = Palette.rgb(Palette.GRID);

    public static int BG;
    public static int PANEL;
    public static int PANEL_DARK;
    public static int PANEL_LIGHT;
    public static int BORDER;
    public static int BORDER_LIGHT;
    public static int GRID;

    // текст и акценты — всегда читаемые
    public static final int ACCENT = Palette.ACCENT;
    public static final int ACCENT_DIM = Palette.ACCENT_DIM;
    public static final int TEXT = Palette.TEXT;
    public static final int TEXT_DIM = Palette.TEXT_DIM;
    public static final int WARN = Palette.WARN;
    // цвет ошибки входит в переключаемую шкалу, поэтому это метод, а не константа
    public static int error() {
        return Palette.error();
    }
    public static final int OK = Palette.OK;
    public static final int PORT_IN = Palette.PORT_ANY;
    public static final int PORT_OUT = Palette.PORT_ITEM;
    public static final int HOVER = 0xFF4E4E68;
    /** Фон слота механизма. */
    public static final int SLOT_BG = Palette.SLOT_BG;
    public static final int OVERLAY = 0x26000000;

    /** Минимальный размер интерфейса, ниже этого окна не сжимается. */
    public static final int MIN_W = 420;
    public static final int MIN_H = 230;

    static {
        applyOpacity(SpellConfig.get().uiOpacity);
    }

    /** Пересчитывает прозрачность панелей. alpha: 0..1 */
    public static void applyOpacity(float alpha) {
        float a = Math.clamp(alpha, 0f, 1f);
        // текст/рамки не должны совсем пропадать, поэтому им минимум 45%
        float aa = 0.45f + 0.55f * a;
        BG = withAlpha(BG_BASE, a * 0.55f);
        PANEL = withAlpha(PANEL_BASE, a);
        PANEL_DARK = withAlpha(PANEL_DARK_BASE, a * 0.92f);
        PANEL_LIGHT = withAlpha(PANEL_LIGHT_BASE, a);
        BORDER = withAlpha(BORDER_BASE, a * 0.7f + 0.3f);
        BORDER_LIGHT = withAlpha(BORDER_LIGHT_BASE, aa);
        GRID = withAlpha(GRID_BASE, a * 0.6f + 0.25f);
    }

    private static int withAlpha(int rgb, float alpha) {
        int a = Math.clamp((int) Math.round(alpha * 255f), 0, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    /** Цвет блока модуля с учётом прозрачности (шапка остаётся заметной). */
    public static int moduleColor(int rgb) {
        return withAlpha(rgb, 0.55f + 0.45f * SpellConfig.get().uiOpacity);
    }

    /**
     * Рамка слота инвентаря: 18×18 с тёмной подложкой.
     *
     * <p>Своя, а не ванильный рендер: у мода своя панель и свои цвета, и
     * ванильная рамка на тёмном фоне выглядела бы чужой.
     */
    public static void slot(GuiGraphics gfx, int x, int y) {
        gfx.fill(x, y, x + 18, y + 18, SLOT_BG);
        gfx.fill(x, y, x + 18, y + 1, BORDER_LIGHT);
        gfx.fill(x, y, x + 1, y + 18, BORDER_LIGHT);
        gfx.fill(x + 17, y, x + 18, y + 18, BORDER_LIGHT);
        gfx.fill(x, y + 17, x + 18, y + 18, BORDER_LIGHT);
    }

    public static void panel(GuiGraphics gfx, int x, int y, int w, int h) {
        panel(gfx, x, y, w, h, PANEL, BORDER);
    }

    public static void panel(GuiGraphics gfx, int x, int y, int w, int h, int fill, int border) {
        gfx.fill(x, y, x + w, y + h, fill);
        gfx.fill(x, y, x + w, y + 1, border);
        gfx.fill(x, y + h - 1, x + w, y + h, border);
        gfx.fill(x, y, x + 1, y + h, border);
        gfx.fill(x + w - 1, y, x + w, y + h, border);
    }

    public static void header(GuiGraphics gfx, Font font, int x, int y, int w, String title) {
        gfx.fill(x, y, x + w, y + 12, PANEL_LIGHT);
        gfx.fill(x, y + 11, x + w, y + 12, BORDER_LIGHT);
        gfx.drawString(font, title, x + 5, y + 3, TEXT, false);
    }

    public static void text(GuiGraphics gfx, Font font, String s, int x, int y, int color) {
        gfx.drawString(font, s, x, y, color, false);
    }

    /** Обрезает строку под ширину с многоточием. */
    public static String fit(Font font, String s, int maxWidth) {
        if (font.width(s) <= maxWidth) {
            return s;
        }
        String out = s;
        while (out.length() > 1 && font.width(out + "…") > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "…";
    }

    /** Разбивает текст на строки по ширине. */
    public static List<String> wrap(Font font, String s, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (s == null || s.isEmpty()) {
            return lines;
        }
        StringBuilder current = new StringBuilder();
        for (String word : s.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (font.width(candidate) > maxWidth && !current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    public static void vGradient(GuiGraphics gfx, int x, int y, int w, int h, int top, int bottom) {
        gfx.fillGradient(x, y, x + w, y + h, top, bottom);
    }

    public static void tab(GuiGraphics gfx, Font font, int x, int y, int w, int h, String label,
                           boolean active, int accent) {
        int fill = active ? PANEL_LIGHT : PANEL_DARK;
        gfx.fill(x, y, x + w, y + h, fill);
        gfx.fill(x, y + h - 1, x + w, y + h, active ? accent : BORDER);
        gfx.fill(x, y, x + 1, y + h, active ? accent : BORDER);
        int color = active ? TEXT : TEXT_DIM;
        gfx.drawString(font, fit(font, label, w - 6),
                x + (w - font.width(fit(font, label, w - 6))) / 2 + 1,
                y + (h - font.lineHeight) / 2 + 1, color, false);
    }

    public static void scrollBar(GuiGraphics gfx, int x, int y, int h, float progress,
                                 float visibleFraction) {
        gfx.fill(x, y, x + 2, y + h, PANEL_DARK);
        int thumbH = Math.max(8, (int) (h * Math.clamp(visibleFraction, 0.1f, 1f)));
        int travel = Math.max(1, h - thumbH);
        int thumbY = y + (int) (travel * Math.clamp(progress, 0f, 1f));
        gfx.fill(x, thumbY, x + 2, thumbY + thumbH, BORDER_LIGHT);
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public static Font font() {
        return Minecraft.getInstance().font;
    }
}