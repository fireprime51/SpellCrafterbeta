package com.github.fireprime5.spellcraft.fluid;

import com.github.fireprime5.spellcraft.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Бак: хранит энергию бездны.
 *
 * <p>Это единственное место, где бездна копится в приличном объёме. Труба
 * переносит, машина расходует, бак служит складом — без него бездну некуда
 * сливать, а извлекатель без бака работал бы впустую.
 */
public class AbyssTankBlock extends BaseEntityBlock {

    public static final MapCodec<AbyssTankBlock> CODEC = simpleCodec(AbyssTankBlock::new);

    /** Ёмкость бака: 20 000 единиц, двадцать труб по 1000. */
    public static final int CAPACITY = 20000;

    public AbyssTankBlock(Properties properties) {
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
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return SHAPE;
    }

    /** Бак ниже пояса: он высокий, и полноразмерный куб выглядел бы бочкой. */
    private static final VoxelShape SHAPE = Shapes.box(2, 0, 2, 14, 16, 14);

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TankBlockEntity(pos, state);
    }

    /** ПКМ без предмета: показать запас оператору. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide || !player.hasPermissions(2)) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof TankBlockEntity tank) {
            player.sendSystemMessage(Component.translatable("message.spellcraft.tank_abyss",
                    tank.abyssStored(), CAPACITY));
        }
        return InteractionResult.SUCCESS;
    }

    /** Блок-сущность бака. */
    public static class TankBlockEntity extends BlockEntity implements AbyssPort {

        private int stored;

        public TankBlockEntity(BlockPos pos, BlockState state) {
            super(ModBlocks.ABYSS_TANK_ENTITY.get(), pos, state);
        }

        @Override
        public int abyssStored() {
            return Math.clamp(stored, 0, CAPACITY);
        }

        @Override
        public int abyssCapacity() {
            return CAPACITY;
        }

        @Override
        public int abyssReceive(int amount) {
            if (amount <= 0) {
                return 0;
            }
            int added = Math.min(CAPACITY - abyssStored(), amount);
            stored = abyssStored() + Math.max(0, added);
            return Math.max(0, added);
        }

        @Override
        public int abyssExtract(int amount) {
            if (amount <= 0) {
                return 0;
            }
            int taken = Math.min(abyssStored(), amount);
            stored = abyssStored() - Math.max(0, taken);
            return Math.max(0, taken);
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.putInt("abyss", abyssStored());
        }

        @Override
        public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            stored = Math.clamp(tag.getInt("abyss"), 0, CAPACITY);
        }
    }
}