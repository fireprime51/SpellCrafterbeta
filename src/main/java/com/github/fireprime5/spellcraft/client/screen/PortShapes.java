package com.github.fireprime5.spellcraft.client.screen;

import com.github.fireprime5.spellcraft.spell.PortType;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Рисование маркеров портов разной формы: круг (сущность), ромб (точка),
 * квадрат (вектор), треугольник (число), крест (предмет),
 * шестиугольник (место — точка вместе с измерением).
 */
public final class PortShapes {

    private PortShapes() {
    }

    /** cx, cy — центр маркера, size — его половина в пикселях. */
    public static void draw(GuiGraphics gfx, PortType.Shape shape, int cx, int cy, int size,
                            int color) {
        switch (shape) {
            case CIRCLE -> circle(gfx, cx, cy, size, color);
            case DIAMOND -> diamond(gfx, cx, cy, size, color);
            case TRIANGLE -> triangle(gfx, cx, cy, size, color);
            case CROSS -> cross(gfx, cx, cy, size, color);
            case HEXAGON -> hexagon(gfx, cx, cy, size, color);
            case SQUARE -> gfx.fill(cx - size, cy - size, cx + size + 1, cy + size + 1, color);
            case OUTLINE -> outline(gfx, cx, cy, size, color);
        }
    }

    private static void circle(GuiGraphics gfx, int cx, int cy, int size, int color) {
        int r2 = size * size + size / 2;
        for (int dy = -size; dy <= size; dy++) {
            for (int dx = -size; dx <= size; dx++) {
                if (dx * dx + dy * dy <= r2) {
                    gfx.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
                }
            }
        }
    }

    private static void diamond(GuiGraphics gfx, int cx, int cy, int size, int color) {
        for (int dy = -size; dy <= size; dy++) {
            int half = size - Math.abs(dy);
            if (half < 0) {
                continue;
            }
            gfx.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    private static void triangle(GuiGraphics gfx, int cx, int cy, int size, int color) {
        for (int dy = -size; dy <= size; dy++) {
            // треугольник: верхняя сторона узкая, нижняя широкая
            int half = size - (int) Math.round((dy + size) / 2.0);
            if (half < 0) {
                continue;
            }
            gfx.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    /**
     * Шестиугольник — единственная форма с двумя «пиками» по бокам.
     *
     * <p>Её форма выбрана так, чтобы читаться как «место»: у ромба (точка) нет
     * отличия от места, а у места по определению два конца — вот и два свечения
     * по краям вместо острия сверху.
     */
    private static void hexagon(GuiGraphics gfx, int cx, int cy, int size, int color) {
        for (int dy = -size; dy <= size; dy++) {
            // чем ниже (выше) центра, тем уже ряд
            int shrink = Math.max(0, Math.abs(dy) - size / 2);
            int half = size - shrink;
            gfx.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
        // боковые свечения — «два конца»
        gfx.fill(cx - size, cy, cx - size + 1, cy + 1, color);
        gfx.fill(cx + size, cy, cx + size + 1, cy + 1, color);
    }

    private static void cross(GuiGraphics gfx, int cx, int cy, int size, int color) {
        gfx.fill(cx - size, cy - 1, cx + size + 1, cy + 1, color);
        gfx.fill(cx - 1, cy - size, cx + 1, cy + size + 1, color);
    }

    /** Hollow square for wildcard ports: shape carries the "any" meaning. */
    private static void outline(GuiGraphics gfx, int cx, int cy, int size, int color) {
        int left = cx - size;
        int top = cy - size;
        int right = cx + size + 1;
        int bottom = cy + size + 1;
        gfx.fill(left, top, right, top + 1, color);
        gfx.fill(left, bottom - 1, right, bottom, color);
        gfx.fill(left, top + 1, left + 1, bottom - 1, color);
        gfx.fill(right - 1, top + 1, right, bottom - 1, color);
    }
}
