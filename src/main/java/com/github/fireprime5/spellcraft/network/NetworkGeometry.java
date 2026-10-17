package com.github.fireprime5.spellcraft.network;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Геометрия линий в блоке сети: слоты, точки входа в грани и отрезки.
 *
 * <p>Слотов три, и они смещены по диагонали блока: центр линии стоит в
 * (5, 5, 5), (8, 8, 8) или (11, 11, 11) пикселях, а в грани блока линия входит
 * в ту же точку, но по двум осям. Отсюда и нужный вид: три линии разных цветов
 * входят в <b>одну</b> грань машины в трёх разных точках и не пересекаются
 * дальше — коридор из нескольких линий в одном блоке не сваливается в клубок.
 *
 * <p>Смещение взято у Modern Industrialization: там ровно три слота на блок и
 * та же раскладка 5/16, 8/16, 11/16 при сечении 2 пикселя.
 *
 * <p>Каждый отрезок осевой: линия идёт от центра своего слота прямо к точке
 * входа. Никаких наклонных граней — в blockstate-модели их и не собрать, да и
 * в рендерере лишняя работа; изгиб получается сам, когда линия смотрит в две
 * стороны сразу.
 */
public final class NetworkGeometry {

    /** Сколько линий помещается в блок. */
    public static final int SLOTS = 3;

    /** Сечение линии в пикселях: два, чтобы линия читалась кабелем, а не блоком. */
    public static final double SIDE = 2;

    /** Смещение центра слота от угла блока, в пикселях. */
    public static final double[] OFFSET = {5, 8, 11};

    /** Все шесть сторон: маска подключений целиком. */
    public static final int ALL_SIDES = 0b111111;

    private NetworkGeometry() {
    }

    /** Смещение слота, зажатое в пределы: битый номер не должен ломать геометрию. */
    public static double offset(int slot) {
        int слот = Math.clamp(slot, 0, SLOTS - 1);
        return OFFSET[слот];
    }

    /** Точка входа линии в грань: смещение по двум осям, перпендикулярным оси грани. */
    public static double[] port(int slot, Direction direction) {
        double смещение = offset(slot);
        double[] точка = new double[3];
        int ось = direction.getAxis().ordinal();
        точка[ось] = direction.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? 0 : 16;
        for (int i = 0; i < 3; i++) {
            if (i != ось) {
                точка[i] = смещение;
            }
        }
        return точка;
    }

    /** Центр линии в блоке: смещение по всем трём осям сразу. */
    public static double[] center(int slot) {
        double смещение = offset(slot);
        return new double[]{смещение, смещение, смещение};
    }

    /**
     * Отрезки линии: от центра слота к каждой подключённой грани.
     *
     * <p>Сердцевина в списке всегда, даже без подключений: она держит форму
     * блока, по ней снимается линия и по ней видно, что блок не пуст.
     *
     * <p>Точка входа берётся из {@link #port}, а не считается тут же: два места,
     * где решается, где входит линия, рано или поздно разойдутся, и линия
     * окажется не у грани.
     */
    public static List<Segment> segments(int slot, Set<Direction> стороны) {
        double смещение = offset(slot);
        // сердцевина есть всегда: по ней блок выделяется и снимается линия, и
        // без единого подключения блок не превращается в пустое место, которое
        // нельзя ни тронуть, ни освободить
        List<Segment> отрезки = new ArrayList<>(стороны.size() + 1);
        отрезки.add(new Segment(смещение, смещение, смещение,
                смещение + SIDE, смещение + SIDE, смещение + SIDE));
        for (Direction direction : стороны) {
            double[] точка = port(slot, direction);
            int ось = direction.getAxis().ordinal();
            // вход в грань блока: луч тянется от центра слота до самой грани,
            // то есть до 0 или 16 по своей оси
            double[] low = {смещение, смещение, смещение};
            double[] high = {смещение + SIDE, смещение + SIDE, смещение + SIDE};
            if (direction.getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
                low[ось] = точка[ось];
            } else {
                high[ось] = точка[ось];
            }
            отрезки.add(new Segment(low[0], low[1], low[2], high[0], high[1], high[2]));
        }
        return отрезки;
    }

    /**
     * Форма линии: сердцевина плюс отрезки по подключённым сторонам.
     *
     * @param slot номер слота, 0..{@link #SLOTS}-1
     * @param стороны стороны, в которые линия смотрит
     */
    public static VoxelShape shape(int slot, Set<Direction> стороны) {
        VoxelShape форма = null;
        for (Segment отрезок : segments(slot, стороны)) {
            форма = форма == null ? отрезок.shape()
                    : Shapes.join(форма, отрезок.shape(), BooleanOp.OR);
        }
        return форма == null ? Shapes.empty() : форма;
    }

    /**
     * Попадает ли точка в линию.
     *
     * <p>Точка — в координатах блока, то есть в пределах 0..1 по всем трём осям.
     * Проверка своя, а не через {@code VoxelShape}: у формы в 1.21 нет метода
     * «точка внутри», и через {@code clip} пришлось бы городить луч из точки в
     * точку.
     *
     * @param slot номер слота
     * @param стороны стороны, в которые линия смотрит
     * @param точка точка внутри блока, 0..1
     */
    public static boolean inside(int slot, Set<Direction> стороны, net.minecraft.world.phys.Vec3 точка) {
        for (Segment отрезок : segments(slot, стороны)) {
            if (точка.x() >= отрезок.x1() / 16 && точка.x() < отрезок.x2() / 16
                    && точка.y() >= отрезок.y1() / 16 && точка.y() < отрезок.y2() / 16
                    && точка.z() >= отрезок.z1() / 16 && точка.z() < отрезок.z2() / 16) {
                return true;
            }
        }
        return false;
    }

    /**
     * Грани отрезка: шесть штук, у каждой четыре вершины и одна постоянная
     * координата — та самая ось, по которой грань лежит.
     *
     * <p>Грань строится здесь, а не в рендерере, по двум причинам. Первая:
     * перекошенная «грань» с двумя разными значениями одной оси не рисуется
     * вообще, и ошибка выглядит как «провод невидим» — нигде не сказано, что
     * дело в координатах вершин. Вторая: такую ошибку можно проверить тестом, а
     * код рендерера — нельзя, он запускается только в игре.
     *
     * @param отрезок отрезок линии в координатах блока
     */
    public static List<Face> faces(Segment отрезок) {
        List<Face> грани = new ArrayList<>(6);
        грани.add(face(отрезок, 1, отрезок.y1(), 0, -1, 0));
        грани.add(face(отрезок, 1, отрезок.y2(), 0, 1, 0));
        грани.add(face(отрезок, 2, отрезок.z1(), 0, 0, -1));
        грани.add(face(отрезок, 2, отрезок.z2(), 0, 0, 1));
        грани.add(face(отрезок, 0, отрезок.x1(), -1, 0, 0));
        грани.add(face(отрезок, 0, отрезок.x2(), 1, 0, 0));
        return грани;
    }

    /**
     * Грань на постоянной координате: четыре вершины по периметру прямоугольника.
     *
     * <p>Поперечные оси берутся у отрезка по именам, а не «первая поперечная и
     * вторая». Предыдущая редакция подставляла на место дальнего края оси
     * начало сечения, и грань схлопывалась в линию нулевой площади: у четырёх
     * граней из шести не было ни единого квадратного сантиметра, и они не
     * рисовались вообще.
     *
     * <p>Порядок вершин идёт по периметру, а не «бандом» с диагоналями, и
     * разворачивается на противоположной стороне оси. У {@code cutout} отсечение
     * задних граней включено, поэтому обход не с той стороны отсекает грань
     * наполовину, а бант вместо прямоугольника отсекает половину площади.
     */
    private static Face face(Segment отрезок, int ось, double значение,
                             float nx, float ny, float nz) {
        int[] поперечные = {1, 0, 0};
        int u = поперечные[ось];
        int v = ось == 2 ? 1 : 2;
        double[] мин = {отрезок.x1(), отрезок.y1(), отрезок.z1()};
        double[] макс = {отрезок.x2(), отрезок.y2(), отрезок.z2()};
        double u1 = мин[u];
        double u2 = макс[u];
        double v1 = мин[v];
        double v2 = макс[v];
        double[][] вершины = new double[4][3];
        for (int i = 0; i < 4; i++) {
            вершины[i][ось] = значение;
            вершины[i][u] = i == 0 || i == 3 ? u1 : u2;
            вершины[i][v] = i < 2 ? v1 : v2;
        }
        // знак вкосого произведения u на v вдоль оси грани: (0,1,2), (1,2,0) и
        // (2,0,1) дают плюс, перестановка — минус
        int знак = ось == 1 ? -1 : 1;
        float[] нормаль = {nx, ny, nz};
        if (Math.signum(нормаль[ось]) != знак) {
            double[][] обратный = {вершины[3], вершины[2], вершины[1], вершины[0]};
            вершины = обратный;
        }
        return new Face(вершины, (float) значение, nx, ny, nz);
    }

    /**
     * Грань отрезка: четыре вершины и нормаль.
     *
     * @param вершины четыре вершины в координатах блока
     * @param значение постоянная координата грани по её оси
     * @param nx нормаль по X
     * @param ny нормаль по Y
     * @param nz нормаль по Z
     */
    public record Face(double[][] вершины, float значение, float nx, float ny, float nz) {

        /** Постоянная ось грани: 0 — X, 1 — Y, 2 — Z. */
        public int axis() {
            if (nx != 0) {
                return 0;
            }
            return ny != 0 ? 1 : 2;
        }
    }

    /** Прямоугольный отрезок линии в координатах блока. */
    public record Segment(double x1, double y1, double z1,
                          double x2, double y2, double z2) {

        /** Форма отрезка: коробка Minecraft из тех же координат. */
        public VoxelShape shape() {
            return net.minecraft.world.level.block.Block.box(x1, y1, z1, x2, y2, z2);
        }
    }

    /** Форма всех линий блока: объединение по слотам. */
    public static VoxelShape shapeAll(List<NetworkLine> линии) {
        VoxelShape форма = Shapes.empty();
        for (int slot = 0; slot < линии.size() && slot < SLOTS; slot++) {
            NetworkLine линия = линии.get(slot);
            if (линия != null) {
                форма = Shapes.join(форма, shape(slot, стороны(линия)), BooleanOp.OR);
            }
        }
        return форма;
    }

    private static Set<Direction> стороны(NetworkLine линия) {
        Set<Direction> стороны = EnumSet.noneOf(Direction.class);
        for (Direction direction : Direction.values()) {
            if (линия.connected(direction)) {
                стороны.add(direction);
            }
        }
        return стороны;
    }
}
