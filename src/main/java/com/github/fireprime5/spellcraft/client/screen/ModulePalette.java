package com.github.fireprime5.spellcraft.client.screen;

import com.github.fireprime5.spellcraft.palette.Palette;
import com.github.fireprime5.spellcraft.spell.SpellModuleType;
import com.github.fireprime5.spellcraft.spell.SpellModules;
import com.github.fireprime5.spellcraft.spell.SpellSubsections;
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

    /**
     * Порядок вкладок.
     *
     * <p>Список явный, а не {@code Category.values()}: категория может
     * появиться в enum раньше, чем вкладка здесь, и её модули были бы видны
     * только через поиск. Пять вкладок помещаются в строку, переноса нет.
     */
    private static final SpellModuleType.Category[] TABS = {
            SpellModuleType.Category.NUMBERS,
            SpellModuleType.Category.ENTITIES,
            SpellModuleType.Category.ACTIONS,
            SpellModuleType.Category.LOGIC,
            SpellModuleType.Category.EFFECTS,
    };

    private static final int TAB_H = 14;
    private static final int TITLE_H = 12;
    /** Высота строки списка при нулевом размере палитры. */
    private static final int BASE_ROW_H = 13;
    /** Верх списка и высота блока описания — не зависят от размера. */
    private static final int LIST_TOP = 42;
    private static final int DESC_H = 68;

    /** Отступ вкладок от края панели и зазор между ними. */
    private static final int TAB_PAD = 5;
    private static final int TAB_GAP = 2;
    /** Подпись вкладки сжимается только до этой ширины. */
    private static final int TAB_MIN_LABEL = 14;

    /**
     * Размеры палитры.
     *
     * <p>Масштабируется раскладка, а не пиксели: растут панель, высота строки и
     * число видимых модулей, а шрифт остаётся native. Настоящий зум через
     * {@code pose().scale()} мылит текст (см. docs/ui-notes.md), поэтому
     * выбран обратный путь: «больше» значит «больше места», а не «крупнее
     * буквы». Строка при этом растёт всего на пиксель-два — иначе крупный
     * список съедал бы экран.
     */
    private record Size(int width, int rowHeight, int rows) {
    }

    private static final Size[] SIZES = {
            new Size(244, BASE_ROW_H, 8),
            new Size(272, 14, 11),
            new Size(300, 15, 14),
    };

    private int sizeLevel;

    /** Режим показа: от него зависит, какие модули вообще доступны. */
    public enum Mode {
        /** Обычный конструктор заклинаний. */
        SPELL,
        /** Схема модулей брони. */
        ARMOR,
        /** Сетка модулей машины. */
        MACHINE,
    }

    private Mode mode = Mode.SPELL;

    /** Ширина панели при текущем размере. */
    public int width() {
        return SIZES[sizeLevel].width();
    }

    /** Высота панели при текущем размере. */
    public int height() {
        return LIST_TOP + rowsVisible() * rowHeight() + DESC_H;
    }

    public int sizeLevel() {
        return sizeLevel;
    }

    /** Предельный уровень размера палитры. */
    public static int maxSizeLevel() {
        return SIZES.length - 1;
    }

    /** Меняет размер плитками. Возвращает true, если размер изменился. */
    public boolean setSizeLevel(int level) {
        int clamped = Math.clamp(level, 0, SIZES.length - 1);
        if (clamped == sizeLevel) {
            return false;
        }
        sizeLevel = clamped;
        scroll = 0;
        // поле поиска — виджет Minecraft, он не умеет менять ни позицию, ни
        // размер, поэтому после смены габаритов его пересоздаём
        if (search != null) {
            resize();
        }
        return true;
    }

    public void setMode(Mode mode) {
        this.mode = mode == null ? Mode.SPELL : mode;
        scroll = 0;
        refresh();
    }

    private int rowHeight() {
        return SIZES[sizeLevel].rowHeight();
    }

    /**
     * Ширины вкладок, посчитанные один раз на отрисовку.
     *
     * <p>Раньше ширина считалась в двух местах — при отрисовке и при hit-тесте —
     * и обе копии складывали подписи без ограничения. На английском пять вкладок
     * как раз влезали, а на русском кириллица шире, и последняя уезжала за
     * правый край панели: хвост вкладки выглядел кликабельным, но по коду
     * промах мимо неё закрывал палитру.
     *
     * <p>Теперь раскладка одна: подписи ужимаются до {@link #TAB_MIN_LABEL},
     * а остаток свободного места делится между вкладками поровну, так что
     * полоса занимает ровно ширину панели при любом языке и размере.
     */
    private record TabLayout(String[] labels, int[] widths) {
    }

    private TabLayout tabLayout(Font font) {
        int count = TABS.length;
        String[] labels = new String[count];
        int inner = width() - TAB_PAD * 2 - TAB_GAP * (count - 1);
        int per = Math.max(1, inner / count);
        // на узкой панели подписи ужимаются сильнее, поля вкладок — слабее
        boolean tight = per < 40;

        int totalText = 0;
        for (int i = 0; i < count; i++) {
            String label = Component.translatable(TABS[i].translationKey()).getString();
            int limit = tight ? Math.max(TAB_MIN_LABEL, per - 4) : per - 8;
            labels[i] = UiKit.fit(font, label, limit);
            totalText += font.width(labels[i]);
        }

        int extra = inner - totalText;
        int pad = Math.max(2, Math.min(6, extra / (2 * count)));
        int[] widths = new int[count];
        int used = 0;
        for (int i = 0; i < count; i++) {
            widths[i] = font.width(labels[i]) + pad * 2;
            used += widths[i];
        }
        // остаток отдаём в поля вкладок поровну, чтобы полоса заняла всю панель
        int slack = inner - used;
        for (int i = 0; i < count; i++) {
            int share = slack / (count - i);
            widths[i] += share;
            slack -= share;
        }
        return new TabLayout(labels, widths);
    }


    private int x;
    private int y;
    private final Consumer<SpellModuleType> onSelect;
    private final Runnable onClose;

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

    /** Фильтр режима машины; null в обычном конструкторе. */
    private java.util.function.Predicate<SpellModuleType> filter;
    private EditBox search;
    private String query = "";
    private SpellModuleType.Category category;
    private List<SpellModuleType> filtered;
    /**
     * Список с заголовками подразделов.
     *
     * <p>Раньше строки списка совпадали со строками {@link #filtered}, и
     * прокрутка велась по индексам модулей. С подразделами это перестало работать:
     * между модулями появились строки-заголовки, и индекс перестал совпадать с
     * позицией. Поэтому список — это отдельная модель, а {@code filtered} остаётся
     * источником для поиска и проверок.
     */
    private List<Line> lines = List.of();
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

    /**
     * Пересоздаёт поле поиска под текущую позицию панели, сохраняя текст.
     *
     * <p>Нужно при ресайзе окна: {@code EditBox} не умеет менять позицию, а
     * панель можно перетащить куда угодно. Раньше на каждый ресайз палетра
     * звала {@code init()}, который создавал новое поле поверх старого: текст
     * и курсор пропадали, а старое поле оставалось висеть.
     */
    public void resize() {
        if (search != null) {
            String text = search.getValue();
            search.setFocused(false);
            search = new EditBox(UiKit.font(), searchX(), searchY(), width() - 8, 12,
                    Component.translatable("gui.spellcraft.search"));
            search.setMaxLength(48);
            search.setHint(Component.translatable("gui.spellcraft.search"));
            search.setValue(text);
            search.setResponder(this::onSearchText);
            search.setFocused(true);
        }
    }

    public void init(Minecraft minecraft) {
        search = new EditBox(minecraft.font, searchX(), searchY(), width() - 8, 12,
                Component.translatable("gui.spellcraft.search"));
        search.setMaxLength(48);
        search.setHint(Component.translatable("gui.spellcraft.search"));
        search.setValue("");
        search.setResponder(this::onSearchText);
        search.setFocused(true);
        refresh();
    }

    private void onSearchText(String text) {
        query = text == null ? "" : text;
        scroll = 0;
        refresh();
    }

    /** Строка списка: либо модуль, либо заголовок подраздела. */
    private record Line(SpellModuleType type, String sub) {
        static Line of(SpellModuleType type) {
            return new Line(type, null);
        }

        static Line header(String sub) {
            return new Line(null, sub);
        }

        boolean isHeader() {
            return type == null;
        }
    }

    /**
     * Заголовок подраздела внутри списка.
     *
     * <p>Не занимает отдельную строку целиком: текст рисуется в той же полосе,
     * что и строка, а под ним — тонкая линия во всю ширину. Иначе группа из
     * двух модулей съедала бы треть панели.
     */
    private void drawSubHeader(ModuleFrame.Surface skin, Font font, String sub, int y) {
        String label = Component.translatable(SpellSubsections.nameKey(sub)).getString();
        skin.text(label, x + 9, y + 2, UiKit.TEXT_DIM);
        int lineX = x + 11 + font.width(label);
        int lineY = y + rowHeight() / 2;
        skin.fill(lineX, lineY, x + width() - 8, lineY + 1, UiKit.BORDER);
    }

    private void refresh() {
        filtered = selectVisible(SpellModules.search(query, category), unlocked, restrict, filter,
                mode);
        lines = buildLines();
        scroll = Math.clamp(scroll, 0, maxScroll());
    }

    /**
     * Отбор модулей для палитры.
     *
     * <p>Вынесено отдельным методом без графики и шрифта, потому что это и есть
     * правило игры «что игрок видит», а проверять его иначе нечем: палитре нужен
     * живой {@code Font}, и в тестах она не поднимается.
     *
     * <p>Правил три, и все они убирают модули из чужого режима:
     *
     * <ul>
     *   <li><b>Фильтр режима</b> — у машины и брони свой белый список. Показывать
     *       остальные серыми бессмысленно: игрок видел бы в палитре плавильни
     *       телепорт и гадал бы, почему он не ставится.
     *   <li><b>Модули брони в заклинаниях</b> — у них нет портов, они висят на
     *       нагруднике. В общей полосе они выглядели бы обычными заклинаниями:
     *       игрок уносил бы их на холст и только потом узнавал, что схема не
     *       сохранится.
     *   <li><b>Неоткрытые модули скрыты полностью</b>, а не показываются серыми.
     *       Раньше палитра показывала весь набор модулей игры, и открытие
     *       превращалось в сверку с этим списком вместо поиска по миру. Теперь
     *       палитра отвечает на вопрос «чем я сейчас могу пользоваться», а не
     *       «что бывает в игре». Личные модули попадают под то же правило: до
     *       «Анализа себя» их нет ни в одном списке.
     * </ul>
     *
     * @param unlocked модули, доступные игроку
     * @param restrict считать ли «доступность» (в обычном конструкторе — да)
     * @param filter белый список режима или null
     * @param mode режим палитры
     */
    static List<SpellModuleType> selectVisible(
            List<SpellModuleType> found,
            java.util.Set<net.minecraft.resources.ResourceLocation> unlocked,
            boolean restrict,
            java.util.function.Predicate<SpellModuleType> filter,
            Mode mode) {
        java.util.List<SpellModuleType> result = new java.util.ArrayList<>(found.size());
        for (SpellModuleType type : found) {
            if (filter != null && !filter.test(type)) {
                continue;
            }
            if (mode == Mode.SPELL && SpellSubsections.isArmor(type)) {
                continue;
            }
            if (restrict && !unlocked.contains(type.id())) {
                continue;
            }
            result.add(type);
        }
        return List.copyOf(result);
    }

    /** Ограничение набора модулей: null — показывать всё. */
    public void setFilter(java.util.function.Predicate<SpellModuleType> filter) {
        this.filter = filter;
        scroll = 0;
        refresh();
    }

    /**
     * Раскладывает отфильтрованные модули по строкам с заголовками.
     *
     * <p>Заголовок ставится только на первой строке группы, и только если
     * подразделов больше одного: при единственной группе заголовок занимал бы
     * место впустую.
     *
     * @param sorted модули уже разложены по подразделам (см. {@link SpellModules#search})
     */
    private List<Line> buildLines() {
        if (filtered.isEmpty()) {
            return List.of();
        }
        java.util.List<String> groups = SpellSubsections.presentIn(filtered);
        boolean showHeaders = groups.size() > 1;
        java.util.List<Line> out = new java.util.ArrayList<>(filtered.size() + groups.size());
        String previous = null;
        for (SpellModuleType type : filtered) {
            String sub = type.subCategory();
            if (showHeaders && !sub.equals(previous)) {
                out.add(Line.header(sub));
            }
            out.add(Line.of(type));
            previous = sub;
        }
        return List.copyOf(out);
    }

    public void setCategory(SpellModuleType.Category category) {
        this.category = category;
        scroll = 0;
        refresh();
    }

    public SpellModuleType.Category category() {
        return category;
    }

    private int searchX() {
        return x + 4;
    }

    private int searchY() {
        return y + TITLE_H + 18;
    }

    /** Поле поиска, чтобы экран знал, когда нужно уступить клавиатуру. */
    public EditBox searchBox() {
        return search;
    }

    public SpellModuleType pendingType() {
        return pendingType;
    }

    private int rowsVisible() {
        return SIZES[sizeLevel].rows();
    }

    private int maxScroll() {
        return Math.max(0, lines.size() - rowsVisible());
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    /**
     * Границы перетаскивания.
     *
     * <p>Задаются экраном: палитра не знает размеров окна. Без клампа её можно
     * было увести за нижний или правый край, и она оставалась там до следующего
     * открытия — класс уже на месте, но за пределы экрана не видно.
     */
    private int maxX = Integer.MAX_VALUE;
    private int maxY = Integer.MAX_VALUE;
    private int minX = Integer.MIN_VALUE / 2;
    private int minY = Integer.MIN_VALUE / 2;

    public void setBounds(int minX, int minY, int maxX, int maxY) {
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
        moveTo(x, y);
    }

    public void moveTo(int newX, int newY) {
        this.x = Math.clamp(newX, minX, Math.max(minX, maxX));
        this.y = Math.clamp(newY, minY, Math.max(minY, maxY));
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
        return UiKit.inside(mx, my, x, y, width(), height());
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

        // вкладки категорий: раскладка одна и та же, что и при отрисовке
        int tabY = y + TITLE_H + 3;
        int tabX = x + TAB_PAD;
        Font font = UiKit.font();
        TabLayout tabs = tabLayout(font);
        for (int i = 0; i < TABS.length; i++) {
            if (UiKit.inside(mx, my, tabX, tabY, tabs.widths()[i], TAB_H)) {
                setCategory(TABS[i]);
                return true;
            }
            tabX += tabs.widths()[i] + TAB_GAP;
        }

        // заголовок — перетаскивание
        if (button == 0 && UiKit.inside(mx, my, x, y, width(), TITLE_H)) {
            dragging = true;
            dragOffsetX = (int) (mx - x);
            dragOffsetY = (int) (my - y);
            return true;
        }

        int listTop = y + LIST_TOP;
        if (my >= listTop && my < listTop + rowsVisible() * rowHeight()) {
            int row = (int) ((my - listTop) / rowHeight()) + scroll;
            if (row >= 0 && row < lines.size()) {
                SpellModuleType type = lines.get(row).type();
                // заголовок подраздела — не модуль, по нему ничего не выбрать
                if (type == null) {
                    return true;
                }
                // закрытых модулей в списке нет: refresh() их отсекает,
                // поэтому сюда попадают только доступные
                pendingType = type;
                onSelect.accept(pendingType);
            }
            return true;
        }
        return true;
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
        skin.chamfer(x, y, width(), height(), cut, UiKit.PANEL);
        skin.chamferOutline(x, y, width(), height(), cut, UiKit.BORDER);

        // заголовок: светлая плашка, под ней волосяная линия акцента,
        // а слева засечка -- панель должна читаться как прибор, а не как окно
        skin.chamfer(x + 2, y + 2, width() - 4, TITLE_H - 1, 3, UiKit.PANEL_LIGHT);
        skin.fill(x + 2, y + TITLE_H, x + width() - 2, y + TITLE_H + 1, UiKit.ACCENT_DIM);
        skin.cornerTick(x + 5, y + 5, 4, UiKit.ACCENT);
        skin.text(Component.translatable("gui.spellcraft.palette").getString(),
                x + 12, y + 3, UiKit.TEXT);

        // вкладки
        int tabY = y + TITLE_H + 3;
        int tabX = x + TAB_PAD;
        TabLayout tabs = tabLayout(font);
        for (int i = 0; i < TABS.length; i++) {
            SpellModuleType.Category c = TABS[i];
            String label = tabs.labels()[i];
            int tw = tabs.widths()[i];
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
            // подпись уже подогнана по ширине вкладки, второй раз ужимать не надо
            skin.text(label,
                    tabX + (tw - font.width(label)) / 2,
                    tabY + (TAB_H - font.lineHeight) / 2 + 1,
                    active ? UiKit.TEXT : UiKit.TEXT_DIM);
            tabX += tw + TAB_GAP;
        }

        if (search != null) {
            search.setX(searchX());
            search.setY(searchY());
            search.setWidth(width() - 8);
            search.setHeight(12);
            search.render(gfx, (int) mouseX, (int) mouseY, 0);
        }

        // список
        int listTop = y + LIST_TOP;
        int listH = rowsVisible() * rowHeight();
        skin.chamfer(x + 3, listTop - 1, width() - 6, listH + 2, 3, UiKit.PANEL_DARK);
        skin.chamferOutline(x + 3, listTop - 1, width() - 6, listH + 2, 3, UiKit.BORDER);

        gfx.enableScissor(x + 4, listTop, x + width() - 4, listTop + listH);
        hoveredRow = -1;
        for (int i = scroll; i < Math.min(lines.size(), scroll + rowsVisible() + 1); i++) {
            Line line = lines.get(i);
            if (line.isHeader()) {
                drawSubHeader(skin, font, line.sub(), listTop + (i - scroll) * rowHeight());
                continue;
            }
            SpellModuleType type = line.type();
            int ry = listTop + (i - scroll) * rowHeight();
            boolean over = UiKit.inside(mouseX, mouseY, x + 4, ry, width() - 12, rowHeight());
            boolean pressed = pendingType == type;
            int accent = UiKit.moduleColor(Palette.category(type.category()));
            if (over || pressed) {
                hoveredRow = i;
                skin.fill(x + 4, ry, x + width() - 5, ry + rowHeight(),
                        pressed ? UiKit.HOVER : UiKit.PANEL_LIGHT);
                // полоса слева: строка должна выделяться формой, а не только
                // заливкой -- заливку видно плохо на тёмной панели
                skin.fill(x + 4, ry, x + 6, ry + rowHeight(),
                        pressed ? UiKit.ACCENT : UiKit.ACCENT_DIM);
            }
            // Тот же знак, что на холсте: категория различима и без цвета.
            String glyph = ModuleStyle.glyph(type);
            skin.chamfer(x + 7, ry + 1, 11, 11, 2, UiKit.PANEL_DARK);
            // Все строки открыты: закрытые отсекаются в refresh(), поэтому
            // рамка и подпись всегда основным цветом
            skin.chamferOutline(x + 7, ry + 1, 11, 11, 2, accent);
            skin.text(glyph, x + 7 + (11 - font.width(glyph)) / 2, ry + 2, UiKit.TEXT);
            String status = String.valueOf(type.manaCost());
            int statusX = x + width() - 14 - font.width(status);
            // Ширина локализованного статуса не фиксирована: оставляем зазор.
            skin.text(UiKit.fit(font, Component.translatable(type.nameKey()).getString(),
                            Math.max(0, statusX - (x + 20) - 6)),
                    x + 20, ry + 2, UiKit.TEXT);
            skin.text(status, statusX, ry + 2, UiKit.WARN);
        }
        gfx.disableScissor();

        if (filtered.isEmpty()) {
            String none = Component.translatable("gui.spellcraft.no_modules").getString();
            skin.text(none, x + (width() - font.width(none)) / 2, listTop + 4, UiKit.TEXT_DIM);
        }

        if (maxScroll() > 0) {
            UiKit.scrollBar(gfx, x + width() - 6, listTop, listH,
                    (float) scroll / maxScroll(), (float) rowsVisible() / lines.size());
        }

        // описание
        SpellModuleType described = pendingType != null ? pendingType
                : (hoveredRow >= 0 && hoveredRow < lines.size() ? lines.get(hoveredRow).type() : null);
        int descTop = y + LIST_TOP + rowsVisible() * rowHeight();
        int descH = DESC_H - 2;
        skin.chamfer(x + 3, descTop, width() - 6, descH, 3, UiKit.PANEL_DARK);
        skin.chamferOutline(x + 3, descTop, width() - 6, descH, 3, UiKit.BORDER);
        if (described != null) {
            skin.cornerTick(x + 6, descTop + 4, 3, UiKit.ACCENT_DIM);
        }
        if (described != null) {
            String io = Component.translatable("gui.spellcraft.ports_short",
                    described.inputs(), described.outputs()).getString();
            int ioX = x + width() - 14 - font.width(io);
            skin.text(UiKit.fit(font, Component.translatable(described.nameKey()).getString(),
                            Math.max(0, ioX - (x + 12) - 6)),
                    x + 12, descTop + 2, UiKit.ACCENT);
            skin.text(io, ioX, descTop + 2, UiKit.TEXT_DIM);

            // подраздел — под названием: видно, куда модуль попадёт в списке,
            // и работает как напоминание, что у категории есть вторая ступень
            String sub = described.subName().getString();
            skin.text(UiKit.fit(font, sub, width() - 16), x + 12, descTop + 12, UiKit.TEXT_DIM);

            int ly = descTop + 23;
            for (String line : UiKit.wrap(font,
                    described.description().getString(), width() - 18)) {
                // +3 на подзаголовок: раньше описание начиналось на той же
                // строке, и теперь уходило на строку ниже и обрезалось
                if (ly > descTop + descH - 30) {
                    break;
                }
                skin.text(line, x + 12, ly, UiKit.TEXT);
                ly += font.lineHeight;
            }
            // типы портов — прямо под описанием
            if (ly < descTop + descH - 9) {
                String types = Component.translatable("gui.spellcraft.ports_types",
                        described.inputList(), described.outputList()).getString();
                for (String line : UiKit.wrap(font, types, width() - 18)) {
                    if (ly > descTop + descH - 9) {
                        break;
                    }
                    skin.text(line, x + 12, ly, UiKit.PORT_IN);
                    ly += font.lineHeight;
                }
            }
        } else {
            int ly = descTop + 7;
            for (String line : UiKit.wrap(font,
                    Component.translatable("gui.spellcraft.palette_hint").getString(), width() - 18)) {
                skin.text(line, x + 12, ly, UiKit.TEXT_DIM);
                ly += font.lineHeight;
            }
        }
    }

    private static int withAlpha(int argb, int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | (argb & 0x00FFFFFF);
    }
}
