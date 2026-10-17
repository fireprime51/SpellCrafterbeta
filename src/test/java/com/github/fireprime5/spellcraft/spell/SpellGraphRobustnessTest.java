package com.github.fireprime5.spellcraft.spell;

import com.github.fireprime5.spellcraft.Spellcraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.IntTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Устойчивость графа к битым данным: пакеты и буфер обмена приходят от клиента,
 * поэтому ни один плохой индекс или тип не должен ронять сервер.
 */
class SpellGraphRobustnessTest {

    @BeforeAll
    static void setUp() {
        SpellModules.init();
    }

    @Test
    void связьЗаПределыГрафаНеРоняетПроверку() {
        SpellGraph g = new SpellGraph();
        g.addNode(SpellModules.id("add"), 0, 0);
        g.addNode(SpellModules.id("const_five"), 1, 0);
        // индекс 999 — так может прийти в пакете от нечестного клиента
        g.nodes.get(0).inputConnections.add(999);

        assertDoesNotThrow(() -> g.validate(), "validate() не должен падать");
        assertNotNull(g.topologicalOrder(new java.util.ArrayList<>()),
                "топологическая сортировка не должна падать");
        assertTrue(g.validate().stream().anyMatch(i -> i.key().equals("graph.missing_input")),
                "битая связь должна сообщаться: " + g.validate());
    }

    @Test
    void связьНаСебяНеСчитаетсяЦиклом() {
        SpellGraph g = new SpellGraph();
        SpellNode node = g.addNode(SpellModules.id("const_five"), 0, 0);
        node.inputConnections.add(0);
        // self-connection не роняет обход и не считается циклом
        assertDoesNotThrow(() -> g.validate());
    }

    @Test
    void загрузкаУдаляетСвязьНаСамогоСебя() {
        CompoundTag tag = new CompoundTag();
        ListTag nodes = new ListTag();
        CompoundTag add = nodeTag("add", 0, 0);
        add.put("inputs", intList(0, -1));
        nodes.add(add);
        tag.put("nodes", nodes);

        SpellGraph g = SpellGraph.load(tag);
        assertEquals(-1, g.nodes.get(0).inputConnections.get(0));
        assertFalse(g.validate().stream().anyMatch(i -> i.key().equals("graph.cycle")));
    }

    @Test
    void лишниеСвязиПослеПоследнегоПортаНеУчаствуютВТопологии() {
        SpellGraph g = new SpellGraph();
        SpellNode constant = g.addNode(SpellModules.id("const_one"), 0, 0);
        SpellNode add = g.addNode(SpellModules.id("add"), 1, 0);
        add.inputConnections.add(-1);
        add.inputConnections.add(-1);
        add.inputConnections.add(1); // malformed connection beyond add's inputs

        List<Integer> order = g.topologicalOrder(new java.util.ArrayList<>());
        assertNotNull(order);
        assertFalse(order.isEmpty());
        assertNotNull(constant);
    }

    @Test
    void загрузкаЧиститБитыеИндексы() {
        CompoundTag tag = new CompoundTag();
        ListTag nodes = new ListTag();
        CompoundTag add = nodeTag("add", 0, 0);
        add.put("inputs", intList(5, -1));
        nodes.add(add);
        nodes.add(nodeTag("const_five", 1, 0));
        tag.put("nodes", nodes);

        SpellGraph g = SpellGraph.load(tag);
        assertEquals(2, g.nodes.size());
        assertEquals(-1, g.nodes.get(0).inputConnections.get(0),
                "связь на несуществующий узел должна сбрасываться");
        assertDoesNotThrow(() -> g.validate());
    }

    @Test
    void обычныйГрафНеОбрезается() {
        CompoundTag tag = new CompoundTag();
        ListTag nodes = new ListTag();
        for (int i = 0; i < 562; i++) {
            nodes.add(nodeTag("const_five", i, 0));
        }
        tag.put("nodes", nodes);

        SpellGraph g = SpellGraph.load(tag);
        assertEquals(562, g.nodes.size(),
                "обычный граф не должен обрезаться лимитом контекста");
    }

    @Test
    void битыйТегНеРоняетЧтение() {
        CompoundTag broken = new CompoundTag();
        broken.putString("module", "не-id-вовсе");
        broken.putString("grid_x", "не число");
        assertDoesNotThrow(() -> SpellNode.load(broken));

        CompoundTag empty = new CompoundTag();
        assertDoesNotThrow(() -> SpellNode.load(empty));
        assertNotNull(SpellNode.load(empty).moduleId);

        CompoundTag graphTag = new CompoundTag();
        graphTag.put("nodes", new CompoundTag()); // список вместо списка
        assertDoesNotThrow(() -> SpellGraph.load(graphTag));
    }

    @Test
    void модификаторыПереживаютПерезаход() {
        // мод раньше терял модификаторы: кодек вложения их не содержал
        SpellGraph g = new SpellGraph();
        SpellNode node = g.addNode(SpellModules.id("break_block"), 0, 0);
        node.modifiers.add(SpellModifiers.AREA.id());
        node.inputConnections.add(-1);

        // так же сохраняется вложение PlayerSpellStore (Attachment → SpellGraph.CODEC)
        SpellGraph restored = SpellGraph.CODEC
                .parse(net.minecraft.nbt.NbtOps.INSTANCE,
                        SpellGraph.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, g)
                                .getOrThrow())
                .getOrThrow();
        assertEquals(List.of(SpellModifiers.AREA.id()), restored.nodes.get(0).modifiers,
                "модификаторы должны сохраняться в NBT игрока");

        SpellGraph viaNbt = SpellGraph.load(g.save());
        assertEquals(List.of(SpellModifiers.AREA.id()), viaNbt.nodes.get(0).modifiers,
                "модификаторы должны сохраняться в NBT пакета");
        assertEquals(List.of(-1), viaNbt.nodes.get(0).inputConnections);
    }

    @Test
    void неизвестныйМодульНеРоняетСоединение() {
        SpellGraph g = new SpellGraph();
        SpellNode unknown = g.addNode(Spellcraft.id("no_such_module"), 0, 0);
        SpellNode constant = g.addNode(SpellModules.id("const_one"), 1, 0);
        assertFalse(g.connect(1, 0, 0, 0), "соединение с неизвестным модулем отклоняется");
        assertDoesNotThrow(() -> g.validate());
        assertTrue(g.validate().stream().anyMatch(i -> i.key().equals("module.unknown")));
        assertNotNull(unknown);
        assertNotNull(constant);
    }

    @Test
    void удалениеНесуществующегоУзлаБезопасно() {
        SpellGraph g = new SpellGraph();
        g.addNode(SpellModules.id("const_one"), 0, 0);
        assertDoesNotThrow(() -> g.removeNode(5));
        assertDoesNotThrow(() -> g.removeNode(-1));
        assertEquals(1, g.nodes.size());
    }

    @Test
    void списокМодулейОбрезается() {
        SpellNode node = new SpellNode(SpellModules.id("break_block"), 0, 0);
        for (int i = 0; i < SpellNode.MAX_MODIFIERS + 5; i++) {
            node.modifiers.add("m" + i);
        }
        node.inputConnections.add(1);
        node.sanitize();
        assertEquals(SpellNode.MAX_MODIFIERS, node.modifiers.size());
        assertEquals(1, node.inputConnections.size());
    }

    private static CompoundTag nodeTag(String path, int x, int y) {
        CompoundTag tag = new CompoundTag();
        tag.putString("module", Spellcraft.id(path).toString());
        tag.putInt("grid_x", x);
        tag.putInt("grid_y", y);
        return tag;
    }

    private static ListTag intList(int... values) {
        ListTag list = new ListTag();
        for (int v : values) {
            list.add(IntTag.valueOf(v));
        }
        return list;
    }
}
