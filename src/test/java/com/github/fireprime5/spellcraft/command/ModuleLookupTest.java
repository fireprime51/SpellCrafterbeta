package com.github.fireprime5.spellcraft.command;

import com.github.fireprime5.spellcraft.spell.SpellModuleType;
import com.github.fireprime5.spellcraft.spell.SpellModules;
import com.github.fireprime5.spellcraft.spell.UniqueModules;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Поиск модуля для {@code /spellcraft give}.
 *
 * <p>Команду целиком проверить нечем: {@code CommandSourceStack} в тестах не
 * создаётся. Но вся её решающая часть — как строка превращается в модуль — живёт в
 * {@link ModuleLookup} и проверяется здесь по-настоящему.
 */
class ModuleLookupTest {

    @BeforeAll
    static void setUp() {
        SpellModules.init();
    }

    private static String path(ModuleLookup.Result result) {
        assertTrue(result.found(), "модуль не найден: кандидатов " + result.candidates().size());
        return result.exact().id().getPath();
    }

    /** Полный идентификатор со пространством имён. */
    @Test
    void находитПоПолномуИдентификатору() {
        assertEquals(UniqueModules.ETERNITY,
                path(ModuleLookup.resolve("spellcraft:unique_eternity")));
    }

    /** Тот же путь без пространства имён — так его видно в подсказках и логе. */
    @Test
    void находитПоПутиБезПространстваИмён() {
        assertEquals(UniqueModules.ETERNITY, path(ModuleLookup.resolve("unique_eternity")));
        assertEquals(UniqueModules.ETERNITY, path(ModuleLookup.resolve("  unique_eternity  ")));
    }

    /**
     * Название на русском.
     *
     * <p>Локализованное название проверить нельзя: в тестах переводы не
     * загружены, {@code Component.translatable} возвращает сам ключ. Поэтому
     * проверяются синонимы из кода — они работают и в игре, и здесь.
     */
    @Test
    void находитПоНазванию() {
        assertEquals("damage", path(ModuleLookup.resolve("урон")));
        assertEquals(UniqueModules.RESONANCE, path(ModuleLookup.resolve("резонанс")));
        assertEquals(UniqueModules.ETERNITY, path(ModuleLookup.resolve("вечность")));
        assertEquals(UniqueModules.ETERNITY, path(ModuleLookup.resolve("бессмертие")));
    }

    /** Регистр, пробелы и подчёркивания не должны мешать. */
    @Test
    void находитНезависимоОтРегистраИПробелов() {
        assertEquals(UniqueModules.RESONANCE, path(ModuleLookup.resolve("РЕЗОНАНС")));
        assertEquals("raycast_entity", path(ModuleLookup.resolve("ПОРАЖЕНИЕ")));
        assertEquals(UniqueModules.ETERNITY, path(ModuleLookup.resolve("unique eternity")));
    }

    /**
     * Синоним «личный» есть у всех шести — и именно поэтому он неоднозначен.
     *
     * <p>Проверка наоборот: если бы поиск выбрал один модуль наугад, игрок
     * получил бы не тот.
     */
    @Test
    void общийСинонимНеоднозначен() {
        ModuleLookup.Result result = ModuleLookup.resolve("личный");
        assertFalse(result.found(), "общий синоним не должен выбирать модуль");
        assertTrue(result.candidates().size() > 1,
                "синоним «личный» есть у всех личных, кандидатов: "
                        + result.candidates().size());
    }

    /**
     * Начало названия тоже годится: «вечн» — один модуль, выбирать не из чего.
     */
    @Test
    void находитПоНачалуНазвания() {
        assertEquals(UniqueModules.ETERNITY, path(ModuleLookup.resolve("вечн")));
    }

    /**
     * Под несколько модулей — выбор не делаем.
     *
     * <p>Выдать наугад хуже, чем отказать: игрок получил бы не тот модуль и
     * потратил бы на это время, не понимая почему.
     */
    @Test
    void неоднозначныйЗапросНеВыбираетСам() {
        ModuleLookup.Result result = ModuleLookup.resolve("стек");
        assertFalse(result.found(), "неоднозначный запрос не должен выбирать модуль");
        assertTrue(result.ambiguous(), "ожидалась неоднозначность");
        assertTrue(result.candidates().size() > 1);
    }

    /**
 * Неоднозначным должен быть и нечёткий запрос.
 *
 * <p>«стек» и «личный» ловятся точным поиском по синониму, и этого мало: если
 * разрешить выбор наугад только там, неоднозначность появляется снова там, где
 * запрос — общее начало («извл» — это и «извлечение», и «извлечь»).
 */
    @Test
    void нечёткийЗапросТожеНеОднозначен() {
        ModuleLookup.Result result = ModuleLookup.resolve("извл");
        assertFalse(result.found(), "нечёткий запрос не должен выбирать модуль");
        assertTrue(result.ambiguous(), "ожидалась неоднозначность нечёткого поиска");
        assertEquals(2, result.candidates().size(),
                "под «извл» подходит не то число модулей");
    }

    @Test
    void неизвестныйЗапросНеНаходитНичего() {
        assertNull(ModuleLookup.resolve("такого модуля нет").exact());
        assertFalse(ModuleLookup.resolve("такого модуля нет").ambiguous());
        assertTrue(ModuleLookup.resolve("такого модуля нет").candidates().isEmpty());
    }

    /** Пустой ввод — это промах, а не «выдать что-нибудь». */
    @Test
    void пустойЗапросПромах() {
        for (String запрос : new String[]{null, "", "   "}) {
            assertFalse(ModuleLookup.resolve(запрос).found(), "пустой запрос нашёл модуль");
        }
    }

    /**
     * Чужое пространство имён не должно подсовывать чужой модуль.
     *
     * <p>Все модули — в {@code spellcraft}. Если бы поиск принимал
     * {@code minecraft:damage}, оператор мог бы выдать несуществующее и получить
     * отказ вместо результата.
     */
    @Test
    void чужоеПространствоИмёнНеВыдаётЧужойМодуль() {
        assertFalse(ModuleLookup.resolve("minecraft:damage").found(),
                "модуль выдан из чужого пространства имён");
    }

    /** Резолв обязан находить вообще всё, что зарегистрировано. */
    @Test
    void каждыйЗарегистрированныйМодульНаходитсяПоПути() {
        for (SpellModuleType type : SpellModules.all()) {
            assertEquals(type.id().getPath(), path(ModuleLookup.resolve(type.id().getPath())),
                    "путь не находится: " + type.id());
        }
    }

    /** И наоборот: личные модули — то, ради чего команда и писалась. */
    @Test
    void личныеМодулиНаходятсяВсе() {
        for (String личный : UniqueModules.ALL) {
            ModuleLookup.Result result = ModuleLookup.resolve(личный);
            assertTrue(result.found(), "личный модуль не найден: " + личный);
            assertTrue(result.exact().isUnique(), личный + ": перестал быть личным");
        }
    }
}