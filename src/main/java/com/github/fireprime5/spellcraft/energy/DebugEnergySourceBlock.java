package com.github.fireprime5.spellcraft.energy;

import com.github.fireprime5.spellcraft.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Отладочный источник энергии: безостановочно отдаёт энергию в небольшом
 * радиусе.
 *
 * <p>Нужен, чтобы проверять машины без установки Mekanism: поставил, положил
 * извлекатель рядом — и тот работает. Генератора как такового в моде нет и не
 * планируется, поэтому блок по умолчанию <b>не выдаётся игроку</b>: в обычной
 * игре это была бы машина, производящая энергию из воздуха.
 *
 * <p>Сам блок при этом всегда работает. Выключается не он, а выдача предмета
 * — иначе ради одной проверки пришлось бы перезагружать мод.
 *
 * <p>Радиус конечен и меньше рабочего радиуса машины: бесконечная энергия
 * прямо в блоке не отличалась бы от вечной, а из-за меньшего радиуса «поставить
 * рядом» сохраняет смысл.
 */
public class DebugEnergySourceBlock extends BaseEntityBlock {

    public static final MapCodec<DebugEnergySourceBlock> CODEC =
            simpleCodec(DebugEnergySourceBlock::new);

    /** Радиус раздачи в блоках. */
    public static final double RADIUS = 5.0;

    /** Энергия за тик на каждую машину в зоне. */
    public static final int ENERGY_PER_TICK = 40;

    public DebugEnergySourceBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DebugEnergyBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlocks.DEBUG_ENERGY_SOURCE_ENTITY.get(),
                DebugEnergyBlockEntity.ticker());
    }

    /** Энергия доступна с любой стороны, как у настоящей трубы. */
    public IEnergyStorage energy(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof DebugEnergyBlockEntity be ? be : null;
    }

    /**
     * Блок-сущность отладочного источника.
     *
     * <p>Хранилища у него нет и быть не может: отдавать надо бесконечно,
     * хранить нечего. {@link #receiveEnergy} всегда возвращает 0 — блок не
     * копит энергию и потому не может оказаться источником для трубы изнутри,
     * а {@link #extractEnergy} возвращает 0 по той же причине.
     *
     * <p>Раздачу делает не интерфейс, а собственный тик: интерфейс честно
     * отвечает «у меня ничего нет», а подпитка соседей — это отдельное
     * намерение блока, а не следствие того, что он объявил себя хранилищем.
     */
    public static class DebugEnergyBlockEntity extends BlockEntity implements IEnergyStorage {

        public DebugEnergyBlockEntity(BlockPos pos, BlockState state) {
            super(ModBlocks.DEBUG_ENERGY_SOURCE_ENTITY.get(), pos, state);
        }

        /**
         * Раздаёт энергию всем, кто её принимает, в радиусе.
         *
         * <p>Идёт через capability, а не через «свои» классы. Раньше здесь стояло
         * {@code instanceof MachineBlockEntity}, и из-за этого источник не видел
         * провод (у него другой класс), а любой блок чужого мода не видел тем
         * более. Запрос capability — единственный способ накормить всё, что
         * заявляет энергию, включая трубы Mekanism.
         */
        public void work(Level level, BlockPos pos) {
            double r2 = RADIUS * RADIUS;
            int minX = pos.getX() - (int) RADIUS;
            int minY = pos.getY() - (int) RADIUS;
            int minZ = pos.getZ() - (int) RADIUS;
            int maxX = pos.getX() + (int) RADIUS;
            int maxY = pos.getY() + (int) RADIUS;
            int maxZ = pos.getZ() + (int) RADIUS;
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockPos target = new BlockPos(x, y, z);
                        // сравниваем квадраты: корни не нужны, а куб 11³
                        // блоков — это 1331 проверка за тик на источник
                        if (target.distSqr(pos) > r2) {
                            continue;
                        }
                        IEnergyStorage target2 = level.getCapability(
                                net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                                target, null);
                        if (target2 != null && target2.canReceive()) {
                            target2.receiveEnergy(ENERGY_PER_TICK, false);
                        }
                    }
                }
            }
        }

        public static <T extends BlockEntity> BlockEntityTicker<T> ticker() {
            return (level, pos, state, be) -> {
                if (!level.isClientSide && be instanceof DebugEnergyBlockEntity debug) {
                    debug.work(level, pos);
                }
            };
        }

        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            // источник ничего не принимает: иначе труба решила бы, что в нём
            // есть место под энергию, и сливала бы в него своё
            return 0;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            // вся энергия раздаётся соседям напрямую, а не через интерфейс:
            // иначе источник выглядел бы как обычное хранилище с полным
            // запасом, и его можно было бы опустошить, вытащив трубой
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return 0;
        }

        @Override
        public int getMaxEnergyStored() {
            return 0;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    }
}
