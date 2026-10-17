package com.github.fireprime5.spellcraft.client.screen;

import com.github.fireprime5.spellcraft.network.SpellResultPayload;
import com.github.fireprime5.spellcraft.spell.PortType;
import com.github.fireprime5.spellcraft.spell.SpellIssue;
import com.github.fireprime5.spellcraft.spell.SpellModuleType;
import com.github.fireprime5.spellcraft.spell.SpellValue;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Правая колонка конструктора: модуль, стек, предпросмотр, ошибки.
 *
 * <p>Разбита на секции с шапкой-полосой. Шапка рисуется фоном до текста и
 * повторяется при прокрутке — иначе при длинном графе заголовки уезжали за
 * край и становилось непонятно, к какой секции относится строка.
 *
 * <p>Подвал (энергия и подсказка) не прокручивается: он всегда на виду, чтобы
 * не искать энергию бездны и горячую клавишу справки по всему экрану.
 */
public class SidePanelWidget {

    private static final int PAD = 5;
    private static final int SECTION_GAP = 5;
    /** Высота полосы-шапки секции. */
    private static final int HEADER_H = 10;
    /** Высота подвала: энергия сверху, подсказка снизу. */
    private static final int FOOTER_H = 20;

    private final int x;
    private final int y;
    private final int w;
    private final int h;

    private int scroll;
    private int contentHeight;

    private SpellModuleType module;
    private List<SpellValue> stack = List.of();
    private List<StackPreview.Entry> preview = List.of();

    private List<SpellIssue> issues = List.of();

    /** Энергия бездны и цена каста — рисуются в подвале. */
    private int abyss;
    private int cost;
    /** Бесконечная энергия бездны: подвал показывает «∞», а не число. */
    private boolean infinite;

    public SidePanelWidget(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    /**
     * Ошибки всегда живые: актуальные проблемы графа плюс итоги последнего
     * каста (иначе после первого каста ошибки графа переставали бы обновляться).
     */
    public void update(SpellModuleType module, SpellResultPayload result,
                       List<SpellIssue> graphIssues,
                       List<StackPreview.Entry> previewEntries) {
        this.module = module;
        this.preview = previewEntries == null ? List.of() : previewEntries;
        if (result != null) {
            this.stack = result.stack();
            List<SpellIssue> merged = new ArrayList<>(graphIssues);
            for (SpellIssue issue : result.issues()) {
                boolean same = merged.stream()
                        .anyMatch(i -> i.key().equals(issue.key()) && i.arg().equals(issue.arg()));
                if (!same) {
                    merged.add(issue);
                }
            }
            this.issues = merged;
        } else {
            this.stack = List.of();
            this.issues = graphIssues;
        }
    }

    /** Энергия бездны и стоимость каста для подвала. */
    public void setEnergy(int abyss, int cost, boolean infinite) {
        this.abyss = abyss;
        this.cost = cost;
        this.infinite = infinite;
    }

    public boolean inside(double mx, double my) {
        return UiKit.inside(mx, my, x, y, w, h);
    }

    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (!inside(mx, my)) {
            return false;
        }
        scroll = Math.clamp(scroll - (int) Math.signum(dy), 0, maxScroll());
        return true;
    }

    /** Прокручивать можно только содержимое: подвал всегда на месте. */
    private int maxScroll() {
        return Math.max(0, contentHeight - (h - FOOTER_H));
    }

    public void render(GuiGraphics gfx) {
        // скролл зажимается по contentHeight предыдущего кадра: удаление
        // последнего модуля или выбор другого сокращали содержимое, а
        // прокрутка оставалась за концом и прятала верх списка до прокрутки
        // обратно. Считаем актуальную высоту заранее.
        scroll = Math.clamp(scroll, 0, maxScroll());
        Font font = UiKit.font();
        UiKit.panel(gfx, x, y, w, h, UiKit.PANEL, UiKit.BORDER);

        int cx = x + PAD;
        int right = w - PAD * 2;
        // подвал не прокручивается: под ним своя высота
        int viewH = h - FOOTER_H;
        gfx.enableScissor(x + 1, y + 1, x + w - 1, y + viewH - 1);

        int cy = y + PAD - scroll;
        cy = moduleSection(gfx, font, cx, cy, right);
        cy = stackSection(gfx, font, cx, cy, right);
        cy = previewSection(gfx, font, cx, cy, right);
        issuesSection(gfx, font, cx, cy, right);

        gfx.disableScissor();
        contentHeight = (cy + scroll) - y + PAD;
        scroll = Math.clamp(scroll, 0, maxScroll());

        if (maxScroll() > 0) {
            UiKit.scrollBar(gfx, x + w - 3, y + 2, viewH - 4,
                    (float) scroll / maxScroll(),
                    (float) (viewH - 4) / contentHeight);
        }

        footer(gfx, font, cx, y + h - FOOTER_H, right);
    }

    // ------------------------------------------------------------------ секции

    /**
     * Шапка секции: полоса с заголовком.
     *
     * <p>Фон рисуется до текста, иначе буквы «съедаются» подложкой.
     */
    private int header(GuiGraphics gfx, Font font, int x, int y, int right, String title) {
        gfx.fill(x - PAD, y, x - PAD + right + PAD * 2, y + HEADER_H, UiKit.PANEL_LIGHT);
        gfx.fill(x - PAD, y + HEADER_H - 1, x - PAD + right + PAD * 2, y + HEADER_H,
                UiKit.BORDER);
        gfx.drawString(font, UiKit.fit(font, title, right - 30), x, y + 1, UiKit.ACCENT, false);
        return y + HEADER_H + 2;
    }

    private int moduleSection(GuiGraphics gfx, Font font, int cx, int cy, int right) {
        cy = header(gfx, font, cx, cy, right,
                Component.translatable("panel.spellcraft.module").getString());
        if (module == null) {
            gfx.drawString(font, Component.translatable("panel.spellcraft.no_module").getString(),
                    cx, cy, UiKit.TEXT_DIM, false);
            return cy + font.lineHeight + SECTION_GAP;
        }
        gfx.drawString(font, UiKit.fit(font,
                        Component.translatable(module.nameKey()).getString(), right),
                cx, cy, UiKit.TEXT, false);
        cy += font.lineHeight;
        gfx.drawString(font, Component.translatable(module.category().translationKey()).getString(),
                cx, cy, UiKit.BORDER_LIGHT, false);
        cy += font.lineHeight + 1;
        for (String line : UiKit.wrap(font,
                module.description().getString(), right)) {
            gfx.drawString(font, line, cx, cy, UiKit.TEXT_DIM, false);
            cy += font.lineHeight;
        }
        cy += 3;

        gfx.drawString(font, Component.translatable("panel.spellcraft.in_ports").getString(),
                cx, cy, UiKit.TEXT_DIM, false);
        cy += font.lineHeight;
        for (String line : UiKit.wrap(font, module.inputList(), right - 8)) {
            gfx.drawString(font, line, cx + 2, cy, UiKit.PORT_IN, false);
            cy += font.lineHeight;
        }
        if (module.optionalInputs() > 0) {
            String optional = Component.translatable("gui.spellcraft.optional_ports",
                    module.optionalInputs()).getString();
            gfx.drawString(font, UiKit.fit(font, optional, right - 8), cx + 2, cy,
                    UiKit.TEXT_DIM, false);
            cy += font.lineHeight;
        }

        gfx.drawString(font, Component.translatable("panel.spellcraft.out_ports").getString(),
                cx, cy, UiKit.TEXT_DIM, false);
        cy += font.lineHeight;
        for (String line : UiKit.wrap(font, module.outputList(), right - 8)) {
            gfx.drawString(font, line, cx + 2, cy, UiKit.PORT_OUT, false);
            cy += font.lineHeight;
        }
        cy += 1;
        gfx.drawString(font, Component.translatable("panel.spellcraft.cost",
                        module.manaCost()).getString(),
                cx, cy, UiKit.WARN, false);
        return cy + font.lineHeight + SECTION_GAP;
    }

    /** Стек — результат последнего каста. */
    private int stackSection(GuiGraphics gfx, Font font, int cx, int cy, int right) {
        cy = header(gfx, font, cx, cy, right,
                Component.translatable("panel.spellcraft.stack").getString());
        String stackCount = Component.translatable("panel.spellcraft.stack_count", stack.size())
                .getString();
        gfx.drawString(font, stackCount, cx + right - font.width(stackCount), cy - 1,
                UiKit.TEXT_DIM, false);
        if (stack.isEmpty()) {
            gfx.drawString(font, Component.translatable("panel.spellcraft.stack_empty").getString(),
                    cx, cy, UiKit.TEXT_DIM, false);
            return cy + font.lineHeight + SECTION_GAP;
        }
        for (int i = stack.size() - 1; i >= 0; i--) {
            SpellValue v = stack.get(i);
            drawStackEntry(gfx, font, cx, cy, right, v.describe().getString(),
                    kindToPort(v.kind()));
            cy += font.lineHeight + 1;
        }
        return cy + SECTION_GAP;
    }

    /** Предпросмотр: что посчитается до каста. */
    private int previewSection(GuiGraphics gfx, Font font, int cx, int cy, int right) {
        if (preview.isEmpty()) {
            return cy;
        }
        cy = header(gfx, font, cx, cy, right,
                Component.translatable("panel.spellcraft.preview").getString());
        for (int i = preview.size() - 1; i >= 0; i--) {
            StackPreview.Entry e = preview.get(i);
            int color = e.known() ? UiKit.TEXT_DIM : UiKit.TEXT;
            drawStackEntry(gfx, font, cx, cy, right, e.label(),
                    e.known() ? kindToPort(e.kind()) : PortType.ANY);
            cy += font.lineHeight + 1;
        }
        return cy + SECTION_GAP;
    }

    private void issuesSection(GuiGraphics gfx, Font font, int cx, int cy, int right) {
        cy = header(gfx, font, cx, cy, right,
                Component.translatable("panel.spellcraft.errors").getString());
        if (issues.isEmpty()) {
            gfx.drawString(font, Component.translatable("panel.spellcraft.errors_none").getString(),
                    cx, cy, UiKit.OK, false);
            return;
        }
        for (SpellIssue issue : issues) {
            for (String line : UiKit.wrap(font, "✕ " + issue.text().getString(), right)) {
                gfx.drawString(font, line, cx, cy, UiKit.error(), false);
                cy += font.lineHeight;
            }
        }
    }

    /**
     * Подвал колонки: энергия и подсказка.
     *
     * <p>Энергия красная, когда энергии бездны не хватает на каст: это то же
     * состояние, что раньше показывала плашка над холстом.
     */
    private void footer(GuiGraphics gfx, Font font, int cx, int fy, int right) {
        gfx.fill(x + 1, fy, x + w - 1, y + h - 1, UiKit.PANEL_LIGHT);
        gfx.fill(x + 1, fy, x + w - 1, fy + 1, UiKit.BORDER);

        // при бесконечной энергии бездны показывается «∞»: иначе панель светилась бы красным
        // «не хватает энергии бездны», хотя каст как раз бесплатный
        String energy = infinite
                ? Component.translatable("gui.spellcraft.infinite", cost).getString()
                : Component.translatable("gui.spellcraft.energy", abyss, cost).getString();
        gfx.drawString(font, UiKit.fit(font, energy, right), cx, fy + 3,
                infinite || cost <= abyss ? UiKit.OK : UiKit.error(), false);

        // «H — справка» вместо зума в процентах: цифры зума только мешали
        String help = Component.translatable("gui.spellcraft.help_hint").getString();
        gfx.drawString(font, help, cx, fy + 11, UiKit.TEXT_DIM, false);
    }

    private void drawStackEntry(GuiGraphics gfx, Font font, int x, int y, int width, String value,
                                PortType type) {
        PortShapes.draw(gfx, type.shape(), x + 3, y + font.lineHeight / 2, 3, type.color());
        gfx.drawString(font, UiKit.fit(font, value, width - 12), x + 10, y, UiKit.TEXT, false);
    }

    public static PortType kindToPort(SpellValue.Kind kind) {
        return switch (kind) {
            case NUMBER -> PortType.NUMBER;
            case ENTITY -> PortType.ENTITY;
            case POSITION -> PortType.POINT;
            case VECTOR -> PortType.VECTOR;
            case ITEM -> PortType.ITEM;
            case LOCATION -> PortType.LOCATION;
        };
    }
}