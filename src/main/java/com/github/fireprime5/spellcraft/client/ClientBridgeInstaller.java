package com.github.fireprime5.spellcraft.client;

import com.github.fireprime5.spellcraft.network.ManaSyncPayload;
import com.github.fireprime5.spellcraft.network.OpenBuilderPayload;
import com.github.fireprime5.spellcraft.network.SpellResultPayload;
import com.github.fireprime5.spellcraft.network.SyncSpellGraphPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Устанавливает клиентскую реализацию обработчиков в ClientBridge.
 * Класс загружается только на клиенте.
 */
@EventBusSubscriber(modid = "spellcraft", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientBridgeInstaller {

    private ClientBridgeInstaller() {
    }

    /**
     * Клавиша листает палитру модулей. Обработка идёт на тике, а не в
     * обработчике нажатия: иначе переключение ловило бы и повтор клавиши при
     * удержании, и игрок прокликивал бы все восемь вариантов за одно нажатие.
     */
    private static final KeyMapping NEXT_PALETTE = new KeyMapping(
            "key.spellcraft.next_palette", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9,
            "key.categories.spellcraft");

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(NEXT_PALETTE);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!NEXT_PALETTE.consumeClick()) {
            return;
        }
        var config = com.github.fireprime5.spellcraft.config.SpellConfig.get();
        if (config.moduleHue >= 0) {
            // был выбран свободный оттенок: сначала возвращаемся к варианту
            config.moduleHue = -1;
            config.save();
            com.github.fireprime5.spellcraft.client.screen.PaletteCatalog.apply(config.modulePalette, null);
        }
        com.github.fireprime5.spellcraft.client.screen.PaletteCatalog.cycleAndReport(config);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // конфиг уже загружен в commonSetup — здесь применяем прозрачность и палитру
        com.github.fireprime5.spellcraft.config.SpellConfig.load();
        float opacity = com.github.fireprime5.spellcraft.config.SpellConfig.get().uiOpacity;
        com.github.fireprime5.spellcraft.client.screen.UiKit.applyOpacity(opacity);
        var config = com.github.fireprime5.spellcraft.config.SpellConfig.get();
        com.github.fireprime5.spellcraft.client.screen.PaletteCatalog.apply(config.modulePalette,
                config.moduleHue < 0 ? null : (double) config.moduleHue);

        ClientBridge.setHandler(new ClientBridge.Handler() {
            @Override
            public void onSyncGraph(SyncSpellGraphPayload payload) {
                Minecraft.getInstance().execute(() -> ClientSpellState.setGraph(payload.graph()));
            }

            @Override
            public void onSpellResult(SpellResultPayload payload) {
                Minecraft.getInstance().execute(() -> {
                    ClientSpellState.setLastResult(payload);
                    // узлы с ошибками тоже нужно синхронизировать: без этого
                    // красные рамки на canvas никогда не появлялись
                    ClientSpellState.setErrorNodes(
                            new java.util.LinkedHashSet<>(payload.errorNodes()));
                });
            }

            @Override
            public void onMana(ManaSyncPayload payload) {
                Minecraft.getInstance().execute(() -> {
                    ClientSpellState.setMana(payload.mana(),
                            payload.knownModules(), payload.knownModifiers(), payload.total());
                    ClientSpellState.setInfiniteMana(payload.infinite());
                });
            }

            @Override
            public void onLibrary(
                    com.github.fireprime5.spellcraft.network.LibrarySyncPayload payload) {
                Minecraft.getInstance().execute(() -> ClientSpellState.setLibrary(payload.names(),
                        payload.sizes()));
            }

            @Override
            public void onOpenBuilder(OpenBuilderPayload payload) {
                Minecraft.getInstance().execute(() -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.screen == null) {
                        mc.setScreen(new com.github.fireprime5.spellcraft.client.screen.SpellBuilderScreen());
                    }
                });
            }
        });
    }
}