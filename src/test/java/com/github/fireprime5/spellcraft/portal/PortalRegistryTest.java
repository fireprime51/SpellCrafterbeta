package com.github.fireprime5.spellcraft.portal;

import com.github.fireprime5.spellcraft.spell.PortalModules;
import com.github.fireprime5.spellcraft.spell.ScanRewards;
import com.github.fireprime5.spellcraft.spell.SpellModuleType;
import com.github.fireprime5.spellcraft.spell.SpellModules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Система порталов: реестр, срок жизни и правила модулей.
 *
 * <p>Проверяется то, что не требует мира: реестр целиком работает на NBT и
 * чистой геометрии, а модули проверяются как объявления. Сам переход и блок
 * в мире живут без тестов — для них нужен сервер.
 */
class PortalRegistryTest {

    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    @BeforeAll
    static void setUp() {
        SpellModules.init();
    }

    private static PortalRegistry registryWith(PortalRegistry.Entry... entries) {
        PortalRegistry registry = new PortalRegistry();
        for (PortalRegistry.Entry entry : entries) {
            registry.add(entry);
        }
        return registry;
    }

    private static PortalRegistry.Entry entry(String fromDim, BlockPos from, String toDim,
                                              BlockPos to, long expires, UUID owner) {
        return entry(fromDim, from, toDim, to, true, expires, owner);
    }

    private static PortalRegistry.Entry entry(String fromDim, BlockPos from, String toDim,
                                              BlockPos to, boolean twoWay, long expires,
                                              UUID owner) {
        return new PortalRegistry.Entry(UUID.randomUUID(), fromDim, from, toDim, to,
                twoWay, expires, owner);
    }

    // ------------------------------------------------------- реестр

    @Test
    void порталИщетсяПоОбоимКонцам() {
        BlockPos a = new BlockPos(10, 64, 10);
        BlockPos b = new BlockPos(-100, 70, 250);
        var e = entry(OVERWORLD, a, NETHER, b, 0, UUID.randomUUID());
        PortalRegistry registry = registryWith(e);

        assertEquals(e.id(), registry.at(OVERWORLD, a).id(), "вход");
        assertEquals(e.id(), registry.at(NETHER, b).id(), "выход");
        assertNull(registry.at(OVERWORLD, new BlockPos(11, 64, 10)), "чужая точка");
    }

    /** Односторонний портал не должен находиться по своему выходу. */
    @Test
    void одностороннийНеИщетсяПоВыходу() {
        BlockPos a = new BlockPos(1, 64, 1);
        BlockPos b = new BlockPos(2, 64, 2);
        var e = entry(OVERWORLD, a, NETHER, b, false, 0, UUID.randomUUID());
        PortalRegistry registry = registryWith(e);

        assertEquals(e.id(), registry.at(OVERWORLD, a).id());
        assertNull(registry.at(NETHER, b),
                "у одностороннего портала блока на выходе нет, и искать там нечего");
    }

    @Test
    void удалениеЧиститОбаКонца() {
        BlockPos a = new BlockPos(1, 64, 1);
        BlockPos b = new BlockPos(2, 64, 2);
        var e = entry(OVERWORLD, a, NETHER, b, 0, UUID.randomUUID());
        PortalRegistry registry = registryWith(e);

        assertNotNull(registry.remove(e.id()));
        assertNull(registry.at(OVERWORLD, a), "остался хвост входа");
        assertNull(registry.at(NETHER, b), "остался хвост выхода");
        assertEquals(0, registry.size());
    }

    /** Сохранение и чтение обязаны быть симметричны: иначе портал теряется. */
    @Test
    void сохранениеНеТеряетДанные() {
        BlockPos a = new BlockPos(15, -60, -15);
        BlockPos b = new BlockPos(300, 70, 120);
        UUID owner = UUID.randomUUID();
        var e = entry(OVERWORLD, a, NETHER, b, 123456789L, owner);
        var oneWay = entry(OVERWORLD, new BlockPos(0, 64, 0), NETHER,
                new BlockPos(1, 64, 1), false, 0, owner);

        var loaded = PortalRegistry.load(
                registryWith(e, oneWay).save(new CompoundTag(), null), null);
        assertEquals(2, loaded.size());

        var restored = loaded.get(e.id());
        assertNotNull(restored);
        assertEquals(NETHER, restored.toDim());
        assertEquals(b, restored.to());
        assertEquals(123456789L, restored.expiresAt());
        assertEquals(owner, restored.owner());
        assertEquals(NETHER, restored.exitDim(OVERWORLD, a), "вход ведёт на выход");
        assertEquals(b, restored.exitPos(OVERWORLD, a));
        assertEquals(OVERWORLD, restored.exitDim(NETHER, b), "выход ведёт на вход");
        assertEquals(a, restored.exitPos(NETHER, b));
        // адрес назначения есть даже у одностороннего, блока на выходе — нет
        var restoredOneWay = loaded.get(oneWay.id());
        assertNotNull(restoredOneWay);
        assertFalse(restoredOneWay.hasReturn(), "односторонний не должен иметь возврата");
        assertEquals(new BlockPos(1, 64, 1), restoredOneWay.exitPos(OVERWORLD, new BlockPos(0, 64, 0)),
                "куда ведёт — знать надо даже без обратного хода");
    }

    /**
     * Врата внутри одного измерения: концы различаются ТОЛЬКО по точке.
     *
     * <p>Это ровно то, что ломалось в игре: «Здесь» и «Точка возрождения» оба
     * в обычном мире, и при выборе конца по измерению обе ветви совпадали —
     * портал на спавне отправлял обратно на спавн.
     */
    @Test
    void вратаВОдномИзмеренииВозвращаютНаДругойКонец() {
        BlockPos entry = new BlockPos(10, 64, 10);
        BlockPos exit = new BlockPos(-300, 71, 900);
        var e = entry(OVERWORLD, entry, OVERWORLD, exit, 0, UUID.randomUUID());
        PortalRegistry registry = registryWith(e);

        // смотрим в портал на спавне
        assertNotNull(registry.at(OVERWORLD, exit), "портал на спавне не найден");
        assertEquals(entry, e.exitPos(OVERWORLD, exit),
                "со спавна надо вернуться к входу, а не остаться на месте");
        assertEquals(OVERWORLD, e.exitDim(OVERWORLD, exit));

        // и наоборот
        assertEquals(exit, e.exitPos(OVERWORLD, entry),
                "со входа надо попасть на выход");
    }

    /** Односторонний портал всегда ведёт в одну сторону, откуда бы ни вошли. */
    @Test
    void одностороннийВсегдаВедётКВыходу() {
        BlockPos entry = new BlockPos(0, 64, 0);
        BlockPos exit = new BlockPos(999, 64, 0);
        var e = entry(OVERWORLD, entry, OVERWORLD, exit, false, 0, UUID.randomUUID());
        assertEquals(exit, e.exitPos(OVERWORLD, entry));
        // на выходе стоять негде: блока там нет, это и есть односторонность
        assertNull(registryWith(e).at(OVERWORLD, exit),
                "односторонний портал не ставит блок на выходе");
    }

    /**
     * Врата между мирами возвращают обратно, а не в тот же мир.
     *
     * <p>Отдельный случай от «враг в одном измерении»: тут измерения разные, и
     * ошибка была бы обратной — с выхода в Незере игрока уносило бы в Незер же.
     */
    @Test
    void вратаМеждуМирамиВозвращаютВДругой() {
        BlockPos entry = new BlockPos(0, 64, 0);
        BlockPos exit = new BlockPos(100, 70, -50);
        var e = entry(OVERWORLD, entry, NETHER, exit, 0, UUID.randomUUID());

        assertEquals(NETHER, e.exitDim(OVERWORLD, entry), "из мира — в Незер");
        assertEquals(exit, e.exitPos(OVERWORLD, entry));
        assertEquals(OVERWORLD, e.exitDim(NETHER, exit), "из Незера — обратно в мир");
        assertEquals(entry, e.exitPos(NETHER, exit));
    }

    // ---------------------------------------------------- срок жизни

    @Test
    void вечныйПорталНеИстекает() {
        var e = entry(OVERWORLD, new BlockPos(0, 64, 0), NETHER, new BlockPos(1, 64, 1),
                0, UUID.randomUUID());
        assertTrue(e.permanent());
        assertFalse(e.expired(Long.MAX_VALUE));
        assertEquals(0, e.remainingTicks(Long.MAX_VALUE));
    }

    @Test
    void порталИстекаетВСрок() {
        var e = entry(OVERWORLD, new BlockPos(0, 64, 0), NETHER, new BlockPos(1, 64, 1),
                1000L, UUID.randomUUID());
        assertFalse(e.expired(999), "до срока ещё жив");
        assertTrue(e.expired(1000), "на сроке уже мёртв");
        assertTrue(e.expired(1001));
        // портал исчезает В момент expiresAt, поэтому последний живой тик —
        // предыдущий: на 1000-м остатка уже ноль
        assertEquals(1, e.remainingTicks(999));
        assertEquals(0, e.remainingTicks(1000));
        assertEquals(0, e.remainingTicks(2000));
    }

    @Test
    void истёкшиеЧистятсяУборщиком() {
        BlockPos a = new BlockPos(0, 64, 0);
        UUID owner = UUID.randomUUID();
        var expired = entry(OVERWORLD, a, NETHER, new BlockPos(1, 64, 1), 500L, owner);
        var alive = entry(OVERWORLD, new BlockPos(50, 64, 50), NETHER,
                new BlockPos(51, 64, 51), 5000L, owner);
        PortalRegistry registry = registryWith(expired, alive);

        long now = 600;
        var due = registry.all().stream().filter(e -> e.expired(now)).toList();
        assertEquals(1, due.size());
        assertEquals(expired.id(), due.get(0).id());

        registry.remove(expired.id());
        assertEquals(1, registry.size(), "живой портал должен уцелеть");
        assertNotNull(registry.get(alive.id()));
    }

    @Test
    void порталыСчитаютсяПоВладельцу() {
        UUID mine = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        PortalRegistry registry = registryWith(
                entry(OVERWORLD, new BlockPos(0, 64, 0), NETHER, new BlockPos(1, 64, 1), 0, mine),
                entry(OVERWORLD, new BlockPos(2, 64, 0), NETHER, new BlockPos(3, 64, 1), 0, mine),
                entry(OVERWORLD, new BlockPos(4, 64, 0), NETHER, new BlockPos(5, 64, 1), 0, other));
        assertEquals(2, registry.countOwnedBy(mine));
        assertEquals(1, registry.countOwnedBy(other));
    }

    // --------------------------------------------------------- модули

    @Test
    void модулиПорталовНаМесте() {
        for (String path : new String[]{"portal_gate", "portal_passage", "location_here",
                "location_overworld", "location_nether", "location_end"}) {
            assertNotNull(SpellModules.get(SpellModules.id(path)), "нет модуля " + path);
        }
    }

    @Test
    void порталыПринимаютАдреса() {
        SpellModuleType gate = SpellModules.get(SpellModules.id("portal_gate"));
        assertEquals(PortTypeAlias.LOCATION, gate.inputType(0), "вход");
        assertEquals(PortTypeAlias.LOCATION, gate.inputType(1), "выход");
        assertEquals(PortTypeAlias.NUMBER, gate.inputType(2), "время");
        assertEquals(1, gate.optionalInputs(),
                "время необязательное: без него портал вечный");
    }

    @Test
    void адресаОтдаютМесто() {
        assertEquals(PortTypeAlias.LOCATION,
                SpellModules.get(SpellModules.id("location_here")).outputType(0));
        for (PortalModules.Dimension dim : PortalModules.Dimension.values()) {
            SpellModuleType type = SpellModules.get(SpellModules.id("location_" + dim.key()));
            assertNotNull(type, "нет модуля адреса " + dim.key());
            assertEquals(PortTypeAlias.POINT, type.inputType(0),
                    "на вход идёт обычная точка, измерение задаёт сам модуль");
            assertEquals(PortTypeAlias.LOCATION, type.outputType(0));
        }
    }

    /** Стартовые адреса нужны, иначе первый портал собрать нечем. */
    @Test
    void адресаОбычногоМираСтартовые() {
        assertTrue(StartModulesAlias.isStart("location_here"));
        assertTrue(StartModulesAlias.isStart("location_overworld"));
        assertFalse(StartModulesAlias.isStart("location_nether"),
                "Незер открывается посещением, а не сразу");
        assertFalse(StartModulesAlias.isStart("location_end"));
    }

    /** Вечный портал дороже временного, иначе срок жизни ничего не значит. */
    @Test
    void вечныйДорожеВременного() {
        assertTrue(PortalModules.PERMANENT_MULTIPLIER > 1,
                "иначе можно всегда ставить вечные: " + PortalModules.PERMANENT_MULTIPLIER);
        assertEquals(PortalModules.GATE_TIMED_COST * PortalModules.PERMANENT_MULTIPLIER,
                PortalModules.GATE_TIMED_COST * 3);
        assertTrue(PortalModules.GATE_TIMED_COST > PortalModules.PASSAGE_TIMED_COST,
                "односторонний дешевле двустороннего");
    }

    /** Ключи измерений должны совпадать с ванильными, иначе портал не найдёт мир. */
    @Test
    void ключиИзмеренийВалидны() {
        for (PortalModules.Dimension dim : PortalModules.Dimension.values()) {
            ResourceLocation id = ResourceLocation.tryParse(dim.id());
            assertNotNull(id, "ключ измерения не разбирается: " + dim.id());
            var key = ResourceKey.create(Registries.DIMENSION, id);
            assertEquals(id, key.location());
        }
    }

    /**
     * Как добываются модули порталов.
     *
     * <p>Сканированием ВАНИЛЬНЫХ порталов, а не наших собственных: модуль
     * нужен, чтобы поставить портал, поэтому «просканируй свой» было бы
     * замкнутым кругом и модуль остался бы недостижимым навсегда.
     */
    @Test
    void порталыОткрываютсяСканированием() {
        assertEquals("portal_passage", ScanRewards.rewardFor("minecraft:nether_portal"),
                "ванильный портал Незера открывает односторонний «Проход»");
        assertEquals("portal_gate", ScanRewards.rewardFor("minecraft:end_portal"),
                "ванильный портал Энда открывает двусторонние «Врата»");
    }

    /** Порядок по сложности: Незер проходим раньше Энда, и модуль тоже. */
    @Test
    void порядокПолученияСоответствуетСложности() {
        String nether = ScanRewards.rewardFor("minecraft:nether_portal");
        String end = ScanRewards.rewardFor("minecraft:end_portal");
        assertTrue(PortalModules.PASSAGE_TIMED_COST < PortalModules.GATE_TIMED_COST,
                "односторонний портал должен быть и проще, и дешевле двустороннего");
        assertNotNull(nether);
        assertNotNull(end);
    }

    /**
     * Свой блок портала не должен быть целью сканирования.
     *
     * <p>Если бы он был, игрок получил бы модуль за сканирование того, что сам
     * и поставил, — но поставить он мог бы только уже имея модуль.
     */
    @Test
    void собственныйБлокПорталаНеЯвляетсяНаградой() {
        assertFalse(ScanRewards.blocks().containsKey("minecraft:spellcraft_portal"),
                "награда за свой собственный блок портала сделала бы добычу "
                        + "модуля замкнутым кругом");
    }

    // ---------------------------------------------------------- ссылки

    /** Короткие ссылки, чтобы тест не тащил с собой четыре импорта. */
    private static final class PortTypeAlias {
        static final com.github.fireprime5.spellcraft.spell.PortType LOCATION =
                com.github.fireprime5.spellcraft.spell.PortType.LOCATION;
        static final com.github.fireprime5.spellcraft.spell.PortType POINT =
                com.github.fireprime5.spellcraft.spell.PortType.POINT;
        static final com.github.fireprime5.spellcraft.spell.PortType NUMBER =
                com.github.fireprime5.spellcraft.spell.PortType.NUMBER;
    }

    private static final class StartModulesAlias {
        static boolean isStart(String path) {
            return com.github.fireprime5.spellcraft.spell.StartModules
                    .isStartModule(com.github.fireprime5.spellcraft.Spellcraft.id(path));
        }
    }
}