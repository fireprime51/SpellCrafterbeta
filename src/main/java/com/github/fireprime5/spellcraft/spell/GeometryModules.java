package com.github.fireprime5.spellcraft.spell;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.github.fireprime5.spellcraft.spell.PortType.NUMBER;
import static com.github.fireprime5.spellcraft.spell.PortType.POINT;
import static com.github.fireprime5.spellcraft.spell.PortType.VECTOR;

/**
 * Модули векторов и геометрии.
 *
 * <p>До этих модулей вектор в заклинании можно было только получить одним
 * способом — посмотреть взглядом ({@code entity_look}). Дальше с ним нельзя
 * было ничего сделать: ни посчитать длину, ни развернуть, ни сложить два
 * вектора. А точка бралась только как готовая координата блока, и сдвинуть её
 * на «свои» три блока тоже было нечем.
 *
 * <p>Здесь закрываются оба пробела: векторная математика (длина, знак, углы,
 * повороты, разброс) и геометрия точек (расстояние, направление, сдвиг,
 * середина, длина линии в блоках).
 *
 * <p>Повороты считаются в тех же градусах, что и у самой игры: рыскание 0
 * смотрит в +Z, положительное рыскание поворачивает влево, положительный
 * тангаж — вниз. Поэтому поворот вектора взгляда ведёт себя ровно так же,
 * как поворот головы, и собранный из этих модулей луч попадает туда же,
 * куда смотрит игрок.
 */
public final class GeometryModules {

    private GeometryModules() {
    }

    private static final SpellModuleType.Category NUM = SpellModuleType.Category.NUMBERS;

    /**
     * Отдельный цвет от арифметики: числа оранжевые, векторы и точки голубые.
     * Иначе «Сложить векторы» не отличить от «Сложить» по блоку в палитре.
     */
    private static final int C_VECTOR = 0xFF5CC8E8;

    private static final double RAD = Math.PI / 180.0;
    /** Направление считается вырожденным ближе этого, чтобы не делить на ноль. */
    private static final double EPS = 1e-9;

    /**
     * Потолок длины отрезка для «Блоков в линии», в блоках.
     *
     * <p>С запасом перекрывает {@code SpellContext.MAX_RANGE}: обе точки
     * доступны, поэтому честная дальность между ними около 48 блоков, а круглый
     * предел в 256 берёт и то, и другое с запасом.
     */
    public static final int MAX_LINE_BLOCKS = 256;

    public static void registerAll() {
        // ---------------------------------------------------------- векторы

        // Единственный способ получить вектор «из ничего»: три числа.
        // Без него вся остальная геометрия недостижима — вектор в заклинании
        // больше не приходится только смотреть под себя.
        SpellModules.register(new SpellModuleType(SpellModules.id("vector_from_coords"),
                NUM, C_VECTOR) {
            @Override public int inputs() { return 3; }
            @Override public int outputs() { return 1; }
            @Override public PortType[] inputPorts() {
                return new PortType[]{NUMBER, NUMBER, NUMBER};
            }
            @Override public PortType[] outputPorts() { return new PortType[]{VECTOR}; }
            @Override public int manaCost() { return 3; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                out.add(finite(new Vec3(in.get(0).asNumber(), in.get(1).asNumber(),
                        in.get(2).asNumber()), SpellValue.Kind.VECTOR));
            }
        });

        in1("vector_length", VECTOR, NUMBER, 2,
                (ctx, v) -> SpellValue.of(v.asVec().length()));

        in1("vector_normalize", VECTOR, VECTOR, 3, (ctx, v) -> {
            Vec3 vector = v.asVec();
            double length = vector.length();
            // у нулевого вектора нет направления: нормализовать нечего,
            // и деление на ноль дало бы NaN, который ушёл бы дальше по графу
            if (length < EPS) {
                return SpellValue.of(vector, SpellValue.Kind.VECTOR);
            }
            return SpellValue.of(vector.scale(1.0 / length), SpellValue.Kind.VECTOR);
        });

        in1("vector_flat", VECTOR, VECTOR, 3,
                (ctx, v) -> SpellValue.of(new Vec3(v.asVec().x, 0, v.asVec().z),
                        SpellValue.Kind.VECTOR));

        in2("vector_scale", VECTOR, NUMBER, VECTOR, 3,
                (ctx, v, n) -> SpellValue.of(v.asVec().scale(n.asNumber()),
                        SpellValue.Kind.VECTOR));

        in2("vector_add", VECTOR, VECTOR, VECTOR, 2,
                (ctx, a, b) -> SpellValue.of(a.asVec().add(b.asVec()),
                        SpellValue.Kind.VECTOR));

        in2("vector_subtract", VECTOR, VECTOR, VECTOR, 2,
                (ctx, a, b) -> SpellValue.of(a.asVec().subtract(b.asVec()),
                        SpellValue.Kind.VECTOR));

        // Скалярное произведение в его обычном смысле (длина × длина × cos угла),
        // а не косинус угла: разделить результат на произведение длин можно и
        // модулями «Длина вектора», а нормализованное значение всё равно есть
        // в «Угле между векторами».
        in2("vector_dot", VECTOR, VECTOR, NUMBER, 3,
                (ctx, a, b) -> SpellValue.of(a.asVec().dot(b.asVec())));

        in2("vector_cross", VECTOR, VECTOR, VECTOR, 3,
                (ctx, a, b) -> SpellValue.of(a.asVec().cross(b.asVec()),
                        SpellValue.Kind.VECTOR));

        // Угол в градусах, а не косинус: результат сразу подходит входом
        // «Поворота», который тоже ждёт градусы, и в «Нормализовать угол».
        in2("vector_angle", VECTOR, VECTOR, NUMBER, 3,
                (ctx, a, b) -> SpellValue.of(angleOf(a.asVec(), b.asVec())));

        in2("vector_rotate_yaw", VECTOR, NUMBER, VECTOR, 4, (ctx, v, degrees) -> {
            Vec3 vector = v.asVec();
            // у вертикального вектора рыскание не определено: поворачивать
            // вокруг вертикали такой вектор бессмысленно
            if (Math.hypot(vector.x, vector.z) < EPS) {
                return SpellValue.of(vector, SpellValue.Kind.VECTOR);
            }
            return SpellValue.of(fromYawPitch(yawOf(vector) + degrees.asNumber(),
                    pitchOf(vector)), SpellValue.Kind.VECTOR);
        });

        in2("vector_rotate_pitch", VECTOR, NUMBER, VECTOR, 4, (ctx, v, degrees) -> {
            Vec3 vector = v.asVec();
            if (vector.length() < EPS) {
                return SpellValue.of(vector, SpellValue.Kind.VECTOR);
            }
            return SpellValue.of(fromYawPitch(yawOf(vector),
                    pitchOf(vector) + degrees.asNumber()), SpellValue.Kind.VECTOR);
        });

        // Разброс: направление уводится на случайный угол в пределах ±градусов
        // по обеим осям. Из одного вектора взгляда получается «веер» — ровно то,
        // чего нельзя было собрать без этой пары модулей.
        in2("vector_cone", VECTOR, NUMBER, VECTOR, 6, (ctx, v, degrees) -> {
            Vec3 vector = v.asVec();
            if (vector.length() < EPS) {
                return SpellValue.of(vector, SpellValue.Kind.VECTOR);
            }
            double spread = Math.clamp(Math.abs(degrees.asNumber()), 0, 180);
            return SpellValue.of(spread(vector, spread, randomStep(), randomStep()),
                    SpellValue.Kind.VECTOR);
        });

        in1("wrap_angle", NUMBER, NUMBER, 1,
                (ctx, n) -> SpellValue.of(wrapDegrees(n.asNumber())));

        // ----------------------------------------------------------- точки

        in2("point_distance", POINT, POINT, NUMBER, 3,
                (ctx, a, b) -> SpellValue.of(a.asVec().distanceTo(b.asVec())));

        in2("point_direction", POINT, POINT, VECTOR, 3,
                (ctx, a, b) -> SpellValue.of(b.asVec().subtract(a.asVec()),
                        SpellValue.Kind.VECTOR));

        in2("point_midpoint", POINT, POINT, POINT, 3,
                (ctx, a, b) -> SpellValue.of(midpoint(a.asVec(), b.asVec()),
                        SpellValue.Kind.POSITION));

        // Необязательное число — привычный приём модулей добычи: без него сдвиг
        // ровно на вектор, с ним — на вектор, умноженный на число. Так «шаг на
        // два блока» собирается одним модулем, а не двумя.
        SpellModules.register(new SpellModuleType(SpellModules.id("point_offset"),
                NUM, C_VECTOR) {
            @Override public int inputs() { return 3; }
            @Override public int optionalInputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public PortType[] inputPorts() {
                return new PortType[]{POINT, VECTOR, NUMBER};
            }
            @Override public PortType[] outputPorts() { return new PortType[]{POINT}; }
            @Override public int manaCost() { return 3; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                Vec3 offset = in.get(1).asVec();
                SpellValue scale = in.get(2);
                Vec3 moved = scale == null
                        ? in.get(0).asVec().add(offset)
                        : in.get(0).asVec().add(offset.scale(scale.asNumber()));
                out.add(SpellValue.of(moved, SpellValue.Kind.POSITION));
            }
        });

        // Сколько позиций блоков проходит отрезок. Отличается от
        // «Расстояния между точками»: то возвращает точную длину, это —
        // целое число блоков, то есть ровно то, с чем сравнивают дальность
        // луча или натяжение растяжки.
        in2("line_blocks", POINT, POINT, NUMBER, 4, (ctx, a, b) -> {
            ctx.requireInRange(a.asVec());
            ctx.requireInRange(b.asVec());
            return SpellValue.of(blocksInLine(a.asVec(), b.asVec()));
        });
    }

    // ---------------------------------------------------------- конструкторы

    private interface In1 {
        SpellValue apply(SpellContext ctx, SpellValue input);
    }

    private interface In2 {
        SpellValue apply(SpellContext ctx, SpellValue first, SpellValue second);
    }

    /**
     * Регистрирует модуль с одним обязательным входом и одним выходом.
     *
     * <p>Регистрация спрятана в конструкторе, а не в вызывающем коде: иначе
     * легко забыть её у очередного модуля, и он молча пропадёт из палитры —
     * компилятор на такую ошибку не ругается.
     */
    private static void in1(String path, PortType in, PortType out, int cost, In1 op) {
        SpellModules.register(new SpellModuleType(SpellModules.id(path), NUM, C_VECTOR) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public PortType[] inputPorts() { return new PortType[]{in}; }
            @Override public PortType[] outputPorts() { return new PortType[]{out}; }
            @Override public int manaCost() { return cost; }
            @Override public void execute(SpellContext ctx, List<SpellValue> inputs,
                                          List<SpellValue> outputs) {
                outputs.add(op.apply(ctx, inputs.get(0)));
            }
        });
    }

    /**
     * Регистрирует модуль с двумя входами и одним выходом.
     *
     * <p>Типы входов задаются отдельно: они почти всегда разные (вектор и
     * число, точка и точка).
     */
    private static void in2(String path, PortType first, PortType second, PortType out,
                            int cost, In2 op) {
        SpellModules.register(new SpellModuleType(SpellModules.id(path), NUM, C_VECTOR) {
            @Override public int inputs() { return 2; }
            @Override public int outputs() { return 1; }
            @Override public PortType[] inputPorts() {
                return new PortType[]{first, second};
            }
            @Override public PortType[] outputPorts() { return new PortType[]{out}; }
            @Override public int manaCost() { return cost; }
            @Override public void execute(SpellContext ctx, List<SpellValue> inputs,
                                          List<SpellValue> outputs) {
                outputs.add(op.apply(ctx, inputs.get(0), inputs.get(1)));
            }
        });
    }

    // ------------------------------------------------------------- углы

    /**
     * Рыскание вектора в градусах по правилам игры: 0 смотрит в +Z,
     * положительное значение поворачивает влево (в минус X).
     *
     * <p>Знак минус у X обязателен и согласован с {@link #fromYawPitch}: если
     * взять {@code atan2(z, x)} без него, рыскание уедет на 90°, и поворот
     * «налево на 90°» развернёт направление ровно назад, а не вбок.
     */
    static double yawOf(Vec3 v) {
        return Math.toDegrees(Math.atan2(-v.x, v.z));
    }

    /**
     * Тангаж вектора в градусах: положительное значение смотрит вниз.
     * Длина выброшена, потому что направление интересует, а не размах.
     */
    static double pitchOf(Vec3 v) {
        double length = v.length();
        if (length < EPS) {
            return 0;
        }
        return Math.toDegrees(Math.asin(clamp(-v.y / length, -1.0, 1.0)));
    }

    /** Обратное к {@link #yawOf} и {@link #pitchOf}: углы обратно в вектор. */
    static Vec3 fromYawPitch(double yaw, double pitch) {
        double cosPitch = Math.cos(pitch * RAD);
        return new Vec3(-Math.sin(yaw * RAD) * cosPitch,
                -Math.sin(pitch * RAD),
                Math.cos(yaw * RAD) * cosPitch);
    }

    /**
     * Случайное число из [-1, 1].
     *
     * <p>Разброс не связан с миром, поэтому используется потоковый генератор:
     * он не трогает последовательность мира и не требует загруженного уровня,
     * а значит работает и при разборе схемы вне игры.
     */
    private static double randomStep() {
        return java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 2 - 1;
    }

    /**
     * Направление, отклонённое от исходного не более чем на {@code spread}
     * градусов — то есть случайная точка внутри конуса.
     *
     * <p>Смещать отдельно рыскание и тангаж нельзя: два независимых сдвига
     * складываются по диагонали, и суммарное отклонение доходит до
     * {@code spread * √2} — «разброс 15°» тихо превратился бы в 21°. Здесь
     * сначала берётся одно случайное отклонение нужной величины, а вокруг
     * направления — случайный азимут, поэтому граница держится точно.
     *
     * <p>Длина результата равна 1: разброс меняет только направление.
     */
    static Vec3 spread(Vec3 direction, double spread, double magnitudeStep, double azimuthStep) {
        // отклонение берётся по модулю одного числа, поэтому граница держится
        // точно: |magnitudeStep| всегда в пределах 1
        double deviation = Math.abs(spread) * Math.abs(magnitudeStep);
        if (deviation < EPS) {
            return direction.normalize();
        }
        return rotateAround(direction, axisAround(direction, azimuthStep), deviation * RAD);
    }

    /**
     * Единичная ось, перпендикулярная направлению; поворот вокруг неё на угол
     * {@code azimuthStep * 180°} обходит все стороны, то есть распределение
     * отклонений равномерно по кругу, а не скапливается спереди.
     */
    private static Vec3 axisAround(Vec3 direction, double azimuthStep) {
        Vec3 candidate = Math.abs(direction.y) < 0.9
                ? new Vec3(0, 1, 0)
                : new Vec3(1, 0, 0);
        Vec3 axis = direction.cross(candidate).normalize();
        return rotateAround(axis, direction, azimuthStep * 180.0 * RAD);
    }

    /** Поворот вектора вокруг единичной оси по правилу Родригеса. */
    private static Vec3 rotateAround(Vec3 vector, Vec3 axis, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return vector.scale(cos)
                .add(axis.cross(vector).scale(sin))
                .add(axis.scale(axis.dot(vector) * (1 - cos)));
    }

    /** Угол между векторами в градусах, от 0 до 180. */
    static double angleOf(Vec3 a, Vec3 b) {
        double first = a.length();
        double second = b.length();
        if (first < EPS || second < EPS) {
            return 0;
        }
        return Math.toDegrees(Math.acos(clamp(a.dot(b) / (first * second), -1.0, 1.0)));
    }

    /**
     * Приводит угол к диапазону от −180 до 180, поэтому 370° превращается в 10°,
     * а 720° — в 0°. Так «развернуть на 400°» работает как ожидают, а не
     * уезжает за пределы, откуда тригонометрия ещё считает осмысленно.
     */
    public static double wrapDegrees(double degrees) {
        if (!Double.isFinite(degrees)) {
            return 0;
        }
        double wrapped = degrees % 360.0;
        if (wrapped > 180.0) {
            wrapped -= 360.0;
        }
        if (wrapped < -180.0) {
            wrapped += 360.0;
        }
        return wrapped;
    }

    // --------------------------------------------------------- геометрия

    static Vec3 midpoint(Vec3 a, Vec3 b) {
        return new Vec3((a.x + b.x) / 2.0, (a.y + b.y) / 2.0, (a.z + b.z) / 2.0);
    }

    /**
     * Сколько позиций блоков проходит отрезок.
     *
     * <p>Считается не длина, а именно число занятых блоков: шаг берётся по
     * самой длинной оси, соседние позиции склеиваются, и каждая считается один
     * раз. Отрезок в 10 блоков отвечает 11 — столько же, сколько блоков он
     * действительно пересекает, включая оба конца.
     *
     * <p>Длина отрезка ограничена сверху {@link #MAX_LINE_BLOCKS}. Точки в
     * схеме собираются арифметикой от произвольных чисел, и две точки в
     * трёхстах миллионах блоков apart давали {@code steps = 3·10⁸}: цикл
     * крутился бы на серверном потоке столько же раз. Приведение к целому
     * тут не спасает — переполнение double насыщается до
     * {@code Integer.MAX_VALUE}, то есть к худшему случаю.
     */
    static int blocksInLine(Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        double longest = Math.max(Math.abs(delta.x),
                Math.max(Math.abs(delta.y), Math.abs(delta.z)));
        if (!Double.isFinite(longest) || longest > MAX_LINE_BLOCKS) {
            throw new SpellContext.SpellException("line.too_long", MAX_LINE_BLOCKS);
        }
        int steps = (int) Math.ceil(longest);
        if (steps == 0) {
            return 1;
        }
        int blocks = 0;
        BlockPos previous = null;
        for (int i = 0; i <= steps; i++) {
            BlockPos pos = BlockPos.containing(from.add(delta.scale(i / (double) steps)));
            if (!pos.equals(previous)) {
                blocks++;
                previous = pos;
            }
        }
        return blocks;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Отсекает нечисловые координаты.
     *
     * <p>В схему можно положить что угодно, поэтому на вход арифметики могут
     * прийти NaN или бесконечности (деление на ноль, умножение на 1e308). Дальше
     * они уехали бы в точку, а оттуда — в {@code BlockPos.containing} и
     * {@code setBlock}: нечисловая координата там ведёт себя как ноль, то есть
     * игрок чинит блок у себя под ногами вместо блока на другом конце луча.
     */
    private static SpellValue finite(Vec3 v, SpellValue.Kind kind) {
        if (!Double.isFinite(v.x) || !Double.isFinite(v.y) || !Double.isFinite(v.z)) {
            throw new SpellContext.SpellException("value.not_finite");
        }
        return SpellValue.of(v, kind);
    }
}
