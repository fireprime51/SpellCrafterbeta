package com.github.fireprime5.spellcraft.client.screen;

import com.github.fireprime5.spellcraft.spell.PortType;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Рисование маркеров портов разной формы: круг (сущность), ромб (точка),
 * квадрат (вектор), треугольник (число), крест (предмет).
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
            case SQUARE -> gfx.fill(cx - size, cy - size, cx + size + 1, cy + size + 1, color);
        }
    }

    private static void circle(GuiGraphics gfx, int cx, int cy, int size, int color) {
        int r2 = size * size + size / 2;
        gfx.fill(cx - size, cy - size, cx + size + 1, cy + size + 1, color & 0x00FFFFFF);
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

    private static void cross(GuiGraphics gfx, int cx, int cy, int size, int color) {
        gfx.fill(cx - size, cy - 1, cx + size + 1, cy + 1, color);
        gfx.fill(cx - 1, cy - size, cx + 1, cy + size + 1, color);
    }
}