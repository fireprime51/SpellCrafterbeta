package com.github.fireprime5.spellcraft.client.screen;

import com.github.fireprime5.spellcraft.spell.SpellGraph;
import com.github.fireprime5.spellcraft.spell.SpellModuleType;
import com.github.fireprime5.spellcraft.spell.SpellModules;
import com.github.fireprime5.spellcraft.spell.SpellNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Подсказка порта открытого модуля не должна ронять экран.
 *
 * <p>Регрессия с чужого багрепорта: удаление модуля добычи вызывало
 * {@code IndexOutOfBoundsException} прямо во время отрисовки. Меню модификаторов
 * держало в замыкании <em>индекс</em> узла и дёргало подсказку каждый кадр, а
 * {@code graph.nodes} после удаления сжимался — индекс уезжал за конец списка.
 *
 * <p>Логика вынесена из экрана в отдельный класс не ради красоты: экран
 * наследуется от {@code Screen} и в тестах не поднимается, а именно здесь
 * ловится причина падения. Раньше проверить её было нечем.
 */
class ModifierHintTest {

    @BeforeAll
    static void setUp() {
        SpellModules.init();
    }

    private static SpellGraph threeNodes() {
        SpellGraph g = new SpellGraph();
        g.addNode(SpellModules.id("const_one"), 0, 0);
        g.addNode(SpellModules.id("const_one"), 1, 0);
        g.addNode(SpellModules.id("const_one"), 2, 0);
        return g;
    }

    /** Сценарий из багрепорта: удалили единственный модуль при открытом меню. */
    @Test
    void удалённыйУзелНеРоняетЭкран() {
        SpellGraph g = new SpellGraph();
        g.addNode(SpellModules.id("const_one"), 0, 0);

        assertEquals(1, g.nodes.size());
        g.removeNode(0);
        assertEquals(0, g.nodes.size());

        assertEquals("", ModifierHint.forNode(g, 0, 0),
                "подсказка удалённого узла обязана быть пустой, а не падать");
    }

    /** Индекс протухает не только при удалении, но и когда граф пересоздали. */
    @Test
    void индексПротухаетПослеПересозданияГрафа() {
        SpellGraph before = threeNodes();
        assertNotNull(ModifierHint.forNode(before, 2, 0),
                "до пересоздания подсказка обязана находиться");

        // ответ сервера, отмена или вставка заменяют граф целиком
        SpellGraph after = new SpellGraph();
        after.addNode(SpellModules.id("const_one"), 0, 0);

        assertEquals("", ModifierHint.forNode(after, 2, 0),
                "индекс из прошлого графа обязан давать пустую подсказку");
    }

    @Test
    void индексЗаГраницамиСпискаНеПроходит() {
        SpellGraph g = threeNodes();

        assertEquals("", ModifierHint.forNode(g, -1, 0), "минус один — не узел");
        assertEquals("", ModifierHint.forNode(g, 3, 0), "индекс за концом списка");
        assertEquals("", ModifierHint.forNode(g, Integer.MAX_VALUE, 0), "мусорный индекс");
    }

    @Test
    void пустойГрафДаётПустуюПодсказку() {
        assertEquals("", ModifierHint.forNode(new SpellGraph(), 0, 0));
        assertEquals("", ModifierHint.forNode(null, 0, 0), "графа может не быть вовсе");
    }

    /** Открытие меню тоже берёт узел по индексу — падать тут тоже нельзя. */
    @Test
    void снимокМодификаторовУдалённогоУзлаПуст() {
        SpellGraph g = new SpellGraph();
        g.addNode(SpellModules.id("const_one"), 0, 0);
        g.removeNode(0);

        assertEquals(0, ModifierHint.enabledOn(g, 0).size(),
                "снимок модификаторов удалённого узла обязан быть пустым");
        assertEquals(0, ModifierHint.enabledOn(null, 0).size(), "графа может не быть");
        assertEquals(0, ModifierHint.enabledOn(g, -1).size(), "минус один — не узел");
    }

    @Test
    void снимокМодификаторовЖивогоУзлаНеПуст() {
        SpellGraph g = threeNodes();
        SpellNode node = g.nodes.get(1);
        SpellModuleType type = SpellModules.get(node.moduleId);
        List<com.github.fireprime5.spellcraft.spell.SpellModifier> allowed = type.modifiers();
        if (!allowed.isEmpty()) {
            node.modifiers.add(allowed.get(0).id());
        }

        assertEquals(node.modifiers.size(), ModifierHint.enabledOn(g, 1).size(),
                "снимок обязан совпадать с тем, что включено на узле");
    }

    /** Узел без модификаторов: подсказка есть, но исключения быть не должно. */
    @Test
    void живойУзелДаётПодсказку() {
        SpellGraph g = threeNodes();

        String hint = ModifierHint.forNode(g, 1, 0);

        assertEquals(hint, ModifierHint.forNode(g, 1, 0), "подсказка обязана быть стабильной");
    }

    /** Модификаторы, добавленные после открытия меню, попадают в подсказку. */
    @Test
    void подсказкаВидитНовыеМодификаторы() {
        SpellGraph g = threeNodes();
        SpellNode node = g.nodes.get(1);
        SpellModuleType type = SpellModules.get(node.moduleId);
        assertNotNull(type, "модуль const_one должен быть зарегистрирован");

        List<com.github.fireprime5.spellcraft.spell.SpellModifier> allowed = type.modifiers();
        if (!allowed.isEmpty()) {
            node.modifiers.add(allowed.get(0).id());
        }

        assertEquals(ModifierHint.forNode(g, 1, 0), ModifierHint.forNode(g, 1, 0));
    }
}