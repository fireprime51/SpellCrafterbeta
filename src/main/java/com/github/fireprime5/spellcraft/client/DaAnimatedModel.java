package com.github.fireprime5.spellcraft.client;

import com.github.fireprime5.spellcraft.Spellcraft;
import com.github.fireprime5.spellcraft.visual.DaAnimatedBlock;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** GeckoLib resource mapping for the user-provided DA model. */
public final class DaAnimatedModel extends GeoModel<DaAnimatedBlock.DaBlockEntity> {
    private static final ResourceLocation MODEL = Spellcraft.id("geo/da.geo.json");
    private static final ResourceLocation TEXTURE = Spellcraft.id("textures/entity/da.png");
    private static final ResourceLocation ANIMATIONS = Spellcraft.id("animations/da.animation.json");

    @Override
    public ResourceLocation getModelResource(DaAnimatedBlock.DaBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(DaAnimatedBlock.DaBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(DaAnimatedBlock.DaBlockEntity animatable) {
        return ANIMATIONS;
    }
}
