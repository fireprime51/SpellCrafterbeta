package com.github.fireprime5.spellcraft.spell;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Векторы и геометрия: регистрация модулей, порты и сама арифметика.
 *
 * <p>В отличие от модулей сети эти ничего не меняют в мире — они только
 * считают, поэтому их исполнение проверяется здесь полностью, без игры.
 */
class GeometryModulesTest {

    private static final double EPS = 1e-6;

    @BeforeAll
    static void setUp() {
        SpellModules.init();
    }

    private static SpellModuleType module(String path) {
        SpellModuleType type = SpellModules.get(SpellModules.id(path));
        assertNotNull(type, "модуль " + path + " не зарегистрирован");
        return type;
    }

    /** Модуль одного входа: считает и отдаёт один результат. */
    private static SpellValue run1(String path, SpellValue input) {
        return run(path, List.of(input));
    }

    /** Модуль двух входов. */
    private static SpellValue run2(String path, SpellValue first, SpellValue second) {
        return run(path, List.of(first, second));
    }

    /** Модуль трёх входов; null означает неподключённый необязательный вход. */
    private static SpellValue run3(String path, SpellValue first, SpellValue second,
                                   SpellValue third) {
        List<SpellValue> inputs = new ArrayList<>();
        inputs.add(first);
        inputs.add(second);
        inputs.add(third);
        return run(path, inputs);
    }

    private static SpellValue run(String path, List<SpellValue> inputs) {
        List<SpellValue> outputs = new ArrayList<>();
        // контекст без кастующего: проверку дальности (она зовёт requireInRange)
        // в тестах пропускают, а сама геометрия проверяется отдельно в
        // SpellContextRangeTest
        module(path).execute(new SpellContext(null, null, Vec3.ZERO), inputs, outputs);
        assertEquals(1, outputs.size(), path + " должен отдать ровно одно значение");
        return outputs.get(0);
    }

    private static SpellValue vec(double x, double y, double z) {
        return SpellValue.of(new Vec3(x, y, z), SpellValue.Kind.VECTOR);
    }

    private static SpellValue point(double x, double y, double z) {
        return SpellValue.of(new Vec3(x, y, z), SpellValue.Kind.POSITION);
    }

    private static void assertVec(SpellValue actual, double x, double y, double z) {
        Vec3 v = actual.asVec();
        assertEquals(x, v.x, EPS, "x");
        assertEquals(y, v.y, EPS, "y");
        assertEquals(z, v.z, EPS, "z");
    }

    // ------------------------------------------------------- регистрация

    @Test
    void всеГеометрическиеМодулиНаМесте() {
        for (String path : List.of("vector_from_coords", "vector_length",
                "vector_normalize", "vector_flat", "vector_scale", "vector_add",
                "vector_subtract", "vector_dot", "vector_cross", "vector_angle",
                "vector_rotate_yaw", "vector_rotate_pitch", "vector_cone",
                "wrap_angle", "point_distance", "point_direction", "point_midpoint",
                "point_offset", "line_blocks", "sin", "cos", "lerp")) {
            SpellModuleType type = module(path);
            assertEquals(SpellModuleType.Category.NUMBERS, type.category(),
                    path + " должен лежать во вкладке «Числа»");
        }
    }

    /**
     * Вектор строится из чисел, а не берётся готовым.
     *
     * <p>Без этого модуля остальная геометрия недостижима: единственным
     * источником вектора был взгляд, то есть любое заклинание зависело бы от
     * того, куда игрок случайно смотрит.
     */
    @Test
    void векторСобираетсяИзТрёхЧисел() {
        SpellModuleType type = module("vector_from_coords");
        assertEquals(3, type.inputs());
        for (int port = 0; port < 3; port++) {
            assertEquals(PortType.NUMBER, type.inputType(port), "вход " + port);
        }
        assertEquals(PortType.VECTOR, type.outputType(0));

        List<SpellValue> inputs = List.of(SpellValue.of(1), SpellValue.of(2),
                SpellValue.of(3));
        List<SpellValue> outputs = new ArrayList<>();
        type.execute(null, inputs, outputs);
        assertVec(outputs.get(0), 1, 2, 3);
    }

    @Test
    void уГеометрииПравильныеПорты() {
        assertEquals(PortType.VECTOR, module("vector_length").inputType(0));
        assertEquals(PortType.NUMBER, module("vector_length").outputType(0));

        assertEquals(PortType.VECTOR, module("vector_scale").inputType(0));
        assertEquals(PortType.NUMBER, module("vector_scale").inputType(1));

        // направление берётся из двух точек, а не из двух векторов
        assertEquals(PortType.POINT, module("point_direction").inputType(0));
        assertEquals(PortType.POINT, module("point_direction").inputType(1));
        assertEquals(PortType.VECTOR, module("point_direction").outputType(0));

        assertEquals(PortType.POINT, module("point_distance").inputType(0));
        assertEquals(PortType.NUMBER, module("point_distance").outputType(0));

        assertEquals(PortType.POINT, module("point_midpoint").outputType(0));
    }

    /**
     * У «Сдвинуть точку» необязателен именно третий вход.
     *
     * <p>Первые два обязательны: без точки нечего двигать, без вектора нечем
     * двигать. Необязательным сделано только число, потому что без него сдвиг
     * идёт ровно на вектор.
     */
    @Test
    void уСдвигаТочкиНеобязателенТолькоЧисло() {
        SpellModuleType type = module("point_offset");
        assertEquals(3, type.inputs());
        assertEquals(1, type.optionalInputs());
        assertFalse(type.isOptional(0), "точка обязательна");
        assertFalse(type.isOptional(1), "вектор обязателен");
        assertTrue(type.isOptional(2), "число необязательно");
    }

    // ------------------------------------------------------- арифметика

    @Test
    void длинаВектора() {
        assertEquals(5, run1("vector_length", vec(3, 4, 0)).asNumber(), EPS);
        assertEquals(0, run1("vector_length", vec(0, 0, 0)).asNumber(), EPS);
    }

    @Test
    void нормализацияДаётЕдиницуИСохраняетНаправление() {
        assertVec(run1("vector_normalize", vec(3, 4, 0)), 0.6, 0.8, 0);
    }

    /**
     * Нулевой вектор не имеет направления.
     *
     * <p>Если бы модуль делил на длину как обычно, в схему ушёл бы NaN, который
     * дальше тихо портит любое вычисление вместо явной ошибки.
     */
    @Test
    void нормализацияНуляНеДаётNan() {
        SpellValue result = run1("vector_normalize", vec(0, 0, 0));
        assertVec(result, 0, 0, 0);
        assertFalse(Double.isNaN(result.asVec().x));
    }

    @Test
    void сложениеИВычитаниеВекторов() {
        assertVec(run2("vector_add", vec(1, 2, 3), vec(10, 20, 30)), 11, 22, 33);
        assertVec(run2("vector_subtract", vec(10, 20, 30), vec(1, 2, 3)), 9, 18, 27);
    }

    @Test
    void масштабИСплющивание() {
        assertVec(run2("vector_scale", vec(1, 2, 3), SpellValue.of(2)), 2, 4, 6);
        assertVec(run1("vector_flat", vec(1, 7, 3)), 1, 0, 3);
    }

    @Test
    void скалярноеПроизведение() {
        // перпендикулярные векторы дают ноль
        assertEquals(0, run2("vector_dot", vec(1, 0, 0), vec(0, 1, 0)).asNumber(), EPS);
        // сонаправленные единичные — единица, у второго длина 2
        assertEquals(2, run2("vector_dot", vec(1, 0, 0), vec(2, 0, 0)).asNumber(), EPS);
    }

    @Test
    void векторноеПроизведениеПерпендикулярноОбоим() {
        Vec3 result = run2("vector_cross", vec(1, 0, 0), vec(0, 1, 0)).asVec();
        assertEquals(0, result.dot(new Vec3(1, 0, 0)), EPS, "перпендикулярен первому");
        assertEquals(0, result.dot(new Vec3(0, 1, 0)), EPS, "перпендикулярен второму");
    }

    @Test
    void уголМеждуВекторами() {
        assertEquals(0, run2("vector_angle", vec(1, 0, 0), vec(2, 0, 0)).asNumber(), EPS);
        assertEquals(90, run2("vector_angle", vec(1, 0, 0), vec(0, 1, 0)).asNumber(), EPS);
        assertEquals(180, run2("vector_angle", vec(1, 0, 0), vec(-1, 0, 0)).asNumber(), EPS);
        // вырожденный случай не должен давать NaN
        assertFalse(Double.isNaN(
                run2("vector_angle", vec(0, 0, 0), vec(1, 0, 0)).asNumber()));
    }

    @Test
    void поворотМеняетНаправление() {
        Vec3 turned = run2("vector_rotate_yaw", vec(0, 0, 1), SpellValue.of(90)).asVec();
        assertEquals(1, turned.length(), EPS, "длина сохраняется");
        assertEquals(90, GeometryModules.angleOf(new Vec3(0, 0, 1), turned), 1e-4);
    }

    /**
 * Поворот вокруг вертикали сохраняет направление, но не длину.
 *
 * <p>Поворот работает с направлением и приводит вектор к единичному, поэтому
 * сравнивать высоту напрямую можно только для единичного входа. У строго
 * вертикального вектора горизонтальной части нет, и такой поворот специально
 * не делается (см. тест про вертикальный вектор).
 */
    @Test
    void поворотВокругВертикалиМеняетТолькоРыскание() {
        Vec3 unit = new Vec3(0, 1, 1).normalize();
        Vec3 after = run2("vector_rotate_yaw", SpellValue.of(unit, SpellValue.Kind.VECTOR),
                SpellValue.of(45)).asVec();
        assertEquals(unit.y, after.y, EPS, "высота не меняется");
        assertEquals(1, after.length(), EPS, "длина сохраняется");
        // направление горизонтальной части действительно сдвинулось
        assertTrue(Math.abs(after.x) > EPS || Math.abs(after.z - unit.z) > EPS,
                "вектор должен отклониться вбок");
    }

    /**
     * Наклон меняет высоту, но не азимут.
     *
     * <p>Проверяется направление горизонтальной проекции, а не её длина: при
     * наклоне длина проекции честно уменьшается, и сравнивать её с исходной
     * бессмысленно. А вот куда смотрит горизонтальная часть — должно совпасть.
     */
    @Test
    void наклонМеняетТолькоВысоту() {
        Vec3 after = run2("vector_rotate_pitch", vec(0, 0, 1), SpellValue.of(30)).asVec();
        assertEquals(0, after.x, EPS, "азимут не должен уходить вбок");
        assertTrue(after.z > 0, "горизонтальная часть смотрит туда же, куда была");
        assertTrue(after.y < 0, "положительный наклон смотрит вниз");
        assertEquals(1, after.length(), EPS, "длина сохраняется");
    }

    /**
     * Вертикальный вектор нельзя повернуть вокруг вертикали.
     *
     * <p>У него нет горизонтальной части, то есть нет и рыскания. Без
     * проверки получился бы вектор нулевой длины, и следующий за ним «Выстрел»
     * улетел бы в произвольную сторону.
     */
    @Test
    void вертикальныйВекторНеЛомаетсяПриПовороте() {
        assertVec(run2("vector_rotate_yaw", vec(0, 1, 0), SpellValue.of(90)), 0, 1, 0);
        assertVec(run2("vector_rotate_pitch", vec(0, 0, 0), SpellValue.of(90)), 0, 0, 0);
    }

    /**
     * Разброс остаётся в заданных границах.
     *
     * <p>Равенства границ ждать нельзя: угол берётся случайно, попасть точно в
     * предел почти невозможно, поэтому проверяем, что он не выходит наружу.
     */
    @Test
    void разбросНеВыходитЗаПределы() {
        SpellValue direction = vec(0, 0, 1);
        SpellValue spread = SpellValue.of(15);
        for (int i = 0; i < 200; i++) {
            Vec3 result = run2("vector_cone", direction, spread).asVec();
            assertEquals(1, result.length(), 1e-6, "длина сохраняется");
            double angle = GeometryModules.angleOf(new Vec3(0, 0, 1), result);
            assertTrue(angle <= 15.0 + 1e-6, "разброс шире заданного: " + angle);
        }
    }

    /** Нулевой разброс — это то же самое направление, а не случайный сдвиг. */
    @Test
    void нулевойРазбросНеСдвигает() {
        for (int i = 0; i < 50; i++) {
            assertVec(run2("vector_cone", vec(0, 0, 1), SpellValue.of(0)), 0, 0, 1);
        }
    }

    // ----------------------------------------------------------- углы

    @Test
    void нормализацияУгла() {
        assertEquals(0, GeometryModules.wrapDegrees(0), EPS);
        assertEquals(0, GeometryModules.wrapDegrees(360), EPS);
        assertEquals(10, GeometryModules.wrapDegrees(370), EPS);
        assertEquals(-10, GeometryModules.wrapDegrees(-10), EPS);
        assertEquals(180, GeometryModules.wrapDegrees(180), EPS);
        assertEquals(-180, GeometryModules.wrapDegrees(-180), EPS);
        assertEquals(170, GeometryModules.wrapDegrees(-190), EPS);
        assertEquals(0, GeometryModules.wrapDegrees(720), EPS);
    }

    /** NaN не должен уезжать в арифметику и портить её. */
    @Test
    void нормализацияУглаПереживаетМусор() {
        assertEquals(0, GeometryModules.wrapDegrees(Double.NaN), EPS);
        assertEquals(0, GeometryModules.wrapDegrees(Double.POSITIVE_INFINITY), EPS);
    }

    /**
     * Рыскание 0 смотрит в +Z — как у взгляда в игре.
     *
     * <p>От этого зависит всё поведение поворотов: если бы ноль смотрел в
     * другом направлении, повёрнутый взгляд целился бы в стену позади игрока.
     */
    @Test
    void нулевоеРысканиеСмотритВПлюсZ() {
        assertEquals(1, new Vec3(0, 0, 1).dot(GeometryModules.fromYawPitch(0, 0)), EPS);
        assertEquals(0, new Vec3(0, 0, 1).x, EPS);
    }

    /**
     * Положительное рыскание поворачивает влево, положительный тангаж — вниз.
     *
     * <p>Слева в игре это минус X: взгляд, повёрнутый на 90°, должен смотреть
     * в стену слева, а не вправо. Знак проверяется явно, потому что ошибка в
     * нём даёт поворот на 90° в другую сторону и выглядит как «поворот сломался
     * только иногда».
     */
    @Test
    void знакиУгловСогласованыСИгрой() {
        assertTrue(GeometryModules.fromYawPitch(90, 0).x < 0, "рыскание влево — это -X");
        assertTrue(GeometryModules.fromYawPitch(0, 90).y < 0, "тангаж вниз");
        assertEquals(0, GeometryModules.fromYawPitch(0, 0).y, EPS, "горизонтально Y = 0");
    }

    /**
     * Углы восстанавливают направление, но не размах.
     *
     * <p>Рыскание и тангаж задают направление, а длину теряют: их негде хранить.
     * Поэтому сравнивается направление — то есть единичный вектор, — а не исходный.
     * Проверка нужна, чтобы повороты не «разъезжались» относительно взгляда:
     * два шага туда-обратно должны возвращать исходное направление.
     */
    @Test
    void углыИВекторОбратимы() {
        for (Vec3 v : List.of(new Vec3(0, 0, 1), new Vec3(1, 0, 0),
                new Vec3(0.5, 0.5, 0.5), new Vec3(-2, 3, -4), new Vec3(0, -1, 0))) {
            Vec3 back = GeometryModules.fromYawPitch(
                    GeometryModules.yawOf(v), GeometryModules.pitchOf(v));
            Vec3 expected = v.normalize();
            assertEquals(expected.x, back.x, 1e-6, "x у " + v);
            assertEquals(expected.y, back.y, 1e-6, "y у " + v);
            assertEquals(expected.z, back.z, 1e-6, "z у " + v);
        }
    }

    /** Поворот на угол и обратно возвращает то же направление. */
    @Test
    void поворотТудаИОбратноВозвращаетНаправление() {
        Vec3 start = new Vec3(0, 0, 1);
        Vec3 turned = run2("vector_rotate_yaw", vec(0, 0, 1), SpellValue.of(37)).asVec();
        Vec3 back = run2("vector_rotate_yaw", SpellValue.of(turned, SpellValue.Kind.VECTOR),
                SpellValue.of(-37)).asVec();
        assertEquals(start.x, back.x, 1e-6, "x");
        assertEquals(start.y, back.y, 1e-6, "y");
        assertEquals(start.z, back.z, 1e-6, "z");
    }

    // ----------------------------------------------------------- точки

    @Test
    void расстояниеМеждуТочками() {
        assertEquals(5, run2("point_distance", point(0, 0, 0), point(3, 0, 4)).asNumber(),
                EPS);
        assertEquals(0, run2("point_distance", point(2, 2, 2), point(2, 2, 2)).asNumber(),
                EPS);
    }

    @Test
    void направлениеОтПервойКоВторой() {
        assertVec(run2("point_direction", point(0, 0, 0), point(0, 0, 5)), 0, 0, 5);
        // порядок важен: из второй в первую — противоположное направление
        assertVec(run2("point_direction", point(0, 0, 5), point(0, 0, 0)), 0, 0, -5);
    }

    @Test
    void серединаОтрезка() {
        assertVec(run2("point_midpoint", point(0, 0, 0), point(4, 6, 8)), 2, 3, 4);
    }

    @Test
    void сдвигТочкиНаВектор() {
        assertVec(run3("point_offset", point(1, 1, 1), vec(0, 0, 2), null), 1, 1, 3);
    }

    /** С числом на третьем входе вектор умножается на него перед сдвигом. */
    @Test
    void сдвигСЧисломМасштабируетВектор() {
        assertVec(run3("point_offset", point(0, 0, 0), vec(1, 0, 0), SpellValue.of(4)),
                4, 0, 0);
    }

    @Test
    void сдвигВозвращаетТочку() {
        assertEquals(SpellValue.Kind.POSITION,
                run3("point_offset", point(0, 0, 0), vec(1, 2, 3), null).kind(),
                "сдвиг должен оставаться точкой, а не вектором");
    }

    /**
     * Число блоков в линии, а не длина отрезка.
     *
     * <p>Луч длиной 10 блоков пересекает 11 позиций: обе крайние считаются.
     * Это ровно то число, с которым сравнивают дальность луча, поэтому
     * «на 10 блоков» и «11 блоков» должны сходиться, а не путаться.
     */
    @Test
    void блокиВЛинииСчитаютсяПоПозициям() {
        assertEquals(11, GeometryModules.blocksInLine(
                new Vec3(0.5, 64, 0.5), new Vec3(10.5, 64, 0.5)));
        assertEquals(1, GeometryModules.blocksInLine(
                new Vec3(1.5, 64, 1.5), new Vec3(1.5, 64, 1.5)),
                "точка в точку — один блок");
        // по диагонали считаются все три оси
        assertEquals(4, GeometryModules.blocksInLine(
                new Vec3(0.5, 64, 0.5), new Vec3(3.5, 66, 0.5)));
    }

    /** Модуль отдаёт столько же, сколько просчитано напрямую. */
    @Test
    void блокиВЛинииЧерезМодуль() {
        assertEquals(11, run2("line_blocks", point(0.5, 64, 0.5), point(10.5, 64, 0.5))
                .asNumber(), EPS);
    }

    // -------------------------------------------------- тригонометрия

    @Test
    void синусИКосинусВГрадусах() {
        assertEquals(0, run1("sin", SpellValue.of(0)).asNumber(), EPS);
        assertEquals(1, run1("sin", SpellValue.of(90)).asNumber(), EPS);
        assertEquals(1, run1("cos", SpellValue.of(0)).asNumber(), EPS);
        assertEquals(0, run1("cos", SpellValue.of(90)).asNumber(), EPS);
        assertEquals(-1, run1("cos", SpellValue.of(180)).asNumber(), EPS);
    }

    /**
     * Градусы, а не радианы.
     *
     * <p>Если бы считалось в радианах, «синус 90°» вернул бы почти ноль, и
     * игрок, собравший график, не понял бы, где ошибка.
     */
    @Test
    void тригонометрияСчитаетВГрадусах() {
        assertEquals(0.5, run1("sin", SpellValue.of(30)).asNumber(), 1e-6);
        assertEquals(0.5, run1("cos", SpellValue.of(60)).asNumber(), 1e-6);
    }

    /** Единичная окружность: пифагорова тройка на всякий случай. */
    @Test
    void синусИКосинусСогласованы() {
        for (double degrees = -180; degrees <= 180; degrees += 15) {
            double s = run1("sin", SpellValue.of(degrees)).asNumber();
            double c = run1("cos", SpellValue.of(degrees)).asNumber();
            assertEquals(1, s * s + c * c, 1e-9, "на " + degrees + "°");
        }
    }

    @Test
    void плавноеЗначение() {
        assertEquals(10, run3("lerp", SpellValue.of(10), SpellValue.of(20), SpellValue.of(0)).asNumber(), EPS);
        assertEquals(20, run3("lerp", SpellValue.of(10), SpellValue.of(20), SpellValue.of(1)).asNumber(), EPS);
        assertEquals(15, run3("lerp", SpellValue.of(10), SpellValue.of(20), SpellValue.of(0.5)).asNumber(), EPS);
    }

    /** За пределами 0..1 значение не уезжает за концы отрезка. */
    @Test
    void плавноеЗначениеЗажимается() {
        assertEquals(10, run3("lerp", SpellValue.of(10), SpellValue.of(20), SpellValue.of(-5)).asNumber(), EPS);
        assertEquals(20, run3("lerp", SpellValue.of(10), SpellValue.of(20), SpellValue.of(5)).asNumber(), EPS);
    }

    // ------------------------------------------------------- прогрессия

    /**
     * Каждый новый модуль открывается сканированием.
     *
     * <p>Иначе модуль есть в палитре, но недостижим: ни стартовый, ни в
     * таблице наград, и его невозможно получить в игре никак.
     */
    @Test
    void новыеМодулиОткрываютсяСканированием() {
        List<String> reachable = List.of(
                "vector_from_coords", "vector_length", "vector_normalize", "vector_flat",
                "vector_scale", "vector_add", "vector_subtract", "vector_dot",
                "vector_cross", "vector_angle", "vector_rotate_yaw",
                "vector_rotate_pitch", "vector_cone", "wrap_angle", "point_distance",
                "point_direction", "point_midpoint", "point_offset", "line_blocks",
                "sin", "cos", "lerp");
        List<String> rewards = ScanRewards.blocks().values().stream().toList();
        for (String path : reachable) {
            assertTrue(rewards.contains(path),
                    path + " не открывается ни одной целью — модуль недостижим");
            assertFalse(module(path).isStartModule(),
                    path + " не должен быть стартовым: векторы дают слишком много");
        }
    }

    /** Новые модули обязаны находиться в палитре поиском. */
    @Test
    void новыеМодулиНаходятсяПоиском() {
        for (String path : List.of("vector_length", "point_offset", "line_blocks",
                "vector_cone", "lerp", "wrap_angle")) {
            assertTrue(SpellModules.search(path, null).stream()
                            .anyMatch(t -> t.id().getPath().equals(path)),
                    "поиск не нашёл " + path);
        }
        assertTrue(SpellModules.search("вектор", null).size() >= 10,
                "поиск по «вектор» должен находить всю семью");
        assertTrue(SpellModules.search("точка", null).stream()
                        .anyMatch(t -> t.id().getPath().equals("point_midpoint")),
                "поиск по «точка»");
        assertTrue(SpellModules.search("разброс", null).stream()
                        .anyMatch(t -> t.id().getPath().equals("vector_cone")),
                "поиск по «разброс»");
        assertTrue(SpellModules.search("синус", null).stream()
                        .anyMatch(t -> t.id().getPath().equals("sin")),
                "поиск по «синус»");
    }

    /**
     * Результат проходит по типу в существующие модули.
     *
     * <p>Иначе векторную математику не к чему подключить: связь проверяется
     * по типам портов, и несовпадение типов молча запретило бы сборку.
     */
    @Test
    void результатыПодходятПоТипуКСтарымМодулям() {
        // вектор взгляда → поворот → стрела
        assertTrue(module("vector_rotate_yaw").inputType(0)
                .accepts(module("entity_look").outputType(0)));
        assertTrue(module("arrow").inputType(0)
                .accepts(module("vector_rotate_yaw").outputType(0)));
        // точка после сдвига → взрыв
        assertTrue(module("explode").inputType(0)
                .accepts(module("point_offset").outputType(0)));
        // направление на точку → луч по блокам
        assertTrue(module("raycast_block").inputType(0)
                .accepts(module("point_direction").outputType(0)));
        // число из угла → сравнение
        assertTrue(module("compare").inputType(0)
                .accepts(module("vector_angle").outputType(0)));
    }

    /** Модули стоят не дороже арифметики, иначе цикл дороже цели. */
    @Test
    void стоимостьМаныПосоразмерна() {
        for (String path : List.of("vector_length", "vector_normalize", "vector_add",
                "point_distance", "point_midpoint", "wrap_angle", "lerp", "sin")) {
            assertTrue(module(path).manaCost() <= 6,
                    path + " слишком дорог: " + module(path).manaCost());
        }
    }

    /** Модули читаются из реестра, а не создаются на лету — иначе поиск разъедется. */
    @Test
    void одинаковыйМодульНеРегистрируетсяДважды() {
        SpellModuleType first = module("vector_length");
        SpellModuleType second = module("vector_length");
        assertSame(first, second);
    }
}
