package com.github.fireprime5.spellcraft.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Client runtime events; unlike registration events, these belong to NeoForge's game bus. */
@EventBusSubscriber(modid = "spellcraft", value = Dist.CLIENT)
public final class ClientRuntimeEvents {
    private ClientRuntimeEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ClientBridgeInstaller.NEXT_PALETTE.consumeClick()) {
            return;
        }
        var config = com.github.fireprime5.spellcraft.config.SpellConfig.get();
        if (config.moduleHue >= 0) {
            config.moduleHue = -1;
            config.save();
            com.github.fireprime5.spellcraft.client.screen.PaletteCatalog.apply(
                    config.modulePalette, null);
        }
        com.github.fireprime5.spellcraft.client.screen.PaletteCatalog.cycleAndReport(config);
    }
}
