package com.github.fireprime5.spellcraft.registry;

import com.github.fireprime5.spellcraft.Spellcraft;
import com.github.fireprime5.spellcraft.item.SpellbookItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Spellcraft.MODID);

    /** Гримуар: им кастуют и в нём же открывают конструктор. */
    public static final Supplier<SpellbookItem> SPELLBOOK = ITEMS.register("spellbook",
            () -> new SpellbookItem(new Item.Properties().stacksTo(1)));

    /** Модульный нагрудник: схема модулей хранится в самом предмете. */
    public static final Supplier<com.github.fireprime5.spellcraft.armor.ModularChestplateItem>
            MODULAR_CHESTPLATE = ITEMS.register("modular_chestplate",
            () -> new com.github.fireprime5.spellcraft.armor.ModularChestplateItem(
                    new Item.Properties()));

    /**
     * Труба бездны: линия, а не блок.
     *
     * <p>Кладётся в блок сети так же, как провод: если под курсором уже стоит
     * блок с линиями, предмет добавляет туда ещё и трубу, иначе — ставит блок.
     * Отдельного блока трубы больше нет: он занимал целый блок на одну линию и
     * поэтому в одну грань машины влезал только один кабель.
     */
    /** Предмет-блок для бака бездны. */
    public static final Supplier<Item> ABYSS_TANK = ITEMS.register("abyss_tank",
            () -> new BlockItem(ModBlocks.ABYSS_TANK.get(), new Item.Properties()));

    /** Предмет тестового блока пользовательской GeckoLib-модели. */
    public static final Supplier<Item> DA_ANIMATED = ITEMS.register("da_animated",
            () -> new BlockItem(ModBlocks.DA_ANIMATED.get(), new Item.Properties()));

    /** Предмет-блок для транспортёра. */
    public static final Supplier<Item> CONVEYOR = ITEMS.register("conveyor",
            () -> new BlockItem(ModBlocks.CONVEYOR.get(), new Item.Properties()));

    /** Предмет-блок для плавильни. */
    public static final Supplier<Item> SMELTER = ITEMS.register("smelter",
            () -> new BlockItem(ModBlocks.SMELTER.get(), new Item.Properties()));

    /** Предмет-блок для сосуда энергии бездны. */
    public static final Supplier<Item> MANA_VESSEL = ITEMS.register("mana_vessel",
            () -> new BlockItem(ModBlocks.MANA_VESSEL.get(), new Item.Properties()));

    /** Предмет-блок для провода без выбранного цвета. */
    /** Труба жидкости: одна штука на все жидкости, вид узнаётся от соседа. */
    public static final Supplier<Item> FLUID_PIPE = ITEMS.register("fluid_pipe",
            () -> new FluidPipeItem(new Item.Properties().stacksTo(64)));

    public static final Supplier<Item> WIRE = ITEMS.register("wire",
            () -> new WireItem(net.minecraft.world.item.DyeColor.WHITE, new Item.Properties()));

    /**
     * Шестнадцать проводов — по одному на цвет.
     *
     * <p>Цвет и есть идентичность сети: провод соединяется только с проводом
     * своего цвета, поэтому рядом свободно идут несколько независимых линий.
     * Цвет выбирается предметом, а не свойством блока: перекрасить стоящий
     * провод нельзя, на секунду он отсоединился бы от всей своей сети.
     *
     * <p>Список нужен не для красоты: по нему проходятся механики, где цвет
     * выбирает игрок, а не id предмета. Порядок совпадает с порядком
     * {@link net.minecraft.world.item.DyeColor}, поэтому у провода всегда есть
     * «нулевой» цвет — белый.
     */
    public static final java.util.Map<net.minecraft.world.item.DyeColor, Supplier<Item>> WIRE_COLORS =
            new java.util.LinkedHashMap<>();

    /**
     * Запоминает провод за его цветом.
     *
     * <p>Сам предмет регистрируется на месте вызова, а не здесь: идентификатор
     * обязан быть литералом в выражении {@code ITEMS.register}, иначе проверка
     * реестра, вычитывающая id регуляркой из исходника, не найдёт провода — и
     * шестнадцать предметов останутся без модели, текстуры и перевода, то есть
     * без проверки вообще. Сначала был общий метод {@code провод(id, цвет)} с
     * переменной внутри, и реестр молча проверял ноль проводов из шестнадцати.
     */
    private static Supplier<Item> провод(net.minecraft.world.item.DyeColor цвет,
                                         Supplier<Item> предмет) {
        WIRE_COLORS.put(цвет, предмет);
        return предмет;
    }

    public static final Supplier<Item> WIRE_WHITE = провод(net.minecraft.world.item.DyeColor.WHITE,
            ITEMS.register("wire_white", () -> new WireItem(
                    net.minecraft.world.item.DyeColor.WHITE, new Item.Properties())));
    public static final Supplier<Item> WIRE_ORANGE = провод(net.minecraft.world.item.DyeColor.ORANGE,
            ITEMS.register("wire_orange", () -> new WireItem(
                    net.minecraft.world.item.DyeColor.ORANGE, new Item.Properties())));
    public static final Supplier<Item> WIRE_MAGENTA = провод(net.minecraft.world.item.DyeColor.MAGENTA,
            ITEMS.register("wire_magenta", () -> new WireItem(
                    net.minecraft.world.item.DyeColor.MAGENTA, new Item.Properties())));
    public static final Supplier<Item> WIRE_LIGHT_BLUE = провод(net.minecraft.world.item.DyeColor.LIGHT_BLUE,
            ITEMS.register("wire_light_blue", () -> new WireItem(
                    net.minecraft.world.item.DyeColor.LIGHT_BLUE, new Item.Properties())));
    public static final Supplier<Item> WIRE_YELLOW = провод(net.minecraft.world.item.DyeColor.YELLOW,
            ITEMS.register("wire_yellow", () -> new WireItem(
                    net.minecraft.world.item.DyeColor.YELLOW, new Item.Properties())));
    public static final Supplier<Item> WIRE_LIME = провод(net.minecraft.world.item.DyeColor.LIME,
            ITEMS.register("wire_lime", () -> new WireItem(
                    net.minecraft.world.item.DyeColor.LIME, new Item.Properties())));
    public static final Supplier<Item> WIRE_PINK = провод(net.minecraft.world.item.DyeColor.PINK,
            ITEMS.register("wire_pink", () -> new WireItem(
                    net.minecraft.world.item.DyeColor.PINK, new Item.Properties())));
    public static final Supplier<Item> WIRE_GRAY = провод(net.minecraft.world.item.DyeColor.GRAY,
            ITEMS.register("wire_gray", () -> new WireItem(
                    net.minecraft.world.item.DyeColor.GRAY, new Item.Properties())));
    public static final Supplier<Item> WIRE_LIGHT_GRAY = провод(net.minecraft.world.item.DyeColor.LIGHT_GRAY,
            ITEMS.register("wire_light_gray", () -> new WireItem(
                    net.minecraft.world.item.DyeColor.LIGHT_GRAY, new Item.Properties())));
    public static final Supplier<Item> WIRE_CYAN = провод(net.minecraft.world.item.DyeColor.CYAN,
            ITEMS.register("wire_cyan", () -> new WireItem(
                    net.minecraft.world.item.DyeColor.CYAN, new Item.Properties())));
    public static final Supplier<Item> WIRE_PURPLE = провод(net.minecraft.world.item.DyeColor.PURPLE,
            ITEMS.register("wire_purple", () -> new WireItem(
                    net.minecraft.world.item.DyeColor.PURPLE, new Item.Properties())));
    public static final Supplier<Item> WIRE_BLUE = провод(net.minecraft.world.item.DyeColor.BLUE,
            ITEMS.register("wire_blue", () -> new WireItem(
                    net.minecraft.world.item.DyeColor.BLUE, new Item.Properties())));
    public static final Supplier<Item> WIRE_BROWN = провод(net.minecraft.world.item.DyeColor.BROWN,
            ITEMS.register("wire_brown", () -> new WireItem(
                    net.minecraft.world.item.DyeColor.BROWN, new Item.Properties())));
    public static final Supplier<Item> WIRE_GREEN = провод(net.minecraft.world.item.DyeColor.GREEN,
            ITEMS.register("wire_green", () -> new WireItem(
                    net.minecraft.world.item.DyeColor.GREEN, new Item.Properties())));
    public static final Supplier<Item> WIRE_RED = провод(net.minecraft.world.item.DyeColor.RED,
            ITEMS.register("wire_red", () -> new WireItem(
                    net.minecraft.world.item.DyeColor.RED, new Item.Properties())));
    public static final Supplier<Item> WIRE_BLACK = провод(net.minecraft.world.item.DyeColor.BLACK,
            ITEMS.register("wire_black", () -> new WireItem(
                    net.minecraft.world.item.DyeColor.BLACK, new Item.Properties())));

    /** Предмет провода своего цвета: кладёт линию в блок сети. */
    private static final class WireItem extends com.github.fireprime5.spellcraft.network.NetworkItem {

        private WireItem(net.minecraft.world.item.DyeColor цвет, Item.Properties свойства) {
            super(ModBlocks.WIRE.get(), com.github.fireprime5.spellcraft.network.LineKind.ENERGY,
                    цвет, свойства);
        }
    }

    /** Предмет трубы жидкости: предмет один, жидкость узнаётся от соседа. */
    private static final class FluidPipeItem extends com.github.fireprime5.spellcraft.network.NetworkItem {

        private FluidPipeItem(Item.Properties свойства) {
            super(ModBlocks.WIRE.get(), com.github.fireprime5.spellcraft.network.LineKind.FLUID,
                    net.minecraft.world.item.DyeColor.WHITE, свойства);
        }
    }


    /** Предмет-блок для извлекателя энергии бездны. */
    public static final Supplier<Item> MANA_EXTRACTOR = ITEMS.register("mana_extractor",
            () -> new BlockItem(ModBlocks.MANA_EXTRACTOR.get(), new Item.Properties()));

    /**
     * Предмет-блок отладочного источника.
     *
     * <p>Регистрируется всегда, но попадает в креативную вкладку только при
     * {@code debugEnergySource} в конфиге. Иначе пришлось бы пересоздавать
     * реестр ради включения блока.
     */
    public static final Supplier<Item> DEBUG_ENERGY_SOURCE = ITEMS.register(
            "debug_energy_source",
            () -> new BlockItem(ModBlocks.DEBUG_ENERGY_SOURCE.get(), new Item.Properties()));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
