package com.github.fireprime5.spellcraft.network;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    /** Вода и лава как идентификаторы: стек жидкости без мира не собирается. */
    private static final net.minecraft.resources.ResourceLocation ВОДА =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "water");
    private static final net.minecraft.resources.ResourceLocation ЛАВА =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "lava");

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

    /**
     * У каждой грани одна постоянная координата.
     *
     * <p>Так было не так: у нижней грани две вершины стояли на нижней плоскости
     * и две — на верхней. Такая «грань» не рисуется вообще, и снаружи это
     * выглядит как невидимый провод: блок есть, линии есть, а на экране пусто.
     */
    @Test
    void граниПлоские() {
        for (int slot = 0; slot < NetworkGeometry.SLOTS; slot++) {
            for (NetworkGeometry.Segment отрезок
                    : NetworkGeometry.segments(slot, Set.of())) {
                List<NetworkGeometry.Face> грани = NetworkGeometry.faces(отрезок);
                assertEquals(6, грани.size(),
                        "у коробки шесть граней, а не " + грани.size());
                for (NetworkGeometry.Face грань : грани) {
                    int ось = грань.axis();
                    for (double[] вершина : грань.вершины()) {
                        assertEquals(грань.значение(), вершина[ось], 0.001,
                                "грань по оси " + ось + " должна лежать на одной "
                                        + "плоскости, а вершины разошлись: "
                                        + java.util.Arrays.toString(вершина));
                    }
                    // нормаль смотрит наружу коробки
                    float[] нормаль = {грань.nx(), грань.ny(), грань.nz()};
                    assertTrue(Math.abs(нормаль[ось]) == 1f,
                            "нормаль грани должна быть по её оси, а не "
                                    + java.util.Arrays.toString(нормаль));
                }
            }
        }
    }

    /**
     * У каждой грани есть площадь, и обход вершин идёт по периметру.
     *
     * <p>Проверка на глаз по «плоской грани» тут не годилась: грань нулевой
     * площади тоже плоская, все её вершины лежат на одной плоскости. Четыре грани
     * из шести были схлопнуты в линию — по постоянной координате это не видно.
     *
     * <p>Знак площади по шнуровке проверяется вместе с нормалью: обход «бант» с
     * диагоналями вместо сторон даёт треугольники противоположной ориентации, и
     * при отсечении задних граней половина грани не рисуется.
     */
    @Test
    void граниНеВырождены() {
        List<NetworkGeometry.Segment> отрезки = List.of(
                new NetworkGeometry.Segment(5, 5, 5, 7, 7, 7),
                new NetworkGeometry.Segment(5, 5, 5, 16, 7, 7),
                new NetworkGeometry.Segment(0, 5, 5, 7, 7, 7),
                new NetworkGeometry.Segment(5, 5, 5, 7, 16, 7),
                new NetworkGeometry.Segment(5, 0, 5, 7, 7, 7),
                new NetworkGeometry.Segment(5, 5, 5, 7, 7, 16),
                new NetworkGeometry.Segment(5, 5, 0, 7, 7, 7));
        for (NetworkGeometry.Segment отрезок : отрезки) {
            List<NetworkGeometry.Face> грани = NetworkGeometry.faces(отрезок);
            assertEquals(6, грани.size(), "у коробки шесть граней");
            for (NetworkGeometry.Face грань : грани) {
                int ось = грань.axis();
                int u = ось == 0 ? 1 : 0;
                int v = ось == 2 ? 1 : 2;
                double площадь = 0;
                for (int i = 0; i < 4; i++) {
                    double[] p = грань.вершины()[i];
                    double[] q = грань.вершины()[(i + 1) % 4];
                    площадь += p[u] * q[v] - q[u] * p[v];
                }
                assertTrue(Math.abs(площадь) > 1e-6,
                        "грань оси " + ось + " на " + грань.значение()
                                + " схлопнулась в линию: площадь " + площадь);
                float[] нормаль = {грань.nx(), грань.ny(), грань.nz()};
                int знак = (int) Math.signum(площадь) * (ось == 1 ? -1 : 1);
                assertTrue(знак == Math.signum(нормаль[ось]),
                        "обход вершин не совпадает с нормалью: площадь " + площадь
                                + " при нормали " + java.util.Arrays.toString(нормаль)
                                + " — такую грань отсечёт по обратной стороне");
            }
        }
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

    /**
     * Снятие линии не сдвигает остальных.
     *
     * <p>Слот — это номер геометрии (5/16, 8/16, 11/16), и соседний блок про ту же
     * линию знает по виду и цвету, а не по слоту. Если после снятия первой линии
     * вторая переехала с 8/16 на 5/16, её точка входа в грань разошлась с
     * соседним блоком на три пикселя, а снятая по клику линия уже не та.
     */
    @Test
    void снятиеНеСдвигаетОстальныхЛиний() {
        NetworkLines линии = new NetworkLines();
        линии.add(LineKind.ENERGY, DyeColor.RED);
        линии.add(LineKind.ENERGY, DyeColor.BLUE);
        линии.add(LineKind.ENERGY, DyeColor.GREEN);
        assertEquals(DyeColor.RED, линии.remove(0).color(), "снята именно та линия, под которую кликнули");

        assertEquals(3, линии.size(),
                "длина списка — это номер последнего занятого слота плюс один");
        assertNull(линии.line(0), "слот 0 должен остаться пустым, а не сдвинуться");
        assertEquals(DyeColor.BLUE, линии.line(1).color(),
                "синяя осталась в своём слоте, а не переехала на 5/16");
        assertEquals(DyeColor.GREEN, линии.line(2).color(),
                "зелёная осталась в своём слоте");
        assertEquals(2, линии.count(), "линий осталось две, а не три");
        assertTrue(линии.hasFreeSlot(), "пустой слот — это свободный слот");
    }

    /**
     * Новая линия занимает первый пустой слот, а не следующий по счёту.
     *
     * <p>Иначе блок из двух линий в слотах 0 и 2 не влез бы: третья пошла бы в
     * слот 3, которого нет.
     */
    @Test
    void новаяЛинияЗанимаетПервыйПустойСлот() {
        NetworkLines линии = new NetworkLines();
        линии.add(LineKind.ENERGY, DyeColor.RED);
        линии.add(LineKind.ENERGY, DyeColor.BLUE);
        линии.add(LineKind.ENERGY, DyeColor.GREEN);
        линии.remove(0);
        assertTrue(линии.add(LineKind.ENERGY, DyeColor.YELLOW));
        assertEquals(DyeColor.YELLOW, линии.line(0).color(),
                "освободившийся слот должен занять новая линия");
        assertFalse(линии.hasFreeSlot(), "все три слота снова заняты");
    }

    /**
     * Слоты переживают запись и чтение NBT.
     *
     * <p>Если номер слота не писать в NBT, чтение файла снова сдвинет линии: у
     * блока «красная, пусто, зелёная» после перезагрузки мира зелёная оказалась
     * бы во втором слоте на 8/16 вместо своего 11/16.
     */
    @Test
    void слотыПереживаютNbt() {
        NetworkLines линии = new NetworkLines();
        линии.add(LineKind.ENERGY, DyeColor.RED);
        линии.add(LineKind.ENERGY, DyeColor.BLUE);
        линии.add(LineKind.ENERGY, DyeColor.GREEN);
        линии.remove(1);

        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        линии.save(tag);

        NetworkLines после = new NetworkLines();
        после.load(tag);
        assertEquals(3, после.size(), "пустой слот не должен обрезаться при записи");
        assertEquals(DyeColor.RED, после.line(0).color());
        assertNull(после.line(1), "середина должна остаться пустой");
        assertEquals(DyeColor.GREEN, после.line(2).color(),
                "зелёная обязана вернуться в свой слот 11/16");
    }

    /**
     * Старый плотный список читается по порядку.
     *
     * <p>Миры, сохранённые до появления номеров слотов, лежат без ключа: такой
     * список нельзя разъезжать, иначе линии поменяются местами после первого
     * же чтения.
     */
    @Test
    void старыйПлотныйСписокЧитаетсяПоПорядку() {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        net.minecraft.nbt.ListTag список = new net.minecraft.nbt.ListTag();
        for (DyeColor цвет : new DyeColor[]{DyeColor.RED, DyeColor.BLUE, DyeColor.GREEN}) {
            список.add(new NetworkLine(LineKind.ENERGY, цвет, 0, 0)
                    .save(new net.minecraft.nbt.CompoundTag()));
        }
        tag.put("lines", список);

        NetworkLines линии = new NetworkLines();
        линии.load(tag);
        assertEquals(DyeColor.RED, линии.line(0).color());
        assertEquals(DyeColor.BLUE, линии.line(1).color());
        assertEquals(DyeColor.GREEN, линии.line(2).color());
    }

    /**
     * Пустой блок сети не считается занятым ни в одном счётчике.
     *
     * <p>На этом стоит ранний выход из рендерера и «блок пуст, разбирать его».
     */
    @Test
    void пустойСлотНеСчитаетсяЛинией() {
        NetworkLines линии = new NetworkLines();
        линии.add(LineKind.ENERGY, DyeColor.RED);
        линии.add(LineKind.ENERGY, DyeColor.BLUE);
        линии.remove(1);
        assertEquals(1, линии.count());
        assertFalse(линии.isEmpty());
        линии.remove(0);
        assertTrue(линии.isEmpty(), "блок без линий должен считаться пустым");
        assertEquals(0, линии.size(), "хвост из пустот обрезан");
    }

    /**
     * Линия переезжает в слот соседа, чтобы стык был ровным.
     *
     * <p>Слот — это точка входа в грань (5/16, 8/16, 11/16). Сосед узнаёт линию по
     * виду и цвету, а стоит она в своём слоте: в одном блоке красная может быть
     * первой, в соседнем — второй. Без переноса оба блока тянутся навстречу друг
     * другу из разных точек, и на стыке видна ступенька в три пикселя.
     */
    @Test
    void линияПереезжаетВСлотСоседа() {
        NetworkLines линии = new NetworkLines();
        линии.add(LineKind.ENERGY, DyeColor.RED);
        линии.add(LineKind.ENERGY, DyeColor.BLUE);
        линии.add(LineKind.ENERGY, DyeColor.GREEN);
        линии.remove(1);

        assertTrue(линии.move(0, 1), "свободный слот должен принять линию");
        assertNull(линии.line(0), "прежний слот линии остаётся пустым");
        assertEquals(DyeColor.RED, линии.line(1).color(),
                "красная встала в слот соседа, а не осталась на 5/16");
        assertEquals(DyeColor.GREEN, линии.line(2).color(), "остальные не тронуты");
        assertFalse(линии.move(1, 2), "занятый слот линию не принимает");
        assertFalse(линии.move(-1, 2), "несуществующего слота нет");
    }

    /**
     * Обмен слотами, когда сосед заполнил их в обратном порядке.
     *
     * <p>Красная у соседа во втором слоте, синяя — в первом. Ни одну из них нельзя
     * сдвинуть в одиночку: обе встанут на место, если поменяться слотами.
     */
    @Test
    void обменСлотовССоседом() {
        NetworkLines линии = new NetworkLines();
        линии.add(LineKind.ENERGY, DyeColor.RED);
        линии.add(LineKind.ENERGY, DyeColor.BLUE);
        assertTrue(линии.swap(0, 1));
        assertEquals(DyeColor.BLUE, линии.line(0).color());
        assertEquals(DyeColor.RED, линии.line(1).color());
        assertFalse(линии.swap(0, 5), "обмен с несуществующим слотом невозможен");
    }

    /** Номер слота ищется по виду и цвету, а не по позиции в списке. */
    @Test
    void номерСлотаИщетсяПоЛинии() {
        NetworkLines линии = new NetworkLines();
        линии.add(LineKind.ENERGY, DyeColor.RED);
        линии.add(LineKind.ABYSS, DyeColor.WHITE);
        assertEquals(0, линии.slot(LineKind.ENERGY, DyeColor.RED));
        assertEquals(1, линии.slot(LineKind.ABYSS, DyeColor.WHITE),
                "у бездны цвет не различает линии");
        assertEquals(-1, линии.slot(LineKind.ENERGY, DyeColor.LIME));

        линии.remove(0);
        assertEquals(-1, линии.slot(LineKind.ENERGY, DyeColor.RED), "снятой линии нет");
        assertEquals(1, линии.slot(LineKind.ABYSS, DyeColor.WHITE),
                "оставшаяся линия не переезжает на освободившийся слот сама");
    }

    /**
     * Перенос в последний слот, когда список короче трёх.
     *
     * <p>Ровно этот случай вылетел в игре: в блоке стояли красная и синяя, зелёный
     * провод клался в третий слот, список дорастал до двух элементов, и запись в
     * третий падала с выходом за границу — на сервере, в обработчике клика.
     */
    @Test
    void переносВПоследнийСлот() {
        NetworkLines линии = new NetworkLines();
        линии.add(LineKind.ENERGY, DyeColor.RED);
        линии.add(LineKind.ENERGY, DyeColor.BLUE);
        assertTrue(линии.move(1, 2), "список должен дорасти до третьего слота");
        assertEquals(DyeColor.BLUE, линии.line(2).color());
        assertEquals(3, линии.size());
        assertEquals(2, линии.count(), "обе линии на месте: синяя просто ушла в третий слот");
        assertEquals(DyeColor.RED, линии.line(0).color(), "красная не тронута");
        assertNull(линии.line(1), "прежний слот синей освободился");
    }

    /**
     * Добавление в последний слот после снятия первой линии.
     *
     * <p>Тот же выход за границу, только на пустом месте: блок из двух линий в
     * слотах 1 и 2 и добавление третьей в слот 0.
     */
    @Test
    void добавлениеВСвободныйСлотПослеСнятия() {
        NetworkLines линии = new NetworkLines();
        линии.add(LineKind.ENERGY, DyeColor.RED);
        линии.add(LineKind.ENERGY, DyeColor.BLUE);
        линии.add(LineKind.ENERGY, DyeColor.GREEN);
        линии.remove(0);
        assertTrue(линии.add(LineKind.ENERGY, DyeColor.LIME));
        assertEquals(DyeColor.LIME, линии.line(0).color());
        assertEquals(3, линии.count(), "третья линия влезла в освободившийся слот");
    }

    /**
     * Обход по слотам переживает перенос линии.
     *
     * <p>Пересчёт подключений идёт по слотам и переносит линии прямо по ходу
     * обхода. Обход по самому списку в этот момент отставал бы или падал бы с
     * ConcurrentModificationException — список меняется структурно.
     */
    @Test
    void переносВоВремяОбходаНеЛомаетСписок() {
        NetworkLines линии = new NetworkLines();
        линии.add(LineKind.ENERGY, DyeColor.RED);
        линии.add(LineKind.ENERGY, DyeColor.BLUE);
        for (int slot = 0; slot < NetworkGeometry.SLOTS; slot++) {
            NetworkLine линия = линии.line(slot);
            if (линия != null && линия.color() == DyeColor.RED) {
                линии.move(slot, 2);
            }
        }
        assertEquals(DyeColor.RED, линии.line(2).color());
        assertEquals(DyeColor.BLUE, линии.line(1).color(), "синяя осталась на месте");
        assertEquals(2, линии.count());
    }

    /**
     * Труба не набирает чужую жидкость в полную.
     *
     * <p>Труба одна на все жидкости, и вид узнаёт от соседа, но наполниться
     * должна только тем, с чем связана: вода и лава в одной линии — это две
     * разные сети в одном блоке.
     */
    @Test
    void трубаНеБерётЧужуюЖидкость() {
        NetworkLine труба = NetworkLine.emptyFluid();
        assertEquals(1000, NetworkFluids.fill(труба, ВОДА, 1000, false),
                "пустая труба без жидкости должна принять воду и запомнить её");
        assertEquals(ВОДА, труба.fluid());
        assertEquals(0, NetworkFluids.fill(труба, ЛАВА, 1000, false),
                "лава в трубу с водой не льётся");
        assertEquals(1000, труба.stored(), "запас не тронут");
    }

    /** В трубу влезает ровно ёмкость, лишнее остаётся у донора. */
    @Test
    void трубаНеПереполняется() {
        NetworkLine труба = NetworkLine.emptyFluid();
        NetworkFluids.fill(труба, ВОДА, NetworkFluids.CAPACITY, false);
        assertEquals(NetworkFluids.CAPACITY, труба.stored());
        assertEquals(0, NetworkFluids.fill(труба, ВОДА, 100, false),
                "полная труба не берёт больше");
        assertEquals(0, NetworkFluids.fill(труба, ВОДА, 100, true),
                "симуляция тоже не должна обещать заливку в полную трубу");
    }

    /** Симуляция ничего не меняет: ею пользуются вёдра и чужие моды. */
    @Test
    void симуляцияНеМеняетТрубу() {
        NetworkLine труба = NetworkLine.emptyFluid();
        assertEquals(1000, NetworkFluids.fill(труба, ВОДА, 1000, true));
        assertEquals(0, труба.stored(), "симуляция не должна наполнять трубу");
        NetworkFluids.fill(труба, ВОДА, 1000, false);
        assertEquals(500, NetworkFluids.чтоМожноСлить(труба, 500, true),
                "симуляция обязана сказать, сколько слилось бы");
        assertEquals(1000, труба.stored(), "симуляция слива не должна уменьшать запас");
        assertEquals(500, NetworkFluids.чтоМожноСлить(труба, 500, false),
                "настоящий слив отдаёт столько, сколько просили");
        assertEquals(500, труба.stored(), "и запас уменьшается на столько же");
    }

    /** Смена жидкости на непустой трубе запрещена: иначе она исчезнет молча. */
    @Test
    void непустаяТрубаНеМеняетЖидкость() {
        NetworkLine труба = NetworkLine.emptyFluid();
        NetworkFluids.fill(труба, ВОДА, 1000, false);
        assertEquals(ВОДА, труба.fluid());
        труба.fluid(ЛАВА);
        assertEquals(ВОДА, труба.fluid(),
                "в трубе вода: смена жидкости молча бы её выбросила");
        труба.stored(0);
        труба.fluid(ЛАВА);
        assertEquals(ЛАВА, труба.fluid(),
                "пустая труба обязана принять другую жидкость");
    }

    /**
     * Две трубы стыкуются, если одна ещё не знает, что в ней.
     *
     * <p>Иначе труба, поставленная рядом с баком, не соединилась бы с ним до
     * первого заполнения, а заполниться ей было бы нечем.
     */
    @Test
    void неизвестнаяТрубаСтыкуетсяСИзвестной() {
        NetworkLine пустая = NetworkLine.emptyFluid();
        NetworkLine сВодой = NetworkLine.emptyFluid();
        NetworkFluids.fill(сВодой, ВОДА, 100, false);
        assertTrue(пустая.sameNetwork(сВодой),
                "труба, которая ещё не знает жидкости, подходит к любой");
        NetworkLine сЛавой = NetworkLine.emptyFluid();
        NetworkFluids.fill(сЛавой, ЛАВА, 100, false);
        assertFalse(сВодой.sameNetwork(сЛавой),
                "уже с водой труба больше не подходит к лаве");
        assertTrue(сВодой.sameNetwork(пустая),
                "но к ещё пустой трубе вода по-прежнему подходит");
    }

    /** Жидкость и её заполнение переживают NBT: труба не превращается в пустую. */
    @Test
    void жидкостьПереживаетNbt() {
        NetworkLine труба = NetworkLine.emptyFluid();
        NetworkFluids.fill(труба, ВОДА, 4321, false);
        NetworkLine после = NetworkLine.load(труба.save(new CompoundTag()));
        assertEquals(4321, после.stored());
        assertEquals(ВОДА, после.fluid());
        assertTrue(NetworkFluids.принимает(после, ВОДА));
        assertFalse(NetworkFluids.принимает(после, ЛАВА));
    }

    /** Ступень заливки делит заполнение на четыре части: кэш меша обязан меняться. */
    @Test
    void ступеньЗаполненияМеняетКартинку() {
        NetworkLine труба = NetworkLine.emptyFluid();
        assertEquals(0, NetworkFluids.ступень(труба));
        NetworkFluids.fill(труба, ВОДА, NetworkFluids.CAPACITY / 2, false);
        int середина = NetworkFluids.ступень(труба);
        assertTrue(середина > 0, "наполовину полная труба видна иначе, чем пустая");
        NetworkFluids.fill(труба, ВОДА, NetworkFluids.CAPACITY, false);
        assertEquals(4, NetworkFluids.ступень(труба));
    }

    /**
     * Жидкость рисуется уже оболочки, иначе её не видно сквозь стекло.
     */
    @Test
    void жидкостьВписываетсяВОтрезок() {
        NetworkGeometry.Segment отрезок = new NetworkGeometry.Segment(5, 5, 5, 7, 7, 7);
        NetworkGeometry.Segment сок = NetworkGeometry.inset(отрезок, 0.5);
        assertTrue(сок.x2() - сок.x1() < отрезок.x2() - отрезок.x1(),
                "жидкость уже оболочки по X");
        assertEquals(5.5, сок.x1(), "жидкость вписана в сечение, а не сдвинута");
        assertEquals(6.5, сок.x2(), "жидкость вписана в сечение с другой стороны");
        // нулевая ширина не схлопывается в точку
        NetworkGeometry.Segment тонкий = new NetworkGeometry.Segment(5, 5, 5, 5, 7, 7);
        NetworkGeometry.Segment внутри = NetworkGeometry.inset(тонкий, 0.5);
        assertEquals(тонкий.x1(), внутри.x1(),
                "сторона без толщины остаётся на месте, а не пропадает");
    }

    /** Труба одна на блок: предмет один, и отличать её нечем. */
    @Test
    void трубаВБлокеОдна() {
        NetworkLines линии = new NetworkLines();
        assertTrue(линии.add(LineKind.FLUID, DyeColor.WHITE));
        assertFalse(линии.add(LineKind.FLUID, DyeColor.WHITE),
                "вторая труба в блок не влезает: предмет один на все жидкости");
        assertTrue(линии.add(LineKind.ENERGY, DyeColor.RED),
                "провод в блок с трубой всё ещё влезает");
        assertEquals(2, линии.count());
        assertEquals(LineKind.FLUID, линии.line(LineKind.FLUID, DyeColor.WHITE).kind());
    }

    /** Труба и труба бездны — разные линии: вид и есть тождество. */
    @Test
    void безднаИТрубаНеПутаются() {
        NetworkLines линии = new NetworkLines();
        assertTrue(линии.add(LineKind.ABYSS, DyeColor.WHITE));
        assertTrue(линии.add(LineKind.FLUID, DyeColor.WHITE),
                "труба жидкости и труба бездны в одном блоке не мешают друг другу");
        assertEquals(2, линии.count());
        assertEquals(LineKind.ABYSS, линии.line(LineKind.ABYSS, DyeColor.WHITE).kind());
        assertEquals(LineKind.FLUID, линии.line(LineKind.FLUID, DyeColor.WHITE).kind());
    }
}
