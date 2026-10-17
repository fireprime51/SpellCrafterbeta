package com.github.fireprime5.spellcraft.machine;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Модульные сетки машин.
 *
 * <p>Сетка ограничивает не только набор модулей, но и то, что происходит при
 * чистке сохранённого графа. Раньше чистка стирала всё, кроме первого узла:
 * игрок терял схему молча, без единого сообщения.
 */
class MachineGridTest {

    @BeforeAll
    static void setUp() {
        com.github.fireprime5.spellcraft.spell.SpellModules.init();
    }

    private static MachineGrid smelter() {
        return new MachineGrid(MachineGrid.Sets.smelter());
    }

    private static void place(MachineGrid grid, String module, int x, int y) {
        var type = com.github.fireprime5.spellcraft.spell.SpellModules.get(
                com.github.fireprime5.spellcraft.spell.SpellModules.id(module));
        assertTrue(grid.add(type, x, y), "не поставился модуль " + module);
    }

    /** Чужой модуль нельзя поставить даже если очень хочется. */
    @Test
    void чужойМодульНеСтавится() {
        MachineGrid grid = smelter();
        var teleport = com.github.fireprime5.spellcraft.spell.SpellModules.get(
                com.github.fireprime5.spellcraft.spell.SpellModules.id("effect_glowing"));
        assertFalse(grid.add(teleport, 0, 0),
                "в плавильню нельзя поставить модуль эффекта");
        assertTrue(grid.isEmpty());
    }

    /**
     * Регрессия: чистка не должна стирать схему.
     *
     * <p>Модуль исчезает при смене версии мода — и из графа выбрасывается
     * только он. Остальные обязаны остаться: иначе обновление игры обнуляло бы
     * работу игрока.
     */
    @Test
    void чисткаОставляетОстальныеМодули() {
        MachineGrid grid = smelter();
        place(grid, "const_zero", 0, 0);
        place(grid, "const_one", 1, 0);
        place(grid, "const_five", 2, 0);
        assertEquals(3, grid.moduleCount());

        // подсовываем модуль, которого машина не разрешает
        var tag = grid.save();
        var graph = com.github.fireprime5.spellcraft.spell.SpellGraph.load(tag);
        graph.addNode(com.github.fireprime5.spellcraft.spell.SpellModules.id("effect_glowing"),
                3, 0);

        grid.load(graph.save());
        assertEquals(3, grid.moduleCount(),
                "после чистки пропало всё, кроме первого модуля");
    }

    /** Связи после чистки остаются валидными: индексы не должны уезжать. */
    @Test
    void связиПослеЧисткиНеЛомаются() {
        MachineGrid grid = smelter();
        place(grid, "const_zero", 0, 0);
        place(grid, "add", 1, 0);
        place(grid, "const_one", 2, 0);
        grid.graph().connect(0, 0, 1, 0);
        grid.graph().connect(2, 0, 1, 1);

        var graph = grid.graph();
        graph.addNode(com.github.fireprime5.spellcraft.spell.SpellModules.id("effect_glowing"),
                0, 1);
        grid.load(graph.save());

        assertEquals(3, grid.moduleCount());
        assertTrue(grid.graph().validate().isEmpty(),
                "после чистки связи ссылаются в пустоту: " + grid.graph().validate());
    }

    /** Пустая сетка не требует бездны: налог платится за настроенное, а не за блок. */
    @Test
    void пустаяСеткаНеТребуетБездны() {
        MachineGrid grid = smelter();
        assertEquals(0, grid.abyssDemand());
        place(grid, "const_zero", 0, 0);
        assertEquals(MachineGrid.ABYSS_PER_MODULE, grid.abyssDemand(),
                "один модуль должен стоить ровно одного налога");
    }

    /** Модуль на занятом месте заменяется, а не добавляется вторым. */
    @Test
    void занятаяКлеткаЗаменяется() {
        MachineGrid grid = smelter();
        place(grid, "const_zero", 0, 0);
        place(grid, "const_one", 0, 0);
        assertEquals(1, grid.moduleCount());
    }

    /** Сетка переживает сохранение и загрузку без потерь. */
    @Test
    void схемаПереживаетСохранение() {
        MachineGrid grid = smelter();
        place(grid, "const_zero", 0, 0);
        place(grid, "const_one", 1, 0);

        MachineGrid loaded = new MachineGrid(MachineGrid.Sets.smelter());
        CompoundTag tag = grid.save();
        loaded.load(tag);
        assertEquals(2, loaded.moduleCount());
    }

    /** Предел модулей не даёт превратить машину в склад схем. */
    @Test
    void сеткаОграничена() {
        MachineGrid grid = smelter();
        var type = com.github.fireprime5.spellcraft.spell.SpellModules.get(
                com.github.fireprime5.spellcraft.spell.SpellModules.id("const_zero"));
        for (int i = 0; i < MachineGrid.MAX_NODES; i++) {
            assertTrue(grid.add(type, i, 0), "модуль " + i + " не поставился");
        }
        assertFalse(grid.add(type, 999, 0), "сетка приняла модуль сверх лимита");
    }
}