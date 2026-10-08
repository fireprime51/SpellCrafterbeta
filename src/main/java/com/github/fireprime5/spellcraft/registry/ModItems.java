package com.github.fireprime5.spellcraft.registry;

import com.github.fireprime5.spellcraft.Spellcraft;
import com.github.fireprime5.spellcraft.item.SpellbookItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Spellcraft.MODID);

    /** Гримуар: единственный предмет мода, им кастуют и в нём же открывают билдер. */
    public static final Supplier<SpellbookItem> SPELLBOOK = ITEMS.register("spellbook",
            () -> new SpellbookItem(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}