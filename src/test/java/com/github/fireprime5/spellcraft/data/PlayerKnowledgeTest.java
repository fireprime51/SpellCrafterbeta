package com.github.fireprime5.spellcraft.data;

import com.github.fireprime5.spellcraft.Spellcraft;
import com.github.fireprime5.spellcraft.spell.ScanRewards;
import com.github.fireprime5.spellcraft.spell.SpellGraph;
import com.github.fireprime5.spellcraft.spell.SpellModifiers;
import com.github.fireprime5.spellcraft.spell.SpellModules;
import com.github.fireprime5.spellcraft.spell.StartModules;
import com.github.fireprime5.spellcraft.spell.UniqueModules;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Система знаний: стартовые модули, награды за сканирование, модификации.
 */
class PlayerKnowledgeTest {

    @BeforeAll
    static void setUp() {
        SpellModules.init();
    }

    @Test
    void стартоваяЦепочкаЕсть() {
        // бесплатные модули: вся цепочка добычи энергии бездны и знаний
        for (String id : new String[]{"caster_entity", "entity_look", "raycast_entity",
                "raycast_block", "analysis_block", "analysis_mob", "extract_block",
                "extract_mob"}) {
            assertNotNull(SpellModules.get(SpellModules.id(id)), "нет стартового модуля " + id);
            assertTrue(StartModules.isStartModule(Spellcraft.id(id)), id + " должен быть стартовым");
            assertEquals(0, SpellModules.get(SpellModules.id(id)).manaCost(),
                    id + " должен быть бесплатным, иначе первую энергию бездны не добыть");
        }

    }

    @Test
    void примерыИзТвоегоЗапросаРаботают() {
        assertEquals("teleport", ScanRewards.rewardFor("minecraft:enderman"));
        assertEquals("explode", ScanRewards.rewardFor("minecraft:creeper"));
        assertEquals("explode", ScanRewards.rewardFor("minecraft:tnt"));
        assertEquals("break_block", ScanRewards.rewardFor("minecraft:obsidian"));
    }

    @Test
    void всеНаградыСуществуют() {
        int checked = 0;
        for (String target : ScanRewards.blocks().keySet()) {
            String reward = ScanRewards.rewardFor(target);
            assertNotNull(SpellModules.get(Spellcraft.id(reward)),
                    "блок " + target + " даёт несуществующий модуль " + reward);
            checked++;
        }
        for (String target : ScanRewards.mobs().keySet()) {
            String reward = ScanRewards.rewardFor(target);
            assertNotNull(SpellModules.get(Spellcraft.id(reward)),
                    "моб " + target + " даёт несуществующий модуль " + reward);
            checked++;
        }
        assertTrue(checked >= 80, "наград должно быть много, а не " + checked);
    }

    @Test
    void неизвестнаяЦельНичегоНеДаёт() {
        assertNull(ScanRewards.rewardFor("minecraft:bedrock"));
    }

    @Test
    void модификаторыДобычиКонфликтуютПоГруппам() {
        assertTrue(SpellModifiers.SILK.conflictsWith(SpellModifiers.FORTUNE),
                "шёлк и удача конфликтуют");
        assertTrue(SpellModifiers.AREA.conflictsWith(SpellModifiers.INSTANT),
                "по области и мгновенная добыча конфликтуют");
        assertFalse(SpellModifiers.SILK.conflictsWith(SpellModifiers.AREA),
                "шёлк + по области можно вместе");
        assertFalse(SpellModifiers.FORTUNE.conflictsWith(SpellModifiers.INSTANT),
                "удача + мгновенная добыча можно вместе");
    }

    @Test
    void модульДобычиИмеетВсеМодификаторы() {
        var mining = SpellModules.get(SpellModules.id("break_block"));
        assertEquals(4, mining.modifiers().size(), "у добычи 4 варианта-галочки");
        for (var modifier : mining.modifiers()) {
            assertTrue(mining.modifiers().contains(modifier));
        }
    }

    @Test
    void уВзрываЕстьГалочки() {
        var explode = SpellModules.get(SpellModules.id("explode"));
        assertEquals(5, explode.modifiers().size(), "у взрыва 5 вариантов-галочек");
        assertTrue(explode.modifiers().contains(SpellModifiers.NO_BLOCKS));
        assertTrue(explode.modifiers().contains(SpellModifiers.POWERFUL));
        // отдельного модуля "взрыв без блоков" больше нет
        org.junit.jupiter.api.Assertions.assertNull(
                SpellModules.get(SpellModules.id("explosion_no_break")));
    }

    @Test
    void конфликтыВзрываПоГруппам() {
        assertTrue(SpellModifiers.NO_BLOCKS.conflictsWith(SpellModifiers.WIDE));
        assertTrue(SpellModifiers.FIRE.conflictsWith(SpellModifiers.SHOVE));
        assertFalse(SpellModifiers.NO_BLOCKS.conflictsWith(SpellModifiers.FIRE),
                "не ломать блоки + поджиг можно вместе");
        assertFalse(SpellModifiers.POWERFUL.conflictsWith(SpellModifiers.WIDE),
                "усиленный не конфликтует ни с чем");
    }

    @Test
    void стоимостьМодификаторовДобавляется() {
        SpellGraph g = new SpellGraph();
        var node = g.addNode(SpellModules.id("break_block"), 0, 0);
        int base = g.totalAbyssCost();
        node.modifiers.add(SpellModifiers.FORTUNE.id());
        node.modifiers.add(SpellModifiers.AREA.id());
        assertEquals(base + SpellModifiers.FORTUNE.castCost() + SpellModifiers.AREA.castCost(),
                g.totalAbyssCost());
    }

    @Test
    void конфликтМодификаторовОшибка() {
        SpellModules.init();
        SpellGraph g = new SpellGraph();
        var node = g.addNode(SpellModules.id("break_block"), 0, 0);
        var constOne = g.addNode(SpellModules.id("const_one"), 1, 0);
        constOne.toString();
        g.connect(1, 0, 0, 0);
        node.modifiers.add(SpellModifiers.SILK.id());
        node.modifiers.add(SpellModifiers.FORTUNE.id());
        assertTrue(g.validate().stream()
                        .anyMatch(i -> i.key().equals("graph.modifier_conflict")),
                "шёлк + удача должны конфликтовать: " + g.validate());
    }

    /**
     * Идентификаторы модулей хранятся только с пространством имён.
     *
     * <p>«Анализ себя» когда-то записывал личный модуль голым путём
     * ({@code "unique_resonance"}). Клиент разбирал такое через
     * {@code ResourceLocation.tryParse} как {@code minecraft:unique_resonance}, а
     * палитра искала {@code spellcraft:unique_resonance} — открытый модуль не
     * появлялся нигде. Проверка ловит именно эту форму записи.
     */
    @Test
    void модульБезПространстваИмёнДополняетсяДоНашего() {
        var knowledge = new PlayerKnowledge.Knowledge(
                Set.of("unique_resonance", "analysis_self"),
                Set.of(), List.of(), Set.of());
        assertTrue(knowledge.modules().contains("spellcraft:unique_resonance"),
                "голый путь не дополнен до spellcraft: — " + knowledge.modules());
        assertTrue(knowledge.modules().contains("spellcraft:analysis_self"),
                "голый путь не дополнен до spellcraft: — " + knowledge.modules());
    }

    /** Чужое пространство имён трогать нельзя. */
    @Test
    void чужиеИдентификаторыНеПереписываются() {
        var knowledge = new PlayerKnowledge.Knowledge(
                Set.of("minecraft:break_block", "spellcraft:extract_mob"),
                Set.of(), List.of(), Set.of());
        assertTrue(knowledge.modules().contains("minecraft:break_block"),
                "чужое пространство имён переписано — " + knowledge.modules());
        assertTrue(knowledge.modules().contains("spellcraft:extract_mob"));
        assertEquals(2, knowledge.modules().size());
    }

    /**
     * В {@code scanned} лежат id сущностей без пространства имён («zombie»),
     * и дописывать им наше было бы ошибкой.
     */
    @Test
    void просканированныеСущностиНеНормализуются() {
        var knowledge = new PlayerKnowledge.Knowledge(
                Set.of(), Set.of("zombie", "minecraft:creeper"), List.of(), Set.of());
        assertTrue(knowledge.scanned().contains("zombie"),
                "id сущности нельзя менять — " + knowledge.scanned());
        assertTrue(knowledge.scanned().contains("minecraft:creeper"));
    }

    /**
     * Открытый личный модуль обязан быть в списке изученных под полным id.
     *
     * <p>Именно этот список уезжает на клиент, и палитра ищет в нём
     * {@code spellcraft:unique_resonance}.
     */
    @Test
    void открытыйЛичныйМодульВиденПодПолнымИдентификатором() {
        var knowledge = new PlayerKnowledge.Knowledge(
                Set.of(UniqueModules.RESONANCE), Set.of(), List.of(), Set.of(),
                Map.of(), Set.of(), UniqueModules.RESONANCE, 0);
        assertTrue(knowledge.modules().contains("spellcraft:" + UniqueModules.RESONANCE),
                "личный модуль записан не так, как его ищет палитра — " + knowledge.modules());
    }

    @Test
    void знанияКодируются() {
        var knowledge = new PlayerKnowledge.Knowledge(
                Set.of("minecraft:break_block", "minecraft:explode"),
                Set.of("minecraft:obsidian"),
                List.of("minecraft:break_block"),
                Set.of("fortune"));
        var tag = PlayerKnowledge.Knowledge.CODEC
                .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, knowledge).getOrThrow();
        var back = PlayerKnowledge.Knowledge.CODEC
                .parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).getOrThrow();

        assertTrue(back.modules().contains("minecraft:break_block"));
        assertTrue(back.scanned().contains("minecraft:obsidian"));
        assertTrue(back.modifiers().contains("fortune"), "модификаторы тоже сохраняются");
        assertEquals(1, back.log().size());
    }

    @Test
    void модулиВнеСтартовыхИЗначальноЗакрыты() {
        // isStartModule проверяет только стартовый набор
        assertFalse(StartModules.isStartModule(Spellcraft.id("explode")));
        assertFalse(StartModules.isStartModule(Spellcraft.id("add")));
        assertTrue(StartModules.isStartModule(Spellcraft.id("extract_block")));
        // цепочка добычи первой энергии бездны (8) плюс два модуля адреса, из которых
        // собирается первый портал, «здесь» и пять констант:
        // константы — подножие, а не умение, и «здесь» без него не собрать
        assertEquals(17, StartModules.all().size(),
                "стартовых модулей должно быть 16");
        for (String path : List.of("caster_entity", "entity_look", "raycast_entity",
                "raycast_block", "analysis_block", "analysis_mob",
                "extract_block", "extract_mob", "self_position",
                "const_zero", "const_one", "const_five",
                "const_ten", "const_neg_one")) {
            assertTrue(StartModules.isStartModule(Spellcraft.id(path)),
                    "стартовый модуль не открыт: " + path);
        }
    }

    /**
     * Держатели вложений нельзя трогать через {@code get()} при создании мода:
     * реестра {@code neoforge:attachment_types} на тот момент ещё нет, и игра
     * падает с «Registry not present for DeferredHolder».
     */
    @Test
    void вложенияРегистрируютсяЧерезРеестр() throws java.io.IOException {
        String mod = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/com/github/fireprime5/spellcraft/"
                        + "SpellcraftMod.java"));
        for (String registry : List.of("PlayerSpellStore.ATTACHMENTS",
                "SpellLibrary.ATTACHMENTS", "PlayerKnowledge.Attachments.REGISTRY")) {
            assertTrue(mod.contains(registry + ".register(modBus)"),
                    "реестр " + registry + " обязан регистрироваться на modBus");
        }
        assertFalse(mod.contains("holder()"),
                "holder() вызывал .get() до открытия реестра и ронял запуск");

        String store = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/github/fireprime5/spellcraft/data/PlayerSpellStore.java"));
        assertTrue(store.contains("DeferredRegister.create(net.neoforged.neoforge.registries"
                + ".NeoForgeRegistries\n                    .ATTACHMENT_TYPES")
                        || store.contains("ATTACHMENT_TYPES"),
                "своё вложение держит собственный реестр");
    }

    @Test
    void таблицаНаградНеПустая() {
        assertTrue(ScanRewards.size() >= 60,
                "наград должно быть достаточно, а не " + ScanRewards.size());
        Map<String, String> blocks = ScanRewards.blocks();
        assertTrue(blocks.containsKey("minecraft:diamond_ore"));
        // древний мусор теперь учит модификатор мгновенной добычи
        assertEquals("instant", ScanRewards.modifierFor("minecraft:ancient_debris"));
    }
}
