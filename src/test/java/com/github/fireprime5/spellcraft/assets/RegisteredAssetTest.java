package com.github.fireprime5.spellcraft.assets;

import com.github.fireprime5.spellcraft.spell.SpellModuleType;
import com.github.fireprime5.spellcraft.spell.SpellModules;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Repository-only checks for the assets promised by the source registries. */
class RegisteredAssetTest {
    private static final Path ROOT = Path.of("src/main");
    private static final Path ASSETS = ROOT.resolve("resources/assets/spellcraft");
    private static final Pattern BLOCK_REGISTER = Pattern.compile("BLOCKS\\.register\\s*\\(\\s*\\\"([a-z0-9_]+)\\\"");
    private static final Pattern ITEM_REGISTER = Pattern.compile("ITEMS\\.register\\s*\\(\\s*\\\"([a-z0-9_]+)\\\"");

    @Test
    void everyRegisteredBlockHasClientAssets() throws IOException {
        Set<String> blocks = registered(ROOT.resolve("java/com/github/fireprime5/spellcraft/registry/ModBlocks.java"), BLOCK_REGISTER);
        List<String> missing = new ArrayList<>();
        for (String id : blocks) {
            if (!Files.isRegularFile(ASSETS.resolve("blockstates/" + id + ".json"))) {
                missing.add(id + ": missing blockstate");
            }
            if (!Files.isRegularFile(ASSETS.resolve("models/block/" + id + ".json"))
                    && !hasReferencedBlockModel(id)) {
                missing.add(id + ": missing block model");
            }
            if (!Files.isRegularFile(ASSETS.resolve("textures/block/" + id + ".png"))) {
                missing.add(id + ": missing block texture");
            }
        }
        assertTrue(missing.isEmpty(), String.join("\n", missing));
    }

    @Test
    void everyRegisteredItemHasModelTextureAndEnglishName() throws IOException {
        Set<String> items = registered(ROOT.resolve("java/com/github/fireprime5/spellcraft/registry/ModItems.java"), ITEM_REGISTER);
        JsonObject lang = json(ASSETS.resolve("lang/en_us.json"));
        List<String> missing = new ArrayList<>();
        for (String id : items) {
            if (!Files.isRegularFile(ASSETS.resolve("models/item/" + id + ".json"))) missing.add(id + ": missing item model");
            if (!Files.isRegularFile(ASSETS.resolve("textures/item/" + id + ".png"))) missing.add(id + ": missing item texture");
            // BlockItem names use the block translation key in Minecraft's fallback
            // chain; standalone items need their own item key.
            if (!lang.has("item.spellcraft." + id) && !lang.has("block.spellcraft." + id)) {
                missing.add(id + ": missing item localization");
            }
        }
        assertTrue(missing.isEmpty(), String.join("\n", missing));
    }

    /**
     * Проверка идёт по настоящему реестру, а не по исходникам.
     *
     * <p>Раньше модули вылавливались регуляркой {@code SpellModules.id("…")}, и
     * она находила меньше половины: модули, собранные через хелперы
     * ({@code constant}, {@code math}, общий {@code register}), а весь цикл
     * модулей эффектов по массиву идентификаторов не попадали вовсе. Реестр
     * доступен в тестах без Minecraft, поэтому надёжнее спросить его самого.
     *
     * <p>Ключи тоже берутся у самого модуля ({@code nameKey()}/
     * {@code descriptionKey()}): не все следуют шаблону
     * {@code module.spellcraft.<id>} — модули эффектов берут название из
     * ванильных переводов ({@code effect.minecraft.*}) и делят одно общее
     * описание. Ванильные ключи в этом моде не проверяются: переводы оформляет
     * Mojang.
     */
    @Test
    void everyRegisteredModuleHasEnglishNameAndDescription() throws IOException {
        SpellModules.init();
        JsonObject english = json(ASSETS.resolve("lang/en_us.json"));
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (SpellModuleType type : SpellModules.all()) {
            String name = type.nameKey();
            String description = type.descriptionKey();
            if (isVanilla(name) && isVanilla(description)) continue;
            checked++;
            for (String key : new String[]{name, description}) {
                if (isVanilla(key) || english.has(key)) continue;
                problems.add(type.id() + ": missing localization " + key);
            }
        }
        assertTrue(checked > 100, "проверено подозрительно мало модулей: " + checked);
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /** Ванильный ключ перевода: {@code effect.minecraft.speed}. Оформляет Mojang. */
    private static boolean isVanilla(String key) {
        return key.startsWith("effect.minecraft.");
    }

    /**
     * Плейсхолдеров в переводах ровно столько, сколько аргументов передаёт код.
     *
     * <p>{@code Component.translatable(key)} без аргументов на строке с
     * {@code %s} не падает — Minecraft тихо отдаёт шаблон целиком, и игрок видит
     * «Выдаёт эффект «%s» сущности». Наоборот, лишний аргумент тоже остаётся
     * в строке. Поэтому сверяем оба конца: сколько плейсхолдеров в json и
     * сколько значений подставляет вызов {@code description()} у модуля.
     */
    @Test
    void descriptionPlaceholdersMatchTheArgumentsModulesPass() throws IOException {
        SpellModules.init();
        JsonObject english = json(ASSETS.resolve("lang/en_us.json"));
        List<String> problems = new ArrayList<>();
        for (SpellModuleType type : SpellModules.all()) {
            String key = type.descriptionKey();
            if (isVanilla(key) || !english.has(key)) continue;
            int placeholders = placeholders(english.get(key).getAsString());
            int arguments = placeholders(type.description());
            if (placeholders != arguments) {
                problems.add(type.id() + ": " + key + " ждёт " + placeholders
                        + " аргументов, а модуль передаёт " + arguments);
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    private static int placeholders(String text) {
        Matcher matcher = Pattern.compile("%(\\d+\\$)?s").matcher(text);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    private static int placeholders(net.minecraft.network.chat.Component component) {
        if (!(component.getContents() instanceof
                net.minecraft.network.chat.contents.TranslatableContents translatable)) {
            return 0;
        }
        Object[] args = translatable.getArgs();
        int count = 0;
        for (Object arg : args) {
            if (arg instanceof java.util.List<?> list) count += list.size();
            else if (arg != null) count++;
        }
        return count;
    }

    @Test
    void russianLocalizationMatchesEnglishWithoutDuplicatesOrMissingKeys() throws IOException {
        Path englishPath = ASSETS.resolve("lang/en_us.json");
        Path russianPath = ASSETS.resolve("lang/ru_ru.json");
        JsonObject english = json(englishPath);
        JsonObject russian = json(russianPath);
        Set<String> duplicateEnglish = duplicateTopLevelKeys(englishPath);
        Set<String> duplicateRussian = duplicateTopLevelKeys(russianPath);
        Set<String> missing = new LinkedHashSet<>(english.keySet());
        missing.removeAll(russian.keySet());
        Set<String> extra = new LinkedHashSet<>(russian.keySet());
        extra.removeAll(english.keySet());
        List<String> problems = new ArrayList<>();
        if (!duplicateEnglish.isEmpty()) problems.add("en_us duplicate keys: " + duplicateEnglish);
        if (!duplicateRussian.isEmpty()) problems.add("ru_ru duplicate keys: " + duplicateRussian);
        if (!missing.isEmpty()) problems.add("ru_ru missing keys: " + missing);
        if (!extra.isEmpty()) problems.add("ru_ru extra keys: " + extra);
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void everyModelTextureReferenceAndPngIsValid() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Path root : List.of(ASSETS.resolve("models/item"), ASSETS.resolve("models/block"))) {
            try (var files = Files.list(root)) {
                for (Path model : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                    JsonObject object = json(model);
                    JsonObject textures = object.getAsJsonObject("textures");
                    if (textures == null) continue;
                    for (String key : textures.keySet()) {
                        String reference = textures.get(key).getAsString();
                        if (reference.startsWith("#")) continue;
                        int colon = reference.indexOf(':');
                        String namespace = colon < 0 ? "spellcraft" : reference.substring(0, colon);
                        String path = colon < 0 ? reference : reference.substring(colon + 1);
                        Path texture = ASSETS.getParent().resolve(namespace).resolve("textures").resolve(path + ".png");
                        checkPng(texture, model + " texture " + reference, problems);
                    }
                }
            }
        }
        try (var files = Files.walk(ASSETS.resolve("textures"))) {
            for (Path texture : files.filter(p -> p.toString().endsWith(".png")).toList()) {
                checkPng(texture, texture.toString(), problems);
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void blockModelsHaveResolvableParentsAndParticleTextures() throws IOException {
        List<String> problems = new ArrayList<>();
        try (var files = Files.list(ASSETS.resolve("models/block"))) {
            for (Path model : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject object = json(model);
                String parent = object.has("parent") ? object.get("parent").getAsString() : null;
                if (parent != null && parent.startsWith("spellcraft:")) {
                    String path = parent.substring("spellcraft:".length());
                    if (!Files.isRegularFile(ASSETS.resolve("models").resolve(path + ".json"))) {
                        problems.add(model + ": missing parent " + parent);
                    }
                }
                JsonObject textures = object.getAsJsonObject("textures");
                boolean hasParticle = textures != null && textures.has("particle");
                boolean inheritsCubeParticle = "block/cube_all".equals(parent)
                        && textures != null && textures.has("all");
                if (!hasParticle && !inheritsCubeParticle) {
                    problems.add(model + ": no particle texture reference");
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void blockItemModelsUseTheirPlacedBlockGeometry() throws IOException {
        // сети и трубы бездны тут нет: их рисует рендерер блок-сущности, и в
        // инвентаре предмет — плоский спрайт, а не геометрия блока
        Set<String> blockItems = Set.of("abyss_tank", "conveyor", "smelter",
                "mana_vessel", "mana_extractor", "debug_energy_source", "da_animated");
        List<String> problems = new ArrayList<>();
        for (String id : blockItems) {
            JsonObject model = json(ASSETS.resolve("models/item/" + id + ".json"));
            String parent = model.has("parent") ? model.get("parent").getAsString() : "";
            String expected = "spellcraft:block/" + id;
            if (!expected.equals(parent)) problems.add(id + ": expected parent " + expected);
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void blockTexturesUseTheNativeGridAndItemTexturesAreTransparent() throws IOException {
        List<String> problems = new ArrayList<>();
        checkTextureDirectory(ASSETS.resolve("textures/block"), true, problems);
        checkTextureDirectory(ASSETS.resolve("textures/item"), false, problems);
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void everyBlockstateModelReferenceExists() throws IOException {
        List<String> problems = new ArrayList<>();
        try (var files = Files.list(ASSETS.resolve("blockstates"))) {
            for (Path blockstate : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                checkBlockstateModels(json(blockstate), blockstate, problems);
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    private static void checkBlockstateModels(JsonElement element, Path blockstate,
                                              List<String> problems) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("model")) {
                String reference = object.get("model").getAsString();
                int colon = reference.indexOf(':');
                String namespace = colon < 0 ? "spellcraft" : reference.substring(0, colon);
                String modelPath = colon < 0 ? reference : reference.substring(colon + 1);
                if ("spellcraft".equals(namespace)
                        && !Files.isRegularFile(ASSETS.resolve("models").resolve(modelPath + ".json"))) {
                    problems.add(blockstate + ": missing blockstate model " + reference);
                }
            }
            for (var entry : object.entrySet()) checkBlockstateModels(entry.getValue(), blockstate, problems);
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) checkBlockstateModels(child, blockstate, problems);
        }
    }

    private static void checkPng(Path path, String label, List<String> problems) {
        if (!Files.isRegularFile(path)) { problems.add(label + ": missing PNG " + path); return; }
        try {
            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) problems.add(label + ": invalid PNG");
        } catch (IOException | RuntimeException e) { problems.add(label + ": invalid PNG (" + e.getMessage() + ")"); }
    }

    private static void checkTextureDirectory(Path directory, boolean nativeGrid,
                                              List<String> problems) throws IOException {
        try (var files = Files.list(directory)) {
            for (Path texture : files.filter(p -> p.toString().endsWith(".png")).toList()) {
                try {
                    BufferedImage image = ImageIO.read(texture.toFile());
                    if (image == null) {
                        problems.add(texture + ": invalid PNG");
                        continue;
                    }
                    if (nativeGrid && (image.getWidth() != 16 || image.getHeight() != 16)) {
                        problems.add(texture + ": block texture must be 16x16, got "
                                + image.getWidth() + "x" + image.getHeight());
                    }
                    if (!nativeGrid && (image.getWidth() != image.getHeight()
                            || (image.getWidth() & (image.getWidth() - 1)) != 0)) {
                        problems.add(texture + ": item texture must be square power-of-two, got "
                                + image.getWidth() + "x" + image.getHeight());
                    }
                    if (!nativeGrid && image.getColorModel().hasAlpha()) {
                        boolean hasTransparentPixel = false;
                        for (int y = 0; y < image.getHeight() && !hasTransparentPixel; y++) {
                            for (int x = 0; x < image.getWidth(); x++) {
                                if ((image.getRGB(x, y) >>> 24) != 0xFF) {
                                    hasTransparentPixel = true;
                                    break;
                                }
                            }
                        }
                        if (!hasTransparentPixel) problems.add(texture + ": item texture has no transparent pixels");
                    }
                } catch (RuntimeException e) {
                    problems.add(texture + ": invalid PNG (" + e.getMessage() + ")");
                }
            }
        }
    }

    private static Set<String> duplicateTopLevelKeys(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        Set<String> seen = new HashSet<>(), duplicates = new LinkedHashSet<>();
        var matcher = Pattern.compile("(?m)^\\s*\\\"([^\\\"]+)\\\"\\s*:").matcher(text);
        while (matcher.find()) if (!seen.add(matcher.group(1))) duplicates.add(matcher.group(1));
        return duplicates;
    }

    private static Set<String> registered(Path source, Pattern pattern) throws IOException {
        Set<String> result = new LinkedHashSet<>();
        var matcher = pattern.matcher(Files.readString(source, StandardCharsets.UTF_8));
        while (matcher.find()) result.add(matcher.group(1));
        return result;
    }
    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private static boolean hasReferencedBlockModel(String id) throws IOException {
        JsonObject state = json(ASSETS.resolve("blockstates/" + id + ".json"));
        String text = state.toString();
        return text.contains("models/block/" + id) || text.contains("block/" + id);
    }
}
