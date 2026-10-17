package com.github.fireprime5.spellcraft.client;

import com.github.fireprime5.spellcraft.network.AbyssEnergySyncPayload;
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
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
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
    static final KeyMapping NEXT_PALETTE = new KeyMapping(
            "key.spellcraft.next_palette", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9,
            "key.categories.spellcraft");

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(NEXT_PALETTE);
    }

    /** Экран плавильни вешается на её меню. */
    @SubscribeEvent
    public static void onMenuScreens(
            net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(com.github.fireprime5.spellcraft.registry.ModMenus.SMELTER.get(),
                com.github.fireprime5.spellcraft.client.screen.SmelterScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterBlockEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                com.github.fireprime5.spellcraft.registry.ModBlocks.DA_ANIMATED_ENTITY.get(),
                context -> new DaAnimatedRenderer());
        // линии сети рисуются рендерером: в свойствах блока их набор не помещается
        event.registerBlockEntityRenderer(
                com.github.fireprime5.spellcraft.registry.ModBlocks.WIRE_ENTITY.get(),
                context -> new com.github.fireprime5.spellcraft.client.WireRenderer());
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
            public void onMana(AbyssEnergySyncPayload payload) {
                Minecraft.getInstance().execute(() -> {
                    ClientSpellState.setAbyss(payload.abyss(), payload.maxAbyss(),
                            payload.knownModules(), payload.knownModifiers(), payload.total());
                    ClientSpellState.setInfiniteAbyss(payload.infinite());
                });
            }

            @Override
            public void onLibrary(
                    com.github.fireprime5.spellcraft.network.LibrarySyncPayload payload) {
                Minecraft.getInstance().execute(() -> ClientSpellState.setLibrary(payload.names(),
                        payload.sizes()));
            }

            @Override
            public void onSyncMachineGrid(
                    com.github.fireprime5.spellcraft.network.SyncMachineGridPayload payload) {
                // Обработчики пакетов идут не из основного потока, а состояние
                // конструктора читает уже главный поток. Без execute() граф мог
                // прийти позже открытия экрана — и конструктор открылся бы пустым
                Minecraft.getInstance().execute(() -> com.github.fireprime5.spellcraft.client
                        .ClientMachineState.set(payload.pos(), payload.kind(),
                                payload.tag().isEmpty()
                                        ? new com.github.fireprime5.spellcraft.spell.SpellGraph()
                                        : com.github.fireprime5.spellcraft.spell.SpellGraph
                                                .load(payload.tag())));
            }

            @Override
            public void onSyncArmorGraph(
                    com.github.fireprime5.spellcraft.network.SyncArmorGraphPayload payload) {
                Minecraft.getInstance().execute(() -> com.github.fireprime5.spellcraft.client
                        .ClientArmorState.set(payload.hand(),
                                payload.tag().isEmpty()
                                        ? new com.github.fireprime5.spellcraft.spell.SpellGraph()
                                        : com.github.fireprime5.spellcraft.spell.SpellGraph
                                                .load(payload.tag())));
            }

            @Override
            public void onOpenMachineGrid(
                    com.github.fireprime5.spellcraft.network.OpenMachineGridPayload payload) {
                Minecraft.getInstance().execute(() -> openScreen(
                        new com.github.fireprime5.spellcraft.client.screen.SpellBuilderScreen(
                                payload.pos(), payload.kind())));
            }

            @Override
            public void onOpenBuilder(OpenBuilderPayload payload) {
                Minecraft.getInstance().execute(() ->
                        openScreen(new com.github.fireprime5.spellcraft.client.screen.SpellBuilderScreen()));
            }

            @Override
            public void onOpenArmorBuilder(
                    com.github.fireprime5.spellcraft.network.OpenArmorBuilderPayload payload) {
                Minecraft.getInstance().execute(() -> openScreen(
                        com.github.fireprime5.spellcraft.client.screen.SpellBuilderScreen
                                .forArmor(payload.hand())));
            }
        });
    }

    /**
     * Открывает конструктор, закрывая то, что открыто сейчас.
     *
     * <p>Раньше стояло {@code if (mc.screen == null)}, и из-за этого нагрудник
     * не открывался по правому клику в инвентаре: в момент ответа сервера
     * экран инвентаря был открыт, условие не выполнялось, и пакет просто терялся.
     * Игрок видел, что нажал, и ничего не происходило.
     *
     * <p>Теперь открытый экран просто заменяется. Особый случай — уже открытый
     * конструктор: его не пересоздаём, иначе правка схемы, сделанная до
     * подтверждения сервера, потерялась бы.
     */
    private static void openScreen(net.minecraft.client.gui.screens.Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == screen) {
            return;
        }
        if (mc.screen instanceof com.github.fireprime5.spellcraft.client.screen.SpellBuilderScreen) {
            // уже в конструкторе — второй ответ не должен переоткрывать его
            return;
        }
        mc.setScreen(screen);
    }
}
