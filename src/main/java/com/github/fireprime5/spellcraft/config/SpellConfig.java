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
 * <p>Один файл на всё: и параметры интерфейса (клиент), и баланс маны с
 * заклинаниями (сервер). Загружается на обеих сторонах — иначе серверные
 * значения не дойдут до вычислителя заклинаний.
 */
public final class SpellConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = Path.of("config", "spellcraft.json");

    private static SpellConfig instance = new SpellConfig();
    private static boolean loaded;

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
     * Вариант шкалы категорий модулей (см. assets/spellcraft/palette.json, раздел
     * variants). Меняется клавишей прямо в игре, значение переживает перезапуск.
     */
    public String modulePalette = "current";

    /**
     * Базовый оттенок шкалы в градусах (0 = красный). Если не меньше нуля, он
     * побеждает modulePalette: шкала строится из одного цвета.
     */
    public int moduleHue = -1;

    // ---------------------------------------------------------------- мана

    /** Ёмкость кошелька игрока (мана). */
    public int playerEnergyCapacity = 1000;

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
                    loadedConfig.clampValues();
                    instance = loadedConfig;
                    Spellcraft.LOGGER.info(
                            "Loaded config: opacity={}, infiniteGrid={}, zoom={}, manaCap={}",
                            loadedConfig.uiOpacity, loadedConfig.infiniteGrid,
                            loadedConfig.defaultZoom, loadedConfig.playerEnergyCapacity);
                    return;
                }
            }
        } catch (IOException | RuntimeException e) {
            Spellcraft.LOGGER.warn("Failed to read config, using defaults", e);
        }
        instance.save();
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this));
        } catch (IOException e) {
            Spellcraft.LOGGER.warn("Failed to write config", e);
        }
    }

    public void clampValues() {
        uiOpacity = clamp(uiOpacity, 0.1f, 1.0f);
        defaultZoom = clamp(defaultZoom, MIN_ZOOM, MAX_ZOOM);
        playerEnergyCapacity = clamp(playerEnergyCapacity, 0, 1_000_000);
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