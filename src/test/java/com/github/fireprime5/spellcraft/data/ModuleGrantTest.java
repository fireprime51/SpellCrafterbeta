package com.github.fireprime5.spellcraft.data;

import com.github.fireprime5.spellcraft.spell.SpellModules;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Список модулей для отладочной команды: он должен совпадать с реестром.
 */
class ModuleGrantTest {

    @BeforeAll
    static void setUp() {
        SpellModules.init();
    }

    @Test
    void выдаютсяВсеЗарегистрированныеМодули() {
        List<String> ids = PlayerKnowledge.allModuleIds();
        assertEquals(SpellModules.all().size(), ids.size(),
                "каждому зарегистрированному модулю соответствует свой id");

        var expected = new HashSet<String>();
        for (var type : SpellModules.all()) {
            expected.add(type.id().toString());
        }
        assertEquals(expected, new HashSet<>(ids), "состав выдачи совпадает с реестром");
    }

    @Test
    void idМодулейРазбираютсяОбратно() {
        for (String id : PlayerKnowledge.allModuleIds()) {
            assertTrue(net.minecraft.resources.ResourceLocation.tryParse(id) != null,
                    "id должен разбираться: " + id);
        }
    }

    @Test
    void стартовыеМодулиТожеВходят() {
        List<String> ids = PlayerKnowledge.allModuleIds();
        assertTrue(ids.contains("spellcraft:caster_entity"), "стартовые модули тоже выдаются явно");
        assertTrue(ids.contains("spellcraft:break_block"));
    }
}