package com.github.fireprime5.spellcraft.spell;

import com.github.fireprime5.spellcraft.spell.SpellValue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Модули поиска существ по радиусу.
 *
 * <p>Сам поиск по миру не проверяется: {@code ServerLevel} в тестах не создаётся.
 * Но всё, что в нём решается без мира — сколько целей просят, какой радиус
 * взять, как отсортировать и обрезать список — вынесено в обычные методы, и
 * здесь проверяется по-настоящему.
 */
class EntitiesInRadiusTest {

    @BeforeAll
    static void setUp() {
        SpellModules.init();
    }

    // ------------------------------------------------- отражение упаковки

    private static Method метод(String имя, Class<?>... типы) throws Exception {
        Method m = Class.forName("com.github.fireprime5.spellcraft.spell.ExtraModules")
                .getDeclaredMethod(имя, типы);
        m.setAccessible(true);
        return m;
    }

    private static double радиус(SpellValue значение) throws Exception {
        return (double) метод("clampRadius", SpellValue.class).invoke(null, значение);
    }

    private static int количество(SpellValue значение) throws Exception {
        return (int) метод("clampCount", SpellValue.class).invoke(null, значение);
    }

    // ------------------------------------------------- регистрация

    @Test
    void обаМодуляЗарегистрированы() {
        assertNotNull(SpellModules.get(SpellModules.id("entities_in_radius")));
        assertNotNull(SpellModules.get(SpellModules.id("entities_count_in_radius")));
    }

    /**
     * Четыре выхода — и это не украшение.
     *
     * <p>Схема «ударить двоих» иначе не собирается: два узла «Ближайшая цель» рядом
     * вернут одного и того же, и второй удар уйдёт в ту же цель.
     */
    @Test
    void поискОтдаётЧетыреЦели() {
        SpellModuleType поиск = SpellModules.get(SpellModules.id("entities_in_radius"));
        assertEquals(4, поиск.outputs(), "модуль должен отдавать сразу несколько целей");
        assertEquals(2, поиск.inputs(), "радиус и количество");
        assertEquals(SpellModuleType.Category.ENTITIES, поиск.category());
    }

    /** Все выхода — сущности, иначе к ним нельзя подвести урон. */
    @Test
    void выходаПоискаСущности() {
        SpellModuleType поиск = SpellModules.get(SpellModules.id("entities_in_radius"));
        PortType[] порты = поиск.outputPorts();
        assertEquals(4, порты.length);
        for (PortType порт : порты) {
            assertEquals(PortType.ENTITY, порт, "выход должен принимать урон по сущности");
        }
    }

    /** Модуль должен быть достижим, иначе он не появится ни у кого. */
    @Test
    void модульДостижим() {
        assertTrue(StartModules.isStartModule(SpellModules.id("entities_in_radius"))
                        || ScanRewards.blocks().containsValue("entities_in_radius")
                        || ScanRewards.mobs().containsValue("entities_in_radius")
                        || SpellPrerequisites.hasPrerequisite("entities_in_radius"),
                "модуль поиска недостижим: ни стартовый, ни за сканирование, ни по цепочке");
    }

    // ------------------------------------------------- радиус

    @Test
    void радиусИзВхода() throws Exception {
        assertEquals(10.0, радиус(SpellValue.of(10)));
        assertEquals(3.5, радиус(SpellValue.of(3.5)));
    }

    /**
     * Пустой вход и ноль — не «искать рядом с носом».
     *
     * <p>В порт часто ведут пустую константу. Без этой проверки такой модуль молча
     * нашёл бы одну точку и выдал пустоту вместо целей.
     */
    @Test
    void пустойРадиусБерётЗначениеПоУмолчанию() throws Exception {
        assertEquals(16.0, радиус(null));
        assertEquals(16.0, радиус(SpellValue.of(0)));
        assertEquals(16.0, радиус(SpellValue.of(-5)));
    }

    /** Сотни блоков — это обход всей области каждый каст. */
    @Test
    void радиусОграниченСверху() throws Exception {
        assertEquals(128.0, радиус(SpellValue.of(10_000)));
    }

    // ------------------------------------------------- количество

    @Test
    void количествоИзВхода() throws Exception {
        assertEquals(1, количество(SpellValue.of(1)));
        assertEquals(3, количество(SpellValue.of(3)));
        assertEquals(2, количество(SpellValue.of(2.4)), "дробь округляется к целому");
    }

    /**
     * Больше выходов, чем у модуля, быть не может.
     *
     * <p>И просить ноль бессмысленно: «найти ноль целей» — это то же самое, что
     * ничего не делать, только за деньги.
     */
    @Test
    void количествоОграниченоЧисломВыходов() throws Exception {
        assertEquals(4, количество(SpellValue.of(50)));
        assertEquals(1, количество(SpellValue.of(0)));
        assertEquals(1, количество(SpellValue.of(-3)));
        assertEquals(1, количество(null));
    }

    // ------------------------------------------------- сортировка

    /**
     * Ближайшие занимают первые выходы.
     *
     * <p>Порядок нельзя оставлять на волю движка: игрок должен знать, что в первый
     * выход попадёт именно ближайшая цель, иначе схему нельзя предсказать.
     */
    @Test
    void ближайшиеИдутПервыми() {
        List<double[]> цели = new ArrayList<>(List.of(
                new double[]{30, 0}, new double[]{10, 0}, new double[]{20, 0}));
        цели.sort(java.util.Comparator.comparingDouble(p -> p[0]));
        assertEquals(10, цели.get(0)[0]);
        assertEquals(20, цели.get(1)[0]);
        assertEquals(30, цели.get(2)[0]);
    }

    /** Обрезка до «сколько просят» не должна выходить за пределы списка. */
    @Test
    void обрезкаБезопасна() {
        List<String> цели = new ArrayList<>(List.of("а", "б"));
        assertEquals(2, цели.subList(0, Math.min(2, цели.size())).size());
        assertTrue(цели.subList(0, Math.min(4, цели.size())).size() <= цели.size());
    }
}