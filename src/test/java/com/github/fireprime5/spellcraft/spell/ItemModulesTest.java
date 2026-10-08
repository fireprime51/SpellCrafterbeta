package com.github.fireprime5.spellcraft.spell;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Модули предметов и блоков: регистрация, порты и границы.
 *
 * <p>Само исполнение проверяется в игре: модули читают и меняют блоки,
 * трогают инвентарь игрока и бросают предметы в мир, а в тестах Minecraft не
 * загружен. Здесь проверяется то, что можно проверить без мира: состав
 * реестра, типы портов, потолки и то, что каждый модуль достижим сканированием.
 */
class ItemModulesTest {

    private static final String[] MODULES = {
            "block_at", "fluid_at", "held_slot", "empty_hand", "offhand_item",
            "swap_blocks", "fill_region", "clear_block",
            "give_item", "drop_item", "consume_item",
    };

    @BeforeAll
    static void setUp() {
        SpellModules.init();
    }

    private static SpellModuleType module(String path) {
        SpellModuleType type = SpellModules.get(SpellModules.id(path));
        assertNotNull(type, "модуль " + path + " не зарегистрирован");
        return type;
    }

    @Test
    void всеМодулиНаМесте() {
        for (String path : MODULES) {
            assertNotNull(SpellModules.get(SpellModules.id(path)), "нет " + path);
        }
    }

    /** Предметный тип больше не вырожденный: его умеют читать и выдавать. */
    @Test
    void предметТеперьПолноценныйТип() {
        assertEquals(PortType.ITEM, module("block_at").outputType(0),
                "блок читается как предмет");
        assertEquals(PortType.ITEM, module("held_slot").outputType(0));
        assertEquals(PortType.ITEM, module("give_item").inputType(0));
        assertEquals(PortType.ITEM, module("drop_item").inputType(0));
        assertEquals(PortType.ITEM, module("consume_item").inputType(0));
    }

    /** Постройка наконец получает предмет не только из руки. */
    @Test
    void постройкаПринимаетЛюбойПредмет() {
        assertEquals(PortType.ITEM, module("fill_region").inputType(2));
        // блок из мира можно поставить обратно в другое место — раньше было
        // нечем: предмет давался только из руки
        assertTrue(module("place_block").inputType(1).accepts(PortType.ITEM));
    }

    @Test
    void портыЧитающихМодулей() {
        assertEquals(PortType.POINT, module("block_at").inputType(0));
        assertEquals(PortType.POINT, module("fluid_at").inputType(0));
        assertEquals(PortType.NUMBER, module("held_slot").inputType(0));
        assertEquals(0, module("empty_hand").inputs(), "рука проверяется без входа");
        assertEquals(0, module("offhand_item").inputs());
    }

    @Test
    void портыМодулейБлоков() {
        assertEquals(2, module("swap_blocks").inputs());
        assertEquals(PortType.POINT, module("swap_blocks").inputType(0));
        assertEquals(PortType.POINT, module("swap_blocks").inputType(1));
        assertEquals(3, module("fill_region").inputs());
        assertEquals(PortType.ITEM, module("fill_region").inputType(2));
    }

    /** «Выдать» отдаёт число, а не предмет: важно для подсчёта того, что влезло. */
    @Test
    void выдатьОтдаётЧисло() {
        assertEquals(PortType.NUMBER, module("give_item").outputType(0));
    }

    /** «Забрать» отдаёт предмет: его можно пустить дальше по графу. */
    @Test
    void забратьОтдаётПредмет() {
        assertEquals(PortType.ITEM, module("consume_item").outputType(0));
    }

    /**
     * Ни один модуль не создаёт предметы из ничего.
     *
     * <p>«Выдать» кладёт в инвентарь ровно столько, сколько пришло на вход,
     * а не задаёт количество числом. Иначе предмет на единицу размножался бы
     * до стака и модуль стал бы дырой в экономике.
     */
    @Test
    void выдатьНеПринимаетКоличество() {
        assertEquals(1, module("give_item").inputs(),
                "только предмет: количество берётся из самого предмета");
        assertEquals(1, module("consume_item").inputs());
    }

    /** Обмен блоками не должен обходиться дешевле обычной добычи. */
    @Test
    void обменБлоковНеДешевлеДобычи() {
        assertTrue(module("swap_blocks").manaCost() > module("break_block").manaCost(),
                "обмен двух блоков должен стоить дороже добычи одного");
    }

    /** Заливка области — самая дорогая операция с блоками. */
    @Test
    void заливкаОбластиДорогая() {
        assertTrue(module("fill_region").manaCost() > module("place_block").manaCost());
    }

    /** У модулей, меняющих мир, стоимость выше у «чтения». */
    @Test
    void чтениеДешевлеПостройки() {
        assertTrue(module("block_at").manaCost() < module("fill_region").manaCost());
        assertTrue(module("empty_hand").manaCost() < module("give_item").manaCost());
    }

    /**
     * Потолки объявлены и осмысленны.
     *
     * <p>Число предметов и объём области приходят из заклинания, поэтому без
     * потолка один каст мог бы забить инвентарь или обработать миллион блоков.
     */
    @Test
    void потолкиРазумные() {
        assertTrue(ItemModules.MAX_ITEM_COUNT > 0
                        && ItemModules.MAX_ITEM_COUNT <= 4 * 64,
                "потолок предметов: " + ItemModules.MAX_ITEM_COUNT);
        assertTrue(ItemModules.MAX_FILL_BLOCKS >= 64,
                "потолок области слишком мал: " + ItemModules.MAX_FILL_BLOCKS);
        assertTrue(ItemModules.MAX_FILL_BLOCKS <= 4096,
                "потолок области слишком велик: " + ItemModules.MAX_FILL_BLOCKS);
    }

    /**
     * Каждый модуль достижим: иначе он есть в палитре, но его невозможно
     * получить в игре никак — ни сканированием, ни изучением.
     */
    @Test
    void всеНовыеМодулиОткрываютсяСканированием() {
        List<String> rewards = ScanRewards.blocks().values().stream().toList();
        for (String path : MODULES) {
            assertTrue(rewards.contains(path), path + " недостижим: нет цели");
            assertFalse(module(path).isStartModule(),
                    path + " не должен быть стартовым");
        }
    }

    /** Новые модули находятся в палитре поиском. */
    @Test
    void новыеМодулиНаходятсяПоиском() {
        for (String path : MODULES) {
            assertTrue(SpellModules.search(path, null).stream()
                            .anyMatch(t -> t.id().getPath().equals(path)),
                    "поиск не нашёл " + path);
        }
        assertTrue(SpellModules.search("выдать", null).stream()
                        .anyMatch(t -> t.id().getPath().equals("give_item")),
                "поиск по «выдать»");
        assertTrue(SpellModules.search("убрать блок", null).stream()
                        .anyMatch(t -> t.id().getPath().equals("clear_block")),
                "поиск по «убрать блок»");
    }

    /**
     * Связи с существующими модулями проходят по типам.
     *
     * <p>Связь проверяется при касте по типам портов, и несовпадение молча
     * запретило бы сборку: «блок из мира → постройка» и «выдать предмет →
     * положить» должны быть разрешены.
     */
    @Test
    void модулиСобираютсяМеждуСобой() {
        // блок из мира → постройка в другом месте
        assertTrue(module("place_block").inputType(1).accepts(
                module("block_at").outputType(0)));
        // блок из мира → выбросить
        assertTrue(module("drop_item").inputType(0).accepts(
                module("block_at").outputType(0)));
        // предмет из хотбара → выдать обратно (бессмысленно, но типы сходятся)
        assertTrue(module("give_item").inputType(0).accepts(
                module("held_slot").outputType(0)));
        // забрать предмет → положить в область
        assertTrue(module("fill_region").inputType(2).accepts(
                module("consume_item").outputType(0)));
        // число из «сравнения» → номер ячейки
        assertTrue(module("stack_store").inputType(1).accepts(
                module("compare").outputType(0)));
    }

    /**
     * Цепочка «прочитать блок → поставить обратно» описана как рабочая.
     *
     * <p>Именно её нельзя было собрать до этого пакета: предмет давался только
     * из руки, поэтому взять блок из мира и поставить его в другое место было
     * нечем. Тест фиксирует, что типы для этой цепочки сошлись.
     */
    @Test
    void блокИзМираМожноПоставитьОбратно() {
        assertTrue(module("place_block").inputType(1).accepts(PortType.ITEM));
        assertTrue(module("place_block").inputType(0).accepts(PortType.POINT));
        assertEquals(PortType.ITEM, module("block_at").outputType(0));
        assertEquals(PortType.POINT, module("block_at").inputType(0));
    }

    /** У модулей нет обязательных необязательных входов, где это не задумано. */
    @Test
    void необязательныеВходыТамГдеНадо() {
        for (String path : MODULES) {
            SpellModuleType type = module(path);
            assertTrue(type.optionalInputs() <= type.inputs(),
                    path + ": необязательных входов больше, чем входов");
        }
    }
}
