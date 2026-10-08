package com.github.fireprime5.spellcraft.spell;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверка реестра модулей: у всех есть порты, описания, нет дублей.
 */
class SpellModulesRegistryTest {

    @BeforeAll
    static void setUp() {
        SpellModules.init();
    }

    @Test
    void модулейДостаточноМного() {
        assertTrue(SpellModules.all().size() >= 60,
                "модулей должно быть много, а не " + SpellModules.all().size());
    }

    @Test
    void уКаждогоМодуляЕстьОписание() {
        for (SpellModuleType type : SpellModules.all()) {
            assertNotNull(type.nameKey());
            assertNotNull(type.descriptionKey());
            assertNotNull(type.category());
        }
    }

    @Test
    void типыПортовСогласованыСТочником() {
        for (SpellModuleType type : SpellModules.all()) {
            for (int i = 0; i < type.inputs(); i++) {
                assertNotNull(type.inputType(i), type.id() + " вход " + i);
            }
            for (int i = 0; i < type.outputs(); i++) {
                assertNotNull(type.outputType(i), type.id() + " выход " + i);
            }
        }
    }

    @Test
    void нетДублейИдентификаторов() {
        Set<String> ids = new HashSet<>();
        for (SpellModuleType type : SpellModules.all()) {
            assertTrue(ids.add(type.id().toString()), "дубль: " + type.id());
        }
    }

    @Test
    void поискНаходитНовыеМодули() {
        assertFalse(SpellModules.search("молния", null).isEmpty(), "поиск по «молния»");
        assertFalse(SpellModules.search("телепорт", null).isEmpty(), "поиск по «телепорт»");
        assertFalse(SpellModules.search("здоровье", null).isEmpty(), "поиск по здоровью");
        assertFalse(SpellModules.search("effect_speed", null).isEmpty());
    }

    @Test
    void условиеSelectРаботаетПоТипам() {
        SpellModuleType select = SpellModules.get(SpellModules.id("select"));
        assertNotNull(select);
        assertEquals(4, select.inputs());
        assertEquals(1, select.outputs());
        assertEquals(PortType.NUMBER, select.inputType(0));
        assertEquals(PortType.ANY, select.inputType(2));
    }

    @Test
    void duplicateОтдаётДваВыхода() {
        SpellModuleType dup = SpellModules.get(SpellModules.id("duplicate"));
        assertEquals(1, dup.inputs());
        assertEquals(2, dup.outputs());
    }

    /**
     * У модуля с несколькими выходами все выходы должны быть одного типа.
     *
     * <p>Связь в графе хранит только индекс узла, а какое значение придёт,
     * решает порт приёмника. Проверка типов в {@code SpellGraph.validate()}
     * тоже сверяет выход 0. Если выходы разнотипные, эти двое места
     * разойдутся: валидация запретит связь, которую исполнитель обслужил бы
     * верно. Новый разнотипный модуль потребует хранить пару (узел, порт) —
     * это ломает формат сохранения графа.
     */
    @Test
    void уМногоуходныхМодулейВыходыОдногоТипа() {
        for (SpellModuleType type : SpellModules.all()) {
            if (type.outputs() < 2) {
                continue;
            }
            PortType first = type.outputType(0);
            for (int i = 1; i < type.outputs(); i++) {
                assertEquals(first, type.outputType(i),
                        type.id() + ": выходы 0 и " + i + " разного типа ("
                                + first + " и " + type.outputType(i)
                                + ") — валидация и исполнитель разойдутся");
            }
        }
    }

    @Test
    void взрывОстаётсяЕдинственным() {
        long explode = SpellModules.all().stream()
                .filter(t -> t.id().getPath().equals("explode")).count();
        assertEquals(1, explode);
        List<SpellModuleType> actions = SpellModules.search("", SpellModuleType.Category.ACTIONS);
        assertTrue(actions.size() >= 12, "действий должно быть много: " + actions.size());
    }

}