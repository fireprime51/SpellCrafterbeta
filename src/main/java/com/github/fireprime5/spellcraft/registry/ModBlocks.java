package com.github.fireprime5.spellcraft.registry;

import com.github.fireprime5.spellcraft.Spellcraft;
import com.github.fireprime5.spellcraft.energy.DebugEnergySourceBlock;
import com.github.fireprime5.spellcraft.energy.AbyssExtractorBlock;
import com.github.fireprime5.spellcraft.energy.AbyssVesselBlock;
import com.github.fireprime5.spellcraft.energy.SmelterBlock;
import com.github.fireprime5.spellcraft.fluid.AbyssTankBlock;
import com.github.fireprime5.spellcraft.fluid.ConveyorBlock;
import com.github.fireprime5.spellcraft.network.NetworkBlockEntity;
import com.github.fireprime5.spellcraft.network.WireBlock;
import com.github.fireprime5.spellcraft.portal.PortalBlock;
import com.github.fireprime5.spellcraft.visual.DaAnimatedBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Блоки мода.
 *
 * <p>Блок один — портал. В творческой вкладке его нет намеренно: портал
 * ставится кастом, а не кладётся в сумку, поэтому и выдавать его вручную
 * незачем.
 */
public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(Spellcraft.MODID);

    /** Типы блочных сущностей — отдельным реестром, так устроен NeoForge. */
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Spellcraft.MODID);

    public static final DeferredHolder<Block, PortalBlock> PORTAL = BLOCKS.register("portal",
            () -> new PortalBlock(BlockBehaviour.Properties.of()
                    .noCollission()
                    // прочности 0: портал ломается рукой с первого удара,
                    // иначе «до сноса» превратилось бы в очередь молотов
                    .strength(0.0F)
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> 11)
                    .noOcclusion()));

    public static final Supplier<BlockEntityType<PortalBlock.PortalBlockEntity>> PORTAL_ENTITY =
            BLOCK_ENTITIES.register("portal", () -> BlockEntityType.Builder
                    .<PortalBlock.PortalBlockEntity>of(PortalBlock.PortalBlockEntity::new,
                            ModBlocks.PORTAL.get())
                    .build(null));

    /**
     * Извлекатель энергии бездны: единственный блок, дающий энергию бездны без заклинания.
     *
     * <p>Есть и в креативной вкладке — это обычный контент, его можно
     * разобрать и поставить снова.
     */
    public static final DeferredHolder<Block, AbyssExtractorBlock> MANA_EXTRACTOR =
            BLOCKS.register("mana_extractor", () -> new AbyssExtractorBlock(
                    BlockBehaviour.Properties.of()
                            .strength(3.0F)
                            .sound(SoundType.AMETHYST)
                            .lightLevel(state -> 6)
                            .requiresCorrectToolForDrops()
                            .noOcclusion()));

    public static final Supplier<BlockEntityType<AbyssExtractorBlock.ExtractorBlockEntity>>
            MANA_EXTRACTOR_ENTITY = BLOCK_ENTITIES.register("mana_extractor",
            () -> BlockEntityType.Builder
                    .<AbyssExtractorBlock.ExtractorBlockEntity>of(
                            AbyssExtractorBlock.ExtractorBlockEntity::new,
                            ModBlocks.MANA_EXTRACTOR.get())
                    .build(null));

    /**
     * Отладочный источник энергии.
     *
     * <p>Существует всегда, но выдаётся предмет только при
     * {@code debugEnergySource} в конфиге: в обычной игре блок, производящий
     * энергию из воздуха, был бы эксплойтом.
     */
    public static final DeferredHolder<Block, DebugEnergySourceBlock> DEBUG_ENERGY_SOURCE =
            BLOCKS.register("debug_energy_source", () -> new DebugEnergySourceBlock(
                    BlockBehaviour.Properties.of()
                            .strength(1.0F)
                            .sound(SoundType.COPPER)
                            .lightLevel(state -> 14)
                            .noOcclusion()));

    public static final Supplier<BlockEntityType<DebugEnergySourceBlock.DebugEnergyBlockEntity>>
            DEBUG_ENERGY_SOURCE_ENTITY = BLOCK_ENTITIES.register("debug_energy_source",
            () -> BlockEntityType.Builder
                    .<DebugEnergySourceBlock.DebugEnergyBlockEntity>of(
                            DebugEnergySourceBlock.DebugEnergyBlockEntity::new,
                            ModBlocks.DEBUG_ENERGY_SOURCE.get())
                    .build(null));

    /**
     * Провод: тонкий блок, передающий энергию по линии.
     *
     * <p>Прочности 0: провод ставят и снимают рукой, ломать молотом его
     * незачем. Стойкости и столкновений нет — сквозь провод проходят.
     *
     * <p>Без {@code replaceable()} и без {@code liquid()}. Обе эти метки были
     * в первой версии блока, и обе ломали провод: {@code replaceable} запрещает
     * ставить блок в уже занятое место, а значит второй провод в линию не
     * вставал вовсе; {@code liquid()} разрешал воде вытеснить провод. Отсюда
     * были жалобы, что линия «то подключается, то нет» — половина сегментов
     * просто не ставилась.
     */
    public static final DeferredHolder<Block, WireBlock> WIRE =
            BLOCKS.register("wire", () -> new WireBlock(
                    BlockBehaviour.Properties.of()
                            .noCollission()
                            .noOcclusion()
                            .strength(0.0F)
                            .sound(SoundType.WOOL)));

    /**
     * Сосуд для энергии бездны: накопитель, к которому сводятся соседние.
     *
     * <p>Прочность средняя: сосуд стоит на полу и должен переживать случайные
     * удары, в отличие от провода, который кладут и вытаскивают рукой.
     */
    public static final DeferredHolder<Block, AbyssVesselBlock> MANA_VESSEL =
            BLOCKS.register("mana_vessel", () -> new AbyssVesselBlock(
                    BlockBehaviour.Properties.of()
                            .strength(1.5F)
                            .sound(SoundType.AMETHYST)
                            .lightLevel(state -> 4)
                            .noOcclusion()));

    public static final Supplier<BlockEntityType<AbyssVesselBlock.VesselBlockEntity>>
            MANA_VESSEL_ENTITY = BLOCK_ENTITIES.register("mana_vessel",
            () -> BlockEntityType.Builder
                    .<AbyssVesselBlock.VesselBlockEntity>of(
                            AbyssVesselBlock.VesselBlockEntity::new,
                            ModBlocks.MANA_VESSEL.get())
                    .build(null));

    /**
     * Плавильня: переплавляет предметы за энергию.
     *
     * <p>Светлее портала — чтобы в сумерках читалась как рабочая машина, а не
     * как часть декорации.
     */
    public static final DeferredHolder<Block, SmelterBlock> SMELTER =
            BLOCKS.register("smelter", () -> new SmelterBlock(
                    BlockBehaviour.Properties.of()
                            .strength(3.5F)
                            .requiresCorrectToolForDrops()
                            .sound(SoundType.STONE)
                            .lightLevel(state -> 13)
                            .noOcclusion()));

    public static final Supplier<BlockEntityType<SmelterBlock.SmelterBlockEntity>> SMELTER_ENTITY =
            BLOCK_ENTITIES.register("smelter", () -> BlockEntityType.Builder
                    .<SmelterBlock.SmelterBlockEntity>of(
                            SmelterBlock.SmelterBlockEntity::new,
                            ModBlocks.SMELTER.get())
                    .build(null));

    /** Бак для энергии бездны: единственное место, где она копится в объёме. */
    public static final DeferredHolder<Block, AbyssTankBlock> ABYSS_TANK =
            BLOCKS.register("abyss_tank", () -> new AbyssTankBlock(
                    BlockBehaviour.Properties.of()
                            .strength(3.0F)
                            .requiresCorrectToolForDrops()
                            .sound(SoundType.GLASS)
                            .lightLevel(state -> 3)
                            .noOcclusion()));

    public static final Supplier<BlockEntityType<AbyssTankBlock.TankBlockEntity>> ABYSS_TANK_ENTITY =
            BLOCK_ENTITIES.register("abyss_tank", () -> BlockEntityType.Builder
                    .<AbyssTankBlock.TankBlockEntity>of(
                            AbyssTankBlock.TankBlockEntity::new, ModBlocks.ABYSS_TANK.get())
                    .build(null));

    /** Тестовый блок для проверки пользовательской GeckoLib-модели и анимации. */
    public static final DeferredHolder<Block, DaAnimatedBlock> DA_ANIMATED =
            BLOCKS.register("da_animated", () -> new DaAnimatedBlock(
                    BlockBehaviour.Properties.of()
                            .strength(1.0F)
                            .sound(SoundType.AMETHYST)
                            .noOcclusion()));

    public static final Supplier<BlockEntityType<DaAnimatedBlock.DaBlockEntity>>
            DA_ANIMATED_ENTITY = BLOCK_ENTITIES.register("da_animated",
            () -> BlockEntityType.Builder
                    .<DaAnimatedBlock.DaBlockEntity>of(
                            DaAnimatedBlock.DaBlockEntity::new, ModBlocks.DA_ANIMATED.get())
                    .build(null));

    /** Транспортёр: переносит предметы в сторону, куда смотрит. */
    public static final DeferredHolder<Block, ConveyorBlock> CONVEYOR =
            BLOCKS.register("conveyor", () -> new ConveyorBlock(
                    BlockBehaviour.Properties.of()
                            .noCollission()
                            .noOcclusion()
                            .strength(0.0F)
                            .sound(SoundType.WOOL)));

    public static final Supplier<BlockEntityType<ConveyorBlock.ConveyorBlockEntity>> CONVEYOR_ENTITY =
            BLOCK_ENTITIES.register("conveyor", () -> BlockEntityType.Builder
                    .<ConveyorBlock.ConveyorBlockEntity>of(
                            ConveyorBlock.ConveyorBlockEntity::new, ModBlocks.CONVEYOR.get())
                    .build(null));

    /**
     * Блок-сущность сети: в ней живут линии, их подключения и запасы.
     *
     * <p>Раньше здесь жил запас одного провода, а подключения лежали в свойствах
     * блока. Теперь в блоке до трёх линий, и свойствами их не выразить: шесть
     * бит на линию плюс цвета в blockstate не помещаются.
     */
    public static final Supplier<BlockEntityType<NetworkBlockEntity>> WIRE_ENTITY =
            BLOCK_ENTITIES.register("wire", () -> BlockEntityType.Builder
                    .of(NetworkBlockEntity::new, ModBlocks.WIRE.get())
                    .build(null));

    private ModBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }
}
