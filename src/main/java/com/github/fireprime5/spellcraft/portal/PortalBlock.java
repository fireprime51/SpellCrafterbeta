package com.github.fireprime5.spellcraft.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.util.RandomSource;

import java.util.UUID;

/**
 * Блок портала: один блок, который переносит вошедшего в другое место.
 *
 * <p>Одиночный блок, а не рамка как у портала Незера. Причины:
 *
 * <ul>
 *   <li>Создаётся кастом, а не предметом из крафта: рамку пришлось бы
 *       выкладывать вручную или возить с собой предметы.</li>
 *   <li>Размер модуля в схеме не должен зависеть от того, как игрок догадается
 *       поставить блоки по кругу.</li>
 * </ul>
 *
 * <p>Сам блок ничего не знает о своей паре — только свой идентификатор. Всё
 * остальное лежит в {@link PortalRegistry}, общем для всех измерений.
 */
public class PortalBlock extends BaseEntityBlock {

    /** Чуть меньше блока, чтобы в портал нельзя было пролезть внутрь. */
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);

    public static final MapCodec<PortalBlock> CODEC = simpleCodec(PortalBlock::new);

    public PortalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                               BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return false;
    }

    /**
     * Вход сущности в блок.
     *
     * <p>В 1.21.1 у NeoForge нет {@code EntityInsideBlockEvent} (он появился
     * позже), поэтому переход сделан на ванильном {@code entityInside}: игра
     * зовёт его каждому блоку, под которым стоит сущность, раз в тик.
     *
     * <p>Раз в тик звучит часто, но от повторных срабатываний спасает отметка
     * о последнем проходе в {@link PortalRegistry}: сущность, только что вышедшая
     * из этого же портала, секунду не переносится снова.
     */
    @Override
    protected void entityInside(BlockState state, net.minecraft.world.level.Level level,
                                BlockPos pos, net.minecraft.world.entity.Entity entity) {
        if (level.isClientSide || !(level instanceof ServerLevel server)) {
            return;
        }
        PortalBlockEntity portal = getPortal(level, pos);
        if (portal == null || portal.getPortalId() == null) {
            return;
        }
        PortalRegistry registry = PortalRegistry.get(server.getServer());
        if (registry == null) {
            return;
        }
        PortalRegistry.Entry entry = registry.at(PortalEvents.dimensionKey(server), pos);
        if (entry == null) {
            return;
        }
        long time = server.getGameTime();
        String dim = PortalEvents.dimensionKey(server);
        String exitDim = entry.exitDim(dim, pos);
        if (exitDim == null || exitDim.isEmpty()) {
            return;
        }
        // срабатывает только вход, а не каждое нахождение внутри: иначе
        // двусторонние врата гоняют игрока между концами, не стоя на месте
        if (!PortalRegistry.shouldEnter(entity.getUUID(), entry.id(), pos, time)) {
            return;
        }
        try {
            PortalRegistry.travel(entity, exitDim, entry.exitPos(dim, pos), server);
        } catch (RuntimeException e) {
            com.github.fireprime5.spellcraft.Spellcraft.LOGGER.warn(
                    "portal {} failed to move {}: {}", entry.id(), entity.getUUID(), e.toString());
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PortalBlockEntity(pos, state);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean movedByPiston) {
        // снос блока = снос портала; иначе в мире осталась бы запись
        // в реестре и пустая точка входа
        if (!state.is(newState.getBlock())) {
            PortalBlockEntity portal = getPortal(level, pos);
            if (portal != null && level.getServer() != null) {
                PortalRegistry registry = PortalRegistry.get(level.getServer());
                if (registry != null) {
                    PortalRegistry.Entry entry = registry.remove(portal.getPortalId());
                    if (entry != null) {
                        // у двустороннего портала надо снести и второй конец
                        unplace(level, entry.fromDim(), entry.from(), entry.id());
                        if (entry.hasReturn()) {
                            unplace(level, entry.toDim(), entry.to(), entry.id());
                        }
                    }
                }
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }

    /** Убирает блок портала, не задевая остальные блоки рядом. */
    private static void unplace(Level level, String dim, BlockPos pos, UUID id) {
        if (level instanceof ServerLevel server) {
            PortalRegistry registry = PortalRegistry.get(server.getServer());
            if (registry != null && registry.at(dim, pos) == null) {
                server.destroyBlock(pos, false);
                return;
            }
        }
        if (level.dimension().location().toString().equals(dim)) {
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
        }
    }

    static PortalBlockEntity getPortal(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof PortalBlockEntity portal) {
            return portal;
        }
        return null;
    }

    /** Частицы: портал должен быть виден, иначе это просто невидимый блок. */
    @Override
    public void animateTick(BlockState state, net.minecraft.world.level.Level level,
                            BlockPos pos, RandomSource random) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.9;
        double y = pos.getY() + random.nextDouble() * 0.9;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.9;
        server.sendParticles(ParticleTypes.PORTAL, x, y, z, 4, 0.15, 0.15, 0.15, 0.0);
    }

    /** Блок-сущность портала. Знает только свой идентификатор. */
    public static class PortalBlockEntity extends BlockEntity {

        private UUID portalId;

        public PortalBlockEntity(BlockPos pos, BlockState state) {
            super(com.github.fireprime5.spellcraft.registry.ModBlocks.PORTAL_ENTITY.get(),
                pos, state);
        }

        public UUID getPortalId() {
            return portalId;
        }

        public void setPortalId(UUID id) {
            this.portalId = id;
            setChanged();
        }

        @Override
        protected void saveAdditional(CompoundTag tag,
                                      net.minecraft.core.HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            if (portalId != null) {
                tag.putUUID("portal", portalId);
            }
        }

        @Override
        public void loadAdditional(CompoundTag tag,
                                   net.minecraft.core.HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            portalId = tag.hasUUID("portal") ? tag.getUUID("portal") : null;
        }
    }
}