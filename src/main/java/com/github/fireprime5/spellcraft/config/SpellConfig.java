package com.github.fireprime5.spellcraft.config;

import com.github.fireprime5.spellcraft.Spellcraft;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Конфиг мода (config/spellcraft.json).
 *
 * <p>Один файл на всё: и параметры интерфейса (клиент), и баланс энергии бездны с
 * заклинаниями (сервер). Загружается на обеих сторонах — иначе серверные
 * значения не дойдут до вычислителя заклинаний.
 */
public final class SpellConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Путь к конфигу.
     *
     * <p>Раньше здесь был относительный {@code Path.of("config", "spellcraft.json")},
     * то есть путь от текущего рабочего каталога процесса. Он совпадает с
     * каталогом игры только при запуске из неё самой: сервер, поднятый
     * systemd, панелью хостинга или скриптом, писал бы конфиг в случайное
     * место, а читать его потом было бы негде. Поэтому берётся настоящий
     * каталог конфигов NeoForge, а на его отсутствие (тесты, запуск до
     * инициализации FML) — безопасная запасная папка рядом с classpath.
     */
    private static Path resolvePath() {
        try {
            return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()
                    .resolve(Spellcraft.MODID + ".json");
        } catch (RuntimeException e) {
            return Path.of("config", Spellcraft.MODID + ".json");
        }
    }

    private static final Path PATH = resolvePath();

    private static volatile SpellConfig instance = new SpellConfig();
    private static volatile boolean loaded;

    // ------------------------------------------------------------ интерфейс

    /** Прозрачность панелей интерфейса: 0 = прозрачно, 1 = непрозрачно. */
    public float uiOpacity = 0.72f;

    /** Показывать бесконечную сетку. */
    public boolean infiniteGrid = true;

    /** Масштаб сетки при открытии интерфейса. */
    public float defaultZoom = 1.0f;

    /** Закреплённая позиция палитры модулей (-1 = использовать по умолчанию). */
    public int paletteX = -1;
    public int paletteY = -1;

    /**
     * Размер палитры модулей: 0 — компактная, дальше больше строк и шире панель.
     *
     * <p>Масштабируется раскладка, а не шрифт: {@code pose().scale()} мылит
     * текст (см. docs/ui-notes.md), поэтому «больше» означает «больше модулей
     * видно сразу», а не «крупнее буквы». Кламп живёт в {@code ModulePalette},
     * здесь хранится только выбранный уровень.
     */
    public int paletteSizeLevel = 0;

    /** Потолок размера палитры; дублируется здесь ради конфига и подсказок. */
    public static final int MAX_PALETTE_SIZE_LEVEL = 2;

    /**
     * Вариант шкалы категорий модулей (см. assets/spellcraft/palette.json, раздел
     * variants). Меняется клавишей прямо в игре, значение переживает перезапуск.
     */
    public String modulePalette = "current";

    /**
     * Базовый оттенок шкалы в градусах (0 = красный). Если не меньше нуля, он
     * побеждает modulePalette: шкала строится из одного цвета.
     */
    public int moduleHue = -1;

    // ---------------------------------------------------------------- энергия бездны

    /** Ёмкость кошелька игрока (энергия бездны). */
    public int playerEnergyCapacity = 1000;

    /**
     * Выдавать ли отладочный источник энергии.
     *
     * <p>Генератора энергии в моде нет и не планируется: источник нужен, чтобы
     * проверять машины без Mekanism. По умолчанию выключен — в обычной игре
     * блок, производящий энергию из воздуха, был бы эксплойтом. Сама
     * способность блока от этого не зависит: выключается только выдача
     * предмета, иначе проверка требовала бы перезапуска мода.
     */
    public boolean debugEnergySource = false;

    public static SpellConfig get() {
        return instance;
    }

    public static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            if (Files.exists(PATH)) {
                SpellConfig loadedConfig = GSON.fromJson(Files.readString(PATH), SpellConfig.class);
                if (loadedConfig != null) {
                    // clamp обязателен и для свежего файла: его могли править
                    // руками, и нулевая ёмкость энергии бездны или зум 0 сделали бы
                    // панель нерабочей без единой строки в логе
                    loadedConfig.clampValues();
                    instance = loadedConfig;
                    Spellcraft.LOGGER.info(
                            "Loaded config from {}: opacity={}, infiniteGrid={}, zoom={}, manaCap={}",
                            PATH, loadedConfig.uiOpacity, loadedConfig.infiniteGrid,
                            loadedConfig.defaultZoom, loadedConfig.playerEnergyCapacity);
                    if (loadedConfig.debugEnergySource) {
                        Spellcraft.LOGGER.warn(
                                "debugEnergySource is ON: machines can be fed by an "
                                        + "infinite source. This is for testing only.");
                    }
                    return;
                }
            }
        } catch (IOException | RuntimeException e) {
            Spellcraft.LOGGER.warn("Failed to read config, using defaults", e);
        }
        instance.clampValues();
        instance.save();
    }

    public void save() {
        try {
            Path parent = PATH.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(PATH, GSON.toJson(this));
        } catch (IOException e) {
            Spellcraft.LOGGER.warn("Failed to write config", e);
        }
    }

    public void clampValues() {
        uiOpacity = Float.isFinite(uiOpacity) ? clamp(uiOpacity, 0.1f, 1.0f) : 0.72f;
        defaultZoom = Float.isFinite(defaultZoom) ? clamp(defaultZoom, MIN_ZOOM, MAX_ZOOM) : 1.0f;
        // ёмкость — целое, NaN/бесконечность сюда не попадут: хватает обычного
        // ограничения диапазоном
        playerEnergyCapacity = clamp(playerEnergyCapacity, 0, 1_000_000);
        // размер палитры: в конфиг могли вписать что угодно, а отрицательный
        // уровень уронил бы панель в ноль
        paletteSizeLevel = clamp(paletteSizeLevel, 0, MAX_PALETTE_SIZE_LEVEL);
    }

    private static float clamp(float value, float min, float max) {
        return Math.clamp(value, min, max);
    }

    private static int clamp(int value, int min, int max) {
        return Math.clamp(value, min, max);
    }

    public static final float MIN_ZOOM = 0.45f;
    public static final float MAX_ZOOM = 3.0f;
}