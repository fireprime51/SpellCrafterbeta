package com.github.fireprime5.spellcraft.spell;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Границы, в которых заклинание вообще имеет право что-то делать.
 *
 * <p>Точка в схеме собирается арифметикой от произвольных чисел, а координаты
 * в мире не ограничены. Без общей проверки «Добыча» ломала блоки по всей карте,
 * «Создать лавы» — заливала чужие постройки, а «Извлечение» — вытягивало по
 * 400 энергии бездны из ancient_debris на другом конце континента.
 */
class SpellContextRangeTest {

    private static final Vec3 ORIGIN = Vec3.ZERO;

    @BeforeAll
    static void setUp() {
        SpellModules.init();
    }

    @Test
    void точкаНаДостижимомРасстоянииПроходит() {
        assertDoesNotThrow(() -> SpellContext.requireWithin(ORIGIN, new Vec3(10, 0, 10)));
        assertDoesNotThrow(() -> SpellContext.requireWithin(ORIGIN, new Vec3(0, 0,
                SpellContext.MAX_RANGE)));
    }

    @Test
    void точкаЗаПределомДосягаемостиОтклоняется() {
        double tooFar = SpellContext.MAX_RANGE + 1;
        SpellContext.SpellException e = assertThrows(SpellContext.SpellException.class,
                () -> SpellContext.requireWithin(ORIGIN, new Vec3(tooFar, 0, 0)));
        assertEquals("cast.too_far", e.getKey(),
                "игрок должен понимать, что дело в дальности, а не в чём-то ещё");
    }

    /** Дальность круговая, а не только по каждой оси: диагональ тоже считается. */
    @Test
    void дальностьСчитаетсяПоДиагонали() {
        double half = SpellContext.MAX_RANGE / Math.sqrt(2);
        assertDoesNotThrow(() -> SpellContext.requireWithin(ORIGIN, new Vec3(half, 0, half)));
        assertThrows(SpellContext.SpellException.class,
                () -> SpellContext.requireWithin(ORIGIN, new Vec3(half + 1, 0, half + 1)));
    }

    /** Нечисловые координаты — ошибка, а не «как получится». */
    @Test
    void нечисловыеТочкиОтклоняются() {
        SpellContext ctx = new SpellContext(null, null, ORIGIN);
        assertThrows(SpellContext.SpellException.class,
                () -> ctx.requireInRange(new Vec3(Double.NaN, 0, 0)));
        assertThrows(SpellContext.SpellException.class,
                () -> ctx.requireInRange(new Vec3(Double.POSITIVE_INFINITY, 0, 0)));
        assertThrows(SpellContext.SpellException.class,
                () -> ctx.requireInRange((Vec3) null));
    }

    /** Исчезающая сущность на стеке не должна ронять каст — см. SpellValue. */
    @Test
    void takeСНевернымИндексомНеПадает() {
        SpellContext ctx = new SpellContext(null, null, ORIGIN);
        assertNull(ctx.take(-1));
        assertNull(ctx.take(SpellContext.SLOTS));
        assertNull(ctx.take(SpellContext.SLOTS + 100));
    }

    @Test
    void ячейкиПишутсяТолькоВСвоиГраницы() {
        SpellContext ctx = new SpellContext(null, null, ORIGIN);
        SpellValue value = SpellValue.of(42);
        assertTrue(ctx.store(0, value));
        // перезапись занятой ячейки запрещена — иначе цикл молча портил бы
        // сохранённое значение
        assertFalse(ctx.store(0, SpellValue.of(7)));
        assertEquals(1, ctx.slotsUsed());
        assertEquals(value, ctx.take(0));
        assertEquals(0, ctx.slotsUsed());
    }

    /**
     * Предметная ветка удалена целиком.
     *
     * <p>«Блок на месте» копировал стопку из мира, «Предмет в руке» — из
     * инвентаря, а «Выдать предмет» клал копию обратно. Три модуля вместе давали
     * бесконечные блоки за 11 энергии бездны.
     */
    /**
     * Предметную ветку нельзя собирать заново.
     *
     * <p>«Блок на месте» копировал стопку из мира, «Предмет в руке» — из
     * инвентаря, а «Выдать предмет» клал копию обратно. Три модуля вместе
     * давали бесконечные блоки за 11 энергии бездны. Вернулось только чтение, и
     * возвращаться не должны именно эти три: они и есть источник дюпа.
     */
    @Test
    void источниковПредметовНет() {
        for (String path : new String[]{"block_at", "fluid_at", "give_item", "drop_item",
                "consume_item"}) {
            assertNull(SpellModules.get(SpellModules.id(path)),
                    "модуль " + path + " не должен возвращаться: он источник дюпа");
        }
    }

    /** Читающие модули предметов вернулись — постройка снова работает. */
    @Test
    void чтениеПредметовЕсть() {
        for (String path : new String[]{"held_item", "held_slot", "item_count",
                "place_block", "fill_region", "place_held_block"}) {
            assertNotNull(SpellModules.get(SpellModules.id(path)),
                    "модуль " + path + " должен быть на месте");
        }
        // единственный источник ITEM — то, что уже у игрока в руках
        assertEquals(PortType.ITEM,
                SpellModules.get(SpellModules.id("held_item")).outputType(0));
        assertEquals(PortType.ITEM,
                SpellModules.get(SpellModules.id("place_block")).inputType(1));
    }

    /** «Расщепление» переименовано и стоит целый кошелёк. */
    @Test
    void расщеплениеСжигаетВсюЭнергиюБездны() {
        SpellModuleType fission = SpellModules.get(SpellModules.id("fission"));
        assertNotNull(fission, "модуль fission");
        assertNull(SpellModules.get(SpellModules.id("clear_block")),
                "старое имя использоваться не должно");
        assertEquals(1000, fission.castCost(1000), "цена = потолок энергии бездны");
        assertEquals(500, fission.castCost(500), "цена следует за кошельком");
        assertEquals(0, fission.castCost(0), "без кошелька цена неизвестна");
    }

    /**
     * Телепорт дорогой, и дороже только «Врата».
     *
     * <p>«Врата» стоят 200: они ставят сразу два блока и работают вдвое
     * чаще. Ограничение «телепорт — самый дорогой» перестало быть верным с
     * появлением порталов, поэтому проверяем конкретное соотношение, а не
     * максимум по модулю.
     */
    @Test
    void телепортДорогой() {
        SpellModuleType teleport = SpellModules.get(SpellModules.id("teleport"));
        assertNotNull(teleport, "модуль teleport");
        assertEquals(150, teleport.manaCost());
        assertEquals(200, SpellModules.get(SpellModules.id("portal_gate")).manaCost(),
                "ворота должны стоить дороже телепорта");
        assertEquals(120, SpellModules.get(SpellModules.id("portal_passage")).manaCost(),
                "проход дешевле врат");
    }

    /** Установка здоровья мгновенно убивала кого угодно — модуль не вернулся. */
    @Test
    void установкиЗдоровьяНет() {
        assertNull(SpellModules.get(SpellModules.id("set_health")),
                "модуль set_health должен быть удалён");
    }

    /** У эффектов есть потолки времени и уровня. */
    @Test
    void уЭффектовЕстьПотолки() {
        assertTrue(WorldModules.MAX_EFFECT_SECONDS <= 600,
                "потолок времени эффекта: " + WorldModules.MAX_EFFECT_SECONDS);
        assertTrue(WorldModules.MAX_EFFECT_AMPLIFIER <= 4,
                "потолок уровня эффекта: " + WorldModules.MAX_EFFECT_AMPLIFIER);
    }

    /** Чем дольше и выше эффект, тем дороже. */
    @Test
    void ценаЭффектаРастёт() {
        int shortLow = WorldModules.effectManaCost(1, 0);
        int longLow = WorldModules.effectManaCost(WorldModules.MAX_EFFECT_SECONDS, 0);
        int shortHigh = WorldModules.effectManaCost(1, WorldModules.MAX_EFFECT_AMPLIFIER);
        int longHigh = WorldModules.effectManaCost(WorldModules.MAX_EFFECT_SECONDS,
                WorldModules.MAX_EFFECT_AMPLIFIER);
        assertTrue(longLow > shortLow, "время должно удорожать эффект");
        assertTrue(shortHigh > shortLow, "уровень должен удорожать эффект");
        assertTrue(longHigh > longLow && longHigh > shortHigh);
    }

    /**
     * Цена эффекта должна быть заметной, но не самоубийственной.
     *
     * <p>Энергия бездны в моде не регенерирует сама (её даёт «Извлечение»), так что
     * цена — и есть единственный ограничитель. Проверяем две крайности:
     * короткий базовый эффект должен быть почти бесплатным (иначе эффекты
     * станут недоступны в начале), а максимальный — съедать большую часть
     * кошелька (иначе «навечно вложить» снова окажется выгодным).
     */
    @Test
    void ценаЭффектаВРазумныхПределах() {
        assertTrue(WorldModules.effectManaCost(1, 0) < 20,
                "эффект на секунду без усиления должен быть почти бесплатным: "
                        + WorldModules.effectManaCost(1, 0));
        int max = WorldModules.effectManaCost(WorldModules.MAX_EFFECT_SECONDS,
                WorldModules.MAX_EFFECT_AMPLIFIER);
        assertTrue(max > 500 && max < 1000,
                "максимальный эффект должен стоить большую часть кошелька, а не 630: " + max);
    }

    /** Секунды и уровень зажимаются, а не обрезают каст до нуля. */
    @Test
    void ценаЭффектаЗажимается() {
        assertEquals(WorldModules.effectManaCost(WorldModules.MIN_EFFECT_SECONDS, 0),
                WorldModules.effectManaCost(-9999, -9999),
                "отрицательные числа не должны давать бесплатный эффект");
        assertEquals(WorldModules.effectManaCost(WorldModules.MAX_EFFECT_SECONDS,
                        WorldModules.MAX_EFFECT_AMPLIFIER),
                WorldModules.effectManaCost(999999, 999999),
                "огромные числа не должны стоить дороже потолка");
    }

    /**
     * Каждая цель сканирования открывает реально существующий модуль.
     *
     * <p>Иначе игрок сканирует цель, получает «пусто», а таблица наград молча
     * указывает на удалённый модуль: {@code KnowledgeHooks.scan} находит null и
     * тихо выходит.
     */
    @Test
    void всеНаградыСканированияСуществуют() {
        for (var entry : ScanRewards.blocks().entrySet()) {
            assertNotNull(SpellModules.get(Spellcraft_id(entry.getValue())),
                    "блок " + entry.getKey() + " открывает несуществующий " + entry.getValue());
        }
        for (var entry : ScanRewards.mobs().entrySet()) {
            assertNotNull(SpellModules.get(Spellcraft_id(entry.getValue())),
                    "моб " + entry.getKey() + " открывает несуществующий " + entry.getValue());
        }
    }

    private static net.minecraft.resources.ResourceLocation Spellcraft_id(String path) {
        return com.github.fireprime5.spellcraft.Spellcraft.id(path);
    }

    /** Потолки объявлены и осмысленны: без них один каст обрабатывал бы тысячи блоков. */
    @Test
    void потолкиМираРазумны() {
        assertTrue(WorldModules.MAX_VEIN_BLOCKS > 0
                        && WorldModules.MAX_VEIN_BLOCKS <= 128,
                "потолок мгновенной добычи: " + WorldModules.MAX_VEIN_BLOCKS);
        assertTrue(WorldModules.MAX_PLACED_BLOCKS > 0
                        && WorldModules.MAX_PLACED_BLOCKS <= 64,
                "потолок постройки: " + WorldModules.MAX_PLACED_BLOCKS);
        assertTrue(WorldModules.MAX_DRAINED_BLOCKS > 0
                        && WorldModules.MAX_DRAINED_BLOCKS <= 128,
                "потолок осушения: " + WorldModules.MAX_DRAINED_BLOCKS);
        assertTrue(WorldModules.MAX_AREA_RADIUS > 0
                        && WorldModules.MAX_AREA_RADIUS <= 8,
                "потолок радиуса: " + WorldModules.MAX_AREA_RADIUS);
    }

    /**
     * Длина отрезка ограничена.
     *
     * <p>Две точки в трёхстах миллионах блоков давали {@code steps = 3·10⁸}, и
     * цикл крутился столько же раз на серверном потоке. Приведение к целому
     * не помогает: переполнение насыщает double до {@code Integer.MAX_VALUE}.
     */
    @Test
    void слишкомДлинныйОтрезокОтклоняется() {
        assertEquals(11, GeometryModules.blocksInLine(
                Vec3.ZERO, new Vec3(10, 0, 0)), "отрезок в 10 блоков — 11 позиций");
        assertThrows(SpellContext.SpellException.class,
                () -> GeometryModules.blocksInLine(Vec3.ZERO,
                        new Vec3(GeometryModules.MAX_LINE_BLOCKS + 1, 0, 0)));
        assertThrows(SpellContext.SpellException.class,
                () -> GeometryModules.blocksInLine(Vec3.ZERO, new Vec3(3e8, 0, 0)));
    }

    /**
     * Стековые модули складываются ровно так, как описаны.
     *
     * <p>Исполнитель клал выход КАЖДОГО модуля в стек и снимал по значению за
     * каждый вход, хотя входы приходят по соединениям. Из-за этого «Взять» не
     * уменьшал стек, а «Дублировать» добавлял сразу два значения. Здесь
     * проверяется поведение самих модулей — то, чем они обязаны быть по
     * описанию.
     */
    @Test
    void стековыеМодулиСчитаютсяКакОписано() {
        SpellContext ctx = new SpellContext(null, null, Vec3.ZERO);
        SpellValue five = SpellValue.of(5);

        push(ctx, five);
        assertEquals(1, ctx.stackSize(), "«Положить» кладёт ровно одно значение");

        SpellValue popped = pop(ctx);
        assertEquals(5, popped.asNumber());
        assertEquals(0, ctx.stackSize(), "«Взять» забирает значение со стека");

        push(ctx, five);
        dup(ctx);
        assertEquals(2, ctx.stackSize(), "«Дублировать» добавляет ровно одну копию");

        drop(ctx);
        assertEquals(1, ctx.stackSize(), "«Сбросить» убирает одно значение");

        clear(ctx);
        assertEquals(0, ctx.stackSize(), "«Очистить» обнуляет стек");
    }

    private static void push(SpellContext ctx, SpellValue v) {
        List<SpellValue> in = new java.util.ArrayList<>(java.util.List.of(v));
        run(ctx, "stack_push", in);
    }

    private static SpellValue pop(SpellContext ctx) {
        return run(ctx, "stack_pop", java.util.List.of()).get(0);
    }

    private static void dup(SpellContext ctx) {
        run(ctx, "stack_dup", java.util.List.of());
    }

    private static void drop(SpellContext ctx) {
        run(ctx, "stack_drop", java.util.List.of());
    }

    private static void clear(SpellContext ctx) {
        run(ctx, "stack_clear", java.util.List.of());
    }

    private static java.util.List<SpellValue> run(SpellContext ctx, String path,
                                                  java.util.List<SpellValue> in) {
        List<SpellValue> out = new ArrayList<>();
        SpellModules.get(SpellModules.id(path)).execute(ctx, in, out);
        return out;
    }
}