package com.github.fireprime5.spellcraft.client.screen;

import com.github.fireprime5.spellcraft.Spellcraft;
import com.github.fireprime5.spellcraft.client.ClientSpellState;
import com.github.fireprime5.spellcraft.network.RequestAbyssEnergyPayload;
import com.github.fireprime5.spellcraft.network.UpdateSpellGraphPayload;
import com.github.fireprime5.spellcraft.spell.SpellGraph;
import com.github.fireprime5.spellcraft.spell.SpellIssue;
import com.github.fireprime5.spellcraft.spell.SpellModuleType;
import com.github.fireprime5.spellcraft.spell.PortType;
import com.github.fireprime5.spellcraft.spell.SpellModules;
import com.github.fireprime5.spellcraft.spell.SpellNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import com.github.fireprime5.spellcraft.spell.SpellModifier;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Построитель заклинаний в стиле Upload Labs / Psi.
 *
 * Виртуальный холст (vw x vh = размер окна / целый масштаб),
 * поэтому интерфейс всегда помещается на экран.
 *
 * Управление:
 *  - ПКМ по пустой клетке холста — палитра модулей (с поиском), модуль ставится в клетку;
 *  - ПКМ по блоку — палитра, модуль ставится справа от него;
 *  - ЛКМ по выходному порту и протяжка к входному порту — соединение;
 *  - ПКМ по выходному порту, затем ЛКМ по входному — соединение;
 *  - ПКМ по входному порту — разорвать связь;
 *  - ЛКМ по блоку — выделить (описание в правой панели);
 *  - Shift + ЛКМ по блоку — удалить;
 *  - ESC — закрыть.
 */
public class SpellBuilderScreen extends Screen {

    private static final int PANEL_W = 152;
    private static final int PAD = 2;

    private SpellGraph graph = new SpellGraph();
    private SpellGraph lastServerGraph;

    private ProgramCanvas canvas;
    private SidePanelWidget side;
    private ModulePalette palette;

    private int selectedNode = -1;
    /** Дополнительные узлы для групповых операций (Alt+клик). */
    private final java.util.LinkedHashSet<Integer> selectedNodes = new java.util.LinkedHashSet<>();

    /** Нажатый модуль: по нему отличим клик от перетаскивания на отпускании. */
    private int clickNode = -1;
    /** Открывать ли меню модификаторов по отпусканию этого клика. */
    private boolean clickOpenMenu;
    /** Клетка, из которой модуль нажали: по ней же судим, было ли перетаскивание. */
    private int pressGridX;
    private int pressGridY;

    /** Остался ли модуль в той же клетке, из которой его нажали. */
    private boolean sameCell(int node, int gridX, int gridY) {
        // обе границы: индекс -1 здесь тоже возможен, а get(-1) падает
        if (node < 0 || node >= graph.nodes.size()) {
            return false;
        }
        var n = graph.nodes.get(node);
        return n.gridX == gridX && n.gridY == gridY;
    }

    /** Размер холста в реальных GUI-координатах (без масштабирования). */
    private int vw = UiKit.MIN_W;
    private int vh = UiKit.MIN_H;

    private int areaX;
    private int areaY;
    private int areaW;
    private int areaH;

    /** Активное панорамирование сетки (ЛКМ/СКМ по пустому месту). */
    private boolean panning;

    /** Позиция машины в режиме сетки; null в обычном конструкторе. */
    private final net.minecraft.core.BlockPos machinePos;
    /** Тип машины: smelter, extractor, tank. */
    private final String machineKind;
    /** Разрешённые модули; null в обычном режиме, когда разрешено всё. */
    private java.util.Set<String> allowed;

    private double panAnchorX;
    private double panAnchorY;

    /**
     * Обычный режим: конструктор заклинания из гримуара.
     */
    public SpellBuilderScreen() {
        this(null, null);
    }

    /**
     * Режим с собственным заголовком.
     *
     * <p>Заголовок передаётся в {@code super()}, а не выставляется после: у
     * {@link Screen} заголовок неизменяемый, поэтому фабрика брони не могла бы
     * переименовать экран и называла бы схему нагрудника «построителем
     * заклинаний».
     */
    private SpellBuilderScreen(Component title) {
        super(title);
        this.machinePos = null;
        this.machineKind = null;
        this.graph = ClientSpellState.getGraph() != null
                ? ClientSpellState.getGraph().copy()
                : new com.github.fireprime5.spellcraft.spell.SpellGraph();
    }

    /**
     * Режим модульной брони.
     *
     * <p>Схема пришла с сервера отдельным пакетом: клиент открывает тот же
     * конструктор, но палитра показывает только модули брони, а правка
     * возвращается в нагрудник, а не в гримуар.
     */
    public static SpellBuilderScreen forArmor(net.minecraft.world.InteractionHand hand) {
        SpellBuilderScreen screen = new SpellBuilderScreen(Component.translatable(
                "gui.spellcraft.armor_builder"));
        screen.armorHand = hand;
        screen.graph = com.github.fireprime5.spellcraft.client.ClientArmorState.graph().copy();
        screen.allowed = com.github.fireprime5.spellcraft.armor.ModularSchema.allowed();
        return screen;
    }

    /**
     * Заголовок по режиму.
     *
     * <p>Экран один на три режима, поэтому заголовок выбирается здесь: в режиме
     * брони «Построитель заклинаний» называл бы схему, которая к заклинанию
     * отношения не имеет.
     */
    private static Component titleFor(net.minecraft.core.BlockPos pos) {
        if (pos != null) {
            return Component.translatable("gui.spellcraft.machine_grid");
        }
        return Component.translatable("gui.spellcraft.spell_builder");
    }

    /** Рука с нагрудником в режиме брони; null в остальных режимах. */
    private net.minecraft.world.InteractionHand armorHand;

    /**
     * Режим модульной сетки машины.
     *
     * <p>Экран тот же самый — так игрок не изучает второй интерфейс. Отличается
     * набором модулей в палитре и тем, что схема уходит обратно в блок.
     *
     * @param pos где стоит машина; null — обычный режим
     * @param kind тип машины: smelter, extractor, tank
     */
    public SpellBuilderScreen(net.minecraft.core.BlockPos pos, String kind) {
        super(titleFor(pos));
        this.machinePos = pos;
        this.machineKind = kind;
        this.allowed = pos == null
                ? null
                : com.github.fireprime5.spellcraft.machine.MachineGrid.Sets.forMachine(kind);
        if (pos != null) {
            // Схема машины лежит в отдельном состоянии клиента. Брать её из
            // ClientSpellState нельзя: там лежит заклинание игрока, и правка
            // сетки записала бы модули прямо в заклинание.
            this.graph = com.github.fireprime5.spellcraft.client.ClientMachineState
                    .graph().copy();
        } else {
            this.graph = ClientSpellState.getGraph() != null
                    ? ClientSpellState.getGraph().copy() : new SpellGraph();
        }
    }

    /** Позиция машины в режиме сетки или null в обычном режиме. */
    public net.minecraft.core.BlockPos machinePos() {
        return machinePos;
    }

    /** Разрешённые машиной модули или null в обычном режиме. */
    public java.util.Set<String> allowedModules() {
        return allowed;
    }

    /** Режим ли это модульной сетки машины. */
    public boolean isMachineGrid() {
        return machinePos != null;
    }

    @Override
    protected void init() {
        super.init();

        // прозрачность панелей из конфига
        UiKit.applyOpacity(com.github.fireprime5.spellcraft.config.SpellConfig.get().uiOpacity);

        // рисуем в натуральных GUI-координатах: любой pose().scale() мылит текст.
        // Потолок снизу обязателен: на маленьком окне (низкое разрешение или
        // высокий GUI-масштаб) vw/vh уходили за width/height, и вместе с ними
        // фон, боковая панель и подвал — то есть интерфейс рисовался за краем.
        vw = Math.min(width, Math.max(UiKit.MIN_W, width));
        vh = Math.min(height, Math.max(UiKit.MIN_H, height));

        // Холст занимает всю высоту: сетка идёт от верхнего до нижнего края.
        areaX = PAD;
        areaY = PAD;
        areaW = Math.max(60, vw - PANEL_W - PAD * 3);
        areaH = vh - PAD * 2;

        // Холст переживает ресайз: создавать его заново нельзя, иначе терялись
        // зум, панорама и начатое перетаскивание модуля. init() зовётся и при
        // открытии экрана, и при каждом ресайзе окна — это разные случаи.
        if (canvas == null) {
            canvas = new ProgramCanvas(areaX, areaY, areaW, areaH);
        } else {
            canvas.resize(areaX, areaY, areaW, areaH);
        }

        // Правая колонка тоже на всю высоту: раньше сверху оставалась пустая
        // полоса под плашку с энергией, а теперь энергия живёт в подвале
        // колонки.
        side = new SidePanelWidget(vw - PANEL_W - PAD, PAD, PANEL_W, vh - PAD * 2);

        if (palette != null) {
            palette.resize();
        }
    }

    /**
     * Готов ли экран к отрисовке.
     *
     * <p>Виджеты создаются в {@link #init()}, но {@code render()} не обязан
     * прийти после него: моды, подменяющие экран (ModernUI, Figura, TRender —
     * все они в списке загрузки), могут позвать отрисовку раньше. Без проверки
     * первый же кадр падал с NPE и ронял игру целиком вместо одного кадра.
     *
     * @return false — кадр пропускается, виджеты ещё не созданы
     */
    private boolean widgetsReady() {
        if (canvas != null && side != null) {
            return true;
        }
        if (!warnedAboutWidgets) {
            warnedAboutWidgets = true;
            com.github.fireprime5.spellcraft.Spellcraft.LOGGER.warn(
                    "Spellcraft: builder got a frame before init(), skipping it");
        }
        return false;
    }

    private boolean warnedAboutWidgets;

    /**
     * Запоминает позицию палитры, если её только что отпустили после перетаскивания.
     *
     * <p>Зовётся именно на отпускании. Раньше проверка стояла в
     * {@code mouseClicked}, и это гасило перетаскивание сразу: первое же событие
     * после нажатия снимало флаг перетаскивания, и дальше панель не двигалась.
     */
    private void pinPalettePosition() {
        if (palette == null || !palette.isDragging()) {
            return;
        }
        palette.stopDragging();
        var config = com.github.fireprime5.spellcraft.config.SpellConfig.get();
        config.paletteX = palette.x();
        config.paletteY = palette.y();
        config.save();
        toast(Component.translatable("gui.spellcraft.palette_pinned"));
    }

    /** Курсор внутри палитры (в gui-координатах). */
    private boolean insidePalette(double mx, double my) {
        return palette != null && palette.inside(mx, my);
    }

    /** Фокус в поле поиска: хоткеи экрана уступают клавиатуру. */
    private boolean searchHasFocus() {
        return palette != null && palette.searchBox() != null
                && palette.searchBox().isFocused();
    }

    private int syncTicks;
    private int libSyncTicks;

    @Override
    public void tick() {
        if (!widgetsReady()) {
            return;
        }

        knownModifiers = new java.util.HashSet<>(ClientSpellState.knownModifiers());
        knownModules = new java.util.HashSet<>();
        for (String id : ClientSpellState.knownModules()) {
            var parsed = net.minecraft.resources.ResourceLocation.tryParse(id);
            // tryParse возвращает null на мусорной строке — такой id не считаем
            if (parsed != null) {
                knownModules.add(parsed);
            }
        }
        learnedModules = unlockedIds().size();
        totalModules = Math.max(1, ClientSpellState.total());
        if (++libSyncTicks % 40 == 0) {
            com.github.fireprime5.spellcraft.network.LibrarySyncRequest.send();
        }
        // раз в секунду обновляем только энергию бездны: граф не перезапрашиваем,
        // иначе правки могут откатываться с задержкой
        if (++syncTicks % 20 == 0) {
            RequestAbyssEnergyPayload.send();
        }
        if (toastTicks > 0) {
            toastTicks--;
        }
        if (modifierMenu != null) {
            modifierMenu.setKnown(knownModifiers);
        }
        resolvePendingLearn();
    }

    // --- координаты мыши ---
    //
    // Раньше здесь стояли vx()/vy(): функции, которые возвращали mouseX и mouseY
    // без изменений. Остались они от удалённого масштабирования интерфейса и
    // только запутывали чтение: при вызове не видно, что преобразования нет.

    @Override
    public void renderBackground(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // Лёгкое затемнение мира: холст и палитра рисуются поверх, но без него
        // интерфейс «висит» в игре и теряет границы.
        gfx.fill(0, 0, width, height, UiKit.OVERLAY);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        if (!widgetsReady()) {
            return;
        }
        // сервер присылает сохранённый граф при открытии экрана и в ответ на
        // правку. Отличать «подтверждение моей правки» от «сервер прислал
        // другую схему» нужно по содержимому: сравнение ссылок не годится,
        // подтверждение приходит уже другим объектом.
        //
        // ClientSpellState хранит ЗАКЛИНАНИЕ игрока. В режиме брони и сетки
        // машины граф живёт в ClientArmorState/ClientMachineState, и тянуть
        // сюда заклинание нельзя: иначе в конструкторе брони открывалось бы
        // сохранённое заклинание вместо схемы нагрудника.
        var fromServer = armorHand == null && machinePos == null
                ? ClientSpellState.getGraph()
                : null;
        if (fromServer != null && fromServer != lastServerGraph) {
            lastServerGraph = fromServer;
            if (!fromServer.sameAs(graph)) {
                graph = fromServer.copy();
                selectedNode = -1;
                selectedNodes.clear();
                // сервер мог прислать более короткую схему: начатая связь,
                // перетаскивание и меню модификаторов указывали бы на индексы,
                // которых в новом графе нет — сбрасываем, иначе render/mouseReleased
                // роняли бы экран с IndexOutOfBounds
                canvas.linkFrom = -1;
                canvas.dragNode = -1;
                if (modifierNode >= 0) {
                    closeModifierMenu();
                }
                graphRevision++;
            }
        }

        // целочисленный масштаб — иначе GUI мылится

        double mx = mouseX;
        double my = mouseY;
        gfx.fill(0, 0, vw, vh, UiKit.BG);

        // холст
        canvas.linkMouseX = mx;
        canvas.linkMouseY = my;
        updateCellHighlight(mx, my);
        // ошибки рисуются на блоках, поэтому список подаём ДО отрисовки холста
        canvas.setErrorNodes(ClientSpellState.errorNodes());
        canvas.render(gfx, graph, mx, my, selectedNode, selectedNodes);
        renderPortTooltip(gfx, mx, my);

        // правая панель
        // hoveredNode живёт в холсте и обновляется при отрисовке, поэтому его
        // индекс мог устареть после правки графа — верхняя граница обязательна
        int hovered = canvas.hoveredNode();
        SpellModuleType selectedType = selectedNode >= 0 && selectedNode < graph.nodes.size()
                ? SpellModules.get(graph.nodes.get(selectedNode).moduleId)
                : hovered >= 0 && hovered < graph.nodes.size()
                ? SpellModules.get(graph.nodes.get(hovered).moduleId) : null;
        List<SpellIssue> issues = issues();
        // стек: результат последнего каста + живой предпросмотр до каста
        side.update(selectedType, ClientSpellState.getLastResult(), issues, cachedPreview);
        side.setEnergy(ClientSpellState.getAbyss(),
                graph.totalAbyssCost(ClientSpellState.getMaxAbyss()),
                ClientSpellState.isInfiniteAbyss());
        side.render(gfx);

        renderBottomBar(gfx);
        renderLinkHint(gfx, mx, my);

        renderToast(gfx);
        renderHelp(gfx);
        if (modifierMenu != null) {
            modifierMenu.render(gfx, mx, my);
            modifierMenu.renderTooltip(gfx, mx, my);
        }
        if (library != null) {
            library.render(gfx, mx, my);
        }

        if (palette != null) {
            palette.render(gfx, mx, my);
            // закреплённую ячейку видно даже если её закрывает палитра
            canvas.renderHighlight(gfx);
        }
    }

    /**
     * Подсветка ячейки, в которую попадёт модуль:
     *  - при перетаскивании из палитры — ячейка под курсором;
     *  - при переносе модуля — его новая ячейка;
     *  - после ПКМ — та ячейка, по которой кликнули.
     */
    private void updateCellHighlight(double mx, double my) {
        // «ячейки нет» — это -1, а не Integer.MIN_VALUE: поля инициализируются
        // и сбрасываются в -1, и никто не писал MIN_VALUE. Старая проверка
        // (-1 > Integer.MIN_VALUE) всегда проходила, поэтому невыбранная ячейка
        // подсвечивалась как (-1, -1)
        if (palette != null && pendingCellX >= 0 && pendingCellY >= 0) {
            // ячейка ЗАКРЕПЛЕНА при ПКМ: курсор в палитре её не меняет
            canvas.highlight(pendingCellX, pendingCellY,
                    occupied(pendingCellX, pendingCellY));
            return;
        }
        if (canvas.dragNode >= 0 && canvas.dragNode < graph.nodes.size()) {
            var node = graph.nodes.get(canvas.dragNode);
            canvas.highlight(node.gridX, node.gridY, occupied(node.gridX, node.gridY));
            return;
        }
        canvas.clearHighlight();
    }

    /**
     * Нижняя полоса: куда встанет новый модуль из палитры.
     *
     * <p>Длинной подсказки по управлению здесь больше нет — она переехала в
     * справку по H, а постоянная строка внизу только занимала место.
     */
    private void renderBottomBar(GuiGraphics gfx) {
        if (palette == null || targetCellText.isEmpty()) {
            return;
        }
        String target = Component.translatable("gui.spellcraft.target_cell", targetCellText).getString();
        int width = Math.min(vw - PAD * 2, font.width(target) + 10);
        int height = 13;
        int y = vh - height - PAD;
        int x = vw - PAD - width;
        UiKit.panel(gfx, x, y, width, height, UiKit.PANEL, UiKit.BORDER);
        gfx.drawString(font, target, x + 5, y + 3, UiKit.ACCENT, false);
    }

    /** Подсказка во время протяжки связи: «отпусти на блоке с входом». */
    private void renderLinkHint(GuiGraphics gfx, double mx, double my) {
        if (canvas.linkFrom < 0 || canvas.linkFrom >= graph.nodes.size()) {
            return;
        }
        var type = com.github.fireprime5.spellcraft.spell.SpellModules.get(
                graph.nodes.get(canvas.linkFrom).moduleId);
        if (type == null) {
            return;
        }
        String hint = Component.translatable("gui.spellcraft.link_hint",
                type.outputType(canvas.linkPort).label().getString()).getString();
        int hx = (int) (mx + 12);
        int hy = (int) (my - 20);
        int w = font.width(hint) + 8;
        gfx.fill(hx, hy, hx + w, hy + 14, UiKit.PANEL_LIGHT);
        gfx.renderOutline(hx, hy, w, 14, UiKit.ACCENT);
        gfx.drawString(font, hint, hx + 4, hy + 3, UiKit.TEXT, false);
    }

    /** Keeps the type cue readable for colour-blind users and at small zooms. */
    private void renderPortTooltip(GuiGraphics gfx, double mx, double my) {
        int node = canvas.nodeAt(graph, mx, my);
        if (node < 0 || node >= graph.nodes.size()) {
            return;
        }
        SpellModuleType type = SpellModules.get(graph.nodes.get(node).moduleId);
        if (type == null) {
            return;
        }
        int input = canvas.inputStripAt(graph, mx, my);
        int output = canvas.outputStripAt(graph, mx, my);
        PortType port = input >= 0 && input < type.inputs() ? type.inputType(input)
                : output >= 0 && output < type.outputs() ? type.outputType(output) : null;
        if (port != null) {
            gfx.renderTooltip(font, port.label(), (int) mx, (int) my);
        }
    }

    // --- Ввод ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!widgetsReady()) {
            return false;
        }

        double mx = mouseX;
        double my = mouseY;
        if (library != null) {
            if (library.mouseClicked(mx, my, button)) {
                return true;
            }
            library = null;
            return true;
        }

        if (modifierMenu != null) {
            if (modifierMenu.mouseClicked(mx, my, button)) {
                return true;
            }
            modifierMenu = null;
            return true;
        }

        if (palette != null) {
            if (palette.mouseClicked(mx, my, button)) {
                return true;
            }
            palette = null;
            return true;
        }

        if (side.inside(mx, my)) {
            return true;
        }

        if (canvas.inside(mx, my)) {
            int node = canvas.nodeAt(graph, mx, my);
            SpellModuleType type = node >= 0 ? SpellModules.get(graph.nodes.get(node).moduleId) : null;

            // СКМ — всегда перемещение сетки
            if (button == 2) {
                startPan(mx, my);
                return true;
            }

            // ЛКМ по линии соединения — разрыв связи
            if (button == 0 && node < 0 && !isControlDown()) {
                int[] conn = canvas.connectionAt(graph, mx, my);
                if (conn != null) {
                    remember();
                    graph.disconnect(conn[0], conn[1]);
                    selectedNode = conn[0];
                    pushGraph();
                    return true;
                }
                startPan(mx, my);
                return true;
            }

            if (button == 0 && type != null && isControlDown()) {
                autoConnect(node);
                return true;
            }

            if (button == 0 && type != null) {
                if (hasAltDown()) {
                    if (!selectedNodes.add(node)) selectedNodes.remove(node);
                    selectedNode = node;
                    canvas.dragNode = -1;
                    return true;
                }
                selectedNodes.clear();
                selectedNodes.add(node);
                if (hasShiftDown()) {
                    remember();
                    graph.removeNode(node);
                    // индексы сдвинулись: связь и меню модификаторов указывали
                    // бы на чужой узел — сбрасываем безусловно, а не только
                    // когда индекс совпадает с удалённым
                    canvas.linkFrom = -1;
                    if (modifierNode >= 0) {
                        closeModifierMenu();
                    }
                    selectedNode = -1;
                    selectedNodes.clear();
                    pushGraph();
                    return true;
                }

                // 1) ЛКМ по правой кромке блока — начать соединение
                //    (не нужно целиться в маленький порт)
                int outStrip = canvas.outputStripAt(graph, mx, my);
                if (outStrip >= 0) {
                    canvas.linkFrom = node;
                    canvas.linkPort = outStrip;
                    selectedNode = node;
                    canvas.linkMouseX = mx;
                    canvas.linkMouseY = my;
                    return true;
                }

                // 2) если уже тяну связь — ЛКМ по левой кромке завершает её
                if (canvas.linkFrom >= 0 && canvas.linkFrom != node) {
                    int inStrip = canvas.inputStripAt(graph, mx, my);
                    if (inStrip >= 0) {
                        remember();
                        graph.connect(canvas.linkFrom, canvas.linkPort, node, inStrip);
                        canvas.linkFrom = -1;
                        pushGraph();
                        return true;
                    }
                }

                // 3) иначе — модуль готовят к перетаскиванию. Меню
                //    модификаторов открывается на отпускании, если курсор не
                //    сдвинулся: ЛКМ должен и выбирать модуль, и открывать его
                //    настройки, а ПКМ занят переназначением цели у модулей
                //    сети и разрывом связи.
                selectedNode = node;
                canvas.dragNode = node;
                clickNode = node;
                pressGridX = graph.nodes.get(node).gridX;
                pressGridY = graph.nodes.get(node).gridY;
                clickOpenMenu = type != null && type.hasModifiers();
                if (canvas.isErrorNode(node)) {
                    toast(Component.translatable("gui.spellcraft.error_here"));
                }
                return true;
            }

            if (button == 1 && type != null && type.hasModifiers()) {
                // ПКМ по модулю — меню модификаторов (дубль простого клика)
                openModifierMenu(node, type, mouseX, mouseY);
                return true;
            }

            if (button == 1 && node < 0) {
                int[] conn = canvas.connectionAt(graph, mx, my);
                if (conn != null) {
                    remember();
                    graph.disconnect(conn[0], conn[1]);
                    pushGraph();
                    return true;
                }
            }

            if (button == 1) {
                if (type != null) {
                    int outPort = canvas.outputPortAt(graph, mx, my);
                    if (outPort >= 0) {
                        canvas.linkFrom = node;
                        canvas.linkPort = outPort;
                        selectedNode = node;
                        return true;
                    }
                    int inPort = canvas.inputPortAt(graph, mx, my);
                    if (inPort >= 0) {
                        remember();
                        graph.disconnect(node, inPort);
                        canvas.linkFrom = -1;
                        pushGraph();
                        return true;
                    }
                    SpellNode src = graph.nodes.get(node);
                    openPalette(mouseX, mouseY, src.gridX + 1, src.gridY);
                    return true;
                }
                // ПКМ по ячейке: ячейка закрепляется — модуль встанет именно сюда,
                // даже если курсор уедет на список модулей или поиск
                pendingCellX = canvas.cellX(mx);
                pendingCellY = canvas.cellY(my);
                targetCellText = cellText(pendingCellX, pendingCellY)
                        + (occupied(pendingCellX, pendingCellY)
                                ? " (" + Component.translatable("gui.spellcraft.occupied").getString() + ")"
                                : "");
                openPalette(mouseX, mouseY, pendingCellX, pendingCellY);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        double mx = mouseX;
        double my = mouseY;

        if (palette != null && button == 0 && insidePalette(mx, my)) {
            // перетаскивание заканчивается здесь: позицию сохраняем
            pinPalettePosition();
        }

        if (palette != null && button == 0) {
            var pending = palette.pendingType();
            if (pending != null) {
                placeModule(pending, pendingCellX, pendingCellY);
                return true;
            }
        }

        if (button == 0 && canvas.dragNode >= 0) {
            canvas.dragNode = -1;
            int moved = clickNode;
            clickNode = -1;
            boolean openMenu = clickOpenMenu;
            clickOpenMenu = false;
            // Клик отличается от перетаскивания не расстоянием курсора, а тем,
            // встал ли модуль в другую клетку. Мышь всегда чуть дрожит, и по
            // пикселям клик с открытием меню не срабатывал бы.
            boolean wasClick = moved >= 0 && moved < graph.nodes.size()
                    && sameCell(moved, pressGridX, pressGridY);
            if (wasClick && openMenu) {
                var type = com.github.fireprime5.spellcraft.spell.SpellModules.get(
                        graph.nodes.get(moved).moduleId);
                if (type != null && type.hasModifiers()) {
                    selectedNode = moved;
                    pushGraph();
                    openModifierMenu(moved, type, mouseX, mouseY);
                    return true;
                }
            }
            pushGraph();
            return true;
        }

        if (palette != null && palette.isDragging()) {
            palette.stopDragging();
            // запоминаем позицию, чтобы в следующий раз панель открылась там же
            var config = com.github.fireprime5.spellcraft.config.SpellConfig.get();
            config.paletteX = palette.x();
            config.paletteY = palette.y();
            config.save();
            toast(Component.translatable("gui.spellcraft.palette_pinned"));
            return true;
        }

        if (panning) {
            panning = false;
            return true;
        }

        if (button == 0 && canvas.linkFrom >= 0 && canvas.linkFrom < graph.nodes.size()) {
            int node = canvas.nodeAt(graph, mx, my);
            if (node >= 0 && node != canvas.linkFrom) {
                var srcType = com.github.fireprime5.spellcraft.spell.SpellModules.get(
                        graph.nodes.get(canvas.linkFrom).moduleId);
                // порт связи мог устареть после замены графа: зажимаем по
                // числу выходов источника, иначе outputType читал бы мимо массива
                int srcOut = srcType != null
                        ? Math.min(canvas.linkPort, Math.max(0, srcType.outputs() - 1))
                        : 0;
                // сначала точный порт, иначе — любой подходящий свободный вход блока
                int inPort = canvas.inputPortAt(graph, mx, my);
                if (inPort < 0) {
                    inPort = canvas.freeCompatibleInput(graph, node,
                            srcType != null ? srcType.outputType(srcOut)
                                    : com.github.fireprime5.spellcraft.spell.PortType.ANY);
                }
                if (inPort >= 0) {
                    remember();
                    graph.connect(canvas.linkFrom, srcOut, node, inPort);
                    selectedNode = node;
                    pushGraph();
                }
            }
            canvas.linkFrom = -1;
            return true;
        }
        if (button == 1 && canvas.linkFrom >= 0) {
            canvas.linkFrom = -1;
            return true;
        }
        // Меню модификаторов по ПКМ открывается на нажатии, поэтому на
        // отпускании закрывать его нельзя: иначе панель живёт только пока
        // кнопка зажата, и галочку в ней не щёлкнуть. Закрывается кликом мимо
        // панели, повторным ПКМ по модулю и Escape.
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!widgetsReady()) {
            return false;
        }

        double mx = mouseX;
        double my = mouseY;
        if (palette != null && palette.mouseDragged(mx, my, button)) {
            return true;
        }
        if (button == 0) {
            if (canvas.linkFrom >= 0) {
                canvas.linkMouseX = mx;
                canvas.linkMouseY = my;
                return true;
            }
            if (canvas.dragNode >= 0 && canvas.dragNode < graph.nodes.size()) {
                int cellX = canvas.cellX(mx);
                int cellY = canvas.cellY(my);
                if (cellX == pressGridX && cellY == pressGridY) {
                    // Модуль ещё не ушёл из своей клетки: это дрожание руки, а
                    // не перетаскивание. Двигать его здесь нельзя — иначе
                    // простой клик слегка сдвигал бы модуль и не открывал меню.
                    return true;
                }
                SpellNode node = graph.nodes.get(canvas.dragNode);
                // модуль нельзя положить на занятую клетку: два блока в одной
                // ячейке делают нижний из них некликабельным (сверху всегда
                // выигрывает нарисованный последним), и добраться до него
                // можно было только через Ctrl+Z
                if (occupiedByOther(cellX, cellY, canvas.dragNode)) {
                    return true;
                }
                node.gridX = cellX;
                node.gridY = cellY;
                // положение изменилось — проверка графа уже неактуальна
                graphRevision++;
                return true;
            }
            if (panning) {
                canvas.panBy(mx - panAnchorX, my - panAnchorY);
                panAnchorX = mx;
                panAnchorY = my;
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /**
     * Ctrl + ЛКМ — быстрое соединение. Работает в обе стороны:
     *  - если у кликнутого модуля есть свободный вход — он приёмник;
     *  - если входов нет (число, константа) — он источник, приёмником
     *    становится выделенный, иначе ближайший совместимый модуль;
     *  - вход выбирается тот, который свободен и подходит по типу.
     */
    private void autoConnect(int clicked) {
        if (clicked < 0 || clicked >= graph.nodes.size()) {
            return;
        }
        var clickedType = SpellModules.get(graph.nodes.get(clicked).moduleId);
        if (clickedType == null) {
            return;
        }

        int freeInput = firstFreeInput(clicked, clickedType, PortType.ANY);
        boolean clickableAsTarget = freeInput >= 0;

        // --- кликнутый модуль как источник ---
        if (!clickableAsTarget && clickedType.outputs() > 0) {
            int target = -1;
            int inPort = -1;

            // приоритет — выделенный модуль
            if (selectedNode >= 0 && selectedNode != clicked && selectedNode < graph.nodes.size()) {
                var st = SpellModules.get(graph.nodes.get(selectedNode).moduleId);
                if (st != null) {
                    for (int p = 0; p < clickedType.outputs(); p++) {
                        int port = firstFreeInput(selectedNode, st, clickedType.outputType(p));
                        if (port >= 0) {
                            target = selectedNode;
                            inPort = port;
                            break;
                        }
                    }
                }
            }

            // иначе — ближайший совместимый приёмник
            if (target < 0) {
                var cr = graph.nodes.get(clicked);
                double best = Double.MAX_VALUE;
                for (int i = 0; i < graph.nodes.size(); i++) {
                    if (i == clicked) {
                        continue;
                    }
                    var st = SpellModules.get(graph.nodes.get(i).moduleId);
                    if (st == null) {
                        continue;
                    }
                    for (int p = 0; p < clickedType.outputs(); p++) {
                        int port = firstFreeInput(i, st, clickedType.outputType(p));
                        if (port < 0) {
                            continue;
                        }
                        var r = graph.nodes.get(i);
                        double dist = Math.hypot(r.gridX - cr.gridX, r.gridY - cr.gridY);
                        if (dist < best) {
                            best = dist;
                            target = i;
                            inPort = port;
                        }
                    }
                }
            }

            if (target >= 0) {
                for (int p = 0; p < clickedType.outputs(); p++) {
                    var st = SpellModules.get(graph.nodes.get(target).moduleId);
                    if (st != null && st.inputType(inPort).accepts(clickedType.outputType(p))
                            && graph.connect(clicked, p, target, inPort)) {
                        selectedNode = target;
                        // без remember() связь не попадала в историю: Ctrl+Z
                        // отменял не её, а правку до неё, что хуже, чем
                        // отсутствия отмены вовсе
                        remember();
                        pushGraph();
                        return;
                    }
                }
            }
            return;
        }

        // --- кликнутый модуль как приёмник ---
        int source = -1;
        int sourcePort = 0;
        int chosenIn = freeInput;

        // 1) выделенный модуль
        if (selectedNode >= 0 && selectedNode != clicked && selectedNode < graph.nodes.size()) {
            var st = SpellModules.get(graph.nodes.get(selectedNode).moduleId);
            if (st != null && st.outputs() > 0) {
                for (int p = 0; p < st.outputs(); p++) {
                    int port = firstFreeInput(clicked, clickedType, st.outputType(p));
                    if (port >= 0) {
                        source = selectedNode;
                        sourcePort = p;
                        chosenIn = port;
                        break;
                    }
                }
            }
        }

        // 2) иначе — ближайший совместимый источник со свободным выходом
        if (source < 0) {
            var tr = graph.nodes.get(clicked);
            double best = Double.MAX_VALUE;
            for (int i = 0; i < graph.nodes.size(); i++) {
                if (i == clicked) {
                    continue;
                }
                var st = SpellModules.get(graph.nodes.get(i).moduleId);
                if (st == null || st.outputs() == 0) {
                    continue;
                }
                for (int p = 0; p < st.outputs(); p++) {
                    if (isOutputUsed(i)) {
                        continue;
                    }
                    int port = firstFreeInput(clicked, clickedType, st.outputType(p));
                    if (port < 0) {
                        continue;
                    }
                    var r = graph.nodes.get(i);
                    double dist = Math.hypot(r.gridX - tr.gridX, r.gridY - tr.gridY);
                    if (dist < best) {
                        best = dist;
                        source = i;
                        sourcePort = p;
                        chosenIn = port;
                    }
                }
            }
        }

        if (source >= 0 && chosenIn >= 0 && graph.connect(source, sourcePort, clicked, chosenIn)) {
            selectedNode = clicked;
            remember();
            pushGraph();
        }
    }

    /** Занята ли клетка кем-то, кроме самого этого узла. */
    private boolean occupiedByOther(int gridX, int gridY, int self) {
        for (int i = 0; i < graph.nodes.size(); i++) {
            if (i == self) {
                continue;
            }
            SpellNode other = graph.nodes.get(i);
            if (other.gridX == gridX && other.gridY == gridY) {
                return true;
            }
        }
        return false;
    }

    /** Первый вход, который свободен и принимает данный тип (или -1). */
    private int firstFreeInput(int node, SpellModuleType type, PortType from) {
        if (node < 0 || node >= graph.nodes.size()) {
            return -1;
        }
        SpellNode n = graph.nodes.get(node);
        int fallback = -1;
        for (int p = 0; p < type.inputs(); p++) {
            boolean used = p < n.inputConnections.size() && n.inputConnections.get(p) >= 0;
            if (used) {
                continue;
            }
            if (type.inputType(p).accepts(from)) {
                return p;
            }
            if (fallback < 0) {
                fallback = p;
            }
        }
        return fallback;
    }

    /** Все выходы модуля уже заняты? */
    private boolean isOutputUsed(int node) {
        for (SpellNode n : graph.nodes) {
            if (n.inputConnections.contains(node)) {
                return true;
            }
        }
        return false;
    }

    private static String cellText(int x, int y) {
        return x + ", " + y;
    }

    private static boolean isControlDown() {
        return Screen.hasControlDown();
    }

/**
     * Открыть меню модификаторов модуля.
     *
     * <p>Панель рисуется на {@code GuiGraphics} и клики обрабатывает сама,
     * поэтому экран только передаёт ей модель и колбэки.
     */
    private void openModifierMenu(int node, SpellModuleType type, double mouseX, double mouseY) {
        modifierNode = node;
        int px = (int) Math.max(PAD, Math.min(mouseX + 12, vw - ModifierMenu.W - PAD));
        int py = (int) Math.max(PAD, Math.min(mouseY - 8, vh - ModifierMenu.heightFor(type)));
        modifierMenu = new ModifierMenu(px, py,
                Component.translatable(type.nameKey()).getString(),
                type.modifiers(),
                ModifierHint.enabledOn(graph, node),
                knownModifiers,
                enabled -> {
                    if (modifierNode >= 0 && modifierNode < graph.nodes.size()) {
                        var target = graph.nodes.get(modifierNode);
                        target.modifiers.clear();
                        target.modifiers.addAll(enabled);
                        pushGraph();
                    }
                },
                this::requestLearnModifier,
                SpellBuilderScreen::learnPrice,
                port -> ModifierHint.forNode(graph, node, port));
    }

    /** Цена изучения модификатора: растёт с каждым уже изученным модификатором. */
    private static int learnPrice(
            com.github.fireprime5.spellcraft.spell.SpellModifier modifier) {
        return com.github.fireprime5.spellcraft.spell.SpellModifiers.learnCost(modifier,
                ClientSpellState.knownModifiers().size());
    }

    /** Просит сервер изучить модификатор: тратится энергия бездны, дальше шанс. */
    private void requestLearnModifier(com.github.fireprime5.spellcraft.spell.SpellModifier modifier) {
        if (pendingLearnId != null) {
            toast(Component.translatable("modifier.spellcraft.wait"));
            return;
        }
        int abyss = ClientSpellState.getAbyss();
        int price = learnPrice(modifier);
        // при бесконечной энергии бездны проверка пропускается: сервер всё равно
        // списывать не будет, и отказ по клиентскому счёту был бы ложным
        if (!ClientSpellState.isInfiniteAbyss() && abyss < price) {
            toast(Component.translatable("modifier.spellcraft.not_enough_mana", price - abyss));
            return;
        }
        pendingLearnId = modifier.id();
        pendingLearnNode = modifierNode;
        pendingLearnMana = abyss;
        pendingLearnTicks = 60;
        com.github.fireprime5.spellcraft.network.ModifierLearnPayload.send(modifier.id());
        toast(Component.translatable("modifier.spellcraft.trying",
                Component.translatable("modifier." + modifier.id()).getString()));
    }

    /** Проверяет результат изучения модификатора по синхронизации с сервером. */
    private void resolvePendingLearn() {
        if (pendingLearnId == null) {
            return;
        }
        if (pendingLearnTicks > 0) {
            pendingLearnTicks--;
        }
        if (knownModifiers.contains(pendingLearnId)) {
            // удалось: сразу включаем галочку на модуле
            if (pendingLearnNode >= 0 && pendingLearnNode < graph.nodes.size()) {
                var target = graph.nodes.get(pendingLearnNode);
                var modifier =
                        com.github.fireprime5.spellcraft.spell.SpellModifiers.byId(pendingLearnId);
                if (modifier != null && com.github.fireprime5.spellcraft.spell.SpellModifiers
                        .canEnable(modifier, new java.util.HashSet<>(target.modifiers))) {
                    target.modifiers.add(pendingLearnId);
                    pushGraph();
                }
            }
            toast(Component.translatable("modifier.spellcraft.learned",
                    Component.translatable("modifier." + pendingLearnId).getString()));
            clearPendingLearn();
            return;
        }
        // при бесконечной энергии бездны списания не происходит вовсе, поэтому падение счёта
        // не может означать «не хватило денег» — ждём только самого ответа
        boolean manaSpent = !ClientSpellState.isInfiniteAbyss()
                && ClientSpellState.getAbyss() < pendingLearnMana;
        if (manaSpent || pendingLearnTicks <= 0) {
            toast(Component.translatable("modifier.spellcraft.learn_failed",
                    Component.translatable("modifier." + pendingLearnId).getString()));
            clearPendingLearn();
        }
    }

    private void clearPendingLearn() {
        pendingLearnId = null;
        pendingLearnNode = -1;
    }

    private void startPan(double mx, double my) {
        panning = true;
        panAnchorX = mx;
        panAnchorY = my;
    }

    private void openPalette(double screenMouseX, double screenMouseY, int cellX, int cellY) {
        pendingCellX = cellX;
        pendingCellY = cellY;
        double mx = screenMouseX;
        double my = screenMouseY;
        // позиция палитры: закреплённая в конфиге или рядом с курсором (первый раз)
        var config = com.github.fireprime5.spellcraft.config.SpellConfig.get();
        // размер восстанавливаем до расчёта позиции: он влияет на габариты,
        // а кламп по правому-нижнему краю нужен уже здесь
        palette = new ModulePalette(PAD, PAD, lastCategory, type -> {
        }, () -> palette = null);
        palette.setMode(paletteMode());
        palette.setSizeLevel(Math.clamp(config.paletteSizeLevel, 0, ModulePalette.maxSizeLevel()));
        // кламп живёт в самой палитре: без него панель уезжала за нижний и
        // правый край экрана и оставалась там до следующего открытия
        int maxX = Math.max(PAD, vw - palette.width() - PANEL_W - PAD * 2);
        int maxY = Math.max(PAD, vh - palette.height() - PAD);
        palette.setBounds(PAD, PAD, maxX, maxY);
        if (config.paletteX >= 0 || config.paletteY >= 0) {
            palette.moveTo(Math.max(PAD, Math.min(config.paletteX, maxX)),
                    Math.max(PAD, Math.min(config.paletteY, maxY)));
        } else {
            palette.moveTo((int) Math.max(PAD, Math.min(mx - palette.width() / 2, maxX)),
                    (int) Math.max(PAD, Math.min(my - palette.height() / 2, maxY)));
        }
        palette.setUnlocked(unlockedIds(), true);
        if (allowed != null) {
            palette.setFilter(type -> allowed.contains(type.id().getPath()));
        }
        palette.init(minecraft);
    }

    /** Режим палитры: он решает, какие модули вообще показывать. */
    private ModulePalette.Mode paletteMode() {
        if (armorHand != null) {
            return ModulePalette.Mode.ARMOR;
        }
        return machinePos != null ? ModulePalette.Mode.MACHINE : ModulePalette.Mode.SPELL;
    }

    /** id модулей, изученных игроком (+ стартовые) и тех, что открылись вместе с ними. */
    private java.util.Set<net.minecraft.resources.ResourceLocation> unlockedIds() {
        var set = new java.util.HashSet<>(
                com.github.fireprime5.spellcraft.spell.StartModules.all().stream()
                        .map(com.github.fireprime5.spellcraft.Spellcraft::id)
                        .toList());
        set.addAll(knownModules);
        // модули открываются по предпосылкам, и цепочки бывают длинные:
        // «сложение → умножение → степень» разворачивается в три шага, а
        // один проход по списку вскрыл бы только первый. Поэтому повторяем,
        // пока открывается хоть что-то, и ограничиваем число шагов длиной
        // списка — защита от зацикливания, если предпосылки вдруг образуют кольцо
        var all = com.github.fireprime5.spellcraft.spell.SpellModules.all();
        for (int pass = 0; pass < all.size(); pass++) {
            boolean changed = false;
            for (var type : all) {
                var need = type.requiresModule();
                // именно ResourceLocation: set это Set<ResourceLocation>, а
                // need.toString() даёт String, и contains сравнивал их через
                // equals — то есть всегда возвращал false и разблокировка
                // зависимых модулей не срабатывала никогда
                if (need != null && set.contains(need) && set.add(type.id())) {
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
        return set;
    }

    private SpellModuleType.Category lastCategory = SpellModuleType.Category.NUMBERS;

    private int pendingCellX = -1;
    private int pendingCellY = -1;
    /** Подпись закреплённой ячейки для нижней панели. */
    private String targetCellText = "";

    /** История для отмены (Ctrl+Z). */
    private final java.util.ArrayDeque<SpellGraph> history = new java.util.ArrayDeque<>();
    private static final int HISTORY_LIMIT = 40;
    private boolean historyBusy;

    private void placeModule(SpellModuleType type, int preferredX, int preferredY) {
        if (!unlockedIds().contains(type.id())) {
            toast(Component.translatable("gui.spellcraft.locked_toast",
                    Component.translatable(type.nameKey()).getString()));
            palette = null;
            return;
        }
        lastCategory = type.category();
        // если выбранная ячейка свободна — модуль встаёт именно в неё
        int[] cell = (preferredX >= 0 && !occupied(preferredX, preferredY))
                ? new int[]{preferredX, preferredY}
                : findFreeCell(preferredX, preferredY);
        if (cell != null) {
            remember();
            SpellNode placed = graph.addNode(type.id(), cell[0], cell[1]);
            selectedNode = graph.nodes.size() - 1;
            // если клетка была занята и модуль встал рядом — показываем это в подсказке
            targetCellText = cell[0] == preferredX && cell[1] == preferredY
                    ? cellText(cell[0], cell[1])
                    : cellText(cell[0], cell[1]) + " (" + cellText(preferredX, preferredY)
                    + " " + Component.translatable("gui.spellcraft.occupied").getString() + ")";
            pushGraph();
        }
        pendingCellX = -1;
        pendingCellY = -1;
        palette = null;
    }

    private boolean occupied(int gx, int gy) {
        for (SpellNode n : graph.nodes) {
            if (n.gridX == gx && n.gridY == gy) {
                return true;
            }
        }
        return false;
    }

    /** Ближайшая свободная клетка (сетка бесконечная, поэтому ищем по кругу). */
    private int[] findFreeCell(int preferredX, int preferredY) {
        if (preferredX >= 0 && !occupied(preferredX, preferredY)) {
            return new int[]{preferredX, preferredY};
        }
        for (int radius = 1; radius <= 24; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int[] a = {preferredX + dx, preferredY - radius};
                if (!occupied(a[0], a[1])) {
                    return a;
                }
                int[] b = {preferredX + dx, preferredY + radius};
                if (!occupied(b[0], b[1])) {
                    return b;
                }
            }
            for (int dy = -radius + 1; dy <= radius - 1; dy++) {
                int[] a = {preferredX - radius, preferredY + dy};
                if (!occupied(a[0], a[1])) {
                    return a;
                }
                int[] b = {preferredX + radius, preferredY + dy};
                if (!occupied(b[0], b[1])) {
                    return b;
                }
            }
        }
        return null;
    }

    private void pushGraph() {
        graphRevision++;
        // ошибки прошлого каста привязаны к номерам узлов, а любая правка
        // графа эти номера сдвигает (удаление уменьшает их на единицу, отмена и
        // вставка подменяют граф целиком). Красная рамка вставала бы на
        // соседний модуль, поэтому после правки она снимается
        if (canvas != null) {
            canvas.setErrorNodes(java.util.Set.of());
        }
        lastPushedRevision = graphRevision;
        if (armorHand != null) {
            // схема брони уходит в нагрудник: сервер проверяет модули и кладёт
            // результат в компонент предмета
            PacketDistributor.sendToServer(new com.github.fireprime5.spellcraft.network
                    .SaveArmorGraphPayload(armorHand, graph.copy()));
        } else if (machinePos != null) {
            // сетка машины: сервер проверит расстояние и разрешённые модули
            PacketDistributor.sendToServer(new com.github.fireprime5.spellcraft.network
                    .SaveMachineGridPayload(machinePos, machineKind, graph.copy()));
        } else {
            PacketDistributor.sendToServer(new UpdateSpellGraphPayload(graph.copy()));
        }
    }

    /**
     * Проверка графа и предпросмотр стека считаются не каждый кадр, а только
     * после правки: validate() обходит весь граф и заметно нагружает кадр.
     */
    private int graphRevision;
    private int cachedRevision = -1;
    private List<SpellIssue> cachedIssues = List.of();
    private List<StackPreview.Entry> cachedPreview = List.of();

    private List<SpellIssue> issues() {
        if (cachedRevision != graphRevision) {
            cachedIssues = graph.validate();
            cachedPreview = StackPreview.compute(graph);
            cachedRevision = graphRevision;
        }
        return cachedIssues;
    }

    /** Сохранить текущее состояние перед изменением (для Ctrl+Z). */
    private void remember() {
        if (historyBusy) {
            return;
        }
        history.push(graph.copy());
        while (history.size() > HISTORY_LIMIT) {
            history.removeLast();
        }
    }

    private void undo() {
        if (history.isEmpty()) {
            return;
        }
        historyBusy = true;
        try {
            graph = history.pop();
            selectedNode = -1;
            selectedNodes.clear();
            if (canvas != null) {
                canvas.dragNode = -1;
                canvas.linkFrom = -1;
            }
            pushGraph();
        } finally {
            historyBusy = false;
        }
    }

    private java.util.List<Integer> layoutSelection() {
        selectedNodes.removeIf(i -> i < 0 || i >= graph.nodes.size());
        if (selectedNodes.size() < 2) return java.util.List.of();
        return new java.util.ArrayList<>(selectedNodes);
    }

    private void alignSelection(char action) {
        var ids = layoutSelection();
        if (ids.isEmpty()) return;
        int target;
        if (action == 'L') target = ids.stream().mapToInt(i -> graph.nodes.get(i).gridX).min().orElse(0);
        else if (action == 'R') target = ids.stream().mapToInt(i -> graph.nodes.get(i).gridX).max().orElse(0);
        else if (action == 'T') target = ids.stream().mapToInt(i -> graph.nodes.get(i).gridY).min().orElse(0);
        else target = ids.stream().mapToInt(i -> graph.nodes.get(i).gridY).max().orElse(0);
        remember();
        for (int i : ids) { var n = graph.nodes.get(i); if (action == 'L' || action == 'R') n.gridX = target; else n.gridY = target; }
        pushGraph();
        toast(Component.translatable("gui.spellcraft.aligned"));
    }

    private void distributeSelection(boolean horizontal) {
        var ids = layoutSelection();
        if (ids.isEmpty()) return;
        ids.sort(java.util.Comparator.comparingInt(i -> horizontal ? graph.nodes.get(i).gridX : graph.nodes.get(i).gridY));
        int first = horizontal ? graph.nodes.get(ids.get(0)).gridX : graph.nodes.get(ids.get(0)).gridY;
        int last = horizontal ? graph.nodes.get(ids.get(ids.size()-1)).gridX : graph.nodes.get(ids.get(ids.size()-1)).gridY;
        remember();
        for (int p = 1; p < ids.size() - 1; p++) { int v = first + Math.round((last-first) * p / (float)(ids.size()-1)); var n=graph.nodes.get(ids.get(p)); if(horizontal)n.gridX=v;else n.gridY=v; }
        pushGraph();
        toast(Component.translatable("gui.spellcraft.distributed"));
    }

    /** Дублировать выделенный модуль со смещением на клетку вправо. */
    private void duplicateSelected() {
        if (selectedNode < 0 || selectedNode >= graph.nodes.size()) {
            return;
        }
        SpellNode src = graph.nodes.get(selectedNode);
        if (SpellModules.get(src.moduleId) == null) {
            return;
        }
        int[] cell = findFreeCell(src.gridX + 1, src.gridY);
        if (cell == null) {
            return;
        }
        remember();
        SpellNode copy = graph.addNode(src.moduleId, cell[0], cell[1]);
        // копируем связи и модификаторы: у нового узла списки пустые,
        // поэтому прежние индексы входов надо перенести вручную
        copy.inputConnections.clear();
        for (int conn : src.inputConnections) {
            copy.inputConnections.add(conn);
        }
        copy.modifiers.clear();
        copy.modifiers.addAll(src.modifiers);
        copy.param = src.param;
        selectedNode = graph.nodes.size() - 1;
        pushGraph();
    }

    /** Скопировать заклинание в буфер обмена как текст. */
    private void copyToClipboard() {
        Minecraft.getInstance().keyboardHandler.setClipboard(serializeGraph(graph));
        toast(Component.translatable("gui.spellcraft.copied", graph.nodes.size()));
    }

    /** Вставить заклинание из буфера обмена. */
    private void pasteFromClipboard() {
        String text = Minecraft.getInstance().keyboardHandler.getClipboard();
        SpellGraph pasted = deserializeGraph(text);
        if (pasted == null || pasted.nodes.isEmpty()) {
            return;
        }
        // remember() кладёт в историю ТЕКУЩИЙ граф — очищать историю после этого
        // нельзя, иначе Ctrl+Z не сработал бы (и пропала бы вся история правок)
        remember();
        graph = pasted;
        selectedNode = -1;
        pushGraph();
        toast(Component.translatable("gui.spellcraft.pasted", graph.nodes.size()));
    }

    /**
     * Компактный текстовый формат:
     * <pre>
     * spellcraft1
     * модуль;x;y;входы через запятую;модификаторы через запятую;цель модуля
     * </pre>
     * Модификаторы тоже сохраняются, иначе копирование теряло бы часть схемы.
     */
    public static String serializeGraph(SpellGraph graph) {
        StringBuilder sb = new StringBuilder("spellcraft1\n");
        for (SpellNode n : graph.nodes) {
            sb.append(n.moduleId.getPath()).append(';').append(n.gridX).append(';')
                    .append(n.gridY).append(';');
            for (int i = 0; i < n.inputConnections.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(n.inputConnections.get(i));
            }
            if (!n.modifiers.isEmpty()) {
                sb.append(';').append(String.join(",", n.modifiers));
            }
            if (n.param != null && !n.param.isEmpty()) {
                sb.append(';').append(n.param);
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    public static SpellGraph deserializeGraph(String text) {
        if (text == null || !text.startsWith("spellcraft1")) {
            return null;
        }
        SpellGraph graph = new SpellGraph();
        String[] lines = text.split("\n");
        for (int i = 1; i < lines.length; i++) {
            String[] parts = lines[i].trim().split(";");
            if (parts.length < 3) {
                continue;
            }
            // разбор id тоже внутри try: fromNamespaceAndPath бросает
            // InvalidNamespaceException на любой заглавной букве или пробеле, а
            // буфер обмена правится руками. Раньше это исключение уходило наружу
            // и роняло весь экран билдера.
            var id = net.minecraft.resources.ResourceLocation.tryParse(Spellcraft.MODID + ":"
                    + parts[0]);
            if (id == null) {
                continue;
            }
            SpellNode node;
            try {
                node = graph.addNode(id, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            } catch (NumberFormatException e) {
                continue;
            }
            if (parts.length > 3 && !parts[3].isEmpty()) {
                for (String v : parts[3].split(",")) {
                    try {
                        node.inputConnections.add(Integer.parseInt(v.trim()));
                    } catch (NumberFormatException ignored) {
                        node.inputConnections.add(-1);
                    }
                }
            }
            if (parts.length > 4 && !parts[4].isEmpty()) {
                for (String m : parts[4].split(",")) {
                    String id2 = m.trim();
                    if (!id2.isEmpty()) {
                        node.modifiers.add(id2);
                    }
                }
            }
            // последнее поле — цель модуля (машина или предмет фильтра);
            // в старых заклинаниях его просто нет
            if (parts.length > 5 && !parts[5].isEmpty()) {
                node.param = parts[5].trim();
            }
            node.sanitize();
        }
        // индексы связей могли прийти из чужого заклинания — чистим
        graph.dropBrokenConnections();
        return graph;
    }

    /** Оверлей справки. */
    private boolean helpOpen;

    /** Библиотека заклинаний. */
    private LibraryPanel library;

    /** Меню модификаторов открытого модуля, пока не закрыто кликом или Escape. */
    private ModifierMenu modifierMenu;
    private int modifierNode = -1;

    /** Закрыть меню модификаторов. */
    private void closeModifierMenu() {
        modifierMenu = null;
        modifierNode = -1;
        pendingLearnNode = -1;
    }

    /** Ожидание ответа сервера по изучению модификатора (галочка с замочком). */
    private String pendingLearnId;
    private int pendingLearnNode = -1;
    private int pendingLearnMana;
    private int pendingLearnTicks;

    /** Знания игрока: изученные модули и счётчики. */
    private java.util.Set<net.minecraft.resources.ResourceLocation> knownModules =
            java.util.Set.of();
    private java.util.Set<String> knownModifiers = java.util.Set.of();
    private int learnedModules;
    private int totalModules = com.github.fireprime5.spellcraft.spell.SpellModules.all().size();

    private void renderHelp(GuiGraphics gfx) {
        if (!helpOpen) {
            return;
        }
        gfx.fill(0, 0, vw, vh, 0xC0000000);
        int w = Math.min(380, vw - 40);
        int h = Math.min(268, vh - 40);
        int x = (vw - w) / 2;
        int y = (vh - h) / 2;
        UiKit.panel(gfx, x, y, w, h, UiKit.PANEL, UiKit.ACCENT);
        gfx.drawString(font, Component.translatable("gui.spellcraft.help_title").getString(),
                x + 10, y + 8, UiKit.ACCENT, false);
        int ly = y + 22;
        for (String key : new String[]{
                "gui.spellcraft.help_1", "gui.spellcraft.help_2", "gui.spellcraft.help_3", "gui.spellcraft.help_4",
                "gui.spellcraft.help_5", "gui.spellcraft.help_6", "gui.spellcraft.help_7", "gui.spellcraft.help_8",
                "gui.spellcraft.help_9", "gui.spellcraft.help_11",
                "gui.spellcraft.help_12",
                "gui.spellcraft.help_mod_1", "gui.spellcraft.help_mod_2",
                "gui.spellcraft.help_mod_3", "gui.spellcraft.help_mod_4", "gui.spellcraft.help_mod_5",
                "gui.spellcraft.help_mod_6"}) {
            String line = UiKit.fit(font, Component.translatable(key).getString(), w - 20);
            gfx.drawString(font, line, x + 10, ly, UiKit.TEXT, false);
            ly += font.lineHeight + 2;
            if (ly > y + h - 14) {
                break;
            }
        }
        String close = Component.translatable("gui.spellcraft.help_close").getString();
        gfx.drawString(font, close, x + (w - font.width(close)) / 2, y + h - 14,
                UiKit.TEXT_DIM, false);
    }

    /** Короткое всплывающее сообщение в углу экрана. */
    private String toastText = "";
    private int toastTicks;

    private void toast(Component message) {
        toastText = message.getString();
        toastTicks = 60;
    }

    private void renderToast(GuiGraphics gfx) {
        if (toastTicks <= 0 || toastText.isEmpty()) {
            return;
        }
        int w = font.width(toastText) + 12;
        int x = vw - w - PAD;
        int y = PAD + 2;
        gfx.fill(x, y, x + w, y + 16, UiKit.PANEL_LIGHT);
        gfx.renderOutline(x, y, w, 16, UiKit.ACCENT);
        gfx.drawString(font, toastText, x + 6, y + 4, UiKit.TEXT, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (!widgetsReady()) {
            return false;
        }

        double mx = mouseX;
        double my = mouseY;
        // Ctrl + колесо над палитрой меняет её размер. Без Ctrl колесо
        // прокручивает список: так же, как в палитре файлов, и случайно
        // изменить размер при прокрутке невозможно
        if (palette != null) {
            if (hasControlDown() && palette.inside(mx, my)) {
                if (resizePalette(palette.sizeLevel() + (dy > 0 ? 1 : -1))) {
                    return true;
                }
            }
            if (palette.mouseScrolled(mx, my, dx, dy)) {
                return true;
            }
            return true;
        }
        if (canvas.inside(mx, my)) {
            canvas.zoomAt(mx, my, dy);
            return true;
        }
        if (side.mouseScrolled(mx, my, dx, dy)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    /** Задан ли размер палитры. true, если он изменился. */
    private boolean resizePalette(int level) {
        if (palette == null || !palette.setSizeLevel(level)) {
            return false;
        }
        // габариты изменились — панель держим внутри экрана, иначе её правый
        // или нижний край уехал бы за окно и стал недоступен
        palette.setBounds(PAD, PAD,
                Math.max(PAD, vw - palette.width() - PANEL_W - PAD * 2),
                Math.max(PAD, vh - palette.height() - PAD));
        palette.moveTo(palette.x(), palette.y());
        var config = com.github.fireprime5.spellcraft.config.SpellConfig.get();
        config.paletteSizeLevel = palette.sizeLevel();
        config.paletteX = palette.x();
        config.paletteY = palette.y();
        config.save();
        toast(Component.translatable(palette.sizeLevel() >= palette.maxSizeLevel()
                ? "gui.spellcraft.palette_largest" : "gui.spellcraft.palette_sized",
                palette.sizeLevel() + 1, palette.maxSizeLevel() + 1));
        return true;
    }

    /**
     * Введённый символ.
     *
     * <p>Здесь нельзя «уступать клавиатуру» экрану, как это делается в
     * {@link #keyPressed}: у {@code Screen} нет своего {@code charTyped} — там
     * просто {@code false}, и символ, отданный {@code super}, пропадает. Поле
     * поиска палитры — это не ванильный элемент на экране, а {@code EditBox},
     * которому символ нужно передать руками.
     *
     * <p>Передаём только тому полю, которое в фокусе, иначе символ достанется
     * не тому окну: палитра рисуется поверх библиотеки.
     */
    @Override
    public boolean charTyped(char code, int modifiers) {
        if (!widgetsReady()) {
            return false;
        }

        if (palette != null && isFocused(palette.searchBox())) {
            return palette.charTyped(code, modifiers);
        }
        if (library != null && isFocused(library.searchBox())) {
            return library.charTyped(code, modifiers);
        }
        return super.charTyped(code, modifiers);
    }

    /**
     * Закрывает самое верхнее открытое окно.
     *
     * <p>Порядок совпадает с порядком отрисовки в {@link #render}: палитра
     * поверх библиотеки, та поверх меню модификаторов, меню поверх справки.
     *
     * @return true, если было что закрыть
     */
    private boolean closeTopOverlay() {
        if (palette != null) {
            palette = null;
            return true;
        }
        if (library != null) {
            library = null;
            return true;
        }
        if (modifierMenu != null) {
            closeModifierMenu();
            return true;
        }
        if (helpOpen) {
            helpOpen = false;
            return true;
        }
        return false;
    }

    private static boolean isFocused(net.minecraft.client.gui.components.EditBox box) {
        return box != null && box.isFocused();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!widgetsReady()) {
            return false;
        }

        // Escape разбирает окна сверху вниз — в порядке их отрисовки — и только
        // потом закрывает сам конструктор. Раньше проверка «закрыть экран» стояла
        // первой, и ветки закрытия палитры, библиотеки, меню модификаторов и
        // справки были мёртвым кодом: Escape закрывал всё сразу.
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (closeTopOverlay()) {
                return true;
            }
            onClose();
            return true;
        }

        // Поле поиска принадлежит палитре, а не списку дочерних элементов
        // экрана, поэтому Screen до него своим ходом не доходит: Backspace,
        // Enter и стрелки приходится отдавать палитре вручную. Раньше это
        // происходило только когда фокус на поле был потерян — то есть ровно
        // тогда, когда он и был, и правка символов до поля не доходила.
        if (palette != null && palette.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (searchHasFocus()) {
            // остальное — экрану, но горячие клавиши ниже уже не должны
            // срабатывать, пока в поле печатают
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        if (keyCode == GLFW.GLFW_KEY_B && !hasControlDown()) {
            if (library != null) {
                library = null;
            } else {
                int px = (vw - LibraryPanel.W) / 2;
                int py = (vh - LibraryPanel.H) / 2;
                library = new LibraryPanel(px, py,
                        // подтверждения нет: сервер может отказать (все 8
                        // слотов заняты — тогда он шлёт своё сообщение), а
                        // раньше тост «сохранено» показывался в любом случае,
                        // и игрок ждал результата, которого не будет.
                        // Список библиотеки приезжает по LibrarySyncPayload —
                        // он и служит подтверждением.
                        name ->
                                com.github.fireprime5.spellcraft.network.SaveSpellPayload.send(name),
                        index ->
                                com.github.fireprime5.spellcraft.network.LoadSpellPayload.send(index),
                        index ->
                                com.github.fireprime5.spellcraft.network.DeleteSpellPayload
                                        .send(index),
                        () -> library = null);
                library.init(minecraft);
            }
            return true;
        }

        // библиотека — модальное окно: пока она открыта, клавиатура её
        if (library != null) {
            library.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_H && !hasControlDown()) {
            helpOpen = !helpOpen;
            return true;
        }

        // Home is a viewport-only recovery action: it never changes the
        // spell graph, selection, or pending edits.
        if (keyCode == GLFW.GLFW_KEY_HOME && !hasControlDown()) {
            canvas.resetView();
            toast(Component.translatable("gui.spellcraft.canvas_reset"));
            return true;
        }

        boolean ctrl = hasControlDown();

        if (ctrl && keyCode == GLFW.GLFW_KEY_Z) {
            undo();
            return true;
        }
        // Group layout shortcuts: Alt+click builds a selection, then Ctrl+Alt
        // keeps the ordinary editor shortcuts free for copy/undo.
        if (ctrl && hasAltDown()) {
            if (keyCode == GLFW.GLFW_KEY_L) { alignSelection('L'); return true; }
            if (keyCode == GLFW.GLFW_KEY_R) { alignSelection('R'); return true; }
            if (keyCode == GLFW.GLFW_KEY_T) { alignSelection('T'); return true; }
            if (keyCode == GLFW.GLFW_KEY_B) { alignSelection('B'); return true; }
            if (keyCode == GLFW.GLFW_KEY_H) { distributeSelection(true); return true; }
            if (keyCode == GLFW.GLFW_KEY_V) { distributeSelection(false); return true; }
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_D) {
            duplicateSelected();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_C) {
            copyToClipboard();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
            pasteFromClipboard();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE && selectedNode >= 0) {
            remember();
            graph.removeNode(selectedNode);
            // removeNode сдвигает индексы: связь и открытое меню указывали бы
            // на чужой (или отсутствующий) узел — роняли бы экран или применяли
            // модификаторы не туда. Поэтому сбрасываем и связь, и меню всегда.
            canvas.linkFrom = -1;
            if (modifierNode >= 0) {
                closeModifierMenu();
            }
            selectedNode = -1;
            selectedNodes.clear();
            pushGraph();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        // removed() зовётся и при закрытии, и при смене мира; холст к этому
        // моменту мог и не появиться, а NPE здесь уронил бы игру на выходе из
        // редактора
        if (canvas != null) {
            canvas.dragNode = -1;
            canvas.linkFrom = -1;
        }
        panning = false;
        super.removed();
    }

    @Override
    public void onClose() {
        // незакрытый экран без правок не должен слать полный NBT графа:
        // правок не было — значит и отправлять нечего
        if (graphRevision != lastPushedRevision) {
            pushGraph();
        }
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    /** Ревизия графа на момент последней отправки на сервер. */
    private int lastPushedRevision = -1;
}
