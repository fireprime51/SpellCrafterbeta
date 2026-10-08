package com.github.fireprime5.spellcraft.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Отрисовка панели модуля: скошенные углы, вырез под шапку, штриховка.
 *
 * <p>Рисунок один и тот же для игры и для PNG-превью: {@link #draw} работает
 * через {@link Surface}, реализации которого живут в клиенте ({@link GuiSurface})
 * и в тестах ({@code AwtSurface}). Две копии рисунка разъезжаются при первой же
 * правке, и превью перестаёт быть правдой.
 *
 * <p>Всё собрано на {@code fill()}: {@code GuiGraphics} не умеет полигоны, а
 * скошенные углы и штриховки дешевле нарисовать горизонтальными отрезками.
 *
 * <p>Детализация по размеру: при штатном модуле примерно 36x23 места хватает
 * на скошенные углы, вырез под шапку и штриховку. Лестницы вентиляции и
 * пунктир появляются только на крупных модулях — иначе они превращаются в кашу
 * из соседних пикселей.
 *
 * <p>Попытка сделать гексагональную рамку (острые верх и низ, вертикальные
 * боковины) была и откачена: на реальном размере 36x23 гекс читается хуже,
 * а боковины съедали высоту, ради которой рисунок и делался.
 */
public final class ModuleFrame {

    private ModuleFrame() {
    }

    /** Минимальная поверхность, которой нужен модуль. */
    public interface Surface {
        void fill(int x1, int y1, int x2, int y2, int color);

        void fillGradient(int x1, int y1, int x2, int y2, int top, int bottom);

        void text(String value, int x, int y, int color);

        int textWidth(String value);

        int lineHeight();

        // ------------------------------------------------------------ общие
        // примитивы техно-стиля живут здесь, а не в UiKit, потому что ими
        // пользуются и игра, и превью: если рисунок описать дважды, через
        // GuiGraphics и через Graphics2D, он разъедется на первой правке.
        // default-методы дают одну реализацию на обе поверхности.

        /** Прямоугольник со срезанными углами, заливка. */
        default void chamfer(int x, int y, int w, int h, int cut, int color) {
            fill(x + cut, y, x + w - cut, y + h, color);
            for (int i = 0; i < cut; i++) {
                int inset = cut - i;
                fill(x + inset, y + i, x + w - inset, y + i + 1, color);
                fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
            }
        }

        /** Прямоугольник со срезанными углами, контур в 1 px. */
        default void chamferOutline(int x, int y, int w, int h, int cut, int color) {
            fill(x + cut, y, x + w - cut, y + 1, color);
            fill(x + cut, y + h - 1, x + w - cut, y + h, color);
            fill(x, y + cut, x + 1, y + h - cut, color);
            fill(x + w - 1, y + cut, x + w, y + h - cut, color);
            for (int i = 0; i < cut; i++) {
                fill(x + cut - i, y + i, x + cut - i + 1, y + i + 1, color);
                fill(x + w - 1 - cut + i, y + i, x + w - cut + i, y + i + 1, color);
                fill(x + cut - i, y + h - 1 - i, x + cut - i + 1, y + h - i, color);
                fill(x + w - 1 - cut + i, y + h - 1 - i, x + w - cut + i, y + h - i, color);
            }
        }

        /** Вырез-«защёлка» в верхней кромке, как на модуле. */
        default void notchBar(int x, int y, int w, int cut, int accent, boolean bright) {
            int notch = Math.max(6, Math.min(w / 2 - cut, w - 2 * cut - 2));
            if (notch < 6) {
                return;
            }
            int nx = x + (w - notch) / 2;
            fill(nx, y, nx + notch, y + 1, ModuleStyle.header(accent));
            fill(nx, y + 1, nx + notch, y + 4, withAlpha(accent, bright ? 190 : 110));
            fill(nx, y + 1, nx + notch, y + 2, withAlpha(accent, bright ? 235 : 140));
        }

        /** Уголок-метка: короткая засечка на ребре панели. */
        default void cornerTick(int x, int y, int length, int color) {
            fill(x, y, x + length, y + 1, color);
            fill(x, y, x + 1, y + length, color);
        }
    }

    /** Высота шапки: в неё вписан значок категории. */
    public static final int HEADER = 7;

    public static void draw(Surface surface, int x, int y, int w, int h, int accent,
                            String glyph, boolean focused) {
        if (w < 8 || h < 8) {
            return;
        }
        int cut = Math.clamp(Math.min(w, h) / 7, 2, 6);
        boolean roomy = h >= 20;
        boolean tall = h >= 28 && w >= 48;

        body(surface, x, y, w, h, cut, accent);
        if (tall) {
            ventLadder(surface, x, y, w, h, accent);
        }
        if (roomy && w >= 28) {
            hatch(surface, x, y, w, h, cut, accent);
        }
        frame(surface, x, y, w, h, cut, accent, focused);
        headerNotch(surface, x, y, w, cut, accent, focused);
        if (glyph != null && !glyph.isEmpty() && w > 16 && h > 16) {
            surface.text(glyph, x + cut + 1, y + 1, accent);
        }
    }

    /** Тело со скошенными углами: средняя полоса одним градиентом, углы — отрезками. */
    private static void body(Surface surface, int x, int y, int w, int h, int cut, int accent) {
        int top = ModuleStyle.bodyTop(accent);
        int bottom = ModuleStyle.bodyBottom(accent);
        surface.fillGradient(x, y + cut, x + w, y + h - cut, top, bottom);
        for (int i = 0; i < cut; i++) {
            int inset = cut - i;
            surface.fill(x + inset, y + i, x + w - inset, y + i + 1, top);
            surface.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, bottom);
        }
    }

    /** Рамка: четыре прямые стороны и четыре диагонали, 1 px. */
    private static void frame(Surface surface, int x, int y, int w, int h, int cut, int accent,
                              boolean focused) {
        int bright = focused ? accent : ModuleStyle.border(accent);
        surface.fill(x + cut, y, x + w - cut, y + 1, bright);
        surface.fill(x + cut, y + h - 1, x + w - cut, y + h, bright);
        surface.fill(x, y + cut, x + 1, y + h - cut, bright);
        surface.fill(x + w - 1, y + cut, x + w, y + h - cut, bright);
        for (int i = 0; i < cut; i++) {
            surface.fill(x + cut - i, y + i, x + cut - i + 1, y + i + 1, bright);
            surface.fill(x + w - 1 - cut + i, y + i, x + w - cut + i, y + i + 1, bright);
            surface.fill(x + cut - i, y + h - 1 - i, x + cut - i + 1, y + h - i, bright);
            surface.fill(x + w - 1 - cut + i, y + h - 1 - i, x + w - cut + i, y + h - i, bright);
        }
    }

    /** Вырез в верхней кромке под шапку: как защёлка на референсе. */
    private static void headerNotch(Surface surface, int x, int y, int w, int cut, int accent,
                                    boolean focused) {
        int notch = Math.max(6, Math.min(w / 2 - cut, w - 2 * cut - 2));
        if (notch < 6) {
            return;
        }
        int nx = x + (w - notch) / 2;
        surface.fill(nx, y, nx + notch, y + 1, ModuleStyle.header(accent));
        surface.fill(nx, y + 1, nx + notch, y + HEADER, withAlpha(accent, focused ? 190 : 120));
        surface.fill(nx, y + 1, nx + notch, y + 2, withAlpha(accent, 235));
    }

    /** Диагональная штриховка в левом нижнем углу. */
    private static void hatch(Surface surface, int x, int y, int w, int h, int cut, int accent) {
        int size = Math.min(8, Math.min(w - 8, h - HEADER - 6));
        if (size < 4) {
            return;
        }
        int color = withAlpha(accent, 200);
        int bx = x + cut + 1;
        int by = y + h - 3 - size;
        for (int i = 0; i < size; i++) {
            surface.fill(bx + i, by + i, bx + i + 1, by + i + 2, color);
        }
    }

    /** Лестница вентиляции на правой кромке — только на крупных модулях. */
    private static void ventLadder(Surface surface, int x, int y, int w, int h, int accent) {
        int color = withAlpha(accent, 150);
        int top = y + HEADER + 2;
        int bottom = y + h - 3;
        for (int ly = top; ly < bottom; ly += 3) {
            surface.fill(x + w - 4, ly, x + w - 2, ly + 1, color);
        }
    }

    private static int withAlpha(int argb, int alpha) {
        int a = Math.clamp(alpha, 0, 255);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /** Реализация поверх {@code GuiGraphics}. */
    public static final class GuiSurface implements Surface {

        private final GuiGraphics gfx;
        private final Font font;

        public GuiSurface(GuiGraphics gfx, Font font) {
            this.gfx = gfx;
            this.font = font;
        }

        @Override
        public void fill(int x1, int y1, int x2, int y2, int color) {
            gfx.fill(x1, y1, x2, y2, color);
        }

        @Override
        public void fillGradient(int x1, int y1, int x2, int y2, int top, int bottom) {
            gfx.fillGradient(x1, y1, x2, y2, top, bottom);
        }

        @Override
        public void text(String value, int x, int y, int color) {
            gfx.drawString(font, value, x, y, color, false);
        }

        @Override
        public int textWidth(String value) {
            return font.width(value);
        }

        @Override
        public int lineHeight() {
            return font.lineHeight;
        }
    }
}
