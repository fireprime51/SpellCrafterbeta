package com.github.fireprime5.spellcraft;

import com.github.fireprime5.spellcraft.energy.AbyssEnergy;
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
 * <p>Мод — заклинания и всё, чем они живут: реестр модулей, исполнитель графа,
 * книга, энергия бездны, конструктор и библиотека. Вокруг них выросли машины
 * (плавильня, добытчики, сетки модулей), энергетическая сеть (провода, трубы,
 * сосуды, конвейер) и модульная броня — обработчики всех этих блоков
 * регистрируются здесь же.
 */
@Mod(Spellcraft.MODID)
public class SpellcraftMod {

    public SpellcraftMod(IEventBus modBus) {
        SpellModules.init();

        com.github.fireprime5.spellcraft.data.PlayerSpellStore.ATTACHMENTS.register(modBus);
        com.github.fireprime5.spellcraft.data.SpellLibrary.ATTACHMENTS.register(modBus);
        com.github.fireprime5.spellcraft.data.PlayerKnowledge.Attachments.REGISTRY.register(modBus);
        com.github.fireprime5.spellcraft.registry.ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModCreativeTabs.register(modBus);
        com.github.fireprime5.spellcraft.registry.ModMenus.register(modBus);
        com.github.fireprime5.spellcraft.armor.ModArmorComponents.REGISTRY.register(modBus);
        com.github.fireprime5.spellcraft.armor.ArmorEvents.register(NeoForge.EVENT_BUS);
        com.github.fireprime5.spellcraft.armor.ShieldEvents.register(NeoForge.EVENT_BUS);
        com.github.fireprime5.spellcraft.spell.FrozenEvents.register(NeoForge.EVENT_BUS);
        ModNetwork.register(modBus);

        modBus.addListener(this::commonSetup);
        modBus.addListener(this::registerCapabilities);
        NeoForge.EVENT_BUS.register(this);
        com.github.fireprime5.spellcraft.portal.PortalEvents.register(NeoForge.EVENT_BUS);
    }

    /**
     * Энергия машин.
     *
     * <p>Регистрируется на modBus, а не на общем: capability не должны
     * меняться после старта, и NeoForge специально разделяет эти шины.
     *
     * <p>Отдаём энергию всем сторонам, включая {@code null}: труба смотрит
     * сторону, а машина, стоящая в этот момент на другой грани, должна всё
     * равно получить энергию. Ограничение по стороне было бы источником
     * необъяснимых «иногда не работает».
     */
    private void registerCapabilities(
            net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                com.github.fireprime5.spellcraft.registry.ModBlocks.MANA_EXTRACTOR_ENTITY.get(),
                (extractor, side) -> extractor.energy());
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                com.github.fireprime5.spellcraft.registry.ModBlocks.DEBUG_ENERGY_SOURCE_ENTITY.get(),
                (source, side) -> source);
        // провод отдаёт и принимает: иначе труба из другого мода не смогла бы
        // ни залить в линию, ни вытянуть из неё
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                com.github.fireprime5.spellcraft.registry.ModBlocks.WIRE_ENTITY.get(),
                (сеть, side) -> сеть.energyStorage());

        // энергия плавильни. Её не было вообще: блок объявлял только
        // предметы, поэтому провод не мог его зарядить и машина молчала
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                com.github.fireprime5.spellcraft.registry.ModBlocks.SMELTER_ENTITY.get(),
                (smelter, side) -> smelter.energy());

        // предметы плавильни видны воронкам и трубам: без этого она работала бы
        // только вручную и в линию фабрики не вставала
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.github.fireprime5.spellcraft.registry.ModBlocks.SMELTER_ENTITY.get(),
                (smelter, side) -> smelter);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // конфиг нужен обеим сторонам: сервер — для баланса энергии бездны и заклинаний,
        // клиент — для прозрачности панелей
        com.github.fireprime5.spellcraft.config.SpellConfig.load();
        Spellcraft.LOGGER.info("Spellcraft: spell system loaded with {} modules, abyss capacity {}",
                SpellModules.all().size(), AbyssEnergy.capacity());
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        // при первом входе заполняем энергию бездны, иначе первые заклинания не кастуются
        AbyssEnergy.init(event.getEntity());
        assignUniqueModule(event.getEntity());
    }

    /**
     * Розыгрывает личный модуль при первом входе в мир.
     *
     * <p>Модуль назначается сразу, но игрок его не видит: в палитре он не
     * появляется, пока не отработает «Анализ себя». Без розыгрыша здесь
     * «Анализ себя» пришлось бы выдавать модуль на лету, и игрок, зашедший в
     * мир второй раз, получил бы другой — «Анализ себя» перестал бы быть
     * открытием и стал бы лотереей.
     */
    private static void assignUniqueModule(Player player) {
        var knowledge = com.github.fireprime5.spellcraft.data.PlayerKnowledge.get(player);
        if (!knowledge.unique().isEmpty()) {
            // розыгрыш в этом мире уже был: повторный бросок при каждом входе
            // менял бы личность игрока и делал «Анализ себя» лотереей
            return;
        }
        String assigned = com.github.fireprime5.spellcraft.data.PlayerKnowledge
                .assignUnique(player, player.getRandom());
        if (!assigned.isEmpty()) {
            Spellcraft.LOGGER.debug("Assigned personal module {} to {}",
                    assigned, player.getGameProfile().getId());
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        java.util.UUID id = event.getEntity().getGameProfile().getId();
        com.github.fireprime5.spellcraft.network.ModifierLearnPayload.cleanup(id);
        com.github.fireprime5.spellcraft.network.UpdateSpellGraphPayload.cleanup(id);
        com.github.fireprime5.spellcraft.network.RequestSpellGraphPayload.cleanup(id);
    }

    /** Энергия бездны не переносится при смерти сама — переносим вручную (см. AbyssEnergy). */
    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        AbyssEnergy.copyAbyss(event.getOriginal(), event.getEntity());
        // бесконечная энергия бездны тоже в persistentData, а он при смерти не
        // переносится: без этого смерть молча отключала бы режим, включённый
        // ради отладки схемы, и пришлось бы вводить команду заново
        AbyssEnergy.copyInfinite(event.getOriginal(), event.getEntity());
        if (event.isWasDeath()) {
            // смерть — единственный повод начинать с одной единицы; тот же
            // ивент приходит и при переносе между измерениями, где сбрасывать
            // кошелёк было бы неверно
            AbyssEnergy.onRespawn(event.getEntity());
        }
    }

    /**
     * Посещение измерения открывает модуль его адреса.
     *
     * <p>Портал ведёт туда, куда игрок уже доходил сам: без этого «Точка:
     * Незер» была бы способом поставить выход в любую точку карты, не
     * побывав там ни разу.
     */
    @SubscribeEvent
    public void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        com.github.fireprime5.spellcraft.data.PlayerKnowledge.markDimension(
                event.getEntity(),
                event.getEntity().level().dimension().location().toString());
    }

    /**
     * Воскрешение.
     *
     * <p>Смена измерения через портал приходит сюда же, и отличить её от
     * смерти в этом событии нельзя: у {@code PlayerRespawnEvent} есть только
     * флаг захода в Край. Поэтому сам сброс энергии бездны делается в
     * {@link #onPlayerClone}, где {@code isWasDeath()} доступен, а здесь
     * остаётся подрезка по потолку — иначе каждый переход через портал
     * обнулял бы кошелёк.
     */
    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        AbyssEnergy.init(event.getEntity());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        com.github.fireprime5.spellcraft.command.ModCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        AbyssEnergy.tickRegen(player);
        // нулевая энергия бездны — это не просто «не хватает на заклинание»: игрок
        // слабеет и через пять минут умирает
        com.github.fireprime5.spellcraft.energy.AbyssDecay.tick(player);
    }
}
