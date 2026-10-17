package com.github.fireprime5.spellcraft.spell;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Подразделы модулей.
 *
 * <p>Подраздел — это вторая ступень палитры: категория отвечает на вопрос
 * «о чём модуль», подраздел — «что он делает». Без второй ступени список из
 * сорока с лишним названий читался сплошной полосой.
 */
class SpellSubsectionsTest {

    private static final Path RU = Path.of("src/main/resources/assets/spellcraft/lang/ru_ru.json");
    private static final Path EN = Path.of("src/main/resources/assets/spellcraft/lang/en_us.json");

    @BeforeAll
    static void setUp() {
        SpellModules.init();
    }

    private static Map<String, String> lang(Path path) throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        Map<String, String> out = new java.util.LinkedHashMap<>();
        root.entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsString()));
        return out;
    }

    /**
     * Ни один модуль не должен провалиться в безымянное «Прочее».
     *
     * <p>Исключение — личные модули: для них подраздел «Личный» удалён намеренно.
     * Отдельной вкладки «Личный» в палитре не было никогда, а заголовок
     * подраздела рисуется только когда в списке больше одного подраздела, то
     * есть открытый личный модуль почти невозможно было найти. В «Действиях» они
     * лежат под подписанным «Прочее» — это имя на экране есть, в отличие от
     * несуществующей вкладки.
     */
    @Test
    void уКаждогоМодуляЕстьПодраздел() {
        for (SpellModuleType type : SpellModules.all()) {
            String sub = type.subCategory();
            assertTrue(SpellSubsections.isKnown(sub),
                    "у модуля " + type.id().getPath() + " неизвестный подраздел " + sub);
            if (SpellSubsections.OTHER.equals(sub)) {
                assertTrue(type.isUnique(),
                        "модуль " + type.id().getPath() + " не приписан ни к одному подразделу");
            }
        }
    }

    /** Категория и подраздел не должны противоречить друг другу. */
    @Test
    void подразделЖивётВСвоейКатегории() {
        for (SpellModuleType type : SpellModules.all()) {
            String sub = type.subCategory();
            List<SpellModuleType> sameSub = SpellModules.all().stream()
                    .filter(t -> t.subCategory().equals(sub)).toList();
            assertEquals(1, sameSub.stream().map(t -> t.category()).distinct().count(),
                    "подраздел " + sub + " расползся по категориям: "
                            + sameSub.stream().map(t -> t.category().name()).distinct().toList());
        }
    }

    /**
     * Внутри категории модули идут подряд, иначе заголовок подраздела в палитре
     * появлялся бы несколько раз за прокрутку.
     */
    @Test
    void модулиСгруппированы() {
        for (SpellModuleType.Category category : SpellModuleType.Category.values()) {
            List<SpellModuleType> types =
                    SpellModules.search("", category);
            String previous = null;
            int headers = 0;
            for (SpellModuleType type : types) {
                String sub = type.subCategory();
                if (!sub.equals(previous)) {
                    headers++;
                    previous = sub;
                }
            }
            int distinct = (int) types.stream().map(SpellModuleType::subCategory).distinct().count();
            assertEquals(distinct, headers,
                    "категория " + category + ": подразделы не подряд");
        }
    }

    /** У каждого подраздела есть название в обоих переводах. */
    @Test
    void уПодразделовЕстьПеревод() throws IOException {
        for (String sub : SpellSubsections.order()) {
            String key = SpellSubsections.nameKey(sub);
            assertTrue(lang(RU).containsKey(key), "нет перевода " + key);
            assertTrue(lang(EN).containsKey(key), "нет перевода " + key);
            assertFalse(SpellSubsections.labelRu(sub).isBlank(),
                    "пустое название подраздела " + sub);
            assertFalse(SpellSubsections.labelEn(sub).isBlank(),
                    "пустое английское название подраздела " + sub);
        }
    }

    /** Переводы ru и en не должны разъезжаться по составу ключей. */
    @Test
    void переводыСовпадаютПоКлючам() throws IOException {
        assertEquals(Set0.of(lang(RU)), Set0.of(lang(EN)), "ключи ru_ru и en_us разошлись");
    }

    /** Поиск находит модуль и по названию его подраздела. */
    @Test
    void поискУчитываетПодраздел() {
        assertFalse(SpellModules.search("стек", null).isEmpty(),
                "поиск по названию подраздела");
        assertTrue(SpellModules.search("стек", null).stream()
                        .anyMatch(t -> t.id().getPath().equals("stack_pop")),
                "«стек» должен находить «Взять из стека»");
        // подраздел учитывается вместе с категорией, а не вместо неё
        assertTrue(SpellModules.search("стек", SpellModuleType.Category.ACTIONS).isEmpty(),
                "в «Действиях» подраздела «Стек» быть не должно");
    }

    /** Порядок подразделов общий и для палитры, и для сортировки. */
    @Test
    void порядокПодразделовСтабилен() {
        List<String> first = SpellSubsections.order();
        assertEquals(first, SpellSubsections.order(), "порядок не должен меняться между вызовами");
        assertSame(first, SpellSubsections.order(), "список возвращается тем же объектом");
    }

    private static final class Set0 {
        private Set0() {
        }

        static java.util.Set<String> of(Map<String, String> map) {
            return java.util.Set.copyOf(map.keySet());
        }
    }
}