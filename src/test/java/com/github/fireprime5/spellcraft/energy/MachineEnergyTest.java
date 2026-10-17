package com.github.fireprime5.spellcraft.energy;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Энергия машин: списание, приём и защита от битого NBT.
 *
 * <p>{@link MachineEnergy} намеренно не трогает реестр Minecraft — это чистая
 * арифметика поверх {@code EnergyStorage}, поэтому её можно проверить целиком.
 * Блок-сущность в тестах не создаётся: для неё нужен мир.
 */
class MachineEnergyTest {

    @Test
    void новаяМашинаПустая() {
        MachineEnergy energy = new MachineEnergy();
        assertEquals(0, energy.getEnergy(), "новая машина не должна быть заряжена");
        assertEquals(MachineEnergy.DEFAULT_CAPACITY, energy.getMaxEnergyStored());
    }

    @Test
    void энергияСписываетсяПоКаплям() {
        MachineEnergy energy = new MachineEnergy(100);
        energy.receiveEnergy(100, false);

        // главное свойство consume: забирает ровно потреблённое, а не всё
        assertTrue(energy.consume(4));
        assertEquals(96, energy.getEnergy());
        assertTrue(energy.consume(4));
        assertEquals(92, energy.getEnergy());
    }

    @Test
    void приНехваткеЭнергииНичегоНеСписывается() {
        MachineEnergy energy = new MachineEnergy(100);
        energy.receiveEnergy(6, false);

        assertFalse(energy.consume(10), "энергии меньше, чем нужно");
        assertEquals(6, energy.getEnergy(),
                "неудачная попытка не должна была списать часть");
    }

    @Test
    void машинаВстаётРовноНаГранице() {
        MachineEnergy energy = new MachineEnergy(8);
        energy.receiveEnergy(8, false);

        assertTrue(energy.consume(4));
        assertTrue(energy.consume(4), "ровно столько энергии — машина работает");
        assertEquals(0, energy.getEnergy());
        assertFalse(energy.consume(4), "дальше энергии нет");
    }

    @Test
    void нольНеТребуетЭнергии() {
        MachineEnergy energy = new MachineEnergy();
        // машина с нулевой ценой обязана работать, иначе блок был бы мёртвым
        assertTrue(energy.consume(0));
        assertEquals(0, energy.getEnergy());
    }

    @Test
    void машинаНужнаНаВсюЁмкость() {
        MachineEnergy energy = new MachineEnergy(MachineEnergy.DEFAULT_CAPACITY);
        fill(energy, Integer.MAX_VALUE);
        assertEquals(MachineEnergy.DEFAULT_CAPACITY, energy.getEnergy(),
                "больше ёмкости энергии не влезть не должно");
    }

    /**
     * Заливает хранилище целиком с учётом лимита приёма за раз.
     *
     * <p>Раньше машина принимала хоть всю ёмкость за один вызов, и тесты лили
     * по тысяче. Теперь приём ограничен {@link MachineEnergy#MACHINE_TRANSFER},
     * поэтому заливка идёт порциями — иначе тесты проверяли бы не то, что
     * происходит в игре.
     */
    private static void fill(MachineEnergy energy, int total) {
        int guard = 0;
        while (energy.getEnergy() < total && guard++ < 10_000) {
            int room = total - energy.getEnergy();
            if (energy.receiveEnergy(Math.min(room, 1000), false) == 0) {
                break;
            }
        }
    }

    /**
     * Машина не объявляет соседям свою ёмкость как скорость приёма.
     *
     * <p>Так было: {@code super(capacity, capacity, 0)} — и любой чужой мод,
     * запрашивающий capability, видел «машина влезает 32 000 за тик». Mekanism
     * рисовал по этому свои потоки, и в интерфейсе появлялось 102к в тик при
     * трёх извлекателях, хотя энергия из куба фактически не уходила: забирать
     * 32 000 за тик просто никто не приходил.
     */
    @Test
    void машинаНеОбъявляетЕмкостьСкоростью() {
        MachineEnergy energy = new MachineEnergy(MachineEnergy.DEFAULT_CAPACITY);
        assertEquals(MachineEnergy.MACHINE_TRANSFER,
                energy.receiveEnergy(MachineEnergy.MACHINE_TRANSFER * 10, true),
                "машина должна принимать не больше объявленной скорости");
    }

    @Test
    void скоростьПриёмаЗаметноБольшеРасхода() {
        // запас нужен, чтобы линия не ограничивала машину, но он не должен
        // превращаться в фантастические числа в чужих интерфейсах
        int spend = com.github.fireprime5.spellcraft.energy.AbyssExtractorBlock.ENERGY_PER_TICK;
        assertTrue(MachineEnergy.MACHINE_TRANSFER > spend,
                "скорость приёма должна перекрывать расход машины");
        assertTrue(MachineEnergy.MACHINE_TRANSFER < MachineEnergy.DEFAULT_CAPACITY,
                "скорость приёма не должна совпадать с ёмкостью");
    }

    @Test
    void энергияПереживаетСохранениеИЗагрузку() {
        MachineEnergy energy = new MachineEnergy(1000);
        fill(energy, 400);

        CompoundTag tag = energy.saveTo(new CompoundTag());
        MachineEnergy loaded = new MachineEnergy();
        loaded.loadFrom(tag);

        assertEquals(400, loaded.getEnergy());
        assertEquals(1000, loaded.getMaxEnergyStored());
    }

    @Test
    void пустойТегДаётЁмкостьПоУмолчанию() {
        // блок, поставленный до того как энергия появилась в коде, не должен
        // после загрузки мира получить ёмкость 0 и молча сломаться
        MachineEnergy loaded = new MachineEnergy();
        loaded.loadFrom(new CompoundTag());

        assertEquals(MachineEnergy.DEFAULT_CAPACITY, loaded.getMaxEnergyStored());
        assertEquals(0, loaded.getEnergy());
    }

    @Test
    void битоеЗначениеЭнергииНеЛомаетМашину() {
        MachineEnergy loaded = new MachineEnergy();
        CompoundTag tag = new CompoundTag();
        tag.putInt("energy", -500);
        tag.putInt("capacity", 1000);

        loaded.loadFrom(tag);
        assertEquals(0, loaded.getEnergy(), "отрицательная энергия должна обнуляться");
        // ноль — это «энергии нет», то есть машина стоит, а не сломана: важно
        // лишь, что состояние достижимо и снимается обычной заливкой
        assertFalse(loaded.consume(1), "без энергии машина стоит");
        loaded.receiveEnergy(100, false);
        assertTrue(loaded.consume(4), "после заливки машина снова работает");
    }

    @Test
    void безумнаяЁмкостьИзNbtОграничивается() {
        // главная защита: вписанная руками ёмкость превратила бы блок в
        // источник почти бесконечной энергии
        MachineEnergy loaded = new MachineEnergy();
        CompoundTag tag = new CompoundTag();
        tag.putInt("energy", Integer.MAX_VALUE);
        tag.putInt("capacity", Integer.MAX_VALUE);

        loaded.loadFrom(tag);
        assertEquals(MachineEnergy.MAX_CAPACITY, loaded.getMaxEnergyStored());
        assertNotEquals(Integer.MAX_VALUE, loaded.getEnergy());
        assertEquals(loaded.getMaxEnergyStored(), loaded.getEnergy());
    }

    @Test
    void энергияБольшеЁмкостиОбрезается() {
        MachineEnergy loaded = new MachineEnergy();
        CompoundTag tag = new CompoundTag();
        tag.putInt("energy", 999_999);
        tag.putInt("capacity", 1000);

        loaded.loadFrom(tag);
        assertEquals(1000, loaded.getEnergy());
    }

    @Test
    void машинаНеОтдаётЭнергиюНаружу() {
        // машина потребляет, но не является источником: иначе труба, подведённая
        // к извлекателю, выкачала бы из него всю накопленную энергию
        MachineEnergy energy = new MachineEnergy(1000);
        fill(energy, 1000);

        assertFalse(energy.canExtract());
        assertEquals(0, energy.extractEnergy(500, false));
        assertEquals(1000, energy.getEnergy(), "энергия не должна была уйти");
    }

    @Test
    void ценаИзвлекателяСогласована() {
        // 80 энергии в секунду по 4 за тик: если формулы разъедутся, машина
        // станет либо вдвое дороже, либо вдвое дешевле задуманного
        assertEquals(80, AbyssExtractorBlock.ENERGY_PER_SECOND);
        assertEquals(4, AbyssExtractorBlock.ENERGY_PER_TICK);
        assertEquals(0, AbyssExtractorBlock.ENERGY_PER_SECOND
                % AbyssExtractorBlock.TICKS_PER_SECOND,
                "энергия за секунду должна делиться на тики без остатка");
        assertEquals(20, AbyssExtractorBlock.ABYSS_TICKS, "одна энергия бездны в секунду = 20 тиков");
    }

    @Test
    void полныйБуферДаётОжидаемоеВремяРаботы() {
        // задумано «около шести с половиной минут» — проверяем, что комментарий
        // не разошёлся с арифметикой
        double seconds = (double) MachineEnergy.DEFAULT_CAPACITY / AbyssExtractorBlock.ENERGY_PER_SECOND;
        double minutes = seconds / 60.0;
        assertTrue(minutes > 6.0 && minutes < 7.0,
                "полный буфер должен кормить машину 6-7 минут, а не " + minutes);
    }

    @Test
    void радиусИсточникаМеньшеРадиусаМашины() {
        // иначе источник кормил бы машину с любой точки и «поставить рядом»
        // перестало бы что-то значить
        assertTrue(DebugEnergySourceBlock.RADIUS < AbyssExtractorBlock.RADIUS,
                "радиус источника должен быть меньше рабочего радиуса машины");
    }
}
