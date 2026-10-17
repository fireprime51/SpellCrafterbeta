package com.github.fireprime5.spellcraft.assets;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверки спрайтов предметов.
 *
 * <p>Предметные текстуры рисуются тем же генератором, что и блочные, поэтому и
 * требования те же: файл существует, размер -- степень двойки. Про uv здесь
 * можно не думать: у {@code minecraft:item/generated} quad всегда занимает весь
 * спрайт ({@code uv = [0,0,16,16]}), независимо от разрешения.
 */
class ItemSpriteTest {

    private static final Path ITEMS = Path.of("src/main/resources/assets/spellcraft/textures/item");

    private static List<Path> sprites() throws IOException {
        try (var stream = Files.list(ITEMS)) {
            return stream.filter(path -> path.toString().endsWith(".png")).sorted().toList();
        }
    }

    private static boolean isPowerOfTwo(int value) {
        return value > 0 && (value & (value - 1)) == 0;
    }

    /**
     * У каждого спрайта должен быть слой в модели предмета, иначе он в игре просто
     * не покажется -- и никакой лог об этом не сообщает.
     */
    @Test
    void уКаждогоСпрайтаЕстьСлойВМодели() throws IOException {
        for (Path sprite : sprites()) {
            String id = sprite.getFileName().toString().replace(".png", "");
            Path model = Path.of("src/main/resources/assets/spellcraft/models/item/" + id + ".json");
            assertTrue(Files.exists(model), "нет модели предмета для " + id);
            String modelJson = Files.readString(model);
            boolean hasOwnSprite = modelJson.contains("spellcraft:item/" + id);
            boolean usesPlacedBlockGeometry = modelJson.contains("spellcraft:block/");
            assertTrue(hasOwnSprite || usesPlacedBlockGeometry,
                    "модель " + id + " не ссылается на спрайт или геометрию блока");
        }
    }

    /**
     * Размер спрайта -- степень двойки по обеим сторонам, см. BlockModelTest про
     * те же ограничения у блочных текстур.
     */
    @Test
    void размерыСпрайтовСтепениДвойки() throws IOException {
        List<Path> found = sprites();
        assertFalse(found.isEmpty(), "в textures/item нет ни одного спрайта");

        for (Path sprite : found) {
            BufferedImage image = ImageIO.read(sprite.toFile());
            assertNotNull(image, "не читается " + sprite);
            assertTrue(isPowerOfTwo(image.getWidth()),
                    sprite + ": ширина " + image.getWidth() + " не степень двойки");
            assertTrue(isPowerOfTwo(image.getHeight()),
                    sprite + ": высота " + image.getHeight() + " не степень двойки");
        }
    }

    /**
     * У спрайта предмета должна быть прозрачность вне силуэта.
     *
     * <p>Предмет рисуется на квадратном quad, поэтому альфа 255 на всех пикселях
     * даёт в игре тёмный квадрат с рамкой вместо вещи. Именно так выглядели все
     * три спрайта: альфа писалась как 255 везде, и проверить это было нечем.
     * Теперь генератор держит цвет и силуэт рядом ({@code Sprite}), а эта
     * проверка ловит возврат к прямоугольной заливке.
     */
    @Test
    void уПредметовЕстьПрозрачность() throws IOException {
        for (Path sprite : sprites()) {
            BufferedImage image = ImageIO.read(sprite.toFile());
            assertTrue(image.getColorModel().hasAlpha(),
                    sprite + ": нет альфа-канала, фон непрозрачен");
            int opaque = 0;
            int pixels = image.getWidth() * image.getHeight();
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getRGB(x, y) >>> 24) == 255) {
                        opaque++;
                    }
                }
            }
            double share = opaque / (double) pixels;
            assertTrue(share < 0.95,
                    sprite + ": непрозрачных " + Math.round(share * 100) + "% -- спрайт"
                            + " рисуется как тёмный квадрат, а не как предмет");
            assertTrue(share > 0.08,
                    sprite + ": непрозрачных всего " + Math.round(share * 100)
                            + "% -- силуэт почти пустой");
        }
    }

    /**
     * Спрайт не должен быть однотонной заглушкой: на 16x16 прошлом заглушке было
     * три цвета, и это выглядело как ошибка загрузки.
     */
    @Test
    void спрайтыНеЗаглушки() throws IOException {
        for (Path sprite : sprites()) {
            BufferedImage image = ImageIO.read(sprite.toFile());
            long colours = image.getRGB(0, 0, image.getWidth(), image.getHeight(),
                    null, 0, image.getWidth()).length;
            long distinct = java.util.Arrays.stream(
                            image.getRGB(0, 0, image.getWidth(), image.getHeight(),
                                    null, 0, image.getWidth()))
                    .distinct().count();
            assertTrue(colours > 0 && distinct >= 16,
                    sprite + ": " + distinct + " цветов из " + colours
                            + " -- похоже на однотонную заглушку");
        }
    }
}
