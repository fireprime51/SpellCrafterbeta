package com.github.fireprime5.spellcraft.client.screen;

import com.github.fireprime5.spellcraft.config.SpellConfig;
import com.github.fireprime5.spellcraft.palette.Palette;
import com.github.fireprime5.spellcraft.spell.PortType;
import com.github.fireprime5.spellcraft.spell.SpellGraph;
import com.github.fireprime5.spellcraft.spell.SpellModuleType;
import com.github.fireprime5.spellcraft.spell.SpellModules;
import com.github.fireprime5.spellcraft.spell.SpellNode;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Бесконечный холст программы в стиле Upload Labs / Psi:
 * сетка без границ, панорамирование мышью, зум колесом, блоки с цветной
 * шапкой, входами слева и выходами справа, соединения — ортогональные
 * ломаные со стрелками.
 */
public class ProgramCanvas {

    public static final int BASE_CELL = 40;

    private int x;
    private int y;
    private int w;
    private int h;

    private float cell = BASE_CELL;
    private float zoom = 1f;
    /** Положение сетки в клетках (можно уходить далеко — сетка бесконечная). */
    private float panX = 0f;
    private float panY = 0f;

    /** Подсвеченная ячейка (куда встанет модуль): -1 — не подсвечивать. */
    private int highlightCellX = Integer.MIN_VALUE;
    private int highlightCellY = Integer.MIN_VALUE;
    private boolean highlightBlocked;

    /** Сегменты нарисованных связей: {x1,y1,x2,y2,targetNode,inputPort} */
    private final List<int[]> connections = new ArrayList<>();
    private int hoveredConnection = -1;

    private int hoveredNode = -1;
    private int hoveredPort = -1;
    private boolean hoveredPortIsInput;
    private PortType hoveredPortType;

    private SpellGraph graphRef;

    /** Узел, который сейчас перетаскивают (-1 если нет). */
    public int dragNode = -1;
    /** Узел, из которого идёт соединение (-1 если нет). */
    public int linkFrom = -1;
    public int linkPort = 0;
    public double linkMouseX;
    public double linkMouseY;

    public ProgramCanvas(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.zoom = SpellConfig.get().defaultZoom;
    }

    // --- геометрия ---

    public float cellPx() {
        return cell * zoom;
    }

    private float originX() {
        return x - panX * cellPx();
    }

    private float originY() {
        return y - panY * cellPx();
    }

    public int cellX(double mx) {
        return (int) Math.floor((mx - originX()) / cellPx());
    }

    public int cellY(double my) {
        return (int) Math.floor((my - originY()) / cellPx());
    }

    /** Подсветить ячейку (drop-подсказка). */
    public void highlight(int gridX, int gridY, boolean blocked) {
        this.highlightCellX = gridX;
        this.highlightCellY = gridY;
        this.highlightBlocked = blocked;
    }

    public void clearHighlight() {
        this.highlightCellX = Integer.MIN_VALUE;
        this.highlightCellY = Integer.MIN_VALUE;
    }

    /** Модули, на которых произошла ошибка в последнем касте. */
    private java.util.Set<Integer> errorNodes = java.util.Set.of();

    public void setErrorNodes(java.util.Set<Integer> nodes) {
        this.errorNodes = nodes == null ? java.util.Set.of() : nodes;
    }

    public boolean isErrorNode(int index) {
        return errorNodes.contains(index);
    }

    /** Подсветка закреплённой ячейки (рисуется поверх всего, даже поверх палитры). */
    public void renderHighlight(GuiGraphics gfx) {
        if (highlightCellX == Integer.MIN_VALUE) {
            return;
        }
        double cpx = cellPx();
        int hx = (int) Math.round(originX() + highlightCellX * cpx);
        int hy = (int) Math.round(originY() + highlightCellY * cpx);
        int size = (int) cpx;
        int fill = highlightBlocked ? 0x70FF6B6B : 0x70FFFFFF;
        gfx.fill(hx + 1, hy + 1, hx + size - 1, hy + size - 1, fill);
        gfx.renderOutline(hx + 1, hy + 1, size - 2, size - 2,
                highlightBlocked ? UiKit.error() : UiKit.ACCENT);
        // крестик в центре — видно, что это именно целевая клетка
        int cx = hx + size / 2;
        int cy = hy + size / 2;
        int col = highlightBlocked ? UiKit.error() : UiKit.ACCENT;
        gfx.fill(cx - 4, cy, cx + 4, cy + 1, col);
        gfx.fill(cx, cy - 4, cx + 1, cy + 4, col);
    }

    public float zoom() {
        return zoom;
    }

    public boolean inside(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public boolean infiniteGrid() {
        return SpellConfig.get().infiniteGrid;
    }

    /**
     * Зум вокруг точки курсора: клетка под мышью остаётся на месте.
     */
    public void zoomAt(double mx, double my, double steps) {
        double oldPx = cellPx();
        double cellUnderCursorX = (mx - originX()) / oldPx;
        double cellUnderCursorY = (my - originY()) / oldPx;
        double factor = Math.pow(1.18, steps);
        float newZoom = (float) Math.clamp(zoom * factor,
                SpellConfig.MIN_ZOOM, SpellConfig.MAX_ZOOM);
        if (Math.abs(newZoom - zoom) < 0.0001f) {
            return;
        }
        zoom = newZoom;
        double newPx = cellPx();
        panX = (float) (cellUnderCursorX - (mx - x) / newPx);
        panY = (float) (cellUnderCursorY - (my - y) / newPx);
        clampPan();
    }

    /** Сдвиг сетки на пиксели (панорамирование мышью). */
    public void panBy(double dxPixels, double dyPixels) {
        double px = cellPx();
        panX -= dxPixels / px;
        panY -= dyPixels / px;
        clampPan();
    }

    private void clampPan() {
        panX = (float) Math.clamp(panX, -2000, 2000);
        panY = (float) Math.clamp(panY, -2000, 2000);
    }

    // --- прямоугольники блоков ---

    private record Rect(int x, int y, int w, int h) {
    }

    /** Размер маркера порта (половина). */
    private int marker() {
        double px = cellPx();
        return px >= 42 ? 3 : (px >= 28 ? 2 : 1);
    }

    /** Шаг между портами по вертикали. */
    private double portStep(int blockHeight, int count) {
        return Math.max(6, Math.min(9, (blockHeight - 10) / (double) Math.max(1, count)));
    }

    /** Экранные координаты маркера входа: {x, y}. */
    private int[] inputPos(Rect r, SpellModuleType type, int port) {
        int m = marker();
        int py = r.y + 5 + (int) Math.round(port * portStep(r.h, type.inputs())) - m;
        return new int[]{r.x - 3 - m, py};
    }

    /** Экранные координаты маркера выхода: {x, y}. */
    private int[] outputPos(Rect r, SpellModuleType type, int port) {
        int m = marker();
        int py = r.y + 5 + (int) Math.round(port * portStep(r.h, type.outputs())) - m;
        return new int[]{r.x + r.w + 3 + m, py};
    }

    /** Адаптер создаётся на каждый кадр: он без полей и стоит одно выделение. */
    private static ModuleFrame.Surface surface(GuiGraphics gfx, Font font) {
        return new ModuleFrame.GuiSurface(gfx, font);
    }

    private Rect blockRect(SpellNode node) {
        double px = cellPx();
        float bx = (float) (originX() + node.gridX * px) + 2;
        float by = (float) (originY() + node.gridY * px) + 2 - (isDragging(node) ? 3 : 0);
        int bw = Math.max(12, (int) Math.round(px) - 4);
        int bh = Math.max(14, (int) Math.round(px * 0.82f) - 10);
        return new Rect(Math.round(bx), Math.round(by), bw, bh);
    }

    private boolean isDragging(SpellNode n) {
        return graphRef != null && indexOf(n) == dragNode;
    }

    private int indexOf(SpellNode n) {
        for (int i = 0; i < graphRef.nodes.size(); i++) {
            if (graphRef.nodes.get(i) == n) {
                return i;
            }
        }
        return -1;
    }

    public int nodeAt(SpellGraph graph, double mx, double my) {
        if (!inside(mx, my)) {
            return -1;
        }
        for (int i = graph.nodes.size() - 1; i >= 0; i--) {
            Rect r = blockRect(graph.nodes.get(i));
            if (mx >= r.x && mx < r.x + r.w && my >= r.y && my < r.y + r.h) {
                return i;
            }
        }
        return -1;
    }

    public int outputPortAt(SpellGraph graph, double mx, double my) {
        int idx = nodeAt(graph, mx, my);
        if (idx < 0) {
            return -1;
        }
        SpellModuleType type = SpellModules.get(graph.nodes.get(idx).moduleId);
        if (type == null) {
            return -1;
        }
        Rect r = blockRect(graph.nodes.get(idx));
        for (int p = 0; p < type.outputs(); p++) {
            int[] pos = outputPos(r, type, p);
            int m = marker();
            if (mx >= pos[0] - m - 3 && mx <= pos[0] + m + 1
                    && my >= pos[1] - 1 && my <= pos[1] + 2 * m + 1) {
                return p;
            }
        }
        return -1;
    }

    public int inputPortAt(SpellGraph graph, double mx, double my) {
        int idx = nodeAt(graph, mx, my);
        if (idx < 0) {
            return -1;
        }
        SpellModuleType type = SpellModules.get(graph.nodes.get(idx).moduleId);
        if (type == null) {
            return -1;
        }
        Rect r = blockRect(graph.nodes.get(idx));
        for (int p = 0; p < type.inputs(); p++) {
            int[] pos = inputPos(r, type, p);
            int m = marker();
            if (mx >= pos[0] - m - 1 && mx <= pos[0] + m + 3
                    && my >= pos[1] - 1 && my <= pos[1] + 2 * m + 1) {
                return p;
            }
        }
        return -1;
    }

    /**
     * Лёгкое наведение: правая полоса блока (куда целиться не нужно впритык).
     * Возвращает номер выходного порта, по вертикали выбирается ближайший.
     */
    public int outputStripAt(SpellGraph graph, double mx, double my) {
        int idx = nodeAt(graph, mx, my);
        if (idx < 0) {
            return -1;
        }
        SpellModuleType type = SpellModules.get(graph.nodes.get(idx).moduleId);
        if (type == null || type.outputs() == 0) {
            return -1;
        }
        Rect r = blockRect(graph.nodes.get(idx));
        if (mx < r.x + r.w - 2 || mx > r.x + r.w + 6) {
            return -1;
        }
        return nearestPort(type.outputs(), my, r);
    }

    /** Лёгкое наведение на левую полосу блока — вход. */
    public int inputStripAt(SpellGraph graph, double mx, double my) {
        int idx = nodeAt(graph, mx, my);
        if (idx < 0) {
            return -1;
        }
        SpellModuleType type = SpellModules.get(graph.nodes.get(idx).moduleId);
        if (type == null || type.inputs() == 0) {
            return -1;
        }
        Rect r = blockRect(graph.nodes.get(idx));
        if (mx > r.x + 2 || mx < r.x - 6) {
            return -1;
        }
        return nearestPort(type.inputs(), my, r);
    }

    /** Ближайший по вертикали порт (выбирается мышью по «полосе» блока). */
    private int nearestPort(int count, double my, Rect r) {
        double step = portStep(r.h, count);
        int best = 0;
        double bestDist = Double.MAX_VALUE;
        int m = marker();
        for (int p = 0; p < count; p++) {
            int py = r.y + 5 + (int) Math.round(p * step);
            double d = Math.abs(my - py);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    /**
     * Первый свободный вход блока, совместимый с типом источника.
     * Позволяет отпустить связь прямо на блоке — не нужно целиться в порт.
     */
    public int freeCompatibleInput(SpellGraph graph, int node, PortType sourceType) {
        if (node < 0 || node >= graph.nodes.size()) {
            return -1;
        }
        SpellModuleType type = SpellModules.get(graph.nodes.get(node).moduleId);
        if (type == null) {
            return -1;
        }
        SpellNode n = graph.nodes.get(node);
        for (int p = 0; p < type.inputs(); p++) {
            boolean connected = p < n.inputConnections.size() && n.inputConnections.get(p) >= 0;
            if (!connected && type.inputType(p).accepts(sourceType)) {
                return p;
            }
        }
        // все заняты — берём первый вход (переподключим)
        return type.inputs() > 0 ? 0 : -1;
    }

    /** Есть ли узел в точке — для отсечения панорамирования. */
    public boolean nodeHit(SpellGraph graph, double mx, double my) {
        return nodeAt(graph, mx, my) >= 0;
    }

    public int hoveredNode() {
        return hoveredNode;
    }

    public int hoveredPort() {
        return hoveredPort;
    }

    public PortType hoveredPortType() {
        return hoveredPortType;
    }

    public boolean hoveredPortIsInput() {
        return hoveredPortIsInput;
    }

    /** Есть ли узел в точке — для отсечения панорамирования. */
    // --- отрисовка ---

    public void render(GuiGraphics gfx, SpellGraph graph, double mouseX, double mouseY,
                       int selectedNode) {
        this.graphRef = graph;
        double px = cellPx();

        // подложка холста (полупрозрачная — видно игру)
        gfx.fill(x, y, x + w, y + h, UiKit.PANEL_DARK);
        gfx.renderOutline(x - 1, y - 1, w + 2, h + 2, UiKit.BORDER);

        // бесконечная сетка
        int c0 = cellX(x) - 1;
        int c1 = cellX(x + w) + 1;
        int r0 = cellY(y) - 1;
        int r1 = cellY(y + h) + 1;
        for (int c = c0; c <= c1; c++) {
            int lx = (int) Math.round(originX() + c * px);
            gfx.fill(lx, y, lx + 1, y + h, c == 0 ? UiKit.GRID : UiKit.GRID);
        }
        for (int r = r0; r <= r1; r++) {
            int ly = (int) Math.round(originY() + r * px);
            gfx.fill(x, ly, x + w, ly + 1, UiKit.GRID);
        }
        renderHighlight(gfx);

        // начало координат
        int ax = (int) Math.round(originX());
        int ay = (int) Math.round(originY());
        gfx.fill(ax, y, ax + 1, y + h, UiKit.BORDER_LIGHT);
        gfx.fill(x, ay, x + w, ay + 1, UiKit.BORDER_LIGHT);

        // соединения
        connections.clear();
        for (int i = 0; i < graph.nodes.size(); i++) {
            SpellNode node = graph.nodes.get(i);
            SpellModuleType type = SpellModules.get(node.moduleId);
            if (type == null) {
                continue;
            }
            Rect target = blockRect(node);
            for (int port = 0; port < node.inputConnections.size() && port < type.inputs(); port++) {
                int from = node.inputConnections.get(port);
                if (from < 0 || from >= graph.nodes.size()) {
                    continue;
                }
                SpellNode srcNode = graph.nodes.get(from);
                SpellModuleType srcType = SpellModules.get(srcNode.moduleId);
                if (srcType == null || srcType.outputs() == 0) {
                    continue;
                }
                Rect src = blockRect(srcNode);
                int srcPort = Math.min(port, srcType.outputs() - 1);
                int[] out = outputPos(src, srcType, srcPort);
                int[] in = inputPos(target, type, port);
                int m = marker();
                // линия идёт от края маркера выхода до края маркера входа
                int x1 = out[0] - m;
                int y1 = out[1] + m;
                int x2 = in[0] + m + 1;
                int y2 = in[1] + m;
                boolean hovered = isSegmentHovered(x1, y1, x2, y2, mouseX, mouseY);
                // конфликт типов проверяем у того же порта, который нарисован
                boolean conflict = !type.inputType(port).accepts(srcType.outputType(srcPort));
                connection(gfx, x1, y1, x2, y2,
                        conflict ? UiKit.error() : (hovered ? UiKit.BORDER_LIGHT : UiKit.TEXT_DIM));
                connections.add(new int[]{x1, y1, x2, y2, i, port});
            }
        }

        // протягиваемая связь
        if (linkFrom >= 0 && linkFrom < graph.nodes.size()) {
            SpellNode src = graph.nodes.get(linkFrom);
            SpellModuleType type = SpellModules.get(src.moduleId);
            if (type != null) {
                Rect r = blockRect(src);
                int[] out = outputPos(r, type, Math.min(linkPort, type.outputs() - 1));
                int m = marker();
                connection(gfx, out[0] - m, out[1] + m,
                        (int) linkMouseX, (int) linkMouseY, UiKit.ACCENT);
            }
        }

        // блоки
        hoveredNode = -1;
        hoveredPort = -1;
        hoveredPortType = null;
        int hoveredInPort = inputPortAt(graph, mouseX, mouseY);
        int hoveredOutPort = outputPortAt(graph, mouseX, mouseY);

        Font font = UiKit.font();
        for (int i = 0; i < graph.nodes.size(); i++) {
            SpellNode node = graph.nodes.get(i);
            SpellModuleType type = SpellModules.get(node.moduleId);
            if (type == null) {
                continue;
            }
            Rect r = blockRect(node);
            boolean over = nodeAt(graph, mouseX, mouseY) == i;

            // панель рисуется одним общим методом с PNG-превью: две копии
            // разъезжаются при первой правке, и превью перестаёт быть правдой
            int category = UiKit.moduleColor(Palette.category(type.category()));
            ModuleFrame.draw(surface(gfx, font), r.x, r.y, r.w, r.h, category,
                    ModuleStyle.glyph(type), over);

            if (isErrorNode(i)) {
                // модуль, где упало значение — красная рамка и маркер
                gfx.renderOutline(r.x - 2, r.y - 2, r.w + 4, r.h + 4, UiKit.error());
                int mx = r.x + r.w - 4;
                int my = r.y + 2;
                gfx.fill(mx - 3, my - 3, mx + 4, my + 4, UiKit.error());
                gfx.fill(mx - 1, my - 2, mx + 1, my + 2, 0xFF1B1B24);
                gfx.fill(mx - 1, my, mx + 1, my + 1, 0xFF1B1B24);
            } else if (i == selectedNode) {
                gfx.renderOutline(r.x - 1, r.y - 1, r.w + 2, r.h + 2, UiKit.ACCENT);
            } else if (i == dragNode) {
                gfx.renderOutline(r.x - 2, r.y - 2, r.w + 4, r.h + 4, UiKit.ACCENT);
            } else if (over) {
                gfx.renderOutline(r.x - 1, r.y - 1, r.w + 2, r.h + 2, UiKit.BORDER_LIGHT);
            }

            int mk = marker();
            // входы: форма и цвет = тип значения, неподключённый — красный
            for (int p = 0; p < type.inputs(); p++) {
                int conn = p < node.inputConnections.size() ? node.inputConnections.get(p) : -1;
                int[] pos = inputPos(r, type, p);
                boolean optional = type.isOptional(p);
                int color = conn >= 0 ? type.inputType(p).color()
                        : (optional ? UiKit.BORDER_LIGHT : UiKit.error());
                gfx.fill(r.x - 1, pos[1] + mk, r.x, pos[1] + mk + 1, color);
                if (optional && conn < 0) {
                    // необязательный вход — полый маркер (подсказка: можно не подключать)
                    portShapesHollow(gfx, type.inputType(p).shape(), pos[0], pos[1] + mk, mk, color);
                } else {
                    PortShapes.draw(gfx, type.inputType(p).shape(), pos[0], pos[1] + mk, mk, color);
                }
            }
            // выходы
            for (int p = 0; p < type.outputs(); p++) {
                int[] pos = outputPos(r, type, p);
                boolean active = linkFrom == i && linkPort == p;
                int color = active ? UiKit.ACCENT : type.outputType(p).color();
                gfx.fill(r.x + r.w + 1, pos[1] + mk, r.x + r.w + 2, pos[1] + mk + 1, color);
                PortShapes.draw(gfx, type.outputType(p).shape(), pos[0], pos[1] + mk, mk, color);
            }

            if (r.w > 34) {
                String label = UiKit.fit(font,
                        Component.translatable(type.nameKey()).getString(), r.w - 6);
                gfx.drawString(font, label, r.x + 3, r.y + r.h - font.lineHeight - 1,
                        UiKit.TEXT, false);
            }

            // включённые модификаторы — короткие буквы в правом верхнем углу
            if (!node.modifiers.isEmpty() && r.w > 40) {
                StringBuilder badges = new StringBuilder();
                for (String id : node.modifiers) {
                    var modifier = com.github.fireprime5.spellcraft.spell.SpellModifiers.byId(id);
                    if (modifier != null) {
                        badges.append(modifier.label().getString().substring(0, 1));
                    }
                }
                if (badges.length() > 0) {
                    gfx.drawString(font, badges.toString(),
                            r.x + r.w - 3 - font.width(badges.toString()), r.y - font.lineHeight - 2,
                            UiKit.WARN, false);
                }
            }

            if (over) {
                hoveredNode = i;
                hoveredPort = hoveredInPort >= 0 ? hoveredInPort : hoveredOutPort;
                hoveredPortIsInput = hoveredInPort >= 0;
                hoveredPortType = hoveredInPort >= 0
                        ? (hoveredInPort < type.inputs() ? type.inputType(hoveredInPort) : null)
                        : (hoveredOutPort >= 0 && hoveredOutPort < type.outputs()
                        ? type.outputType(hoveredOutPort) : null);
            }
        }
    }

    private boolean isSegmentHovered(int x1, int y1, int x2, int y2, double mx, double my) {
        int mid = (x1 + x2) / 2;
        return pointNear(mx, my, x1, y1, mid, y1)
                || pointNear(mx, my, mid, Math.min(y1, y2), mid, Math.max(y1, y2))
                || pointNear(mx, my, mid, y2, x2, y2);
    }

    /** Точка рядом с отрезком (с допуском), чтобы линию было легко кликнуть. */
    private static boolean pointNear(double px, double py, int x1, int y1, int x2, int y2) {
        final double tol = 3.5;
        double dx = x2 - x1;
        double dy = y2 - y1;
        double lenSq = dx * dx + dy * dy;
        double t = lenSq == 0 ? 0 : ((px - x1) * dx + (py - y1) * dy) / lenSq;
        t = Math.clamp(t, 0, 1);
        double cx = x1 + t * dx;
        double cy = y1 + t * dy;
        return Math.abs(px - cx) <= tol && Math.abs(py - cy) <= tol;
    }

    /**
     * Соединение под курсором: возвращает {targetNode, inputPort} или null.
     * Клик по линии разрывает связь.
     */
    public int[] connectionAt(SpellGraph graph, double mx, double my) {
        for (int i = connections.size() - 1; i >= 0; i--) {
            int[] c = connections.get(i);
            if (isSegmentHovered(c[0], c[1], c[2], c[3], mx, my)) {
                if (c[4] >= 0 && c[4] < graph.nodes.size() && c[5] >= 0) {
                    return new int[]{c[4], c[5]};
                }
            }
        }
        return null;
    }

    /** Есть ли связь под курсором (для курсора/подсветки). */
    public boolean overConnection(SpellGraph graph, double mx, double my) {
        return connectionAt(graph, mx, my) != null;
    }

    /** Полый (необязательный) маркер порта. */
    private void portShapesHollow(GuiGraphics gfx, PortType.Shape shape, int cx, int cy, int size,
                                  int color) {
        // рисуем контур фигуры: заливка затемняется, остаётся рамка
        gfx.fill(cx - size - 1, cy - size - 1, cx + size + 2, cy + size + 2,
                UiKit.PANEL_DARK);
        PortShapes.draw(gfx, shape, cx, cy, size + 1, color);
        gfx.fill(cx - size, cy - size, cx + size + 1, cy + size + 1, UiKit.PANEL_DARK);
    }

    /** Ортогональная ломаная со стрелкой. */
    private void connection(GuiGraphics gfx, int x1, int y1, int x2, int y2, int color) {
        int mid = (x1 + x2) / 2;
        gfx.fill(x1, y1, Math.max(mid, x1 + 1), y1 + 1, color);
        gfx.fill(mid, Math.min(y1, y2), mid + 1, Math.max(y1, y2) + 1, color);
        gfx.fill(mid, y2, Math.max(x2, mid + 1), y2 + 1, color);
        // стрелка вправо перед маркером входа
        gfx.fill(x2 - 4, y2 - 2, x2, y2 + 3, color);
        gfx.fill(x2 - 5, y2 - 1, x2 - 4, y2 + 2, color);
    }
}
