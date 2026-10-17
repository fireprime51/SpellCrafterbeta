package com.github.fireprime5.spellcraft.energy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Плавильня: согласованность чисел.
 *
 * <p>Сам переплав и раздача предметов проверяются в игре: рецепты живут в
 * реестре, а блочная сущность без мира не создаётся. Здесь зафиксированы
 * соотношения, из-за которых машина либо работает, либо нет.
 */
class SmelterBlockTest {

    @Test
    void энергияДелитсяНаЦелыйТик() {
        // было бы 800 / 100 = 8, но если бы COOK_TICKS не делил ENERGY_PER_ITEM
        // нацело, энергия за тик и стоимость предмета разошлись бы: машина
        // плавила бы предмет бесплатно либо переплачивала вдвое
        assertEquals(0, SmelterBlock.ENERGY_PER_ITEM % SmelterBlock.COOK_TICKS,
                "энергия предмета должна делиться на тики без остатка");
        assertEquals(8, SmelterBlock.ENERGY_PER_TICK);
    }

    @Test
    void переплавВдвоеБыстрееПечи() {
        // выбрано при разговоре: 100 тиков против 200 у обычной печи
        assertEquals(100, SmelterBlock.COOK_TICKS);
        assertTrue(SmelterBlock.COOK_TICKS < 200, "плавильня должна быть быстрее печи");
    }

    @Test
    void дваСлотаКакУПечи() {
        assertEquals(2, SmelterBlock.SLOTS);
        assertEquals(0, SmelterBlock.SLOT_INPUT);
        assertEquals(1, SmelterBlock.SLOT_OUTPUT);
    }

    @Test
    void слотыНеПутаны() {
        // вход — слот 0, выход — слот 1. Раньше здесь стояла проверка «вход
        // равен единице», что бессмысленно: SLOT_INPUT это индекс, а не
        // количество, и проверка проходила только потому, что ждала неверного
        assertEquals(0, SmelterBlock.SLOT_INPUT);
        assertEquals(1, SmelterBlock.SLOT_OUTPUT);
        assertTrue(SmelterBlock.SLOT_INPUT != SmelterBlock.SLOT_OUTPUT);
    }

    @Test
    void плавильняДорожеИзвлекателя() {
        // извлекатель съедает 80 энергии в секунду и производит энергию бездны, плавильня
        // съедает 160 в секунду и производит предметы. Порядок честный: без
        // извлекателя энергии бездны на жизнь не будет, а предметы можно добыть и иначе
        int extractorPerSecond = AbyssExtractorBlock.ENERGY_PER_SECOND;
        int smelterPerSecond = SmelterBlock.ENERGY_PER_TICK * 20;
        assertTrue(smelterPerSecond > extractorPerSecond,
                "плавильня должна обходиться дороже извлекателя");
        assertEquals(160, smelterPerSecond);
    }

    @Test
    void машинаНеОбъявляетЁмкостьСкоростью() {
        // как и у остальных машин: чужие моды считают по этому числу свои потоки,
        // и «влезает 32000 за тик» превращалось бы в фантастические числа
        // в чужих интерфейсах
        assertEquals(200, MachineEnergy.MACHINE_TRANSFER);
    }
}
