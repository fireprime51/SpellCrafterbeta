package com.github.fireprime5.spellcraft.client.screen;

import com.github.fireprime5.spellcraft.palette.Palette;
import com.github.fireprime5.spellcraft.spell.SpellModuleType;
import com.github.fireprime5.spellcraft.spell.SpellModules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Палитра модулей (всплывающая панель в стиле Psi):
 * вкладки категорий, рабочее поле поиска, скролл-список,
 * описание выбранного модуля внизу панели.
 */
public class ModulePalette {

    public static final int W = 244;
    public static final int H = 214;

    /** Порядок вкладок. Сеть — рядом с модулями, последней вкладкой. */
    private static final SpellModuleType.Category[] TABS = {
            SpellModuleType.Category.NUMBERS,
            SpellModuleType.Category.ENTITIES,
            SpellModuleType.Category.ACTIONS,
            SpellModuleType.Category.LOGIC,
            SpellModuleType.Category.NETWORK,
    };

    private static final int TAB_H = 14;
    private static final int TITLE_H = 12;
    private static final int ROW_H = 13;
    private static final int LIST_TOP = 42;
    private static final int LIST_BOTTOM = H - 62;

    private int x;
    private int y;
    private final Consumer<SpellModuleType> onSelect;
    private final Runnable onClose;
    private Consumer<SpellModuleType> onLocked;

    /** id модулей, доступных игроку. */
    private java.util.Set<net.minecraft.resources.ResourceLocation> unlocked = java.util.Set.of();
    private boolean restrict;

    public void setUnlocked(java.util.Set<net.minecraft.resources.ResourceLocation> unlocked,
                            boolean restrict) {
        this.unlocked = unlocked == null ? java.util.Set.of() : unlocked;
        this.restrict = restrict;
        refresh();
    }

    private boolean isUnlocked(SpellModuleType type) {
        return !restrict || unlocked.contains(type.id());
    }

    private EditBox search;
    private String query = "";
    private SpellModuleType.Category category;
    private List<SpellModuleType> filtered;
    private int scroll;
    private int hoveredRow = -1;
    /** Палитра перетаскивается за заголовок. */
    private boolean dragging;
    private int dragOffsetX;
    private int dragOffsetY;
    /** Модуль, зажатый ЛКМ — ставится при отпускании (в том числе на холст). */
    private SpellModuleType pendingType;

    public ModulePalette(int x, int y, SpellModuleType.Category category,
                         Consumer<SpellModuleType> onSelect, Runnable onClose) {
        this.x = x;
        this.y = y;
        this.category = category;
        this.onSelect = onSelect;
        this.onClose = onClose;
        this.filtered = SpellModules.search("", category);
    }

    public void init(Minecraft minecraft) {
        search = new EditBox(minecraft.font, x + 4, y + TITLE_H + 18, W - 8, 12,
                Component.translatable("gui.spellcraft.search"));
        search.setMaxLength(48);
        search.setHint(Component.translatable("gui.spellcraft.search"));
        search.setValue("");
        search.setResponder(s -> {
            query = s == null ? "" : s;
            scroll = 0;
            refresh();
        });
        search.setFocused(true);
        refresh();
    }

    private void refresh() {
        filtered = SpellModules.search(query, category);
        scroll = Math.clamp(scroll, 0, maxScroll());
    }

    public void setCategory(SpellModuleType.Category category) {
        this.category = category;
        scroll = 0;
        refresh();
    }

    public SpellModuleType.Category category() {
        return category;
    }

    /** Поле поиска, чтобы экран знал, когда нужно уступить клавиатуру. */
    public EditBox searchBox() {
        return search;
    }

    public SpellModuleType pendingType() {
        return pendingType;
    }

    private int rowsVisible() {
        return Math.max(1, (LIST_BOTTOM - LIST_TOP) / ROW_H);
    }

    private int maxScroll() {
        return Math.max(0, filtered.size() - rowsVisible());
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public void setOnLocked(Consumer<SpellModuleType> listener) {
        this.onLocked = listener;
    }

    public void moveTo(int newX, int newY) {
        this.x = newX;
        this.y = newY;
    }

    /** ЛКМ по заголовку — перетаскивание палитры. */
    public boolean mouseDragged(double mx, double my, int button) {
        if (!dragging) {
            return false;
        }
        x = (int) (mx - dragOffsetX);
        y = (int) (my - dragOffsetY);
        return true;
    }

    public void stopDragging() {
        dragging = false;
    }

    public boolean isDragging() {
        return dragging;
    }

    public boolean inside(double mx, double my) {
        return UiKit.inside(mx, my, x, y, W, H);
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (search != null && search.mouseClicked(mx, my, button)) {
            return true;
        }
        if (!inside(mx, my)) {
            return false;
        }
        // сброс сразу: иначе модуль, выбранный до переключения вкладки,
        // «перетаскивался» бы на холст по старой кнопке
        pendingType = null;

        // вкладки категорий (полоса та же, что при отрисовке)
        int tabY = y + TITLE_H + 3;
        int tabX = x + 4;
        Font font = UiKit.font();
        for (SpellModuleType.Category c : TABS) {
            String label = Component.translatable(c.translationKey()).getString();
            int w = font.width(label) + 8;
            if (UiKit.inside(mx, my, tabX, tabY, w, TAB_H)) {
                setCategory(c);
                return true;
            }
            tabX += w + 2;
        }

        // заголовок — перетаскивание
        if (button == 0 && UiKit.inside(mx, my, x, y, W, TITLE_H)) {
            dragging = true;
            dragOffsetX = (int) (mx - x);
            dragOffsetY = (int) (my - y);
            return true;
        }

        int listTop = y + LIST_TOP;
        if (my >= listTop && my < listTop + rowsVisible() * ROW_H) {
            int row = (int) ((my - listTop) / ROW_H) + scroll;
            if (row >= 0 && row < filtered.size()) {
                SpellModuleType type = filtered.get(row);
                if (isUnlocked(type)) {
                    pendingType = type;
                    onSelect.accept(pendingType);
                } else if (onLocked != null) {
                    onLocked.accept(type);
                }
            }
            return true;
        }
        return true;
    }

    /** Причина, по которой модуль закрыт (для подсказки), либо null. */
    private SpellModuleType lockedBecause(SpellModuleType type) {
        if (!restrict || isUnlocked(type)) {
            return null;
        }
        var need = type.requiresModule();
        if (need != null && !unlocked.contains(need.toString())) {
            var required = SpellModules.get(need);
            return required != null ? required : type;
        }
        return type;
    }

    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (!inside(mx, my)) {
            return false;
        }
        scroll = Math.clamp(scroll - (int) Math.signum(dy), 0, maxScroll());
        return true;
    }

    public boolean charTyped(char code, int modifiers) {
        if (search == null) {
            return false;
        }
        boolean handled = search.charTyped(code, modifiers);
        // подстраховка: если responder не сработал, обновляем список сами
        if (handled && !query.equals(search.getValue())) {
            query = search.getValue();
            scroll = 0;
            refresh();
        }
        return handled;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search != null) {
            boolean handled = search.keyPressed(keyCode, scanCode, modifiers);
            if (handled && !query.equals(search.getValue())) {
                query = search.getValue();
                scroll = 0;
                refresh();
            }
            return handled;
        }
        return false;
    }

    /**
     * Отрисовка панели целиком через {@link ModuleFrame.Surface}.
     *
     * <p>Всё, кроме поля поиска, идёт общими примитивами: игра рисует через
     * {@link ModuleFrame.GuiSurface}, PNG-превью -- через адаптер на
     * {@code Graphics2D}. Одна реализация рисунка, иначе превью перестаёт быть
     * правдой ровно тогда, когда понадобится править стиль.
     *
     * <p>Поле поиска остаётся здесь, а не в общем коде: это виджет
     * Minecraft со своим поведением, через {@link Surface} его не провести.
     */
    public void render(GuiGraphics gfx, double mouseX, double mouseY) {
        Font font = UiKit.font();
        ModuleFrame.Surface skin = new ModuleFrame.GuiSurface(gfx, font);

        int cut = 5;
        skin.chamfer(x, y, W, H, cut, UiKit.PANEL);
        skin.chamferOutline(x, y, W, H, cut, UiKit.BORDER);

        // заголовок: светлая плашка, под ней волосяная линия акцента,
        // а слева засечка -- панель должна читаться как прибор, а не как окно
        skin.chamfer(x + 2, y + 2, W - 4, TITLE_H - 1, 3, UiKit.PANEL_LIGHT);
        skin.fill(x + 2, y + TITLE_H, x + W - 2, y + TITLE_H + 1, UiKit.ACCENT_DIM);
        skin.cornerTick(x + 5, y + 5, 4, UiKit.ACCENT);
        skin.text(Component.translatable("gui.spellcraft.palette").getString(),
                x + 12, y + 3, UiKit.TEXT);

        // вкладки
        int tabY = y + TITLE_H + 3;
        int tabX = x + 5;
        for (SpellModuleType.Category c : TABS) {
            String label = Component.translatable(c.translationKey()).getString();
            int tw = font.width(label) + 10;
            boolean active = c == category;
            int tabCut = 3;
            skin.chamfer(tabX, tabY, tw, TAB_H, tabCut, active ? UiKit.PANEL_LIGHT : UiKit.PANEL_DARK);
            skin.chamferOutline(tabX, tabY, tw, TAB_H, tabCut,
                    active ? UiKit.ACCENT : UiKit.BORDER);
            if (active) {
                // у активной вкладки своя шапка, как у модуля: цвет читается
                // сразу, не по тексту
                skin.fill(tabX + tabCut + 1, tabY + 1, tabX + tw - tabCut - 1, tabY + 3,
                        withAlpha(UiKit.ACCENT, 190));
            }
            skin.text(UiKit.fit(font, label, tw - 8),
                    tabX + (tw - font.width(UiKit.fit(font, label, tw - 8))) / 2,
                    tabY + (TAB_H - font.lineHeight) / 2 + 1,
                    active ? UiKit.TEXT : UiKit.TEXT_DIM);
            tabX += tw + 2;
        }

        if (search != null) {
            search.setX(x + 4);
            search.setY(y + TITLE_H + 18);
            search.setWidth(W - 8);
            search.setHeight(12);
            search.render(gfx, (int) mouseX, (int) mouseY, 0);
        }

        // список
        int listTop = y + LIST_TOP;
        int listH = rowsVisible() * ROW_H;
        skin.chamfer(x + 3, listTop - 1, W - 6, listH + 2, 3, UiKit.PANEL_DARK);
        skin.chamferOutline(x + 3, listTop - 1, W - 6, listH + 2, 3, UiKit.BORDER);

        gfx.enableScissor(x + 4, listTop, x + W - 4, listTop + listH);
        hoveredRow = -1;
        for (int i = scroll; i < Math.min(filtered.size(), scroll + rowsVisible() + 1); i++) {
            SpellModuleType type = filtered.get(i);
            int ry = listTop + (i - scroll) * ROW_H;
            boolean over = UiKit.inside(mouseX, mouseY, x + 4, ry, W - 12, ROW_H);
            boolean pressed = pendingType == type;
            boolean open = isUnlocked(type);
            int accent = UiKit.moduleColor(Palette.category(type.category()));
            if (over || pressed) {
                hoveredRow = i;
                skin.fill(x + 4, ry, x + W - 5, ry + ROW_H,
                        pressed ? UiKit.HOVER : UiKit.PANEL_LIGHT);
                // полоса слева: строка должна выделяться формой, а не только
                // заливкой -- заливку видно плохо на тёмной панели
                skin.fill(x + 4, ry, x + 6, ry + ROW_H, over ? UiKit.ACCENT : UiKit.ACCENT_DIM);
            }
            skin.chamfer(x + 8, ry + 3, 7, 7, 2, open ? accent : UiKit.PANEL_LIGHT);
            skin.chamferOutline(x + 8, ry + 3, 7, 7, 2, UiKit.BORDER_LIGHT);
            skin.text(UiKit.fit(font, Component.translatable(type.nameKey()).getString(), W - 44),
                    x + 20, ry + 2, open ? UiKit.TEXT : UiKit.TEXT_DIM);
            if (open) {
                String cost = String.valueOf(type.manaCost());
                skin.text(cost, x + W - 14 - font.width(cost), ry + 2, UiKit.WARN);
            } else {
                String lock = Component.translatable("gui.spellcraft.locked").getString();
                skin.text(lock, x + W - 14 - font.width(lock), ry + 2, UiKit.TEXT_DIM);
            }
        }
        gfx.disableScissor();

        if (filtered.isEmpty()) {
            String none = Component.translatable("gui.spellcraft.no_modules").getString();
            skin.text(none, x + (W - font.width(none)) / 2, listTop + 4, UiKit.TEXT_DIM);
        }

        if (maxScroll() > 0) {
            UiKit.scrollBar(gfx, x + W - 6, listTop, listH,
                    (float) scroll / maxScroll(), (float) rowsVisible() / filtered.size());
        }

        // описание
        SpellModuleType described = pendingType != null ? pendingType
                : (hoveredRow >= 0 && hoveredRow < filtered.size() ? filtered.get(hoveredRow) : null);
        int descTop = y + LIST_BOTTOM - 1;
        int descH = H - 3 - (LIST_BOTTOM - 1);
        skin.chamfer(x + 3, descTop, W - 6, descH, 3, UiKit.PANEL_DARK);
        skin.chamferOutline(x + 3, descTop, W - 6, descH, 3, UiKit.BORDER);
        if (described != null) {
            skin.cornerTick(x + 6, descTop + 4, 3, UiKit.ACCENT_DIM);
        }
        if (described != null && !isUnlocked(described)) {
            skin.text(Component.translatable("gui.spellcraft.locked_title").getString(),
                    x + 12, descTop + 2, UiKit.TEXT_DIM);
            int lockLy = descTop + 13;
            var blocker = lockedBecause(described);
            String hint = blocker == described
                    ? Component.translatable("gui.spellcraft.locked_scan_desc").getString()
                    : Component.translatable("gui.spellcraft.locked_requires_desc",
                    Component.translatable("module." + blocker.id().getNamespace() + "."
                            + blocker.id().getPath()).getString()).getString();
            for (String line : UiKit.wrap(font, hint, W - 18)) {
                if (lockLy > y + H - 14) {
                    break;
                }
                skin.text(line, x + 12, lockLy, UiKit.WARN);
                lockLy += font.lineHeight;
            }
        } else if (described != null) {
            skin.text(UiKit.fit(font, Component.translatable(described.nameKey()).getString(), W - 16),
                    x + 12, descTop + 2, UiKit.ACCENT);
            String io = Component.translatable("gui.spellcraft.ports_short",
                    described.inputs(), described.outputs()).getString();
            skin.text(io, x + W - 14 - font.width(io), descTop + 2, UiKit.TEXT_DIM);

            int ly = descTop + 12;
            for (String line : UiKit.wrap(font,
                    Component.translatable(described.descriptionKey()).getString(), W - 18)) {
                if (ly > y + H - 30) {
                    break;
                }
                skin.text(line, x + 12, ly, UiKit.TEXT);
                ly += font.lineHeight;
            }
            // типы портов — прямо под описанием
            if (ly < y + H - 12) {
                String types = Component.translatable("gui.spellcraft.ports_types",
                        described.inputList(), described.outputList()).getString();
                for (String line : UiKit.wrap(font, types, W - 18)) {
                    if (ly > y + H - 12) {
                        break;
                    }
                    skin.text(line, x + 12, ly, UiKit.PORT_IN);
                    ly += font.lineHeight;
                }
            }
        } else {
            int ly = descTop + 7;
            for (String line : UiKit.wrap(font,
                    Component.translatable("gui.spellcraft.palette_hint").getString(), W - 18)) {
                skin.text(line, x + 12, ly, UiKit.TEXT_DIM);
                ly += font.lineHeight;
            }
        }
    }

    private static int withAlpha(int argb, int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | (argb & 0x00FFFFFF);
    }
}