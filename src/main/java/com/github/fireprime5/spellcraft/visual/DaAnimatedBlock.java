package com.github.fireprime5.spellcraft.visual;

import com.github.fireprime5.spellcraft.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Тестовый блок для просмотра пользовательской модели и анимации GeckoLib. */
public class DaAnimatedBlock extends BaseEntityBlock {

    public static final MapCodec<DaAnimatedBlock> CODEC = simpleCodec(DaAnimatedBlock::new);
    private static final VoxelShape SHAPE = Shapes.block();

    public DaAnimatedBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // Геометрию рисует GeoBlockRenderer, а не ванильная block model.
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DaBlockEntity(pos, state);
    }

    public static final class DaBlockEntity extends BlockEntity implements GeoBlockEntity {
        private static final RawAnimation DA_ANIMATION =
                RawAnimation.begin().thenLoop("animation");
        private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

        public DaBlockEntity(BlockPos pos, BlockState state) {
            super(ModBlocks.DA_ANIMATED_ENTITY.get(), pos, state);
        }

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
            controllers.add(new AnimationController<>(this, "da_animation", 0, state -> {
                state.getController().setAnimation(DA_ANIMATION);
                return PlayState.CONTINUE;
            }));
        }

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return animationCache;
        }

        @Override
        public double getTick(Object identifier) {
            return level == null ? 0 : level.getGameTime();
        }
    }
}
