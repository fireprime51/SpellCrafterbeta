package com.github.fireprime5.spellcraft.network;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Линии в блоке: слоты, подключения, сериализация.
 *
 * <p>Три линии в одном блоке — ради них всё и затевалось, и дешевле всего
 * проверяется именно геометрия: блок-сущность и рендерер в тестах не поднять, а
 * слоты, порты и маски считаются чистыми функциями.
 *
 * <p>Особенно важно, что линии разных слотов не пересекаются. Пересечение
 * даёт ровно то, что было на первом скриншоте Modern Industrialization, только
 * в виде клубка: три линии сливаются в одну точку, и понять, где чья, нельзя.
 */
class NetworkLineTest {

    private static final Set<Direction> ВСЕ = EnumSet.allOf(Direction.class);

    /**
     * В одну грань входят три разные точки.
     *
     * <p>Это и есть «с одной стороны несколько проводов разных цветов»: соседи
     * различаются не блоком, а точкой на грани, поэтому линии стоят рядом, а не
     * одна на другой.
     */
    @Test
    void вОднуГраньВходятТриРазныеТочки() {
        double[] порт0 = NetworkGeometry.port(0, Direction.NORTH);
        double[] порт1 = NetworkGeometry.port(1, Direction.NORTH);
        double[] порт2 = NetworkGeometry.port(2, Direction.NORTH);
        assertNotEquals(Arrays.toString(порт0), Arrays.toString(порт1),
                "слоты 0 и 1 входят в грань в одной точке");
        assertNotEquals(Arrays.toString(порт1), Arrays.toString(порт2),
                "слоты 1 и 2 входят в грань в одной точке");
        // и все три лежат на самой грани, а не внутри блока
        for (double[] порт : List.of(порт0, порт1, порт2)) {
            assertEquals(0.0, порт[2], "порт на северной грани должен иметь z = 0");
        }
    }

    /**
     * То же для боковой и верхней граней: смещение всегда по двум осям.
     *
     * <p>Ошибка с осью выдала бы трубу, торчащую из блока по толщине, и в грань
     * машины она входила бы мимо.
     */
    @Test
    void портЛежитНаСвоейГрани() {
        for (Direction direction : Direction.values()) {
            double[] порт = NetworkGeometry.port(1, direction);
            int ось = direction.getAxis().ordinal();
            assertEquals(direction.getAxisDirection() == Direction.AxisDirection.NEGATIVE
                            ? 0.0 : 16.0, порт[ось],
                    "порт в сторону " + direction + " должен лежать на грани");
            for (int i = 0; i < 3; i++) {
                if (i != ось) {
                    assertEquals(8.0, порт[i],
                            "порт в сторону " + direction + " должен иметь смещение 8");
                }
            }
        }
    }

    /** Линии разных слотов не занимают ни одной общей клетки. */
    @Test
    void линииРазныхСлотовНеПересекаются() {
        for (Set<Direction> стороны : List.of(ВСЕ,
                EnumSet.of(Direction.NORTH, Direction.SOUTH),
                EnumSet.of(Direction.UP, Direction.DOWN, Direction.EAST))) {
            boolean[] первый = клетки(NetworkGeometry.shape(0, стороны));
            boolean[] второй = клетки(NetworkGeometry.shape(1, стороны));
            boolean[] третий = клетки(NetworkGeometry.shape(2, стороны));
            assertTrue(общих(первый, второй) == 0,
                    "слоты 0 и 1 пересекаются при подключениях " + стороны);
            assertTrue(общих(второй, третий) == 0,
                    "слоты 1 и 2 пересекаются при подключениях " + стороны);
            assertTrue(общих(первый, третий) == 0,
                    "слоты 0 и 2 пересекаются при подключениях " + стороны);
        }
    }

    /**
     * Линия доходит до грани блока.
     *
     * <p>Не доходит — она кончается внутри блока, и в машину ничего не входит:
     * визуально это оборванный конец в воздухе.
     */
    @Test
    void линияДоходитДоГрани() {
        for (Direction direction : Direction.values()) {
            boolean[] форма = клетки(NetworkGeometry.shape(1, EnumSet.of(direction)));
            int ось = direction.getAxis().ordinal();
            boolean уГрани = false;
            for (int x = 0; x < 16 && !уГрани; x++) {
                for (int y = 0; y < 16 && !уГрани; y++) {
                    for (int z = 0; z < 16 && !уГрани; z++) {
                        if (!форма[клетка(x, y, z)]) {
                            continue;
                        }
                        int[] p = {x, y, z};
                        if (direction.getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
                            уГрани = p[ось] == 0;
                        } else {
                            уГрани = p[ось] == 15;
                        }
                    }
                }
            }
            assertTrue(уГрани, "линия в сторону " + direction + " не дошла до грани блока");
        }
    }

    /**
     * Линия без подключений видна и её можно схватить.
     *
     * <p>Пустая форма у блока сети означала, что по нему нельзя ни кликнуть, ни
     * поставить рядом другой блок: место выглядело занятым, но пустым, и
     * «провод не ставился». Сердцевина линии держит форму всегда.
     */
    @Test
    void линияБезПодключенийВидна() {
        for (int slot = 0; slot < NetworkGeometry.SLOTS; slot++) {
            assertTrue(покрыто(клетки(NetworkGeometry.shape(slot, Set.of()))),
                    "слот " + slot + " без подключений не виден совсем");
            // сердцевина стоит в центре слота и попадает под точку в блоке
            double смещение = NetworkGeometry.offset(slot);
            double центр = (смещение + NetworkGeometry.SIDE / 2) / 16;
            assertTrue(NetworkGeometry.inside(slot, Set.of(),
                            new net.minecraft.world.phys.Vec3(центр, центр, центр)),
                    "по линии без подключений нельзя кликнуть: " + slot);
        }
    }

    /**
     * Подключения — это маска, а не свойства блока.
     *
     * <p>Свойствами такое не выразить: три линии по шесть сторон плюс цветы —
     * это десятки тысяч вариантов, и blockstate их не переживает. Маска в NBT —
     * шесть бит на линию.
     */
    @Test
    void подключенияЖивутВМаске() {
        NetworkLine линия = NetworkLine.empty(LineKind.ENERGY, DyeColor.RED);
        assertFalse(линия.connected(), "новая линия никуда не подключена");
        линия.connect(Direction.NORTH, true);
        линия.connect(Direction.DOWN, true);
        assertTrue(линия.connected(Direction.NORTH));
        assertTrue(линия.connected(Direction.DOWN));
        assertFalse(линия.connected(Direction.UP));
        assertEquals(EnumSet.of(Direction.NORTH, Direction.DOWN), линия.sides());
        линия.connect(Direction.NORTH, false);
        assertFalse(линия.connected(Direction.NORTH), "отключение не сработало");
        assertTrue(линия.connected(Direction.DOWN), "отключение снесло чужую сторону");
    }

    /**
     * Лишние биты в маске отсекаются: битый NBT не должен рисовать лишние лапы.
     *
     * <p>Проверяются оба входа: и конструктор, и присваивание маски. Раньше маска
     * зажималась только в конструкторе, и блок-сущность, присваивавшая её
     * напрямую, могла притащить в блок биты за пределами шести сторон.
     */
    @Test
    void маскаЗажимаетсяШестьюБитами() {
        NetworkLine линия = new NetworkLine(LineKind.ENERGY, DyeColor.RED,
                0b1111_1111, 0);
        assertEquals(NetworkGeometry.ALL_SIDES, линия.mask(),
                "в маске появились биты за пределами шести сторон");
        линия.mask(0b11111111);
        assertEquals(NetworkGeometry.ALL_SIDES, линия.mask(),
                "присваивание маски не зажало лишние биты");
        int север = 1 << Direction.NORTH.get3DDataValue();
        линия.mask(север);
        assertEquals(север, линия.mask(), "обычная маска должна проходить как есть");
    }

    /**
     * Цвет и вид — это идентичность сети.
     *
     * <p>Два провода одного цвета свариваются в одну сеть, провода разных цветов
     * — нет: иначе цвет перестаёт что-либо разделять, и линии рядом всегда были
     * бы одной сетью.
     */
    @Test
    void цветИВидОпределяютСеть() {
        NetworkLine красный = NetworkLine.empty(LineKind.ENERGY, DyeColor.RED);
        NetworkLine синий = NetworkLine.empty(LineKind.ENERGY, DyeColor.BLUE);
        NetworkLine бездна = NetworkLine.empty(LineKind.ABYSS, DyeColor.WHITE);
        assertTrue(красный.sameNetwork(NetworkLine.empty(LineKind.ENERGY, DyeColor.RED)),
                "два красных провода должны быть одной сетью");
        assertFalse(красный.sameNetwork(синий), "провода разных цветов — разные сети");
        assertFalse(красный.sameNetwork(бездна),
                "провод и труба бездны — разные сети");
        assertFalse(красный.sameNetwork(null), "пустой сосед не сеть");
    }

    /** Линия переживает NBT: иначе после перезагрузки мира сеть распалась бы. */
    @Test
    void линияПереживаетNbt() {
        NetworkLine линия = new NetworkLine(LineKind.ENERGY, DyeColor.LIGHT_BLUE,
                1 << Direction.WEST.get3DDataValue(), 777);
        CompoundTag tag = new CompoundTag();
        линия.save(tag);
        NetworkLine обратно = NetworkLine.load(tag);
        assertEquals(LineKind.ENERGY, обратно.kind());
        assertEquals(DyeColor.LIGHT_BLUE, обратно.color());
        assertEquals(линия.mask(), обратно.mask());
        assertEquals(777, обратно.stored());
    }

    /** Битый NBT читается как обычная линия, а не роняет мир. */
    @Test
    void битыйNbtНеЛомаетЛинию() {
        NetworkLine обратно = NetworkLine.load(new CompoundTag());
        assertEquals(LineKind.ENERGY, обратно.kind());
        assertEquals(DyeColor.WHITE, обратно.color());
        assertEquals(0, обратно.mask());
        assertEquals(0, обратно.stored());
        assertEquals(LineKind.ENERGY, LineKind.byId("бездна"),
                "неизвестное имя вида должно читаться как энергия, а не падать");
    }

    /** У трубы бездны цвета нет: иначе получилось бы шестнадцать одинаковых сетей. */
    @Test
    void уТрубыБездныЦветаНет() {
        NetworkLine бездна = new NetworkLine(LineKind.ABYSS, DyeColor.MAGENTA, 0, 0);
        assertEquals(DyeColor.WHITE, бездна.color(),
                "труба бездны не должна хранить цвет");
    }

    private static int общих(boolean[] a, boolean[] b) {
        int всего = 0;
        for (int i = 0; i < a.length; i++) {
            if (a[i] && b[i]) {
                всего++;
            }
        }
        return всего;
    }

    private static boolean покрыто(boolean[] клетки) {
        for (boolean клетка : клетки) {
            if (клетка) {
                return true;
            }
        }
        return false;
    }

    private static boolean[] клетки(net.minecraft.world.phys.shapes.VoxelShape форма) {
        boolean[] занято = new boolean[16 * 16 * 16];
        for (var aabb : форма.toAabbs()) {
            for (int x = (int) (aabb.minX * 16); x < aabb.maxX * 16; x++) {
                for (int y = (int) (aabb.minY * 16); y < aabb.maxY * 16; y++) {
                    for (int z = (int) (aabb.minZ * 16); z < aabb.maxZ * 16; z++) {
                        занято[клетка(x, y, z)] = true;
                    }
                }
            }
        }
        return занято;
    }

    private static int клетка(int x, int y, int z) {
        return (x << 8) | (y << 4) | z;
    }
}
