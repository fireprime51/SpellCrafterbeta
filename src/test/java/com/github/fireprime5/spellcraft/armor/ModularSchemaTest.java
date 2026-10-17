package com.github.fireprime5.spellcraft.armor;

import com.github.fireprime5.spellcraft.spell.SpellGraph;
import com.github.fireprime5.spellcraft.spell.SpellModules;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Схема модулей брони.
 *
 * <p>Схема едет в предмете, и это главный её смысл: снял, передал, положил в
 * сундук — модули остались с бронёй. Плюс она приходит с клиента, поэтому
 * всё, что в неё пришло, проверяется на сервере ещё раз.
 */
class ModularSchemaTest {

    @BeforeAll
    static void setUp() {
        SpellModules.init();
    }

    private static SpellGraph graphWith(String... modules) {
        SpellGraph graph = new SpellGraph();
        for (String module : modules) {
            graph.addNode(SpellModules.id(module), 0, 0);
        }
        return graph;
    }

    /** Чужой модуль в схему не попадает: клиент мог прислать что угодно. */
    @Test
    void запрещённыйМодульОтбрасывается() {
        var schema = ModularSchema.with(graphWith("effect_glowing"));
        assertTrue(schema.isEmpty(),
                "модуль эффекта не должен попасть в схему брони");
    }

    /**
 * Схема обязана быть полноценным значением, а не объектом по адресу.
 *
 * <p>Она лежит в компоненте данных предмета, и Minecraft требует от значения
 * компонента {@code equals} и {@code hashCode}. Без них любое сохранение падает
 * с «Data components must implement equals and hashCode», и модули в нагруднике
 * не пишутся вообще — при этом все проверки схемы проходят, потому что они
 * работают с ней напрямую и до компонента не добираются. Именно так это и
 * вышло: тесты были зелёные, а нагрудник в игре не сохранял ничего.
 */
    @Test
    void схемаЭтоЗначениеАНеОбъектПоАдресу() {
        var first = ModularSchema.with(graphWith("trigger_on_hit", "modifier_speed"));
        var second = ModularSchema.with(graphWith("trigger_on_hit", "modifier_speed"));
        var other = ModularSchema.with(graphWith("trigger_on_hit"));

        assertEquals(first, second, "одинаковые схемы должны быть равны");
        assertEquals(first.hashCode(), second.hashCode(),
                "у равных схем должен совпадать хэш, иначе они разойдутся по хеш-таблице");
        assertFalse(first.equals(other), "схемы с разным содержимым не должны быть равны");

        // equals обязан быть настоящим, а не унаследованным от Object:
        // иначе hashCode у всех схем был бы адресом и проверка выше прошла бы
        // только потому, что адреса совпали
        assertNotEquals(System.identityHashCode(first), first.hashCode(),
                "hashCode не переопределён — это адрес объекта");
    }

    /** Хэш обязан пережить сохранение и загрузку: тот же NBT — тот же хэш. */
    @Test
    void хэшПереживаетСохранение() {
        var schema = ModularSchema.with(graphWith("creative_flight", "modifier_damage"));
        var loaded = ModularSchema.load(schema.save());
        assertEquals(schema, loaded, "загруженная схема должна равняться сохранённой");
        assertEquals(schema.hashCode(), loaded.hashCode());
    }

    /** Разное содержимое — разный хэш, иначе компоненты склеились бы в предмет. */
    @Test
    void разноеСодержимоеДаётРазныйХэш() {
        assertNotEquals(ModularSchema.with(graphWith("trigger_on_hit")).hashCode(),
                ModularSchema.with(graphWith("trigger_on_hurt")).hashCode());
    }

    /**
     * «Вечность» не облагается постоянным налогом.
     *
     * <p>Налог платит за пассивное действие, которого у неё нет: она отменяет урон
     * и берёт по одной энергии за удар. С облаганием бездна таяла бы сама по себе,
     * и игрок платил бы дважды за одно и то же.
     */
    @Test
    void вечностьНеОблагаетсяНалогом() {
        assertEquals(0.0, ModularSchema.with(graphWith("unique_eternity"))
                .armorUpkeepPerSecond(), 1e-9);
        assertEquals(0, ModularSchema.with(graphWith("unique_eternity")).abyssDemand(),
                "схема из одной «Вечности» не должна требовать бездны постоянно");

        // в смешанной схеме налог считается только с остальных модулей
        assertEquals(1.0, ModularSchema.with(
                graphWith("unique_eternity", "modifier_speed", "modifier_damage"))
                .armorUpkeepPerSecond(), 1e-9);
    }

    /** Обычные модули брони по-прежнему облагаются. */
    @Test
    void обычныеМодулиОблагаютсяКакПрежде() {
        // пол-единицы в секунду на модуль: ровно одна энергия за налоговый период
        assertEquals(1.0, ModularSchema.with(graphWith("trigger_on_hit", "modifier_speed"))
                .armorUpkeepPerSecond(), 1e-9);
        assertEquals(1.5, ModularSchema.with(graphWith("creative_flight", "modifier_speed",
                "modifier_damage")).armorUpkeepPerSecond(), 1e-9);
    }

    /**
     * Барьер в броне стоит тридцать энергий бездны в секунду.
     *
     * <p>Ровно то, что просил игрок. Проверка на точное число, а не «дороже
     * полёта»: налог считается по формуле «в секунду × длину периода», и любой
     * сдвиг константы тихо поменял бы цену круговой обороны.
     */
    @Test
    void барьерСтоитТридцатьВСекунду() {
        double расход = ModularSchema.with(graphWith("barrier")).armorUpkeepPerSecond();
        assertEquals(30.0, расход, 1e-9, "барьер обязан стоить 30 энергий в секунду");
        // налоговый период — 40 тиков, то есть две секунды: 30 × 2 = 60 за цикл
        assertEquals(60, (int) Math.ceil(расход * (40 / 20.0)),
                "за налоговый период с барьером должно уходить 60");
        // и в шестьдесят раз дороже одного полёта, а не «во столько-то модулей»
        assertEquals(60.0, расход
                / ModularSchema.with(graphWith("creative_flight")).armorUpkeepPerSecond(),
                1e-9);
    }

    /** Разрешённые модули сохраняются. */
    @Test
    void разрешённыеМодулиСохраняются() {
        var schema = ModularSchema.with(graphWith("trigger_on_hit", "modifier_speed"));
        assertEquals(2, schema.size());
        assertTrue(schema.has("trigger_on_hit"));
        assertTrue(schema.has("creative_flight") == false);
    }

    /** Схема переживает сохранение и загрузку: она лежит в NBT предмета. */
    @Test
    void схемаПереживаетСохранение() {
        var schema = ModularSchema.with(graphWith("trigger_on_hit", "modifier_damage"));
        ModularSchema loaded = ModularSchema.load(schema.save());
        assertEquals(2, loaded.size());
        assertTrue(loaded.sameAs(schema));
    }

    /** Серверный save-путь не должен терять схему при повторном чтении компонента. */
    @Test
    void серверныйSaveПутьСохраняетСхемуВКомпоненте() {
        var submitted = graphWith("trigger_on_hit", "modifier_speed");
        var stored = ModularSchema.with(submitted.copy());

        // Это ровно преобразования, используемые DataComponentType codec:
        // graph -> CompoundTag -> graph -> ModularSchema.
        var componentTag = stored.save();
        var afterItemRoundTrip = ModularSchema.load(componentTag);

        assertTrue(afterItemRoundTrip.has("trigger_on_hit"));
        assertTrue(afterItemRoundTrip.has("modifier_speed"));
        assertTrue(afterItemRoundTrip.sameAs(stored));
    }

    /** Битый NBT не должен ронять игру при загрузке мира. */
    @Test
    void битоеСохранениеНеЛомает() {
        assertTrue(ModularSchema.load(null).isEmpty());
        assertTrue(ModularSchema.load(new CompoundTag()).isEmpty());
        // граф без узлов — тоже пустая схема, а не исключение
        assertTrue(ModularSchema.load(new SpellGraph().save()).isEmpty());

        // неизвестные модули выбрасываются, а не ломают загрузку
        SpellGraph broken = new SpellGraph();
        broken.addNode(SpellModules.id("mod_from_other_version"), 0, 0);
        assertTrue(ModularSchema.load(broken.save()).isEmpty());
    }

    /**
     * В нагрудник ставится барьер, но не щит.
     *
     * <p>Разница не в цене, а в смысле: щит — это «я сейчас поднял руку перед
     * собой», постоянная стойка из брони в него не превращается. Барьер же
     * защищает со всех сторон, и надетым он держится ровно пока работает броня.
     */
    @Test
    void вНагрудникИдётБарьерАНеЩит() {
        assertTrue(ModularSchema.allows("barrier"),
                "барьер должен ставиться в нагрудник, иначе в броню его не поставить");
        assertFalse(ModularSchema.allows("shield"),
                "щит в нагрудник не ставится: это разовое действие, а не стойка");
    }

    /** Лимит модулей на броне ограничен. */
    @Test
    void броняОграничена() {
        SpellGraph graph = new SpellGraph();
        for (int i = 0; i < ModularSchema.MAX_NODES + 5; i++) {
            graph.addNode(SpellModules.id("modifier_speed"), i, 0);
        }
        assertTrue(ModularSchema.with(graph).isEmpty(),
                "схема больше лимита должна отбрасываться целиком");
    }

    /** Модуль триггера сам по себе ничего не делает — он разрешает схему. */
    @Test
    void безымянныйМодульНеЛомаетСхему() {
        var schema = ModularSchema.with(graphWith("creative_flight"));
        assertTrue(schema.has("creative_flight"));
        assertFalse(schema.has("trigger_on_hit"));
    }

    /** Пустая схема не должна требовать бездны. */
    @Test
    void пустаяСхемаБесплатна() {
        assertEquals(0, ModularSchema.EMPTY.abyssDemand());
        assertEquals(ModularSchema.EMPTY.size(), 0);
    }
}
