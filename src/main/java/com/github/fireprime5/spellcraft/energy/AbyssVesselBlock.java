package com.github.fireprime5.spellcraft.energy;

import com.github.fireprime5.spellcraft.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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
 * Сосуд для энергии бездны: блок, который копит энергию бездны.
 *
 * <p>Сосуд нужен из-за извлекателя: тот даёт энергию бездны постоянно, а кошелёк игрока
 * ограничен, и без сосуда излишки просто пропадали бы. Сосуды рядом с игроком
 * суммируются и работают как один большой кошелёк (см. {@link AbyssPool}).
 *
 * <p>Само по себе хранилище ничего не даёт: чтобы забрать из него энергию бездны, надо
 * стоять рядом. Это осознанно — сосуд на другом конце фабрики не должен кормить
 * игрока через полкарты.
 */
public class AbyssVesselBlock extends BaseEntityBlock {

    public static final MapCodec<AbyssVesselBlock> CODEC = simpleCodec(AbyssVesselBlock::new);

    /**
     * Ёмкость одного сосуда.
     *
     * <p>Раза в три больше кошелька игрока: сосуд должен быть заметной
     * накопительной ёмкостью, а не украшением. Один сосуд заменяет кошелёк
     * целиком, три — дают базу для большой фабрики.
     */
    public static final int CAPACITY = 3000;

    public AbyssVesselBlock(Properties properties) {
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
        return new VesselBlockEntity(pos, state);
    }

    /** Сосуд не мешает ходить: он низкий и узкий, как ваза на полу. */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return SHAPE;
    }

    private static final VoxelShape SHAPE = Shapes.box(3.0, 0.0, 3.0, 13.0, 12.0, 13.0);

    /**
     * ПКМ: показать, сколько энергии бездны внутри.
     *
     * <p>Только оператору. Игроку состояние сосудов и так видно по полоске
     * энергии бездны — она уже учитывает сосуды рядом, — а оператору нужно при отладке.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide || !player.hasPermissions(2)) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof VesselBlockEntity vessel) {
            player.sendSystemMessage(Component.translatable("message.spellcraft.vessel_mana",
                    vessel.getAbyss(), CAPACITY));
        }
        return InteractionResult.SUCCESS;
    }

    /** Блок-сущность сосуда: хранит энергию бездны. */
    public static class VesselBlockEntity extends BlockEntity {

        private int abyss;

        public VesselBlockEntity(BlockPos pos, BlockState state) {
            super(ModBlocks.MANA_VESSEL_ENTITY.get(), pos, state);
        }

        public int getAbyss() {
            return Math.clamp(abyss, 0, CAPACITY);
        }

        /** Кладёт энергию бездны, возвращая сколько влезло. */
        public int add(int amount) {
            if (amount <= 0) {
                return 0;
            }
            int added = Math.min(CAPACITY - getAbyss(), amount);
            if (added > 0) {
                abyss = getAbyss() + added;
                setChanged();
            }
            return added;
        }

        /** Забирает энергию бездны, возвращая сколько удалось. */
        public int take(int amount) {
            if (amount <= 0) {
                return 0;
            }
            int taken = Math.min(getAbyss(), amount);
            if (taken > 0) {
                abyss = getAbyss() - taken;
                setChanged();
            }
            return taken;
        }

        public boolean hasRoom() {
            return getAbyss() < CAPACITY;
        }

        @Override
        public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            // зажимаем при загрузке: значение из файла сохранения недоверенное,
            // а отрицательный запас или запас выше ёмкости сломали бы арифметику
            // пула энергии бездны для всех игроков рядом
            abyss = Math.clamp(tag.getInt("abyss"), 0, CAPACITY);
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.putInt("abyss", getAbyss());
        }
    }
}