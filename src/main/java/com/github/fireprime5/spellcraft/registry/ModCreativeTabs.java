package com.github.fireprime5.spellcraft.registry;

import com.github.fireprime5.spellcraft.Spellcraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Spellcraft.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN =
            TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.spellcraft.main"))
                    .icon(() -> new ItemStack(ModItems.SPELLBOOK.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.SPELLBOOK.get());
                        output.accept(ModItems.MANA_EXTRACTOR.get());
                        output.accept(ModItems.WIRE.get());
                        // цветные провода: по одному на цвет, в порядке DyeColor.
                        // Список выписан явно, а не обходом карты, чтобы вкладка
                        // не зависела от порядка статической инициализации и
                        // чтобы каждый предмет был виден в исходнике
                        output.accept(ModItems.WIRE_WHITE.get());
                        output.accept(ModItems.WIRE_ORANGE.get());
                        output.accept(ModItems.WIRE_MAGENTA.get());
                        output.accept(ModItems.WIRE_LIGHT_BLUE.get());
                        output.accept(ModItems.WIRE_YELLOW.get());
                        output.accept(ModItems.WIRE_LIME.get());
                        output.accept(ModItems.WIRE_PINK.get());
                        output.accept(ModItems.WIRE_GRAY.get());
                        output.accept(ModItems.WIRE_LIGHT_GRAY.get());
                        output.accept(ModItems.WIRE_CYAN.get());
                        output.accept(ModItems.WIRE_PURPLE.get());
                        output.accept(ModItems.WIRE_BLUE.get());
                        output.accept(ModItems.WIRE_BROWN.get());
                        output.accept(ModItems.WIRE_GREEN.get());
                        output.accept(ModItems.WIRE_RED.get());
                        output.accept(ModItems.WIRE_BLACK.get());
                        output.accept(ModItems.MANA_VESSEL.get());
                        output.accept(ModItems.SMELTER.get());
                        output.accept(ModItems.MODULAR_CHESTPLATE.get());
                        output.accept(ModItems.ABYSS_TANK.get());
                        output.accept(ModItems.DA_ANIMATED.get());
                        output.accept(ModItems.FLUID_PIPE.get());
                        output.accept(ModItems.CONVEYOR.get());
                        // отладочный источник — только по явному ключу в конфиге
                        if (com.github.fireprime5.spellcraft.config.SpellConfig.get()
                                .debugEnergySource) {
                            output.accept(ModItems.DEBUG_ENERGY_SOURCE.get());
                        }
                    })
                    .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
