package com.github.fireprime5.spellcraft.registry;

import com.github.fireprime5.spellcraft.Spellcraft;
import com.github.fireprime5.spellcraft.energy.SmelterMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Меню интерфейсов мода.
 *
 * <p>Отдельный реестр нужен потому, что конструктор меню на клиенте не знает
 * позиции блока: сервер отправляет только идентификатор, а клиент собирает
 * меню заново уже без мира. Позиция приходит отдельно, когда игрок открывает
 * панель.
 */
public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Spellcraft.MODID);

    /** Меню плавильни. */
    public static final DeferredHolder<MenuType<?>, MenuType<SmelterMenu>> SMELTER =
            MENUS.register("smelter", () -> IMenuTypeExtension.create(
                    (windowId, inventory, data) ->
                            new SmelterMenu(windowId, inventory)));

    private ModMenus() {
    }

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}