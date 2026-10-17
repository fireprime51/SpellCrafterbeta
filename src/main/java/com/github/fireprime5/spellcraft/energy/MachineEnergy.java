package com.github.fireprime5.spellcraft.energy;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;

/**
 * Накопитель энергии для машин.
 *
 * <p>Собственная реализация, а не {@link EnergyStorage} напрямую, по двумым
 * причинам:
 *
 * <ul>
 *   <li><b>NBT из мира считается враждебным.</b> Значение приходит из файла
 *       сохранения, и {@code load} не имеет права слепо доверять ему: если
 *       записать {@code capacity} больше разумного или отрицательную энергию,
 *       блок либо убьёт сервер переполнением, либо начнёт выдавать энергию из
 *       воздуха. Здесь значения зажимаются и проверяются на загрузке.</li>
 *   <li><b>Разряд по каплям.</b> Пока энергия не кончилась, машина жжёт ровно
 *       столько, сколько нужно на этот тик, а не всю ёмкость разом: иначе
 *       буфер на 32k закончился бы за секунду и разница между «дорогой» и
 *       «дешёвой» машиной была бы незаметна.</li>
 * </ul>
 *
 * <p>Энергия бездны и энергия — разные валюты и намеренно не переводятся друг в друга
 * автоматически: игрок добывает энергию бездны модулем «Извлечение», а машины питаются
 * энергией. Единственный мост между ними — блок-извлекатель, и он требует
 * энергию.
 */
public class MachineEnergy extends EnergyStorage {

    /**
     * Ёмкость по умолчанию. 32k — круглое число, которое встречается и в
     * других модах, поэтому пайп из Mekanism или свой буфер подойдут без
     * подгонки.
     */
    public static final int DEFAULT_CAPACITY = 32000;

    /** Потолок разумной ёмкости: защита от битого NBT, а не игровое ограничение. */
    public static final int MAX_CAPACITY = 1_000_000;

    /**
     * Сколько энергии машина принимает за тик.
     *
     * <p>Раньше машина объявляла соседям скорость приёма, равную всей ёмкости
     * (32 000 за тик). Сама она съедает 4 за тик, но любой чужой мод, который
     * запрашивает capability, видел «я влезаю 32 000» и рисовал по этому свои
     * потоки. В интерфейсе Mekanism это и выглядело как 102к в тик: куб
     * показывал, сколько готов отдать, а отдавать было нечего, потому что
     * забирать 32 000 за тик никто не приходил.
     *
     * <p>Теперь машина объявляет честную скорость: с запасом над своим
     * расходом, но не свою же ёмкость.
     */
    public static final int MACHINE_TRANSFER = 200;

    /**
     * Накопитель машины.
     *
     * <p>Третий аргумент — 0: машина энергию не отдаёт, а только потребляет.
     * Иначе труба из другого мода выкачала бы из неё всё накопленное.
     */
    public MachineEnergy(int capacity) {
        super(capacity, MACHINE_TRANSFER, 0);
    }

    public MachineEnergy() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Потратить энергию за один тик работы.
     *
     * @param need сколько нужно на этот тик
     * @return true — энергии хватило и она списана; false — машина встала
     */
    public boolean consume(int need) {
        if (need <= 0) {
            return true;
        }
        if (energy < need) {
            return false;
        }
        energy -= need;
        return true;
    }

    /** Текущий запас. */
    public int getEnergy() {
        return energy;
    }

    /**
     * Загрузка из NBT с проверкой.
     *
     * <p>Ёмкость восстанавливается из NBT, но зажимается: руками вписанное
     * значение больше {@link #MAX_CAPACITY} сделало бы блок источником
     * бесконечной энергии, а отрицательное — источником пустоты.
     */
    public void loadFrom(CompoundTag tag) {
        int cap = tag.getInt("capacity");
        capacity = cap > 0 ? Math.min(cap, MAX_CAPACITY) : DEFAULT_CAPACITY;
        int stored = tag.getInt("energy");
        energy = Math.clamp(stored, 0, capacity);
    }

    public CompoundTag saveTo(CompoundTag tag) {
        tag.putInt("energy", energy);
        tag.putInt("capacity", capacity);
        return tag;
    }

    /**
     * Блок-сущность, у которой есть накопитель.
     *
     * <p>Отдельный класс нужен только ради тика: {@link BlockEntity} сам по
     * себе не знает про энергию, а машина обязана что-то делать каждый тик.
     */
    public abstract static class MachineBlockEntity extends BlockEntity {

        protected final MachineEnergy energyStorage;

        protected MachineBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type,
                                     BlockPos pos, BlockState state, int capacity) {
            super(type, pos, state);
            this.energyStorage = new MachineEnergy(capacity);
        }

        public MachineEnergy energy() {
            return energyStorage;
        }

        /** Работа за один тик. Вызывается только на сервере. */
        public abstract void work(Level level, BlockPos pos);

        /** Тикер для {@code Block#getTicker}: вызывает {@link #work}. */
        public static <T extends MachineBlockEntity>
                net.minecraft.world.level.block.entity.BlockEntityTicker<T> ticker() {
            return (level, pos, state, be) -> {
                if (!level.isClientSide) {
                    be.work(level, pos);
                }
            };
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            energyStorage.saveTo(tag);
        }

        @Override
        public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            // без этого энергия в загруженном чанке была бы всегда нулевой,
            // и машина после перезахода молча стояла бы до заливки
            energyStorage.loadFrom(tag);
        }

        @Override
        public void onChunkUnloaded() {
            super.onChunkUnloaded();
            // состояние энергии уже лежит в NBT и переживёт выгрузку, но
            // помечаем изменённым, чтобы чанк записался вовремя
            setChanged();
        }
    }
}
