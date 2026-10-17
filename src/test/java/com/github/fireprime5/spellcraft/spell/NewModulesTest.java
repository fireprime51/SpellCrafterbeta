package com.github.fireprime5.spellcraft.spell;

import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Модули, добавленные по разбору пробелов: сравнения, сортировка сущностей,
 * заморозка, векторные недочёты, задержка.
 *
 * <p>Проверяется то, что видно без мира: набор зарегистрирован, у нужных
 * модулей верные порты и цена, состояние заморозки живёт по сроку.
 */
class NewModulesTest {

    @BeforeAll
    static void setUp() {
        SpellModules.init();
    }

    @BeforeEach
    void чисто() {
        Frozen.reset();
    }

    private static SpellModuleType модуль(String путь) {
        SpellModuleType тип = SpellModules.get(SpellModules.id(путь));
        assertNotNull(тип, "модуль не зарегистрирован: " + путь);
        return тип;
    }

    // ------------------------------------------------------ сравнения

    /** Шесть операций, а не один «сравнить». */
    @Test
    void всеСравненияЗарегистрированы() {
        for (String путь : List.of("equals", "not_equals", "greater", "less",
                "greater_or_equal", "less_or_equal")) {
            SpellModuleType тип = модуль(путь);
            assertEquals(2, тип.inputs(), путь + ": должно быть два входа");
            assertEquals(1, тип.outputs(), путь + ": должен быть один выход");
            assertEquals(PortType.NUMBER, тип.inputPorts()[0], путь);
            assertEquals(PortType.NUMBER, тип.outputPorts()[0],
                    путь + ": на выходе должно быть число, а не «истина» — "
                            + "ноль и есть условие для «если»");
        }
    }

    /**
     * Порядок аргументов: слева проверяемое.
     *
     * <p>Переставленные местами «больше» и «меньше» дают ровно противоположный
     * результат, и заметить это можно только на конкретном заклинании.
     */
    @Test
    void сравненияЧитаютсяСлеваНаправо() {
        for (String путь : List.of("equals", "not_equals", "greater", "less",
                "greater_or_equal", "less_or_equal")) {
            assertEquals(PortType.NUMBER, модуль(путь).inputPorts()[0]);
            assertEquals(PortType.NUMBER, модуль(путь).inputPorts()[1]);
        }
    }

    /**
     * Сравнения считают то, что записано, а не что-нибудь похожее.
     *
     * <p>Проверяется настоящая логика из {@link Comparisons}, на которую ссылаются
     * сами модули. Раньше условие было лямбдой в теле модуля, и перевёрнутый
     * «больше» проходил все проверки: модуль зарегистрирован, порты верные, а
     * считает он наоборот.
     */
    @Test
    void сравненияСчитаютПравильно() {
        assertTrue(Comparisons.test("greater", 5, 3), "5 больше 3");
        assertFalse(Comparisons.test("greater", 3, 5), "3 не больше 3");
        assertFalse(Comparisons.test("greater", 3, 3), "3 не больше 3");

        assertTrue(Comparisons.test("less", 3, 5), "3 меньше 5");
        assertFalse(Comparisons.test("less", 5, 3));

        assertTrue(Comparisons.test("equals", 3, 3), "равно самому себе");
        assertFalse(Comparisons.test("equals", 3, 5));
        assertTrue(Comparisons.test("not_equals", 3, 5));
        assertFalse(Comparisons.test("not_equals", 3, 3));

        assertTrue(Comparisons.test("greater_or_equal", 5, 3));
        assertTrue(Comparisons.test("greater_or_equal", 3, 3), "равно — это и «не меньше»");
        assertFalse(Comparisons.test("greater_or_equal", 3, 5));

        assertTrue(Comparisons.test("less_or_equal", 3, 5));
        assertTrue(Comparisons.test("less_or_equal", 3, 3));
        assertFalse(Comparisons.test("less_or_equal", 5, 3));
    }

    /** Порядок аргументов: слева проверяемое. */
    @Test
    void сравненияНеПутаютСтороны() {
        // «больше» и «меньше» на одном и том же входе обязаны давать
        // противоположное, иначе один из них считает наоборот
        // только строго разные пары: на равных оба дают «нет», и это верно
        for (double[] пара : new double[][]{{5, 3}, {3, 5}, {-1, 1}}) {
            assertNotEquals(Comparisons.test("greater", пара[0], пара[1]),
                    Comparisons.test("less", пара[0], пара[1]),
                    "«больше» и «меньше» совпали на " + пара[0] + " и " + пара[1]);
        }
        // отрицательные числа — где «больше» чаще всего и путают
        assertTrue(Comparisons.test("greater", -1, -5), "-1 больше -5");
    }

    // ------------------------------------------------------ сортировка сущностей

    /** Сортировщики идут рядом с «это игрок» и ждут сущность на входе. */
    @Test
    void сортировщикиСущностейЗарегистрированы() {
        for (String путь : List.of("is_mob", "is_item", "is_projectile",
                "is_vehicle", "is_alive")) {
            SpellModuleType тип = модуль(путь);
            assertEquals(1, тип.inputs(), путь + ": на вход сущность");
            assertEquals(PortType.ENTITY, тип.inputPorts()[0], путь);
            assertEquals(PortType.NUMBER, тип.outputPorts()[0], путь);
        }
    }

    /** «Это игрок» был и раньше — он не должен сломаться от соседей. */
    @Test
    void старыйСортировщикНаМесте() {
        SpellModuleType тип = модуль("is_player");
        assertEquals(PortType.ENTITY, тип.inputPorts()[0]);
        assertEquals(PortType.NUMBER, тип.outputPorts()[0]);
    }

    // ------------------------------------------------------ векторные

    @Test
    void векторныеНедочётыЗакрыты() {
        SpellModuleType расстояние = модуль("vector_distance");
        assertEquals(2, расстояние.inputs());
        assertEquals(PortType.VECTOR, расстояние.inputPorts()[0]);
        assertEquals(PortType.VECTOR, расстояние.inputPorts()[1]);
        assertEquals(PortType.NUMBER, расстояние.outputPorts()[0],
                "расстояние между точками — это число, а не вектор");

        SpellModuleType между = модуль("vector_lerp");
        assertEquals(3, между.inputs(), "два вектора и доля пути");
        assertEquals(PortType.NUMBER, между.inputPorts()[2]);
        assertEquals(PortType.VECTOR, между.outputPorts()[0]);
    }

    // ------------------------------------------------------ заморозка

    @Test
    void заморозкаЗарегистрирована() {
        SpellModuleType тип = модуль("freeze");
        assertEquals(2, тип.inputs(), "сущность и сколько тиков");
        assertEquals(PortType.ENTITY, тип.inputPorts()[0]);
        assertEquals(PortType.NUMBER, тип.inputPorts()[1]);
        assertEquals(PortType.ENTITY, тип.outputPorts()[0],
                "на выходе та же сущность: заморозить и дальше что-то с ней сделать");
    }

    @Test
    void заморозкаДержитсяДоСрока() {
        UUID id = UUID.randomUUID();
        Frozen.freeze(id, 1000);
        assertTrue(Frozen.frozen(id, 1000), "до срока заморожена");
        assertTrue(Frozen.frozen(id, 999));
    }

    /** Срок вышел — сущность снова живая и ходит. */
    @Test
    void заморозкаКончаетсяСама() {
        UUID id = UUID.randomUUID();
        Frozen.freeze(id, 1000);
        assertFalse(Frozen.frozen(id, 1001), "после срока отпущена");
    }

    /** Снятие досрочно — чтобы игрок мог освободить цель раньше. */
    @Test
    void разморозкаРаботаетДосрочно() {
        UUID id = UUID.randomUUID();
        Frozen.freeze(id, 1000);
        Frozen.thaw(id);
        assertFalse(Frozen.frozen(id, 500),
                "«Разморозка» обязана отпускать сразу, а не по сроку");
    }

    /** Сущность без заморозки не трогается. */
    @Test
    void незамороженнаяСущностьИдётСвободно() {
        assertFalse(Frozen.frozen(UUID.randomUUID(), 1000));
        assertFalse(Frozen.frozen((Entity) null, 1000));
    }

    /** Замороженная сущность должна гасить движение, а не просто числиться в карте. */
    @Test
    void тикЩитЗаморозкиГаситДвижение() throws Exception {
        var hold = Frozen.class.getDeclaredMethod("hold", Entity.class, long.class);
        String исходник = Files.readString(Path.of(
                "src/main/java/com/github/fireprime5/spellcraft/spell/Frozen.java"),
                StandardCharsets.UTF_8);
        int начало = исходник.indexOf("public static void hold(");
        int конец = исходник.indexOf("\n    }", начало);
        String метод = исходник.substring(начало, конец);
        int обнулений = метод.split("setDeltaMovement", -1).length - 1;
        assertTrue(обнулений >= 2,
                "движение обязано гаситься и у самой сущности, и у живого существа: "
                        + "обнулений " + обнулений);
        assertTrue(метод.contains("entity.setDeltaMovement"),
                "заморозка обязана обнулять движение каждый тик, иначе цель "
                        + "через тик снова поползёт");
        assertNotNull(hold);
    }

    // ------------------------------------------------------ задержка

    @Test
    void задержкаЗарегистрирована() {
        SpellModuleType отложить = модуль("delay_push");
        assertEquals(2, отложить.inputs(), "значение и сколько тиков");
        assertEquals(PortType.ANY, отложить.inputPorts()[0],
                "откладывать можно что угодно, не только число");
        assertEquals(PortType.NUMBER, отложить.inputPorts()[1]);
        assertEquals(0, отложить.outputs(), "отложенное значение не выходит сразу");

        SpellModuleType забрать = модуль("delay_pop");
        assertEquals(1, забрать.inputs(), "на сколько тиков назад смотреть");
        assertEquals(PortType.ANY, забрать.outputPorts()[0]);
    }

    /** Модуль задержки обязан стоить рядом со стеком, а не где попало. */
    @Test
    void задержкаВРазделеСтека() {
        assertEquals(SpellSubsections.STACK, SpellSubsections.of(модуль("delay_push")));
        assertEquals(SpellSubsections.STACK, SpellSubsections.of(модуль("delay_pop")));
    }

    /** Подразделы на месте — иначе модуль не попадёт ни в одну полосу. */
    @Test
    void подразделыНаМесте() {
        assertEquals(SpellSubsections.BOOLEAN,
                SpellSubsections.of(модуль("greater")));
        assertEquals(SpellSubsections.READ_ENTITY,
                SpellSubsections.of(модуль("is_mob")));
        assertEquals(SpellSubsections.AFFECT,
                SpellSubsections.of(модуль("freeze")));
        assertEquals(SpellSubsections.VECTORS,
                SpellSubsections.of(модуль("vector_lerp")));
    }

}
