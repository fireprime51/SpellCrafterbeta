package com.github.fireprime5.spellcraft.assets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Модели блоков разбираются и ссылаются на существующие текстуры.
 *
 * <p>Единственный способ узнать, что модель не загрузится, — разобрать её самому.
 * Ошибка стоила фиолетово-чёрной шахматки вместо провода: у элементов не было
 * граней, Minecraft отказывался грузить модель целиком, а игра подставляла
 * заглушку. Модель при этом была валидным json, и никакой другой тест этого
 * не показывал.
 */
class BlockModelTest {

    private static final Path ASSETS = Path.of(
            "src/main/resources/assets");

    private static final List<String> problems = new ArrayList<>();

    @Test
    void уКаждойМоделиЕстьГраниИТекстуры() throws IOException {
        problems.clear();
        try (var files = Files.list(ASSETS.resolve("spellcraft/models/block"))) {
            for (Path model : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                check(model);
            }
        }
        assertTrue(problems.isEmpty(), "модели сломаны:\n" + String.join("\n", problems));
    }

    private static void check(Path model) throws IOException {
        String name = model.getFileName().toString();
        JsonObject root = JsonParser.parseString(
                Files.readString(model, StandardCharsets.UTF_8)).getAsJsonObject();

        JsonArray elements = root.getAsJsonArray("elements");
        if (elements == null) {
            // элементов может не быть: у пустого блока вроде портала хватает
            // родителя, но у модели с элементами отсутствие граней фатально
            return;
        }
        for (JsonElement element : elements) {
            JsonObject box = element.getAsJsonObject();
            if (box.getAsJsonObject("faces") == null
                    || box.getAsJsonObject("faces").isEmpty()) {
                problems.add(name + ": элемент без граней — модель не загрузится");
            }
            if (!box.has("from") || !box.has("to")) {
                problems.add(name + ": элемент без from/to");
            }
        }

        JsonObject textures = root.getAsJsonObject("textures");
        if (textures == null) {
            return;
        }
        for (String key : textures.keySet()) {
            String reference = textures.get(key).getAsString();
            if (reference.startsWith("#")) {
                continue;
            }
            // ссылка вида spellcraft:block/wire: это пространство имён и путь
            // через двоеточие, а не путь от assets
            int colon = reference.indexOf(':');
            String namespace = colon < 0 ? "spellcraft" : reference.substring(0, colon);
            String path = colon < 0 ? reference : reference.substring(colon + 1);
            Path texture = ASSETS.resolve(namespace).resolve("textures")
                    .resolve(path + ".png");
            if (!Files.exists(texture)) {
                problems.add(name + ": нет текстуры " + reference + " -> " + texture);
            }
        }
    }
}