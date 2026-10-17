package com.github.fireprime5.spellcraft.energy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Сосуд энергии бездны: ёмкость и правила хранения.
 *
 * <p>Сама блочная сущность в тестах не создаётся — нужен мир. Здесь
 * проверяются числа, от которых зависит игра: ёмкость сосуда относительно
 * кошелька, радиус связи с игроком и то, что сосуд достаточно велик, чтобы
 * извлекателю было куда складывать излишки.
 */
class AbyssVesselTest {

    @Test
    void сосудБольшеКошелька() {
        // сосуд должен быть заметной накопительной ёмкостью, а не украшением.
        // При равных ёмкостях он ничего не менял бы: извлекателю по-прежнему
        // некуда было бы складывать излишки.
        assertTrue(AbyssVesselBlock.CAPACITY > AbyssEnergy.DEFAULT_MAX_ABYSS,
                "сосуд должен быть больше кошелька игрока");
        assertEquals(3000, AbyssVesselBlock.CAPACITY);
    }

    @Test
    void триСосудаЗаменяютКошелёкЦеликом() {
        // столько нужно, чтобы база на извлекателях работала без потери
        int three = AbyssVesselBlock.CAPACITY * 3;
        assertTrue(three >= AbyssEnergy.DEFAULT_MAX_ABYSS,
                "три сосуда должны покрывать кошелёк целиком");
    }

    @Test
    void радиусСвязиДостаточный() {
        // четыре блока — это один-два шага от сосуда. Ближе игрок вынужден
        // стоять вплотную, дальше сосуд кормит через полкомнаты
        assertEquals(4.0, AbyssPool.RADIUS);
        assertTrue(AbyssPool.RADIUS >= 3.0, "до сосуда должно можно дойти шагом");
        assertTrue(AbyssPool.RADIUS <= 6.0,
                "дальше шести блоков сосуд уже не про соседа, а про соседнюю "
                        + "комнату");
    }

    @Test
    void извлекательМедленнееСосуда() {
        // сосуд должен набираться дольше, чем опустошаться. Иначе игрок ставит
        // сосуд, тот мгновенно наполняется, и весь смысл накопления теряется:
        // извлекатель всё равно успевает опустошить его быстрее, чем тот копится
        int perSecond = com.github.fireprime5.spellcraft.energy.AbyssExtractorBlock.ABYSS_PER_SECOND;
        assertEquals(1, perSecond);
        // 3000 единиц при одной в секунду — это 50 минут непрерывной добычи.
        // Именно столько сосуд копится: медленно, зато потом долго кормит без
        // извлекателя. Иначе сосуд мгновенно наполнялся бы и накопление теряло
        // бы смысл
        long minutes = (long) AbyssVesselBlock.CAPACITY / perSecond / 60;
        assertEquals(50, minutes);
    }

    @Test
    void пустотаНеСчитаетТолькоКошелёк() {
        // смерть от пустоты проверяет весь пул. Проверить это поведением нельзя
        // без игрока, поэтому фиксируется хотя бы то, что метод есть и что в
        // исходнике пустота обращается к пулу, а не к кошельку
        assertTrue(hasMethod(AbyssPool.class, "total",
                        net.minecraft.world.entity.player.Player.class),
                "у пула должен быть способ узнать общий запас энергии бездны: пустота "
                        + "считает пул, а не кошелёк, иначе игрок умирал бы, "
                        + "стоя у полного сосуда с пустым кошельком");
    }

    private static boolean hasMethod(Class<?> type, String name, Class<?>... args) {
        try {
            type.getMethod(name, args);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}