package com.github.fireprime5.spellcraft.fluid;

import com.github.fireprime5.spellcraft.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.ArrayList;
import java.util.List;

/**
 * Транспортёр: переносит предметы в одну сторону.
 *
 * <p>Одно место предмета и один перенос за тик — сознательно минимально.
 * Скорость важнее для линии на двадцать блоков, чем для перевозки тысяч
 * предметов: игрок всё равно упирается в то, успевает ли опустошить конец.
 *
 * <p>Работает и с воронками: предметы берутся сверху и выходят там же, куда
 * смотрит транспортёр. Отдельный интерфейс не нужен — блок реализует
 * {@link IItemHandler} напрямую, и любая труба из другого мода его увидит.
 */
public class ConveyorBlock extends BaseEntityBlock {

    public static final MapCodec<ConveyorBlock> CODEC = simpleCodec(ConveyorBlock::new);

    /** Тик, через который предмет сдвигается на одну позицию. */
    public static final int MOVE_INTERVAL = 4;

    /** Куда смотрит транспортёр. */
    public static final DirectionProperty FACING =
            BlockStateProperties.HORIZONTAL_FACING;

    /** Сколько тиков до следующего сдвига. */
    public static final IntegerProperty DELAY = IntegerProperty.create("delay", 0, 4);

    private static final VoxelShape BELT = Block.box(0, 4, 0, 16, 12, 16);

    public ConveyorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH).setValue(DELAY, 0));
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
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, DELAY);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConveyorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlocks.CONVEYOR_ENTITY.get(),
                ConveyorBlockEntity.ticker());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return BELT;
    }

    /** Поворот транспортёра. */
    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return BELT;
    }

    /** Смена направления: транспортёр разворачивается и двигает в другую сторону. */
    @Override
    protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /**
     * Блок-сущность транспортёра.
     *
     * <p>Хранит предмет и умеет отдать его соседу спереди. Слот один и намеренно:
     * транспортёр — это конвейер, а не склад. Второй слот превратил бы его в
     * перевалочную станцию, и линия вязла бы на забитых стыках.
     */
    public static class ConveyorBlockEntity extends BlockEntity
            implements IItemHandlerModifiable {

        private ItemStack carried = ItemStack.EMPTY;
        private int delay;

        public ConveyorBlockEntity(BlockPos pos, BlockState state) {
            super(ModBlocks.CONVEYOR_ENTITY.get(), pos, state);
        }

        public static <T extends BlockEntity> BlockEntityTicker<T> ticker() {
            return (level, pos, state, be) -> {
                if (!level.isClientSide && be instanceof ConveyorBlockEntity conveyor) {
                    conveyor.work(level, pos, state);
                }
            };
        }

        /** Один сдвиг за тик с задержкой. */
        public void work(Level level, BlockPos pos, BlockState state) {
            if (carried.isEmpty()) {
                // пусто: пытаемся взять что-нибудь сверху
                carried = pullFromAbove(level, pos);
                if (carried.isEmpty()) {
                    carried = pickupItemEntity(level, pos);
                }
                if (carried.isEmpty()) {
                    return;
                }
            }
            delay++;
            if (delay < MOVE_INTERVAL) {
                setChanged();
                return;
            }
            delay = 0;
            Direction facing = state.getValue(FACING);
            BlockPos ahead = pos.relative(facing);
            // Отдаём всему, что стоит впереди, включая следующий транспортёр:
            // иначе цепочка из двух и более блоков не двигала бы предметы
            // вообще. У занятого транспортёра insertItem вернёт стек обратно,
            // offer вернёт false — линия упрётся в забитый стык, а не
            // потеряет предмет.
            if (offer(ahead, level, carried)) {
                ItemStack moved = carried.copy();
                carried = ItemStack.EMPTY;
                // A small item particle gives clients immediate feedback even
                // when the belt is too small to render a full item model.
                level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, moved),
                        pos.getX() + .5, pos.getY() + .72, pos.getZ() + .5, 0, 0, 0);
            } else if (level.getBlockState(ahead).isAir()) {
                // Discharge into the world only when there is room.  A blocked
                // output keeps the carried stack, so items can never vanish.
                ItemEntity entity = new ItemEntity(level, ahead.getX() + .5, ahead.getY() + .35,
                        ahead.getZ() + .5, carried.copy());
                entity.setDefaultPickUpDelay();
                level.addFreshEntity(entity);
                carried = ItemStack.EMPTY;
            }
            setChanged();
        }

        /** Взятие сверху: воронка или труба кладёт предмет прямо на ленту. */
        private ItemStack pullFromAbove(Level level, BlockPos pos) {
            BlockPos above = pos.above();
            if (level.getBlockEntity(above) instanceof IItemHandler handler) {
                return handler.extractItem(0, 1, false);
            }
            if (level.getBlockState(above).getBlock() instanceof IItemHandler blockHandler) {
                return blockHandler.extractItem(0, 1, false);
            }
            return ItemStack.EMPTY;
        }

        private static ItemStack pickupItemEntity(Level level, BlockPos pos) {
            var box = new net.minecraft.world.phys.AABB(pos).inflate(.15).move(0, .35, 0);
            for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, box)) {
                if (!entity.isRemoved() && !entity.getItem().isEmpty()) {
                    ItemStack result = entity.getItem().split(1);
                    if (entity.getItem().isEmpty()) entity.discard();
                    return result;
                }
            }
            return ItemStack.EMPTY;
        }

        /** Пробуем отдать предмет блоку впереди. */
        private static boolean offer(BlockPos target, Level level, ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            if (level.getBlockEntity(target) instanceof IItemHandler handler
                    && handler.insertItem(0, stack, false).isEmpty()) {
                return true;
            }
            if (level.getBlockState(target).getBlock() instanceof IItemHandler blockHandler
                    && blockHandler.insertItem(0, stack, false).isEmpty()) {
                return true;
            }
            return false;
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot == 0 ? carried : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || !carried.isEmpty() || stack.isEmpty()) {
                return stack;
            }
            // контракт IItemHandler: при simulate переданный стек менять нельзя.
            // Раньше split(1) опустошал стек вызывающего прямо в симуляции, и
            // воронка читала остаток как «принято» — но настоящая вставка потом
            // видела уже пустой стек и предмет исчезал из мира.
            if (simulate) {
                return stack.getCount() > 1 ? stack.copyWithCount(stack.getCount() - 1) : ItemStack.EMPTY;
            }
            ItemStack single = stack.split(1);
            carried = single;
            setChanged();
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0 || carried.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int taken = Math.min(amount, carried.getCount());
            if (simulate) {
                return carried.copyWithCount(taken);
            }
            ItemStack out = carried.split(taken);
            if (carried.isEmpty()) {
                carried = ItemStack.EMPTY;
            }
            setChanged();
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && carried.isEmpty() && !stack.isEmpty();
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            if (slot == 0) {
                carried = stack;
                setChanged();
            }
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.put("item", carried.saveOptional(registries));
            tag.putInt("delay", delay);
        }

        @Override
        public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            carried = ItemStack.parse(registries, tag.getCompound("item"))
                    .filter(stack -> !stack.isEmpty())
                    .orElse(ItemStack.EMPTY);
            delay = Math.clamp(tag.getInt("delay"), 0, MOVE_INTERVAL);
        }
    }
}
