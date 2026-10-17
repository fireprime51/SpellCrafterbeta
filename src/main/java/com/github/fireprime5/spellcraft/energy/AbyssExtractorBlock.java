package com.github.fireprime5.spellcraft.energy;

import com.github.fireprime5.spellcraft.network.NetworkNode;
import com.github.fireprime5.spellcraft.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Извлекатель энергии бездны: единственный блок, который даёт энергию бездны без заклинания.
 *
 * <p>Задуман как медленный подпорный источник: игрок ставит его рядом с
 * гримуаром и получает понемногу, пока хватает энергии. Энергия бездны при этом не
 * появляется из воздуха — за неё платит энергия, которую кто-то произвёл.
 *
 * <p>Скорость и цена подобраны так, чтобы машина не заменяла добычу модулем
 * «Извлечение», а дополняла её. Одна единица энергии бездны в секунду против 80 единиц
 * энергии: полная заливка буфера (32k) даёт около шести с половиной минут
 * работы — чуть больше одного «Удара» и намного меньше одного «Телепорта».
 */
public class AbyssExtractorBlock extends BaseEntityBlock implements NetworkNode {

    public static final MapCodec<AbyssExtractorBlock> CODEC = simpleCodec(AbyssExtractorBlock::new);

    /** Энергия бездны в секунду: одна единица. */
    public static final int ABYSS_PER_SECOND = 1;

    /** Тиков в секунду — константа Minecraft. */
    public static final int TICKS_PER_SECOND = 20;

    /** Энергия за секунду работы: 80 единиц. */
    public static final int ENERGY_PER_SECOND = 80;

    /** Энергия за один тик: ровно 4 единицы, потому что 80 / 20. */
    public static final int ENERGY_PER_TICK = ENERGY_PER_SECOND / TICKS_PER_SECOND;

    /** Тиков накопления до выдачи одной единицы энергии бездны. */
    public static final int ABYSS_TICKS = TICKS_PER_SECOND / ABYSS_PER_SECOND;

    /** Радиус раздачи энергии бездны в блоках. */
    public static final double RADIUS = 8.0;

    public AbyssExtractorBlock(Properties properties) {
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
        return new ExtractorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        // тикает только наш тип: чужая под-сущность на этом месте получила бы
        // тик и упала бы в касте
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlocks.MANA_EXTRACTOR_ENTITY.get(),
                ExtractorBlockEntity.ticker());
    }

    /**
     * Извлекатель — узел сети.
     *
     * <p>Без этого провод, стоящий вплотную к машине, выглядел бы висящим
     * обрывом, а луч по блоку на машину падал бы с «здесь нет провода».
     */
    @Override
    public net.minecraft.world.level.block.Block asBlock() {
        return this;
    }

    /** Энергия доступна с любой стороны: труба подходит и сверху, и сбоку. */
    public IEnergyStorage energy(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ExtractorBlockEntity be ? be.energy() : null;
    }

    /** Блок-сущность извлекателя. */
    public static class ExtractorBlockEntity extends MachineEnergy.MachineBlockEntity {

        /**
         * Счётчик до следующей порции энергии бездны.
         *
         * <p>Энергия бездны целочисленная ({@link AbyssEnergy} хранит int), поэтому
         * «одна единица в секунду» копится счётчиком тиков, а не дробным
         * остатком: дробь превращалась бы в 0 или 1 по своему усмотрению, и
         * игрок получал бы то 19, то 21 единицу в минуту.
         */
        private int manaTicks;

        public ExtractorBlockEntity(BlockPos pos, BlockState state) {
            super(ModBlocks.MANA_EXTRACTOR_ENTITY.get(), pos, state,
                    MachineEnergy.DEFAULT_CAPACITY);
        }

        @Override
        public void work(Level level, BlockPos pos) {
            if (!(level instanceof ServerLevel server)) {
                return;
            }
            // энергия не тратится, пока рядом нечего кормить: иначе машина
            // рядом с игроком на полной энергии бездны жгла бы энергию впустую
            if (manaTicks == 0 && !anyNearbyNeedsMana(server, pos)) {
                return;
            }
            if (!energy().consume(ENERGY_PER_TICK)) {
                // энергия кончилась посреди накопления: недобранная порция
                // не выдаётся, а счётчик обнуляется — иначе игрок получил бы
                // «вдогонку» в первый же тик после заливки
                manaTicks = 0;
                return;
            }
            manaTicks++;
            if (manaTicks >= ABYSS_TICKS) {
                manaTicks = 0;
                giveToNearby(server, pos);
            }
        }

        /** Есть ли рядом игрок, которому энергия бездны ещё нужна. */
        private static boolean anyNearbyNeedsMana(ServerLevel level, BlockPos pos) {
            Vec3 center = Vec3.atCenterOf(pos);
            double r2 = RADIUS * RADIUS;
            for (Player player : level.getPlayers(p -> p.isAlive()
                    && !AbyssEnergy.isInfinite(p)
                    && !AbyssPool.isFull(p)
                    // под «Вечностью» бездна не идёт: считать такого игрока
                    // нуждающимся значит жечь цикл извлекателя впустую
                    && !com.github.fireprime5.spellcraft.armor.Eternity.isActive(p)
                    && p.distanceToSqr(center) <= r2)) {
                return true;
            }
            return false;
        }

        /** Выдаёт по одной единице энергии бездны каждому игроку в радиусе. */
        private static void giveToNearby(ServerLevel level, BlockPos pos) {
            Vec3 center = Vec3.atCenterOf(pos);
            double r2 = RADIUS * RADIUS;
            for (Player player : level.getPlayers(p -> p.isAlive()
                    && !AbyssEnergy.isInfinite(p)
                    && p.distanceToSqr(center) <= r2)) {
                // addMana возвращает, сколько реально влезло, и не даёт
                // переполнить кошелёк: порция в одну единицу этим покрыта
                AbyssEnergy.add(player, 1);
            }
        }
    }
}
