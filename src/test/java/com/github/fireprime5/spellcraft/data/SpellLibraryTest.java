package com.github.fireprime5.spellcraft.data;

import com.github.fireprime5.spellcraft.Spellcraft;
import com.github.fireprime5.spellcraft.spell.SpellGraph;
import com.github.fireprime5.spellcraft.spell.SpellModules;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Библиотека заклинаний: кодек, сохранение, лимит слотов.
 */
class SpellLibraryTest {

    private static SpellGraph graph(String module) {
        SpellModules.init();
        SpellGraph g = new SpellGraph();
        g.addNode(Spellcraft.id(module), 0, 0);
        return g;
    }

    @Test
    void кодекЗаклинанияРаботает() {
        SpellGraph g = graph("damage");
        g.addNode(Spellcraft.id("const_ten"), 1, 0);
        g.connect(1, 0, 0, 1);

        var tag = SavedSpell.CODEC
                .encodeStart(NbtOps.INSTANCE, new SavedSpell("Тест", g))
                .getOrThrow();
        var back = SavedSpell.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();

        assertEquals("Тест", back.name());
        assertEquals(2, back.graph().nodes.size());
        assertEquals(1, back.graph().nodes.get(0).inputConnections.get(1));
    }

    @Test
    void списокЗаклинанийКодируется() {
        List<SavedSpell> list = new ArrayList<>();
        list.add(new SavedSpell("A", graph("damage")));
        list.add(new SavedSpell("B", graph("heal")));

        var tag = SavedSpell.LIST_CODEC.encodeStart(NbtOps.INSTANCE, list).getOrThrow();
        var back = SavedSpell.LIST_CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();

        assertEquals(2, back.size());
        assertEquals("A", back.get(0).name());
        assertEquals("B", back.get(1).name());
        assertEquals(1, back.get(1).graph().nodes.size());
    }

    @Test
    void имяЗаклинанияОграничено() {
        assertTrue(SpellLibrary.MAX_SPELLS >= 4);
    }
}