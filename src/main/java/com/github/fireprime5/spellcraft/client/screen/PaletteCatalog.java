package com.github.fireprime5.spellcraft.client.screen;

import com.github.fireprime5.spellcraft.config.SpellConfig;
import com.github.fireprime5.spellcraft.palette.Palette;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Переключение палитры модулей в игре.
 *
 * <p>Читает {@code assets/spellcraft/palette.json} из classpath: файл лежит в
 * resources, поэтому варианты уезжают в jar вместе с модом, и источник правды
 * остаётся один — тот же файл читает генератор текстур и сверяет
 * {@code PaletteTest}.
 *
 * <p>Два способа задать цвет: выбрать готовый вариант по идентификатору или
 * указать один оттенок, из которого шкала строится сама
 * ({@link Palette#rampFromHue}). Второй нужен потому, что «хочу фиолетовый» —
 * это не один из восьми вариантов, а параметр.
 */
public final class PaletteCatalog {

    /** Один переключаемый вариант: пять категорий и цвет ошибки. */
    public record Variant(String id, String label, int[] categories, int error) {
    }

    private static final String RESOURCE = "/assets/spellcraft/palette.json";
    private static final String[] ORDER = {"numbers", "entities", "actions", "logic", "network"};

    private static Map<String, Variant> variants;
    private static String active = "current";

    private PaletteCatalog() {
    }

    private static Map<String, Variant> variants() {
        if (variants != null) {
            return variants;
        }
        variants = new LinkedHashMap<>();
        try (InputStream stream = PaletteCatalog.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                return variants;
            }
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject section = root.getAsJsonObject("variants");
            for (String id : section.keySet()) {
                JsonObject entry = section.getAsJsonObject(id);
                JsonObject categories = entry.getAsJsonObject("categories");
                int[] ramp = new int[ORDER.length];
                for (int i = 0; i < ORDER.length; i++) {
                    ramp[i] = argb(categories.get(ORDER[i]).getAsString());
                }
                variants.put(id, new Variant(id, entry.get("label").getAsString(), ramp,
                        argb(entry.get("error").getAsString())));
            }
        } catch (IOException | RuntimeException e) {
            // мод без палитры должен запуститься и показать значения по умолчанию
            variants.clear();
        }
        return variants;
    }

    private static int argb(String hex) {
        return 0xFF000000 | Integer.parseInt(hex.replace("#", ""), 16);
    }

    /** Идентификаторы вариантов в порядке переключения, всегда хотя бы один. */
    public static List<String> ids() {
        List<String> out = new ArrayList<>(variants().keySet());
        if (out.isEmpty()) {
            out.add("current");
        }
        return out;
    }

    public static String label(String id) {
        Variant variant = variants().get(id);
        return variant == null ? id : variant.label();
    }

    public static String active() {
        return active;
    }

    /**
     * Применяет выбор из конфига.
     *
     * @param variantId идентификатор варианта, "" или null — оставить по умолчанию
     * @param hue       базовый оттенок в градусах; {@code null} или меньше нуля —
     *                  вариант из конфига, иначе шкала строится из оттенка
     */
    public static void apply(String variantId, Double hue) {
        if (hue != null && hue >= 0) {
            float base = hue.floatValue();
            Palette.setCategories(Palette.rampFromHue(base, 0.62f, 0.72f),
                    Palette.errorFromHue(base));
            active = "hue:" + Math.round(base);
            return;
        }
        String id = variantId == null || variantId.isBlank() ? "current" : variantId;
        Variant variant = variants().get(id);
        if (variant == null) {
            Palette.reset();
            active = "current";
            return;
        }
        Palette.setCategories(variant.categories(), variant.error());
        active = variant.id();
    }

    /** Следующий вариант по кругу; при активном оттенке возвращает к текущему. */
    public static String cycle(String from) {
        List<String> ids = ids();
        if (ids.isEmpty()) {
            return "current";
        }
        int index = ids.indexOf(from);
        return ids.get((Math.max(0, index) + 1) % ids.size());
    }

    /** Переключает и показывает результат в actionbar: иначе непонятно, что нажали. */
    public static void cycleAndReport(SpellConfig config) {
        String next = cycle(config.modulePalette);
        config.modulePalette = next;
        config.moduleHue = -1;
        config.save();
        apply(next, null);
        Minecraft.getInstance().gui.setOverlayMessage(
                Component.translatable("message.spellcraft.palette", label(next)), false);
    }
}