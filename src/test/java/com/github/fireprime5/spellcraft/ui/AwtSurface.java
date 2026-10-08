package com.github.fireprime5.spellcraft.ui;

import com.github.fireprime5.spellcraft.client.screen.ModuleFrame;

import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;

/**
 * Реализация {@link ModuleFrame.Surface} на {@code Graphics2D}.
 *
 * <p>Нужна превью-тестам: {@code GuiGraphics} в тестах не создаётся, поэтому
 * рисунок модуля приходится повторять на AWT. Общий адаптер стоит рядом с
 * игровым не просто так — если рисунок разъедется, это будет видно на обоих
 * превью сразу.
 */
public final class AwtSurface implements ModuleFrame.Surface {

    private final Graphics2D g;

    public AwtSurface(Graphics2D g) {
        this.g = g;
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int color) {
        g.setColor(new Color(color, true));
        g.fillRect(x1, y1, Math.max(1, x2 - x1), Math.max(1, y2 - y1));
    }

    @Override
    public void fillGradient(int x1, int y1, int x2, int y2, int top, int bottom) {
        g.setPaint(new GradientPaint(x1, y1, new Color(top, true), x1, y2,
                new Color(bottom, true)));
        g.fillRect(x1, y1, x2 - x1, y2 - y1);
    }

    @Override
    public void text(String value, int x, int y, int color) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
        g.setColor(new Color(color, true));
        g.drawString(value, x, y);
    }

    @Override
    public int textWidth(String value) {
        return g.getFontMetrics(new Font(Font.SANS_SERIF, Font.BOLD, 9)).stringWidth(value);
    }

    @Override
    public int lineHeight() {
        return 10;
    }
}