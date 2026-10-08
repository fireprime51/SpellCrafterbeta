package com.github.fireprime5.spellcraft;

import com.github.fireprime5.spellcraft.energy.ManaCapability;
import com.github.fireprime5.spellcraft.network.ModNetwork;
import com.github.fireprime5.spellcraft.registry.ModCreativeTabs;
import com.github.fireprime5.spellcraft.registry.ModItems;
import com.github.fireprime5.spellcraft.spell.SpellModules;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Точка входа мода.
 *
 * <p>Мод — только заклинания: реестр модулей, исполнитель графа, книга,
 * мана, конструктор и библиотека заклинаний. Станций, сети машин и карты в
 * проекте нет, поэтому и обработчиков их обслуживания тоже нет.
 */
@Mod(Spellcraft.MODID)
public class SpellcraftMod {

    public SpellcraftMod(IEventBus modBus) {
        SpellModules.init();

        com.github.fireprime5.spellcraft.data.PlayerSpellStore.ATTACHMENTS.register(modBus);
        com.github.fireprime5.spellcraft.data.SpellLibrary.ATTACHMENTS.register(modBus);
        com.github.fireprime5.spellcraft.data.PlayerKnowledge.Attachments.REGISTRY.register(modBus);
        ModItems.register(modBus);
        ModCreativeTabs.register(modBus);
        ModNetwork.register(modBus);

        modBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.register(this);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // конфиг нужен обеим сторонам: сервер — для баланса маны и заклинаний,
        // клиент — для прозрачности панелей
        com.github.fireprime5.spellcraft.config.SpellConfig.load();
        Spellcraft.LOGGER.info("Spellcraft: spell system loaded with {} modules, mana capacity {}",
                SpellModules.all().size(), ManaCapability.capacity());
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        // при первом входе заполняем ману, иначе первые заклинания не кастуются
        ManaCapability.init(event.getEntity());
    }

    /** Мана не переносится при смерти сама — переносим вручную (см. ManaCapability). */
    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        ManaCapability.copyMana(event.getOriginal(), event.getEntity());
        // бесконечная мана тоже в persistentData, а он при смерти не
        // переносится: без этого смерть молча отключала бы режим, включённый
        // ради отладки схемы, и пришлось бы вводить команду заново
        ManaCapability.copyInfinite(event.getOriginal(), event.getEntity());
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        ManaCapability.init(event.getEntity());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        com.github.fireprime5.spellcraft.command.ModCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        ManaCapability.tickRegen(player);
    }
}