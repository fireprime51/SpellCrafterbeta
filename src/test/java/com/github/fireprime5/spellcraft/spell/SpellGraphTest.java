package com.github.fireprime5.spellcraft.spell;

import org.junit.jupiter.api.BeforeAll;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверка графа заклинания: соединения, валидация, топологический порядок.
 */
class SpellGraphTest {

    @BeforeAll
    static void setUp() {
        // в тестах модули регистрируются вручную (в игре это делает SpellcraftMod)
        SpellModules.init();
    }

    private SpellNode add(SpellGraph g, String id, int x, int y) {
        return g.addNode(SpellModules.id(id), x, y);
    }

    @Test
    void пустоеЗаклинаниеДаётОшибку() {
        SpellGraph g = new SpellGraph();
        List<SpellIssue> issues = g.validate();
        assertEquals(1, issues.size());
        assertEquals("graph.empty", issues.get(0).key());
    }

    /** Правильное заклинание: Кастер -> Взгляд -> Луч: блок -> Взрыв, сила = 10. */
    @Test
    void цепочкаКастерВзглядЛучВзрывВалидна() {
        SpellGraph g = new SpellGraph();
        add(g, "caster_entity", 0, 0);
        add(g, "entity_look", 1, 0);
        add(g, "raycast_block", 2, 0);
        add(g, "explode", 3, 0);
        add(g, "const_ten", 2, 1);

        assertTrue(g.connect(0, 0, 1, 0), "кастер -> взгляд");
        assertTrue(g.connect(1, 0, 2, 0), "взгляд -> луч");
        assertTrue(g.connect(2, 0, 3, 0), "луч -> взрыв (точка)");
        assertTrue(g.connect(4, 0, 3, 1), "10 -> взрыв (сила)");

        List<SpellIssue> issues = g.validate();
        assertTrue(issues.isEmpty(), "ошибок быть не должно: " + issues);

        List<Integer> order = g.topologicalOrder(new java.util.ArrayList<>());
        assertNotNull(order, "циклов быть не должно");
        for (int i = 0; i < 3; i++) {
            assertTrue(order.indexOf(i) < order.indexOf(3), "узел " + i + " раньше взрыва");
        }
        assertTrue(order.indexOf(4) < order.indexOf(3), "константа раньше взрыва");
        assertEquals(3, order.get(order.size() - 1), "взрыв выполняется последним");
    }

    @Test
    void несовместимыеТипыЛовятся() {
        // вектор из «Взгляд» нельзя напрямую скормить «Взрыв» — нужен «Луч: блок»
        SpellGraph g = new SpellGraph();
        add(g, "caster_entity", 0, 0);
        add(g, "entity_look", 1, 0);
        add(g, "explode", 2, 0);
        add(g, "const_ten", 1, 1);
        g.connect(0, 0, 1, 0);
        g.connect(1, 0, 2, 0);
        g.connect(3, 0, 2, 1);

        List<SpellIssue> issues = g.validate();
        assertTrue(issues.stream().anyMatch(i -> i.key().equals("graph.type_mismatch")),
                "должна быть ошибка типа: " + issues);
    }

    @Test
    void уУронаЕстьВыходСущности() {
        SpellModuleType damage = SpellModules.get(SpellModules.id("damage"));
        assertEquals(1, damage.outputs(), "у урона есть выход");
        assertEquals(PortType.ENTITY, damage.outputType(0), "выход урона — сущность");
        assertEquals(PortType.ENTITY, damage.inputType(0));
        assertEquals(PortType.NUMBER, damage.inputType(1));
    }

    @Test
    void типыПортовСовместимы() {
        assertTrue(PortType.ANY.accepts(PortType.NUMBER));
        assertTrue(PortType.NUMBER.accepts(PortType.NUMBER));
        assertTrue(!PortType.NUMBER.accepts(PortType.ENTITY), "число не принимает сущность");
        assertTrue(!PortType.POINT.accepts(PortType.VECTOR), "точка не принимает вектор");
    }

    @Test
    void неподключённыйВходДаётОшибку() {
        SpellGraph g = new SpellGraph();
        add(g, "explode", 0, 0);
        List<SpellIssue> issues = g.validate();
        assertEquals(2, issues.size(), "два неподключённых входа: " + issues);
        assertTrue(issues.stream().allMatch(i -> i.key().equals("graph.missing_input")));
    }

    @Test
    void циклОбнаруживается() {
        SpellGraph g = new SpellGraph();
        add(g, "add", 0, 0);
        add(g, "add", 1, 0);
        g.connect(1, 0, 0, 0);
        g.connect(0, 0, 1, 1);
        List<SpellIssue> issues = g.validate();
        assertTrue(issues.stream().anyMatch(i -> i.key().equals("graph.cycle")), "цикл: " + issues);
    }

    @Test
    void удалениеУзлаЧинитИндексы() {
        SpellGraph g = new SpellGraph();
        add(g, "const_one", 0, 0);
        add(g, "add", 1, 0);
        add(g, "const_five", 2, 0);
        g.connect(0, 0, 1, 0);
        g.connect(2, 0, 1, 1);

        g.removeNode(0); // удаляем константу 1
        assertEquals(2, g.nodes.size());
        // порт 0 у add должен стать пустым, порт 1 — переиндексирован на 1 (const_five)
        assertEquals(-1, g.nodes.get(0).inputConnections.get(0));
        assertEquals(1, g.nodes.get(0).inputConnections.get(1));
        assertTrue(g.validate().stream().anyMatch(i -> i.key().equals("graph.missing_input")));
    }

    @Test
    void взрывБезЛучаЛовитОшибкуТипа() {
        SpellGraph g = new SpellGraph();
        add(g, "entity_look", 0, 0);
        add(g, "explode", 1, 0);
        add(g, "const_five", 0, 1);
        g.connect(0, 0, 1, 0);
        g.connect(2, 0, 1, 1);
        assertTrue(g.validate().stream()
                .anyMatch(i -> i.key().equals("graph.type_mismatch")));
    }

    @Test
    void необязательныйВходМожноНеПодключать() {
        SpellGraph g = new SpellGraph();
        add(g, "block_under_crosshair", 0, 0);
        add(g, "break_block", 1, 0);
        g.connect(0, 0, 1, 0); // только точка, область не подключена
        List<SpellIssue> issues = g.validate();
        assertTrue(issues.isEmpty(), "необязательный вход не должен требоваться: " + issues);
    }

    @Test
    void добычаПринимаетЧислоКакРадиус() {
        SpellGraph g = new SpellGraph();
        add(g, "block_under_crosshair", 0, 0);
        add(g, "const_five", 0, 1);
        add(g, "break_block", 1, 0);
        assertTrue(g.connect(0, 0, 2, 0), "блок -> добыча");
        assertTrue(g.connect(1, 0, 2, 1), "число (радиус) -> добыча");
        assertTrue(g.validate().isEmpty(), "ошибок нет: " + g.validate());
    }

    @Test
    void точныеКоординатыСобираются() {
        SpellGraph g = new SpellGraph();
        add(g, "const_five", 0, 0);       // X
        add(g, "const_ten", 1, 0);       // Y
        add(g, "const_neg_one", 2, 0);   // Z
        add(g, "point_from_coords", 3, 0);
        g.connect(0, 0, 3, 0);
        g.connect(1, 0, 3, 1);
        g.connect(2, 0, 3, 2);
        assertTrue(g.validate().isEmpty(), "ошибок нет: " + g.validate());
    }

    @Test
    void модулиЭффектовЗарегистрированы() {
        long effects = SpellModules.all().stream()
                .filter(t -> t.id().getPath().startsWith("effect_")).count();
        assertTrue(effects >= 30, "эффектов должно быть много, а не " + effects);
        SpellModuleType speed = SpellModules.get(SpellModules.id("effect_speed"));
        assertNotNull(speed, "модуль эффекта скорости");
        assertEquals(3, speed.inputs());
        assertEquals(1, speed.outputs());
        assertEquals("effect.minecraft.speed", speed.nameKey(),
                "название берётся из ванильного перевода эффекта");
    }

    /** Стартовая цепочка должна быть полностью бесплатной, иначе первую ману не добыть. */
    @Test
    void стартоваяЦепочкаБесплатна() {
        SpellGraph g = new SpellGraph();
        add(g, "caster_entity", 0, 0);
        add(g, "entity_look", 1, 0);
        add(g, "raycast_block", 2, 0);
        add(g, "extract_block", 3, 0);
        g.connect(0, 0, 1, 0);
        g.connect(1, 0, 2, 0);
        g.connect(2, 0, 3, 0);
        assertEquals(0, g.totalManaCost(),
                "Кастер → Взгляд → Луч: блок → Извлечение должен стоить 0 маны");
        assertTrue(g.validate().isEmpty(), "цепочка должна быть валидна: " + g.validate());

        // и цепочка для мобов тоже
        SpellGraph mobs = new SpellGraph();
        add(mobs, "caster_entity", 0, 0);
        add(mobs, "entity_look", 1, 0);
        add(mobs, "raycast_entity", 2, 0);
        add(mobs, "extract_mob", 3, 0);
        mobs.connect(0, 0, 1, 0);
        mobs.connect(1, 0, 2, 0);
        mobs.connect(2, 0, 3, 0);
        assertEquals(0, mobs.totalManaCost());

        // а анализ — тоже бесплатный
        SpellGraph scan = new SpellGraph();
        add(scan, "caster_entity", 0, 0);
        add(scan, "entity_look", 1, 0);
        add(scan, "raycast_block", 2, 0);
        add(scan, "analysis_block", 3, 0);
        scan.connect(0, 0, 1, 0);
        scan.connect(1, 0, 2, 0);
        scan.connect(2, 0, 3, 0);
        assertEquals(0, scan.totalManaCost(), "анализ тоже должен быть бесплатным");
    }

    @Test
    void стоимостьСуммируется() {
        SpellGraph g = new SpellGraph();
        add(g, "const_ten", 0, 0);
        add(g, "explode", 1, 0);
        int expected = SpellModules.get(SpellModules.id("const_ten")).manaCost()
                + SpellModules.get(SpellModules.id("explode")).manaCost();
        assertEquals(expected, g.totalManaCost());
    }

    @Test
    void сериализацияСохраняетСвязи() {
        SpellGraph g = new SpellGraph();
        add(g, "caster_entity", 0, 0);
        add(g, "entity_look", 1, 0);
        g.connect(0, 0, 1, 0);

        SpellGraph restored = SpellGraph.load(g.save());
        assertEquals(2, restored.nodes.size());
        assertEquals(0, restored.nodes.get(1).inputConnections.get(0));
        assertEquals(g.nodes.get(0).moduleId, restored.nodes.get(0).moduleId);
        assertTrue(restored.validate().isEmpty());
    }

    @Test
    void поискПоНазваниюНаходитМодуль() {
        List<SpellModuleType> found = SpellModules.search("expl", null);
        assertTrue(found.stream().anyMatch(t -> t.id().getPath().equals("explode")));
        assertTrue(SpellModules.search("ffff", null).isEmpty(), "пустой результат ожидаем");
    }

    @Test
    void уКаждогоМодуляЕстьОписание() {
        for (SpellModuleType type : SpellModules.all()) {
            assertNotNull(type.descriptionKey());
            assertTrue(type.inputs() >= 0 && type.outputs() >= 0);
        }
    }

    @Test
    void модификаторЧужогоМодуляОшибка() {
        SpellModules.init();
        SpellGraph g = new SpellGraph();
        var node = g.addNode(SpellModules.id("break_block"), 0, 0);
        var point = g.addNode(SpellModules.id("block_under_crosshair"), 1, 0);
        var one = g.addNode(SpellModules.id("const_one"), 1, 1);
        assertTrue(g.connect(1, 0, 0, 0));
        assertTrue(g.connect(2, 0, 0, 1));
        // поджиг — галочка взрыва, у добычи её быть не должно
        node.modifiers.add(SpellModifiers.FIRE.id());

        assertTrue(g.validate().stream()
                        .anyMatch(i -> i.key().equals("graph.modifier_not_allowed")),
                "нужна ошибка " + g.validate());
    }

    @Test
    void конфликтныеГалочкиЧистятсяНаСервере() {
        SpellModules.init();
        SpellGraph g = new SpellGraph();
        var node = g.addNode(SpellModules.id("break_block"), 0, 0);
        node.modifiers.add(SpellModifiers.SILK.id());
        node.modifiers.add(SpellModifiers.FORTUNE.id());
        node.modifiers.add(SpellModifiers.AREA.id());
        node.modifiers.add("no_such_modifier");
        node.modifiers.add(SpellModifiers.FIRE.id()); // не разрешён для добычи

        assertTrue(g.sanitizeModifiers(null), "граф изменился");
        assertEquals(java.util.Set.of(SpellModifiers.SILK.id(), SpellModifiers.AREA.id()),
                new java.util.HashSet<>(node.modifiers),
                "остались только неконфликтующие и разрешённые");
        assertFalse(g.sanitizeModifiers(null), "повторный вызов ничего не меняет");
    }

    @Test
    void ценаМодификатораРастетСоЗнаниями() {
        SpellModules.init();
        int first = SpellModifiers.learnCost(SpellModifiers.POWERFUL, 0);
        int second = SpellModifiers.learnCost(SpellModifiers.POWERFUL, 1);
        int fifth = SpellModifiers.learnCost(SpellModifiers.POWERFUL, 4);
        assertEquals(SpellModifiers.POWERFUL.learnCost(), first,
                "первый модификатор стоит базовую цену");
        assertEquals(first + SpellModifiers.LEARN_STEP, second,
                "второй дороже на шаг");
        assertTrue(fifth > second, "каждый следующий дороже предыдущего");
        assertEquals(first % 10, 0, "цена кратна десятку");
    }

    @Test
    void модульAreaБольшеНеНужен() {
        SpellModules.init();
        assertNull(SpellModules.get(SpellModules.id("area")),
                "радиус идёт числом прямо в «Добычу»");
        assertNull(SpellModules.get(SpellModules.id("break_block_silk")),
                "шёлк — галочка, а не отдельный модуль");
        assertNull(SpellModules.get(SpellModules.id("break_block_fortune")),
                "удача — галочка, а не отдельный модуль");
    }

    @Test
    void подсказкаВходаМеняетсяОтГалочек() {
        SpellModules.init();
        var type = SpellModules.get(SpellModules.id("break_block"));
        var none = new java.util.HashSet<String>();
        assertEquals(Component.translatable("hint.spellcraft.break_area").getString(),
                type.inputHint(1, new java.util.HashSet<>(
                        java.util.List.of(SpellModifiers.AREA.id()))));
        assertEquals(Component.translatable("hint.spellcraft.break_fortune").getString(),
                type.inputHint(1, new java.util.HashSet<>(
                        java.util.List.of(SpellModifiers.FORTUNE.id()))));
        assertEquals(Component.translatable("hint.spellcraft.break_plain").getString(),
                type.inputHint(1, none));
        assertEquals("", type.inputHint(0, none), "у первого входа подсказки нет");
    }
}
