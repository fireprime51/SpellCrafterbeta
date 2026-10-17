package com.github.fireprime5.spellcraft.assets;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Проверяет, что тестовая GeckoLib-модель поставлена полным комплектом ресурсов. */
class DaAnimatedAssetTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/spellcraft");

    @Test
    void modelAnimationAndTextureArePackaged() throws Exception {
        assertTrue(Files.isRegularFile(ASSETS.resolve("geo/da.geo.json")));
        assertTrue(Files.isRegularFile(ASSETS.resolve("animations/da.animation.json")));
        assertTrue(Files.isRegularFile(ASSETS.resolve("textures/entity/da.png")));

        var geometry = JsonParser.parseString(Files.readString(ASSETS.resolve("geo/da.geo.json")))
                .getAsJsonObject();
        assertTrue(geometry.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                .getAsJsonObject("description").get("identifier").getAsString().equals("geometry.da"));

        var animations = JsonParser.parseString(
                        Files.readString(ASSETS.resolve("animations/da.animation.json")))
                .getAsJsonObject().getAsJsonObject("animations");
        assertTrue(animations.has("animation"), "animation key must match the controller");
    }
}
