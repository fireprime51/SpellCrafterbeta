package com.github.fireprime5.spellcraft.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.item.DyeColor;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Цветные провода: предмет, ассеты, рецепт.
 *
 * <p>Провод без цвета не годится: сеть определяется цветом, и без шестнадцати
 * предметов линии рядом не различить. Проверяется всё, чем игрок пользуется: id в
 * реестре, цвет в переводе, спрайт своего оттенка, место во вкладке и рецепт
 * крашения.
 *
 * <p>Код читается по исходнику, а не вызывается: {@code BlockPlaceContext} и
 * {@code Level} в тестах не поднимаются, а ставить блок в мире ради проверки
 * цвета нельзя. Регулярки вытаскивают именно литеральный {@code ITEMS.register} —
 * так же, как это делает проверка реестра ассетов: идентификатор, собранный
 * конкатенацией, там не виден, и шестнадцать проводов остались бы без проверки
 * вообще.
 */
class WireColorItemsTest {

    private static final Path MAIN = Path.of("src/main");
    private static final Path ASSETS = MAIN.resolve("resources/assets/spellcraft");
    private static final Path RECIPES = MAIN.resolve("resources/data/spellcraft/recipe");
    private static final Path MOD_ITEMS =
            MAIN.resolve("java/com/github/fireprime5/spellcraft/registry/ModItems.java");
    private static final Path MOD_TABS =
            MAIN.resolve("java/com/github/fireprime5/spellcraft/registry/ModCreativeTabs.java");
    private static final Path LOOT =
            MAIN.resolve("resources/data/spellcraft/loot_table/blocks/wire.json");

    /** Насыщенность, ниже которой оттенок считать бессмысленным. */
    private static final float CHROMA = 0.15f;

    /** Допуск по оттенку в градусах: спрайт приглушён, но не перекрашен. */
    private static final float HUE_TOLERANCE = 12f;

    /** Зазор по яркости между серыми проводами, иначе сравнение случайно. */
    private static final float LUMA_GAP = 0.02f;

    /** Доля непрозрачных пикселей, при которой спрайт ещё считается предметом. */
    private static final double MIN_OPAQUE = 0.08;
    private static final double MAX_OPAQUE = 0.95;

    /**
     * Шестнадцать предметов — по одному на цвет {@link DyeColor}.
     *
     * <p>Не хватает одного цвета — и в этом цвете нельзя построить сеть: рядом
     * с чужой линией она не отличится. Плюс проверяется, что id записан
     * литералом: собранная конкатенацией регистрация выглядит в исходнике
     * одинаково, а проверка реестра ассетов её не видит.
     */
    @Test
    void вРеестреШестнадцатьПроводовПоОдномуНаЦвет() throws IOException {
        String source = Files.readString(MOD_ITEMS, StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("ITEMS\\.register\\(\"(wire_[a-z_]+)\"")
                .matcher(source);
        Set<String> ids = new LinkedHashSet<>();
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        Set<String> ожидаемые = new LinkedHashSet<>();
        for (DyeColor цвет : DyeColor.values()) {
            ожидаемые.add("wire_" + цвет.getSerializedName());
        }
        assertEquals(ожидаемые, ids,
                "провода должны регистрироваться литеральными id, по одному на "
                        + "каждый цвет DyeColor");
    }

    /**
     * У каждого предмета своё поле и свой цвет.
     *
     * <p>Ошибка с самыми последствиями здесь — предмет, зарегистрированный под
     * чужим id: в инвентаре «синий», а ставит красный блок, и расхождение
     * замечаешь только в руках. Поэтому сверяется и пара «поле + цвет», и то,
     * что в конструктор уходит тот же цвет, что и в имя.
     */
    @Test
    void каждыйПредметПомнитСвойЦвет() throws IOException {
        String source = Files.readString(MOD_ITEMS, StandardCharsets.UTF_8);
        List<String> проблемы = new ArrayList<>();
        for (DyeColor цвет : DyeColor.values()) {
            String имя = цвет.getSerializedName();
            String КОЛЕКСИЯ = цвет.name();
            Pattern искомое = Pattern.compile("WIRE_" + КОЛЕКСИЯ
                    + "\\s*=\\s*провод\\([^)]*DyeColor\\." + КОЛЕКСИЯ
                    + "\\s*,\\s*ITEMS\\.register\\(\"wire_" + имя
                    + "\"[\\s\\S]{0,240}?DyeColor\\." + КОЛЕКСИЯ);
            if (!искомое.matcher(source).find()) {
                проблемы.add("wire_" + имя + ": нет поля WIRE_" + КОЛЕКСИЯ
                        + " с id wire_" + имя + " и цветом DyeColor." + КОЛЕКСИЯ);
            }
        }
        assertTrue(проблемы.isEmpty(), String.join("\n", проблемы));
    }

    /**
     * Предмет кладёт линию своего цвета, а не блок с чужим.
     *
     * <p>Проверяется по исходнику: {@code BlockPlaceContext} и {@code Level} в
     * тестах не поднимаются. Смысл проверки — в том, что цвет приходит из поля
     * предмета и что предмет умеет докладывать линию в уже стоящий блок сети
     * вместо того, чтобы поставить рядом второй блок.
     */
    @Test
    void предметКладётЛиниюСвоегоЦвета() throws IOException {
        String source = Files.readString(MOD_ITEMS, StandardCharsets.UTF_8);
        assertTrue(source.contains("class WireItem"),
                "в ModItems нет предмета-линии WireItem");
        int начало = source.indexOf("class WireItem");
        assertTrue(source.substring(начало).contains("LineKind.ENERGY"),
                "предмет энергетической трубы должен класть энергетическую линию");
        assertTrue(source.contains("class FluidPipeItem")
                        && source.contains("LineKind.FLUID"),
                "предмет жидкостной трубы должен класть линию жидкости");

        String линия = Files.readString(Path.of(
                "src/main/java/com/github/fireprime5/spellcraft/network/NetworkItem.java"),
                StandardCharsets.UTF_8);
        assertTrue(линия.contains("hasFreeSlot") && линия.contains("сеть.add("),
                "предмет должен докладывать линию в свободный слот блока сети");
        assertTrue(линия.contains("super.useOn"),
                "если слотов нет или такая линия уже есть, предмет ставит блок");
        int place = линия.indexOf("private InteractionResult placeWithLine(");
        assertTrue(place >= 0,
                "предмет должен ставить блок сети сам, а не через BlockItem: "
                        + "через place линия в блок не попадала");
        String установка = линия.substring(place, линия.indexOf("\n    /** Можно ли", place));
        assertTrue(установка.contains("сеть.add(kind, color)"),
                "новый блок должен вставать сразу с линией: иначе игрок увидит "
                        + "блок сети без единой линии и решит, что провод сломан");
        assertTrue(установка.contains("сеть.sync("),
                "после установки клиент должен получить блок заново");
        assertTrue(установка.contains("removeBlock"),
                "если блок-сущность не создалась, блок нужно убрать: иначе он "
                        + "держит место, но пуст и не рисуется");
        assertTrue(линия.contains("сеть.sync("),
                "после добавления линии клиент должен получить блок заново, иначе "
                        + "он продолжит рисовать старый");
    }

    /**
     * У каждого цвета есть имя в обоих языках.
     *
     * <p>Переводы обязаны отличаться: иначе в русском файле лежит английская
     * строка, и игрок читает в инвентаре {@code Red Wire}.
     */
    @Test
    void уЦветныхПроводовЕстьПереводВОбоихЯзыках() throws IOException {
        JsonObject english = json(ASSETS.resolve("lang/en_us.json"));
        JsonObject russian = json(ASSETS.resolve("lang/ru_ru.json"));
        List<String> проблемы = new ArrayList<>();
        for (DyeColor цвет : DyeColor.values()) {
            String ключ = "item.spellcraft.wire_" + цвет.getSerializedName();
            String en = english.has(ключ) ? english.get(ключ).getAsString() : "";
            String ru = russian.has(ключ) ? russian.get(ключ).getAsString() : "";
            if (en.isBlank()) {
                проблемы.add(ключ + ": нет английского перевода");
            }
            if (ru.isBlank()) {
                проблемы.add(ключ + ": нет русского перевода");
            }
            if (!en.isBlank() && en.equals(ru)) {
                проблемы.add(ключ + ": русский перевод совпадает с английским");
            }
        }
        assertTrue(проблемы.isEmpty(), String.join("\n", проблемы));
    }

    /**
     * У каждого цвета есть и модель, и спрайт.
     *
     * <p>Модель предмета берёт геометрию и текстуру блока, поэтому спрайт
     * предмета — запасной: без него предмет всё равно нарисуется, и игрок
     * решит, что всё в порядке, хотя проверить цвет нечем.
     */
    @Test
    void уЦветныхПроводовЕстьМодельИСпрайт() throws IOException {
        List<String> проблемы = new ArrayList<>();
        for (DyeColor цвет : DyeColor.values()) {
            String id = "wire_" + цвет.getSerializedName();
            Path модель = ASSETS.resolve("models/item/" + id + ".json");
            Path спрайт = ASSETS.resolve("textures/item/" + id + ".png");
            if (!Files.isRegularFile(модель)) {
                проблемы.add(id + ": нет модели предмета");
            } else if (!Files.readString(модель, StandardCharsets.UTF_8).contains(id)) {
                проблемы.add(id + ": модель не упоминает свой цвет");
            }
            if (!Files.isRegularFile(спрайт)) {
                проблемы.add(id + ": нет спрайта предмета");
            }
        }
        assertTrue(проблемы.isEmpty(), String.join("\n", проблемы));
    }

    /**
     * Спрайт покрашен в цвет своего провода.
     *
     * <p>Шестнадцать файлов с одинаковой картинкой выглядели бы как один провод в
     * шестнадцати обёртках: в мире блоки разные, а в инвентаре одинаковые, и
     * выбрать нужный невозможно. Поэтому меряется оттенок спрайта и сверяется с
     * ванильным красителем.
     *
     * <p>У чёрного, серого, светло-серого и белого оттенка нет — красители серые.
     * Их порядок проверяется по яркости: чёрный обязан быть самым тёмным
     * проводом, белый — самым светлым.
     */
    @Test
    void спрайтПроводаСвоимЦветом() throws IOException {
        List<String> проблемы = new ArrayList<>();
        for (DyeColor цвет : DyeColor.values()) {
            int краситель = цвет.getTextureDiffuseColor();
            float[] спрайт = среднийТон(ASSETS.resolve(
                    "textures/item/wire_" + цвет.getSerializedName() + ".png"));
            float[] образец = Color.RGBtoHSB((краситель >> 16) & 0xFF,
                    (краситель >> 8) & 0xFF, краситель & 0xFF, null);
            if (образец[1] < CHROMA || спрайт[1] < CHROMA) {
                continue;
            }
            float разница = Math.abs(спрайт[0] - образец[0]) * 360f;
            разница = Math.min(разница, 360f - разница);
            if (разница > HUE_TOLERANCE) {
                проблемы.add("wire_" + цвет.getSerializedName()
                        + ": оттенок спрайта отличается от красителя на "
                        + Math.round(разница) + "°");
            }
        }
        List<String> серые = List.of("black", "gray", "light_gray", "white");
        float[] яркости = new float[серые.size()];
        for (int i = 0; i < серые.size(); i++) {
            яркости[i] = среднийТон(ASSETS.resolve(
                    "textures/item/wire_" + серые.get(i) + ".png"))[2];
        }
        for (int i = 1; i < яркости.length; i++) {
            if (яркости[i - 1] >= яркости[i] - LUMA_GAP) {
                проблемы.add("wire_" + серые.get(i - 1) + " и wire_" + серые.get(i)
                        + ": серые провода не различаются по яркости");
            }
        }
        assertTrue(проблемы.isEmpty(), String.join("\n", проблемы));
    }

    /**
     * Спрайт предмета не рисуется прямоугольником.
     *
     * <p>Предмет рисуется на квадратном quad: спрайт без прозрачности
     * превращается в тёмный квадрат с рамкой, и в инвентаре провода не видно
     * вовсе.
     */
    @Test
    void спрайтПроводаНеПрямоугольник() throws IOException {
        List<String> проблемы = new ArrayList<>();
        for (DyeColor цвет : DyeColor.values()) {
            Path спрайт = ASSETS.resolve("textures/item/wire_"
                    + цвет.getSerializedName() + ".png");
            BufferedImage image = ImageIO.read(спрайт.toFile());
            assertNotNull(image, "не читается " + спрайт);
            int непрозрачных = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getRGB(x, y) >>> 24) == 0xFF) {
                        непрозрачных++;
                    }
                }
            }
            double доля = непрозрачных / (double) (image.getWidth() * image.getHeight());
            if (доля <= MIN_OPAQUE || доля >= MAX_OPAQUE) {
                проблемы.add(спрайт + ": непрозрачных " + Math.round(доля * 100)
                        + "% — это не провод, а плитка");
            }
        }
        assertTrue(проблемы.isEmpty(), String.join("\n", проблемы));
    }

    /**
     * Цветные проводы видны в креативной вкладке.
     *
     * <p>Предмет, которого нет во вкладке, для игрока не существует: добыть его
     * нечем, а рецепт есть. Поэтому каждое поле {@code WIRE_*} должно попасть в
     * вкладку — так это и проверяется, по исходнику {@code ModCreativeTabs}.
     */
    @Test
    void цветныеПроводыВидныВКреативнойВкладке() throws IOException {
        String source = Files.readString(MOD_TABS, StandardCharsets.UTF_8);
        List<String> проблемы = new ArrayList<>();
        for (DyeColor цвет : DyeColor.values()) {
            String поле = "ModItems.WIRE_" + цвет.name() + ".get()";
            if (!source.contains(поле)) {
                проблемы.add("вкладка не выводит " + поле);
            }
        }
        assertTrue(проблемы.isEmpty(), String.join("\n", проблемы));
    }

    /**
     * У каждого цвета есть рецепт крашения, а у бесцветного провода — свой.
     *
     * <p>Красится провод, а не «выбирается цвет при установке»: перекраска уже
     * стоящего провода оторвала бы его от сети на секунду. Рецепт красит предмет,
     * поэтому и проверяется, что на выходе именно провод этого цвета и что в
     * середине схемы лежит нужный краситель — иначе «красный» провод красился бы
     * синим.
     *
     * <p>Число проводов на выходе обязано совпадать с числом проводов в схеме:
     * четыре провода и краситель дают четыре провода. Рецепт, где из провода
     * делается больше, чем было, — генератор.
     */
    @Test
    void уЦветныхПроводовЕстьРецептКрашения() throws IOException {
        JsonObject базовый = json(RECIPES.resolve("wire.json"));
        assertEquals("minecraft:crafting_shaped", базовый.get("type").getAsString(),
                "базовый провод должен вариться по схеме");
        assertEquals("spellcraft:wire",
                базовый.getAsJsonObject("result").get("id").getAsString(),
                "базовый рецепт даёт бесцветный провод");

        List<String> проблемы = new ArrayList<>();
        for (DyeColor цвет : DyeColor.values()) {
            String id = "wire_" + цвет.getSerializedName();
            Path путь = RECIPES.resolve(id + ".json");
            if (!Files.isRegularFile(путь)) {
                проблемы.add(id + ": нет рецепта");
                continue;
            }
            JsonObject рецепт = json(путь);
            String результат = рецепт.getAsJsonObject("result").get("id").getAsString();
            if (!("spellcraft:" + id).equals(результат)) {
                проблемы.add(id + ": рецепт даёт " + результат);
            }
            int количество = рецепт.getAsJsonObject("result").get("count").getAsInt();
            int съедено = countIngredient(рецепт, "spellcraft:wire");
            if (количество != съедено) {
                проблемы.add(id + ": рецепт делает " + количество
                        + " проводов из " + съедено + " — это дюп или потеря");
            }
            String краситель = "minecraft:" + цвет.getSerializedName() + "_dye";
            String в_середине = в_середине(рецепт);
            if (!краситель.equals(в_середине)) {
                проблемы.add(id + ": в середине схемы " + в_середине
                        + ", а нужен краситель " + краситель);
            }
        }
        assertTrue(проблемы.isEmpty(), String.join("\n", проблемы));
    }

    /**
     * Разрушенный блок роняет все линии, которые в нём жили.
     *
     * <p>Лут-таблица знает только про блок и ничего не знает про линии: раньше
     * цвет хранился в NBT предмета, теперь в блоке до трёх линий разных цветов,
     * и таблица роняла бы одну из них, а остальные исчезали бы вместе с блоком.
     * Роняет их сам блок, по одной штуке на линию.
     */
    @Test
    void разрушенныйБлокРоняетВсеЛинии() throws IOException {
        String таблица = Files.readString(LOOT, StandardCharsets.UTF_8);
        JsonObject лут = JsonParser.parseString(таблица).getAsJsonObject();
        assertTrue(лут.getAsJsonArray("pools").isEmpty(),
                "лут-таблица блока сети должна быть пустой: предметы роняет блок");

        String блок = Files.readString(Path.of(
                "src/main/java/com/github/fireprime5/spellcraft/network/WireBlock.java"),
                StandardCharsets.UTF_8);
        assertTrue(блок.contains("dropLines") && блок.contains("NetworkItems.stack"),
                "блок должен ронять предметы своих линий при разрушении");
        String разрушение = блок.substring(блок.indexOf("protected void onRemove"));
        assertTrue(разрушение.contains("for (NetworkLine линия : сеть.lines())"),
                "роняться должна каждая линия блока, а не одна");
    }

    /** Сколько раз предмет уходит в рецепт: столько же проводов и выходит. */
    private static int countIngredient(JsonObject рецепт, String предмет) {
        int всего = 0;
        for (var строка : рецепт.getAsJsonArray("pattern")) {
            for (char символ : строка.getAsString().toCharArray()) {
                if (символ != ' ' && предмет.equals(
                        рецепт.getAsJsonObject("key").getAsJsonObject(String.valueOf(символ))
                                .get("item").getAsString())) {
                    всего++;
                }
            }
        }
        return всего;
    }

    /** Что стоит в середине схемы: там краситель. */
    private static String в_середине(JsonObject рецепт) {
        String средняя = рецепт.getAsJsonArray("pattern")
                .get(рецепт.getAsJsonArray("pattern").size() / 2).getAsString();
        char символ = средняя.charAt(средняя.length() / 2);
        return рецепт.getAsJsonObject("key").getAsJsonObject(String.valueOf(символ))
                .get("item").getAsString();
    }

    /**
     * Средний цвет спрайта по непрозрачным пикселям: тон, насыщенность, яркость.
     *
     * <p>Прозрачные пиксели не считаются: они ничего не говорят о краске и тянули
     * бы тон к чёрному.
     */
    private static float[] среднийТон(Path спрайт) throws IOException {
        BufferedImage image = ImageIO.read(спрайт.toFile());
        assertNotNull(image, "не читается " + спрайт);
        long красный = 0;
        long зелёный = 0;
        long синий = 0;
        long пикселей = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int пиксель = image.getRGB(x, y);
                if ((пиксель >>> 24) != 0xFF) {
                    continue;
                }
                красный += (пиксель >> 16) & 0xFF;
                зелёный += (пиксель >> 8) & 0xFF;
                синий += пиксель & 0xFF;
                пикселей++;
            }
        }
        assertTrue(пикселей > 0, "у " + спрайт + " нет ни одного непрозрачного пикселя");
        return Color.RGBtoHSB((int) (красный / пикселей), (int) (зелёный / пикселей),
                (int) (синий / пикселей), null);
    }

    private static JsonObject json(Path путь) throws IOException {
        return JsonParser.parseString(Files.readString(путь, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }
}
