package com.github.fireprime5.spellcraft.palette;

import com.github.fireprime5.spellcraft.client.screen.ModuleFrame;
import com.github.fireprime5.spellcraft.client.screen.ModuleStyle;
import com.github.fireprime5.spellcraft.spell.SpellModuleType;
import com.github.fireprime5.spellcraft.ui.AwtSurface;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Лист вариантов палитры: один png со всеми шкалами категорий.
 *
 * <p>Сделано как тест, а не как отдельный скрипт, потому что рисунок модуля
 * должен быть ровно тем же кодом, что и в игре: {@link ModuleFrame#draw}
 * принимает акцент параметром, поэтому подставлять любые цвета можно, не
 * трогая саму палитру. Выбранный вариант переносится в
 * {@code tools/models/palette.json}, а этот тест либо удаляется, либо
 * остаётся как визуальный регресс.
 *
 * <p>Каркас (фон, панель, рамка) у всех вариантов общий -- он холодный по
 * решению из палитры, и менять его вместе с категориями незачем.
 */
class PaletteVariantPreviewTest {

    private static final int SCALE = 2;
    private static final int MOD_W = 44;
    private static final int MOD_H = 30;
    private static final int BAND = 70;
    private static final int LEFT = 214;
    private static final int STRIP = 0;

    /** Вариант для листа: тот же json, что читает игра. */
    private record Variant(String id, String label, int[] categories, int error) {

        int accent(SpellModuleType.Category category) {
            return categories[category.ordinal() % categories.length];
        }
    }

    private static List<Variant> variants() throws IOException {
        java.io.InputStream stream = PaletteVariantPreviewTest.class
                .getResourceAsStream("/assets/spellcraft/palette.json");
        assertNotNull(stream, "в jar нет assets/spellcraft/palette.json -- варианты не переключишь");
        com.google.gson.JsonObject root = com.google.gson.JsonParser
                .parseReader(new java.io.InputStreamReader(stream,
                        java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        com.google.gson.JsonObject section = root.getAsJsonObject("variants");
        List<Variant> out = new java.util.ArrayList<>();
        String[] order = {"numbers", "entities", "actions", "logic", "network"};
        for (String id : section.keySet()) {
            com.google.gson.JsonObject entry = section.getAsJsonObject(id);
            com.google.gson.JsonObject categories = entry.getAsJsonObject("categories");
            int[] ramp = new int[order.length];
            for (int i = 0; i < order.length; i++) {
                ramp[i] = 0xFF000000 | Integer.parseInt(
                        categories.get(order[i]).getAsString().replace("#", ""), 16);
            }
            out.add(new Variant(id, entry.get("label").getAsString(), ramp,
                    0xFF000000 | Integer.parseInt(
                            entry.get("error").getAsString().replace("#", ""), 16)));
        }
        assertTrue(out.size() >= 8, "в палитре должно быть не меньше восьми вариантов");
        return out;
    }

    private static String hex(int argb) {
        return String.format("#%06X", argb & 0xFFFFFF);
    }

    @Test
    void листВариантовПалитры() throws IOException {
        List<Variant> variants = variants();
        int width = LEFT + 5 * (MOD_W + 8) + 16;
        int height = 24 + variants.size() * BAND;
        BufferedImage image = new BufferedImage(width * SCALE, height * SCALE,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(Palette.BG, true));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.scale(SCALE, SCALE);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.setColor(new Color(Palette.TEXT, true));
        g.drawString("Palette variants - module frames", 8, 16);

        int y = 28;
        for (Variant variant : variants) {
            band(g, variant, y);
            y += BAND;
        }
        g.dispose();

        Path out = Path.of("build", "ui-preview", "palette-variants.png");
        Files.createDirectories(out.getParent());
        ImageIO.write(image, "png", out.toFile());
        assertTrue(Files.size(out) > 2000, "лист вариантов пустой");
    }

    private void band(Graphics2D g, Variant variant, int y) {
        g.setColor(new Color(Palette.PANEL, true));
        g.fillRect(4, y, LEFT + 5 * (MOD_W + 8) + 8, BAND - 6);
        g.setColor(new Color(Palette.BORDER, true));
        g.drawRect(4, y, LEFT + 5 * (MOD_W + 8) + 8, BAND - 6);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        g.setColor(new Color(Palette.TEXT, true));
        g.drawString(variant.id(), 10, y + 14);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 8));
        g.setColor(new Color(Palette.TEXT_DIM, true));
        g.drawString(variant.label(), 10, y + 26);
        g.setColor(new Color(variant.error(), true));
        g.fillRect(10, y + 32, 9, 9);
        g.setColor(new Color(Palette.TEXT_DIM, true));
        g.drawString("error " + hex(variant.error()), 23, y + 40);

        AwtSurface surface = new AwtSurface(g);
        int x = LEFT;
        for (SpellModuleType.Category category : SpellModuleType.Category.values()) {
            ModuleFrame.draw(surface, x, y + 6, MOD_W, MOD_H, variant.accent(category),
                    ModuleStyle.glyph(category), true);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 7));
            g.setColor(new Color(Palette.TEXT_DIM, true));
            g.drawString(category.name().toLowerCase(), x, y + 50);
            g.setColor(new Color(Palette.TEXT_DIM, true));
            g.drawString(hex(variant.accent(category)), x, y + 59);
            x += MOD_W + 8;
        }

        // рамка палитры: холодный каркас не меняется между вариантами
        g.setColor(new Color(Palette.ACCENT, true));
        g.fillRect(10, y + 46, 120, 5);
        g.setColor(new Color(Palette.BORDER_LIGHT, true));
        g.fillRect(10, y + 53, 120, 3);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 7));
        g.setColor(new Color(Palette.TEXT_DIM, true));
        g.drawString("chrome: fixed, cyan + graphite", 10, y + 64);
    }
}