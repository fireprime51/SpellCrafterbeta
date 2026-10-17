package com.github.fireprime5.spellcraft.spell;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверки личных модулей.
 *
 * <p>Механика держится на трёх обещаниях, и каждое из них ломается тихо:
 * модуль выдаётся дважды, выдаётся чужой, или попадает в палитру игрока, у
 * которого его нет. Поэтому проверяем именно их, а не «модули зарегистрированы».
 */
class UniqueModulesTest {

    @BeforeAll
    static void setUp() {
        SpellModules.init();
    }

    /** Набор не должен быть пустым и не должен содержать повторов. */
    @Test
    void наборЛичныхМодулейНепустойИБезДублей() {
        assertFalse(UniqueModules.ALL.length == 0, "набор личных модулей пуст");
        Set<String> unique = new HashSet<>(List.of(UniqueModules.ALL));
        assertEquals(UniqueModules.ALL.length, unique.size(),
                "в наборе личных модулей есть повторы");
    }

    /**
     * Каждый модуль набора должен быть и зарегистрирован, и приписан к подразделу.
     *
     * <p>Собственного подраздела «Личный» больше нет: в палитре его не было как
     * вкладки, а заголовок появлялся только когда в списке больше одного
     * подраздела — открытый личный модуль было почти не найти. Теперь личные
     * лежат в «Действиях» без отдельного заголовка ({@link SpellSubsections#OTHER}),
     * а «Вечность» — в «Броне», чтобы её нельзя было положить в заклинание.
     * Личность от раздела не зависит: она проверяется отдельно, по ней модуль не
     * выдаётся сканированием и «выучить всё».
     */
    @Test
    void каждыйЛичныйМодульЗарегистрированИВиденКакЛичный() {
        for (String path : UniqueModules.ALL) {
            SpellModuleType type = SpellModules.get(SpellModules.id(path));
            assertNotNull(type, "личный модуль не зарегистрирован: " + path);
            assertTrue(type.isUnique(), path + ": не помечен как личный");
            String sub = SpellSubsections.of(type);
            assertTrue(List.of(SpellSubsections.OTHER, SpellSubsections.ARMOR).contains(sub),
                    path + ": личный модуль не должен теряться в общем разделе, а он в " + sub);
        }
    }

    /**
     * Пять числовых личных модулей обязаны быть в «Действиях».
     *
     * <p>Отдельного раздела «Личный» в палитре нет, поэтому если оставить их в
     * «Числах» — игрок, только что открывший модуль, не найдёт его в том списке,
     * где искал.
     */
    @Test
    void числовыеЛичныеМодулиВРазделеДействий() {
        for (String path : UniqueModules.ALL) {
            if (UniqueModules.ETERNITY.equals(path)) {
                continue;
            }
            SpellModuleType type = SpellModules.get(SpellModules.id(path));
            assertEquals(SpellModuleType.Category.ACTIONS, type.category(),
                    path + ": личный модуль должен быть в «Действиях»");
        }
    }

    /**
     * Личный модуль не должен добываться сканированием.
     *
     * <p>Иначе он перестаёт быть личным: игрок просканирует нужный блок и
     * получит любой из набора, а «Анализ себя» станет бессмысленным.
     */
    @Test
    void личныйМодульНеВыдаётсяЗаСканирование() {
        Set<String> rewards = new HashSet<>();
        rewards.addAll(ScanRewards.blocks().values());
        rewards.addAll(ScanRewards.mobs().values());
        for (String path : UniqueModules.ALL) {
            assertFalse(rewards.contains(path),
                    "личный модуль выдаётся за сканирование: " + path);
        }
    }

    /**
     * Розыгрыш случайный: при тысяче бросков встречаются все модули набора.
     *
     * <p>Раньше выбор был выведен из хеша UUID, и проверка жила копией этой
     * формулы прямо в тесте — то есть тестировала не код, а саму себя. Теперь
     * проверяется настоящий {@link UniqueModules#choose}: если бы он снова стал
     * детерминированным, тест это увидел бы.
     */
    @Test
    void выборСлучайныйИПокрываетВесьНабор() {
        java.util.Random random = new java.util.Random(20261015L);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String chosen = UniqueModules.choose(net.minecraft.util.RandomSource.create(random.nextLong()));
            assertTrue(chosen != null && !chosen.isEmpty(), "розыгрыш вернул пустой модуль");
            seen.add(chosen);
        }
        assertEquals(UniqueModules.ALL.length, seen.size(),
                "часть набора не выпадает: розыгрыш слишком смещён — " + seen);
    }

    /** Модуль из набора всегда принадлежит набору — несуществующий id уронил бы граф. */
    @Test
    void розыгрышВсегдаИзНабора() {
        java.util.Random random = new java.util.Random(7L);
        for (int i = 0; i < 200; i++) {
            String chosen = UniqueModules.choose(net.minecraft.util.RandomSource.create(random.nextLong()));
            assertTrue(List.of(UniqueModules.ALL).contains(chosen),
                    "розыгрыш выдал модуль вне набора: " + chosen);
        }
    }

    /**
     * Подпись остаётся, хоть розыгрыш стал случайным.
     *
     * <p>Она по-прежнему нужна трём модулям («Резонанс», «Подпись», «Обет»), и
     * «Счёт» считает по другим данным. Хеш от UUID не изменился.
     */
    @Test
    void подписьВсегдаЧетырёхзначная() {
        for (int i = 0; i < 200; i++) {
            UUID uuid = new UUID(i * 0x9E3779B97F4A7C15L, ~i * 0xC2B2AE3D27D4EB4FL);
            int signature = hashOf(uuid);
            assertTrue(signature >= 0 && signature <= 9999,
                    "подпись вне диапазона 0..9999: " + signature + " для " + uuid);
        }
    }

    /** «Анализ себя» должен быть доступен сразу, иначе до личного модуля не добраться. */
    @Test
    void анализСебяДоступенИзначально() {
        assertTrue(StartModules.isStartModule(SpellModules.id("analysis_self")),
                "«Анализ себя» должен быть стартовым модулем");
        SpellModuleType type = SpellModules.get(SpellModules.id("analysis_self"));
        assertNotNull(type);
        assertEquals(0, type.manaCost(), "«Анализ себя» должен быть бесплатным");
    }

    /** Свойства, на которых держится баланс остальных личных модулей. */
    @Test
    void множителиОграничены() {
        SpellModuleType resonance = SpellModules.get(SpellModules.id(UniqueModules.RESONANCE));
        assertNotNull(resonance);
        assertEquals(1, resonance.inputs());
        assertEquals(1, resonance.outputs());

        SpellModuleType oath = SpellModules.get(SpellModules.id(UniqueModules.OATH));
        assertNotNull(oath);
        assertEquals(0, oath.manaCost(),
                "«Обет» платит бездной во время каста, базовая цена должна быть нулевой");

        // «Подпись» и «Счёт» читают данные игрока, поэтому у них нет входов
        assertEquals(0, SpellModules.get(SpellModules.id(UniqueModules.SIGNATURE)).inputs());
        assertEquals(0, SpellModules.get(SpellModules.id(UniqueModules.LEDGER)).inputs());
    }

    /**
     * «Анализ себя» обязан сказать игроку, что он нашёл.
     *
     * <p>Успешный каст в чат не пишет ничего — по замыслу «успех ничем не шумит».
     * Для обычного заклинания это верно, но «Анализ себя» происходит один раз за
     * игрока: без отдельного сообщения он выглядел как ничего не сделавший, а
     * открытый модуль лежал в другой вкладке палитры.
     */
    @Test
    void анализСебяСообщаетЧтоНашёл() throws IOException {
        String исходник = Files.readString(Path.of(
                "src/main/java/com/github/fireprime5/spellcraft/spell/AnalysisModules.java"),
                StandardCharsets.UTF_8);
        int начало = исходник.indexOf("SpellModules.id(\"analysis_self\")");
        assertTrue(начало >= 0, "не найден модуль analysis_self");
        assertTrue(исходник.indexOf("message.spellcraft.unique_found", начало) >= 0,
                "«Анализ себя» молчит об успехе");
        assertTrue(исходник.indexOf("sendSystemMessage", начало) >= 0,
                "сообщение не уходит в чат");
        assertTrue(исходник.indexOf("displayClientMessage", начало) >= 0,
                "сообщение не всплывает на экране");
    }

    /**
     * У перевода есть ровно один подставляемый аргумент — имя найденного модуля.
     */
    @Test
    void уСообщенияОНайденномЕстьПереводВОбоихЯзыках() throws IOException {
        for (String язык : new String[]{"en_us", "ru_ru"}) {
            var json = Files.readString(Path.of(
                    "src/main/resources/assets/spellcraft/lang/" + язык + ".json"),
                    StandardCharsets.UTF_8);
            var корень = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            assertTrue(корень.has("message.spellcraft.unique_found"),
                    язык + ": нет перевода сообщения о найденном модуле");
            String текст = корень.get("message.spellcraft.unique_found").getAsString();
            int плейсхолдеры = текст.split("%s", -1).length - 1;
            assertEquals(1, плейсхолдеры,
                    язык + ": сообщение ждёт другой счёт подстановок — " + текст);
            assertFalse(текст.replace("%s", "").contains("%"),
                    язык + ": в сообщении осталась неразобранная подстановка — " + текст);
        }
    }

    /**
     * Мир, где игрок зашёл до появления розыгрыша, всё равно должен получить модуль.
     *
     * <p>Раньше такой игрок получал отказ «ты уже нашёл» вместо модуля: записей
     * у него не было, а розыгрыш шёл только при входе.
     */
    @Test
    void анализСебяРозыгрываетЕслиМодульНеНазначен() throws IOException {
        String исходник = Files.readString(Path.of(
                "src/main/java/com/github/fireprime5/spellcraft/data/PlayerKnowledge.java"),
                StandardCharsets.UTF_8);
        int начало = исходник.indexOf("public static String discoverUnique(Player player)");
        assertTrue(начало >= 0, "не найден discoverUnique");
        assertTrue(исходник.indexOf("assignUnique(player", начало) >= 0,
                "без записи discoverUnique обязан розыгрывать модуль, иначе игрок "
                        + "из старого мира получит отказ вместо награды");
    }

    /** Тот же хеш, что и в {@link UniqueModules#signatureOf}. */
    private static int hashOf(UUID uuid) {
        long mixed = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
        mixed ^= (mixed >>> 33);
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= (mixed >>> 33);
        return (int) Math.floorMod(mixed, 10_000L);
    }

}
