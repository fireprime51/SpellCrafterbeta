package com.github.fireprime5.spellcraft.client.screen;

import com.github.fireprime5.spellcraft.spell.SpellModifier;
import com.github.fireprime5.spellcraft.spell.SpellModuleType;
import com.github.fireprime5.spellcraft.spell.SpellModifiers;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Меню модификаторов модуля (открывается по ПКМ по модулю на холсте).
 *
 * <p>Галочки у модификаторов: можно включить несколько сразу, но конфликтные
 * (шёлк/удача и по области/мгновенная добыча; не ломать блоки/широкий радиус
 * и поджиг/отбрасывание) одновременно нельзя.
 * Неизвестный модификатор покупается за ману со шансом.
 *
 * <p>Вся отрисовка на {@link GuiGraphics}: рамка, галочки, полоса прокрутки и
 * подпись под списком. Ширина подобрана так, чтобы самая длинная подпись и
 * максимальная цена изучения помещались в строку — иначе длинная строка
 * обрезалась по краю.
 */
public class ModifierMenu {

    public static final int W = 216;
    private static final int ROW_H = 18;
    private static final int TITLE_H = 12;
    private static final int FOOTER_H = 22;
    private static final int HINT_H = 10;
    private static final int MAX_ROWS = 6;
    /** Высота заголовка строки над галочкой. */
    private static final int BOX = 9;

    private final int x;
    private final int y;
    private final String title;
    private final List<SpellModifier> modifiers;
    private final Set<String> enabled;
    private final Set<String> knownModifiers;
    private final Consumer<Set<String>> onApply;
    private final Consumer<SpellModifier> onRequestLearn;
    private final Function<SpellModifier, Integer> learnPrice;
    private final IntFunction<String> inputHint;

    private int hoveredRow = -1;
    private int scroll;

    public ModifierMenu(int x, int y, String title, List<SpellModifier> modifiers,
                        Set<String> enabled, Set<String> knownModifiers,
                        Consumer<Set<String>> onApply,
                        Consumer<SpellModifier> onRequestLearn,
                        Function<SpellModifier, Integer> learnPrice,
                        IntFunction<String> inputHint) {
        this.x = x;
        this.y = y;
        this.title = title;
        this.modifiers = modifiers == null ? List.of() : modifiers;
        this.enabled = new LinkedHashSet<>(enabled);
        this.knownModifiers = knownModifiers;
        this.onApply = onApply;
        this.onRequestLearn = onRequestLearn;
        this.learnPrice = learnPrice == null ? modifier -> 0 : learnPrice;
        this.inputHint = inputHint == null ? port -> "" : inputHint;
    }

    /**
     * Высота меню под модуль: заголовок, строки, подсказка и подвал.
     *
     * <p>Нужна снаружи, чтобы экран поставил панель целиком в окно, а не
     * обрезал её нижнюю часть.
     */
    public static int heightFor(SpellModuleType type) {
        int rows = Math.min(type.modifiers().size(), MAX_ROWS);
        return TITLE_H + rows * ROW_H + HINT_H + FOOTER_H;
    }

    public int height() {
        return TITLE_H + visibleRows() * ROW_H
                + (hint().isEmpty() ? 0 : HINT_H) + FOOTER_H;
    }

    private String hint() {
        String hint = inputHint.apply(1);
        return hint == null ? "" : hint;
    }

    private int visibleRows() {
        return Math.min(modifiers.size(), MAX_ROWS);
    }

    private int maxScroll() {
        return Math.max(0, modifiers.size() - visibleRows());
    }

    /** Обновляет список изученных модификаторов (после синхронизации с сервером). */
    public void setKnown(Set<String> known) {
        knownModifiers.clear();
        knownModifiers.addAll(known);
    }

    public boolean inside(double mx, double my) {
        return mx >= x && mx < x + W && my >= y && my < y + height();
    }

    /**
     * Клик по строке: снять, включить или сначала снять конфликтующий.
     *
     * <p>Конфликтный вариант включить нельзя, поэтому клик по нему лишь снимает
     * конфликтующий — включить его придётся вторым кликом. Так игрок видит, что
     * произошло, вместо того чтобы галочка молча не отвечала.
     */
    public boolean mouseClicked(double mx, double my, int button) {
        if (!inside(mx, my)) {
            return false;
        }
        int listTop = y + TITLE_H;
        if (my < listTop) {
            return true;
        }
        int row = (int) ((my - listTop) / ROW_H) - scroll;
        if (row < 0 || row >= modifiers.size()) {
            return true;
        }
        SpellModifier modifier = modifiers.get(row);
        if (enabled.contains(modifier.id())) {
            enabled.remove(modifier.id());
            onApply.accept(new LinkedHashSet<>(enabled));
            return true;
        }
        if (!SpellModifiers.canEnable(modifier, enabled)) {
            // удалять из enabled прямо в цикле нельзя (LinkedHashSet даст
            // ConcurrentModificationException) — сначала собираем ids
            for (String id : new java.util.ArrayList<>(enabled)) {
                SpellModifier other = SpellModifiers.byId(id);
                if (other != null && modifier.conflictsWith(other)) {
                    enabled.remove(id);
                }
            }
            onApply.accept(new LinkedHashSet<>(enabled));
            return true;
        }
        if (knownModifiers.contains(modifier.id())) {
            enabled.add(modifier.id());
            onApply.accept(new LinkedHashSet<>(enabled));
        } else {
            onRequestLearn.accept(modifier);
        }
        return true;
    }

    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (!inside(mx, my)) {
            return false;
        }
        if (dy > 0) {
            scroll = Math.max(0, scroll - 1);
        } else if (dy < 0) {
            scroll = Math.min(maxScroll(), scroll + 1);
        }
        return true;
    }

    /** Подсказка к строке (null, если строка не выбрана). */
    public SpellModifier hoveredModifier() {
        return hoveredRow >= 0 && hoveredRow < modifiers.size()
                ? modifiers.get(hoveredRow)
                : null;
    }

    /**
     * Тултип с описанием варианта.
     *
     * <p>Рисуется только когда список прокручивается: при коротком списке
     * описание и так помещается в строке, а тултип лишь закрывал соседние
     * модули.
     */
    public void renderTooltip(GuiGraphics gfx, double mouseX, double mouseY) {
        SpellModifier modifier = hoveredModifier();
        if (modifier == null || maxScroll() == 0) {
            return;
        }
        Font font = UiKit.font();
        var lines = UiKit.wrap(font,
                Component.translatable("modifier." + modifier.id() + ".desc").getString(),
                W - 16);
        if (lines.isEmpty()) {
            return;
        }
        int w = 0;
        for (String line : lines) {
            w = Math.max(w, font.width(line));
        }
        int h = lines.size() * 10 + 8;
        int tx = (int) (x + W + 4 <= mouseX + 4 ? mouseX + 4 : mouseX - w - 8);
        int ty = (int) Math.min(mouseY, y + height() - h);
        UiKit.panel(gfx, tx, ty, w + 8, h, UiKit.PANEL_LIGHT, UiKit.ACCENT);
        for (int i = 0; i < lines.size(); i++) {
            gfx.drawString(font, lines.get(i), tx + 4, ty + 4 + i * 10, UiKit.TEXT, false);
        }
    }

    public void render(GuiGraphics gfx, double mouseX, double mouseY) {
        Font font = UiKit.font();
        int h = height();
        UiKit.panel(gfx, x, y, W, h, UiKit.PANEL, UiKit.ACCENT);
        gfx.fill(x + 1, y + 1, x + W - 1, y + TITLE_H, UiKit.PANEL_LIGHT);
        gfx.drawString(font, UiKit.fit(font, title, W - 10), x + 5, y + 3, UiKit.TEXT, false);

        int listTop = y + TITLE_H;
        int rows = visibleRows();
        int listH = rows * ROW_H;

        // полоса прокрутки
        if (maxScroll() > 0) {
            int trackH = listH;
            gfx.fill(x + W - 6, listTop, x + W - 4, listTop + trackH, UiKit.PANEL_DARK);
            int knobH = Math.max(12, trackH * rows / modifiers.size());
            int knobY = listTop + (trackH - knobH) * scroll / maxScroll();
            gfx.fill(x + W - 6, knobY, x + W - 4, knobY + knobH, UiKit.ACCENT);
        }

        hoveredRow = -1;
        for (int i = scroll; i < Math.min(modifiers.size(), scroll + rows); i++) {
            SpellModifier modifier = modifiers.get(i);
            int ry = listTop + (i - scroll) * ROW_H;
            if (UiKit.inside(mouseX, mouseY, x + 3, ry, W - 10, ROW_H - 1)) {
                hoveredRow = i;
                gfx.fill(x + 3, ry, x + W - 9, ry + ROW_H - 1, UiKit.PANEL_LIGHT);
            }
            boolean on = enabled.contains(modifier.id());
            boolean known = knownModifiers.contains(modifier.id());
            toggle(gfx, on, known, x + 7, ry + (ROW_H - BOX) / 2);

            int color = on ? UiKit.TEXT : known ? UiKit.TEXT_DIM : UiKit.WARN;
            gfx.drawString(font, UiKit.fit(font, modifier.label().getString(), W - 96),
                    x + 21, ry + 5, color, false);

            // цена: за каст, а если не изучено — цена изучения
            String price = price(modifier, on, known);
            if (!price.isEmpty()) {
                gfx.drawString(font, price, x + W - 14 - font.width(price), ry + 5,
                        on ? UiKit.WARN : UiKit.TEXT_DIM, false);
            }
        }

        // подвал: подсказка про второй вход и постоянная справка
        String inputHintText = hint();
        int footerTop = y + h - FOOTER_H;
        if (!inputHintText.isEmpty()) {
            gfx.fill(x + 3, footerTop - HINT_H, x + W - 3, footerTop, UiKit.PANEL_DARK);
            for (String line : UiKit.wrap(font, inputHintText, W - 12)) {
                gfx.drawString(font, line, x + 7, footerTop - HINT_H + 2, UiKit.WARN, false);
                break;
            }
        }
        gfx.fill(x + 3, footerTop, x + W - 3, y + h - 3, UiKit.PANEL_DARK);
        // справка длиннее строки, поэтому переносится на две строки
        List<String> lines = UiKit.wrap(font,
                Component.translatable("modifier.spellcraft.hint").getString(), W - 12);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            gfx.drawString(font, lines.get(i), x + 7, footerTop + 3 + i * 10, UiKit.TEXT_DIM,
                    false);
        }
    }

    /**
     * Галочка в трёх состояниях: включён, выключен, «не изучен» (замочек).
     *
     * <p>Собственный рисунок, а не {@code CheckBox}: у модификатора три
     * состояния, а у галочки их два, и цена изучения должна быть видна без
     * наведения.
     */
    private void toggle(GuiGraphics gfx, boolean on, boolean known, int x, int y) {
        gfx.fill(x, y, x + BOX, y + BOX, on ? UiKit.ACCENT : UiKit.PANEL_DARK);
        gfx.renderOutline(x, y, BOX, BOX, UiKit.BORDER_LIGHT);
        if (on) {
            // галочка: короткий диагональный штрих и длинный
            gfx.fill(x + 2, y + 5, x + 4, y + 7, UiKit.PANEL_DARK);
            gfx.fill(x + 4, y + 2, x + 7, y + 4, UiKit.PANEL_DARK);
        } else if (!known) {
            // замочек: корпус и дужка
            gfx.fill(x + 2, y + 4, x + 7, y + 8, UiKit.BORDER_LIGHT);
            gfx.fill(x + 3, y + 2, x + 5, y + 4, UiKit.BORDER_LIGHT);
            gfx.fill(x + 5, y + 2, x + 7, y + 4, UiKit.BORDER_LIGHT);
        }
    }

    /** Цена: за каст, а если не изучено — цена изучения. */
    private String price(SpellModifier modifier, boolean on, boolean known) {
        if (on) {
            return Component.translatable("modifier.spellcraft.cast_cost",
                    modifier.castCost()).getString();
        }
        if (known) {
            return "";
        }
        return Component.translatable("modifier.spellcraft.learn_cost",
                learnPrice.apply(modifier)).getString();
    }
}