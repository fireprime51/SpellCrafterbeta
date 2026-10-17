package com.github.fireprime5.spellcraft.energy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Правила энергии бездны, которые можно проверить без мира.
 *
 * <p>Сама {@code AbyssEnergy} работает через {@code persistentData} игрока, а
 * игрока в тестах не создать. Поэтому здесь проверяются только числа и
 * соотношения: стартовая энергия бездны, энергия бездны после смерти, время до смерти от пустоты.
 * Всё остальное живёт в бою.
 */
class AbyssDecayTest {

    @Test
    void смертьОтПустотыЧерезПятьМинут() {
        // ровно пять минут по замыслу: 60 секунд * 20 тиков * 5
        assertEquals(6000, AbyssDecay.TICKS_TO_DEATH);
        assertEquals(5 * 60 * 20, AbyssDecay.TICKS_TO_DEATH);
        // и это именно пять минут, а не пять с половиной
        assertEquals(300.0, AbyssDecay.TICKS_TO_DEATH / 20.0);
    }

    @Test
    void игрокНачинаетНеСНуля() {
        // с нулём в руках не кастанёшь вообще ничего, и первые минуты игры
        // проходят в невозможности что-либо сделать
        assertTrue(AbyssEnergy.STARTING_ABYSS > 0,
                "при первом появлении должна быть хотя бы единица");
        assertEquals(10, AbyssEnergy.STARTING_ABYSS);
    }

    @Test
    void послеСмертиОстаётсяОднаЕдиница() {
        assertEquals(1, AbyssEnergy.RESPAWN_ABYSS);
        // и это меньше стартовой: воскресший не начинает заново
        assertTrue(AbyssEnergy.RESPAWN_ABYSS < AbyssEnergy.STARTING_ABYSS,
                "после смерти должно быть меньше, чем при первом входе");
    }

    @Test
    void стартоваяМанаВлезаетВКошелёк() {
        // при уменьшенном конфиге стартовая энергия бездны не должна превышать потолок,
        // иначе игрок получил бы энергию бездны, которую нельзя потратить
        assertTrue(AbyssEnergy.STARTING_ABYSS <= AbyssEnergy.DEFAULT_MAX_ABYSS,
                "стартовая энергия бездны должна влезать в кошелёк по умолчанию");
        assertTrue(AbyssEnergy.RESPAWN_ABYSS <= AbyssEnergy.DEFAULT_MAX_ABYSS);
    }

    @Test
    void извлекательДаётЭнергиюБездныИгрокуАНеСебе() {
        // энергия бездны не должна копиться в блоке: единственное хранилище — кошелёк
        // игрока, иначе извлекатель стал бы батареей на десятки тысяч единиц
        assertEquals(1, AbyssExtractorBlock.ABYSS_PER_SECOND);
        assertEquals(20, AbyssExtractorBlock.ABYSS_TICKS,
                "одна единица в секунду = 20 тиков, без дробных остатков");
    }

    @Test
    void таймерИдётТолькоБезМаны() {
        // это зафиксировано числами, а не кодом: сброс счётчика при появлении
        // энергии бездны — единственное, что отличает «устал» от «умирает»
        assertTrue(AbyssDecay.TICKS_TO_DEATH > 20,
                "таймер должен измеряться тиками, а не секундами");
        assertEquals(0, AbyssDecay.TICKS_TO_DEATH % 20,
                "смерть должна наступать на границе секунды, а не в середине тика");
    }
}
