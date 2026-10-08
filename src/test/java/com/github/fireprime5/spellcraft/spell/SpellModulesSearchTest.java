package com.github.fireprime5.spellcraft.spell;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Поиск модулей в палитре: по локализованному названию и по id.
 */
class SpellModulesSearchTest {

    @BeforeAll
    static void setUp() {
        SpellModules.init();
    }

    @Test
    void пустойЗапросВозвращаетВсе() {
        assertFalse(SpellModules.search("", null).isEmpty());
        assertFalse(SpellModules.search("   ", null).isEmpty());
    }

    @Test
    void ищетПоАнглийскомуId() {
        List<SpellModuleType> found = SpellModules.search("expl", null);
        assertTrue(found.stream().anyMatch(t -> t.id().getPath().equals("explode")));
        assertTrue(SpellModules.search("raycast", null).stream()
                .anyMatch(t -> t.id().getPath().equals("raycast_block")));
    }

    @Test
    void ищетПоИдентификаторуСПодчёркиванием() {
        assertFalse(SpellModules.search("raycast_block", null).isEmpty());
        assertFalse(SpellModules.search("raycast block", null).isEmpty());
        assertFalse(SpellModules.search("RAYCAST", null).isEmpty());
    }

    @Test
    void ищетПоРусскимСинонимам() {
        // синонимы работают независимо от языка игры
        assertTrue(SpellModules.search("урон", null).stream()
                .anyMatch(t -> t.id().getPath().equals("damage")));
        assertTrue(SpellModules.search("взрыв", null).stream()
                .anyMatch(t -> t.id().getPath().equals("explode")));
        assertTrue(SpellModules.search("взр", null).stream()
                .anyMatch(t -> t.id().getPath().equals("explode")));
        assertTrue(SpellModules.search("лечит", null).stream()
                .anyMatch(t -> t.id().getPath().equals("heal")));
        assertTrue(SpellModules.search("плюс", null).stream()
                .anyMatch(t -> t.id().getPath().equals("add")));
    }

    @Test
    void фильтрПоКатегорииРаботает() {
        List<SpellModuleType> numbers = SpellModules.search("", SpellModuleType.Category.NUMBERS);
        assertFalse(numbers.isEmpty());
        assertTrue(numbers.stream()
                .allMatch(t -> t.category() == SpellModuleType.Category.NUMBERS));
        assertTrue(numbers.stream().anyMatch(t -> t.id().getPath().equals("const_five")));
    }

    @Test
    void бессмысленныйЗапросДаётПусто() {
        assertTrue(SpellModules.search("zzzqqq", null).isEmpty());
    }

    /**
     * Оси координат берут и точку, и число.
     *
     * <p>Число проходит насквозь: без этого нельзя было вернуть в схему число,
     * собранное арифметикой, — а значит и дотянуться до блока, заданного
     * координатами, без подглядывания под прицел.
     */
    @Test
    void осиКоординатПринимаютИТочкуИЧисло() {
        for (String axis : List.of("coord_x", "coord_y", "coord_z")) {
            SpellModuleType type = SpellModules.get(SpellModules.id(axis));
            assertTrue(type.inputType(0).accepts(PortType.POINT),
                    axis + " должен принимать точку");
            assertTrue(type.inputType(0).accepts(PortType.NUMBER),
                    axis + " должен принимать число");
            assertEquals(PortType.NUMBER, type.outputType(0));
        }
    }

    /** Русские синонимы работают независимо от языка интерфейса. */
    @Test
    void русскиеСинонимыНаходятся() {
        assertFalse(SpellModules.search("урон", null).isEmpty());
        assertFalse(SpellModules.search("плавно", null).isEmpty());
        assertFalse(SpellModules.search("добыча", null).isEmpty());
        assertFalse(SpellModules.search("корень", null).isEmpty());
    }
}