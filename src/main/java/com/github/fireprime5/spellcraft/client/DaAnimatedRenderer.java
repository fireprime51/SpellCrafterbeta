package com.github.fireprime5.spellcraft.client;

import com.github.fireprime5.spellcraft.registry.ModBlocks;
import com.github.fireprime5.spellcraft.visual.DaAnimatedBlock;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/** Client renderer for the animated DA test block. */
public final class DaAnimatedRenderer extends GeoBlockRenderer<DaAnimatedBlock.DaBlockEntity> {
    public DaAnimatedRenderer() {
        super(new DaAnimatedModel());
    }
}
