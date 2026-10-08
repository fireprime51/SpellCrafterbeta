package com.github.fireprime5.spellcraft.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Конфиг: значения по умолчанию должны быть рабочими, а мусор из файла —
 * обрезаться до допустимых границ.
 */
class SpellConfigTest {

    @Test
    void дефолтыСоответствуютБалансу() {
        SpellConfig config = new SpellConfig();
        assertEquals(1000, config.playerEnergyCapacity);
        assertEquals(0.72f, config.uiOpacity);
        assertTrue(config.infiniteGrid);
        assertEquals(1.0f, config.defaultZoom);
        assertEquals(-1, config.paletteX, "палитра не закреплена: встанет по умолчанию");
        assertEquals(-1, config.paletteY);
        assertEquals("current", config.modulePalette);
        assertEquals(-1, config.moduleHue, "свободный оттенок не выбран: берётся вариант");
    }

    @Test
    void мусорИзФайлаОбрезается() {
        SpellConfig config = new SpellConfig();
        config.playerEnergyCapacity = -50;
        config.defaultZoom = 100f;
        config.uiOpacity = 5f;

        config.clampValues();

        assertEquals(0, config.playerEnergyCapacity, "отрицательная ёмкость невозможна");
        assertEquals(SpellConfig.MAX_ZOOM, config.defaultZoom);
        assertEquals(1.0f, config.uiOpacity);
    }

    @Test
    void нулевойПотолокМаныДопустим() {
        // потолок 0 -- это не «сломанный конфиг», а осознанный выбор сервера:
        // заклинания перестают стоить ману совсем
        SpellConfig config = new SpellConfig();
        config.playerEnergyCapacity = 0;
        config.clampValues();
        assertEquals(0, config.playerEnergyCapacity);

        config.playerEnergyCapacity = 1_000_001;
        config.clampValues();
        assertEquals(1_000_000, config.playerEnergyCapacity);
    }

    @Test
    void прозрачностьНеПрозрачнееЕдиницы() {
        SpellConfig config = new SpellConfig();
        config.uiOpacity = 0.0f;
        config.clampValues();
        assertEquals(0.1f, config.uiOpacity, "полностью прозрачные панели нечитаемы");
        assertTrue(config.uiOpacity >= 0.1f && config.uiOpacity <= 1.0f);
    }
}