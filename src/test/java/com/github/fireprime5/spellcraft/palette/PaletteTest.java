package com.github.fireprime5.spellcraft.palette;

import com.google.gson.JsonObject;
import com.github.fireprime5.spellcraft.spell.SpellModuleType;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Палитра должна быть одна.
 *
 * <p>Источник правды — {@code tools/models/palette.json}: генератор текстур
 * читает его, {@link Palette} является его зеркалом. Проверка ловит то, что
 * случилось до появления палитры: пятнадцать произвольных оттенков в
 * {@code PortType}, свои числа в {@code UiKit} и отдельные цвета у каждого
 * модуля, из-за чего мод выглядел разноцветным при вполне рабочем коде.
 *
 * <p>Здесь нет Minecraft — только файлы, поэтому проверка быстрая и работает в
 * CI без игры.
 */
class PaletteTest {

    private static final Path JSON = Path.of("src/main/resources/assets/spellcraft/palette.json");

    private static JsonObject section(String name) throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(JSON)).getAsJsonObject();
        assertTrue(root.has(name), "в palette.json нет секции " + name);
        return root.getAsJsonObject(name);
    }

    private static int argb(String hex) {
        return 0xFF000000 | Integer.parseInt(hex.replace("#", ""), 16);
    }

    /** Раскладка java-констант -> путь в json. Имена обязаны совпадать. */
    private static List<String[]> bindings() {
        List<String[]> out = new ArrayList<>();
        out.add(new String[]{"base.bg", "BG"});
        out.add(new String[]{"base.panel", "PANEL"});
        out.add(new String[]{"base.panelDark", "PANEL_DARK"});
        out.add(new String[]{"base.panelLight", "PANEL_LIGHT"});
        out.add(new String[]{"base.border", "BORDER"});
        out.add(new String[]{"base.borderLight", "BORDER_LIGHT"});
        out.add(new String[]{"base.grid", "GRID"});
        out.add(new String[]{"base.accent", "ACCENT"});
        out.add(new String[]{"base.accentDim", "ACCENT_DIM"});
        out.add(new String[]{"base.text", "TEXT"});
        out.add(new String[]{"base.textDim", "TEXT_DIM"});
        out.add(new String[]{"base.warn", "WARN"});
        out.add(new String[]{"base.error", "ERROR"});
        out.add(new String[]{"base.ok", "OK"});
        out.add(new String[]{"base.slotBg", "SLOT_BG"});
        out.add(new String[]{"category.numbers", "CATEGORY_NUMBERS"});
        out.add(new String[]{"category.entities", "CATEGORY_ENTITIES"});
        out.add(new String[]{"category.actions", "CATEGORY_ACTIONS"});
        out.add(new String[]{"category.logic", "CATEGORY_LOGIC"});
        out.add(new String[]{"category.effects", "CATEGORY_EFFECTS"});
        out.add(new String[]{"port.number", "PORT_NUMBER"});
        out.add(new String[]{"port.entity", "PORT_ENTITY"});
        out.add(new String[]{"port.point", "PORT_POINT"});
        out.add(new String[]{"port.vector", "PORT_VECTOR"});
        out.add(new String[]{"port.item", "PORT_ITEM"});
        out.add(new String[]{"port.location", "PORT_LOCATION"});
        out.add(new String[]{"port.any", "PORT_ANY"});
        return out;
    }

    private static int constant(String name) {
        try {
            return (int) Palette.class.getField(name).get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("в Palette нет публичной константы " + name, e);
        }
    }

    /** Каждая java-константа должна совпадать со своим значением в json. */
    @Test
    void javaКонстантыСовпадаютСJson() throws IOException {
        for (String[] binding : bindings()) {
            String path = binding[0];
            String field = binding[1];
            String[] parts = path.split("\\.");
            JsonObject group = section(parts[0]);
            assertTrue(group.has(parts[1]),
                    "в palette.json нет " + path + " -- константа Palette." + field
                            + " осталась бы без источника правды");
            int expected = argb(group.get(parts[1]).getAsString());
            assertEquals(expected, constant(field),
                    "Palette." + field + " разошёлся с palette.json:" + path
                            + " -- правь json, потом этот класс");
        }
    }

    /**
     * Ошибка обязана быть заметно холоднее тёплой шкалы категорий.
     *
     * <p>Медный {@code category.logic} стоит рядом с красным, и если положить
     * туда же ошибку, сломанный узел станет неотличим от рабочего.
     */
    @Test
    void ошибкаОтличимаОтКатегорий() {
        int errorHue = hue(Palette.error());
        for (Map.Entry<SpellModuleType.Category, Integer> entry : Map.of(
                SpellModuleType.Category.NUMBERS, Palette.CATEGORY_NUMBERS,
                SpellModuleType.Category.ENTITIES, Palette.CATEGORY_ENTITIES,
                SpellModuleType.Category.ACTIONS, Palette.CATEGORY_ACTIONS,
                SpellModuleType.Category.LOGIC, Palette.CATEGORY_LOGIC,
                SpellModuleType.Category.EFFECTS, Palette.CATEGORY_EFFECTS).entrySet()) {
            int categoryHue = hue(entry.getValue());
            int distance = Math.abs(errorHue - categoryHue);
            int wrapped = Math.min(distance, 360 - distance);
            assertTrue(wrapped >= 25,
                    "ошибка " + hue(Palette.error()) + "° слишком близка к категории "
                            + entry.getKey() + " " + categoryHue + "°");
        }
    }

    /**
     * Каркас холодный, категории тёплые — иначе панель и её содержимое спорят.
     *
     * <p>Исключение — {@code EFFECTS}: он фиолетовый. Причина не в капризе, а в
     * том, что тридцать семь эффектов в тёплом ряду встали бы впритык к урону
     * и лечению, и игрок перестал бы различать, где бьёт, а где лечит. При этом
     * смысл правила («категория не должна выглядеть как холодный каркас») всё
     * равно соблюдён: холодный каркас живёт в диапазоне 170-260 градусов, а
     * фиолетовый эффектов стоит на 274-м — рядом, но снаружи.
     */
    @Test
    void каркасХолодныйКатегорииТёплые() {
        assertTrue(isCool(Palette.ACCENT), "основной акцент должен быть холодным");
        assertTrue(isCool(Palette.BORDER_LIGHT), "рамка должна быть холодной");
        for (SpellModuleType.Category category : SpellModuleType.Category.values()) {
            int accent = Palette.category(category);
            if (category == SpellModuleType.Category.EFFECTS) {
                assertFalse(isCool(accent),
                        "эффекты не должны совпадать с холодным каркасом: "
                                + hue(accent) + "°");
                continue;
            }
            assertTrue(isWarm(accent),
                    "категория " + category + " должна быть тёплой, а не "
                            + hue(accent) + "°");
        }
    }

    /**
     * Файл обязан лежать в resources: иначе варианты есть в репозитории, но их
     * нет в jar и клавиша в игре переключает пустоту.
     */
    @Test
    void палитраУезжаетВJar() {
        assertTrue(Files.exists(JSON), "палитра должна быть в resources, а не в tools");
        assertTrue(Files.exists(Path.of("src/main/resources/assets/spellcraft/palette.json")));
    }

    /** Каждый вариант из json обязан быть тёплым или холодным и не спорить с ошибкой. */
    @Test
    void вариантыИзJsonКорректны() throws IOException {
        JsonObject section = section("variants");
        assertTrue(section.size() >= 8, "вариантов должно быть не меньше восьми, а их "
                + section.size());
        for (String id : section.keySet()) {
            JsonObject entry = section.getAsJsonObject(id);
            JsonObject categories = entry.getAsJsonObject("categories");
            int error = argb(entry.get("error").getAsString());
            int errorHue = hue(error);
            for (SpellModuleType.Category category : SpellModuleType.Category.values()) {
                String key = category.name().toLowerCase();
                assertTrue(categories.has(key), "вариант " + id + " без категории " + key);
                int accent = argb(categories.get(key).getAsString());
                int distance = Math.abs(errorHue - hue(accent));
                assertTrue(Math.min(distance, 360 - distance) >= 25,
                        "вариант " + id + ": ошибка " + hue(error) + "° слишком близко к "
                                + key + " " + hue(accent) + "°");
            }
        }
    }

    /**
     * Свободный оттенок: шкала должна остаться системой, а не пятью разными
     * цветами, и ошибка обязана отстоять от категорий.
     */
    @Test
    void шкалаИзОттенкаНеЛомаетПравила() {
        for (float hue : new float[]{0f, 30f, 60f, 120f, 180f, 240f, 300f, 330f}) {
            int[] ramp = Palette.rampFromHue(hue, 0.62f, 0.72f);
            assertEquals(5, ramp.length, "в шкале должно быть пять категорий");
            int error = Palette.errorFromHue(hue);
            for (int accent : ramp) {
                int distance = Math.abs(hue(error) - hue(accent));
                assertTrue(Math.min(distance, 360 - distance) >= 25,
                        "оттенок " + hue + "°: ошибка " + hue(error) + "° слишком близко к "
                                + hue(accent) + "°");
                // не насыщенность, а принадлежность к одному оттенку: пятью
                // случайными цветами шкала не читается как система
                int spread = Math.abs(hue(accent) - (int) hue);
                assertTrue(Math.min(spread, 360 - spread) <= 20,
                        "оттенок " + hue + "°: категория ушла на " + hue(accent) + "°");
                assertTrue(lightness(accent) > 0.35f,
                        "оттенок " + hue + "°: категория слишком тёмная, светлота "
                                + lightness(accent));
            }
            // светлота обязана падать монотонно, иначе шкала перестаёт читаться
            for (int i = 1; i < ramp.length; i++) {
                assertTrue(lightness(ramp[i]) < lightness(ramp[i - 1]),
                        "оттенок " + hue + "°: светлота не падает на шаге " + i);
            }
        }
    }

    /** setCategories не должен ронять отрисовку на кривом входе. */
    @Test
    void setCategoriesТерпитКривойВход() {
        Palette.reset();
        try {
            Palette.setCategories(new int[]{0xFF112233}, 0xFF445566);
            assertEquals(SpellModuleType.Category.values().length, Palette.categories().length);
            Palette.setCategories(new int[0], 0);
            assertEquals(SpellModuleType.Category.values().length, Palette.categories().length);
        } finally {
            Palette.reset();
        }
    }

    private static float lightness(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        return (Math.max(r, Math.max(g, b)) + Math.min(r, Math.min(g, b))) / 2f;
    }

    /** 0..360, 0 = красный. */
    private static int hue(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        if (max == min) {
            return 0;
        }
        float delta = max - min;
        int degrees;
        if (max == r) {
            degrees = (int) Math.round(60 * (((g - b) / delta) % 6));
        } else if (max == g) {
            degrees = (int) Math.round(60 * (((b - r) / delta) + 2));
        } else {
            degrees = (int) Math.round(60 * (((r - g) / delta) + 4));
        }
        return (degrees + 360) % 360;
    }

    private static boolean isCool(int argb) {
        int h = hue(argb);
        return h >= 170 && h <= 260;
    }

    private static boolean isWarm(int argb) {
        int h = hue(argb);
        assertFalse(h >= 170 && h <= 260, "цвет " + h + "° не тёплый");
        // 5..65 градусов: янтарь, оранжевый, медь и коралл в конце шкалы.
        // Нижняя граница не 20: #E86A52 это 10°, коралл, и он тёплый.
        // Смысл проверки -- не дать категориям уйти в холод, а не мерить точность.
        return h >= 5 && h <= 65;
    }
}