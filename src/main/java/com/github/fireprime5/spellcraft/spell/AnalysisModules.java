package com.github.fireprime5.spellcraft.spell;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.List;
import java.util.Map;

import static com.github.fireprime5.spellcraft.spell.PortType.ENTITY;
import static com.github.fireprime5.spellcraft.spell.PortType.NUMBER;
import static com.github.fireprime5.spellcraft.spell.PortType.POINT;

/**
 * Модули «Анализ» и «Извлечение» — основа прогрессии и энергии.
 *
 * Анализ: сканирует блок или существо, даёт очки прогресса и открывает новые
 * модули. Извлечение: забирает энергию бездны из блока или существа (у каждого своя цена).
 * Оба модуля бесплатны по энергии бездны — иначе нельзя было бы начать.
 */
public final class AnalysisModules {

    private AnalysisModules() {
    }

    private static final SpellModuleType.Category ENT = SpellModuleType.Category.ENTITIES;
    private static final int C_SCAN = 0xFF6FB3D2;
    private static final int C_EXTRACT = 0xFFD2A65A;

    /** Сколько энергии бездны даёт тот или иной блок/моб (таблицы по id). */
    private static final java.util.Map<String, Integer> BLOCK_MANA = java.util.Map.ofEntries(
            java.util.Map.entry("stone", 8),
            java.util.Map.entry("dirt", 5),
            java.util.Map.entry("grass_block", 5),
            java.util.Map.entry("sand", 6),
            java.util.Map.entry("gravel", 6),
            java.util.Map.entry("oak_log", 10),
            java.util.Map.entry("oak_planks", 10),
            java.util.Map.entry("glass", 9),
            java.util.Map.entry("coal_ore", 40),
            java.util.Map.entry("iron_ore", 55),
            java.util.Map.entry("copper_ore", 55),
            java.util.Map.entry("gold_ore", 90),
            java.util.Map.entry("redstone_ore", 110),
            java.util.Map.entry("lapis_ore", 75),
            java.util.Map.entry("diamond_ore", 180),
            java.util.Map.entry("emerald_ore", 180),
            java.util.Map.entry("deepslate", 14),
            java.util.Map.entry("obsidian", 150),
            java.util.Map.entry("ancient_debris", 400),
            java.util.Map.entry("crafting_table", 25),
            java.util.Map.entry("furnace", 25),
            java.util.Map.entry("enchanting_table", 60),
            java.util.Map.entry("beacon", 250),
            java.util.Map.entry("bedrock", 0),
            java.util.Map.entry("water", 4),
            java.util.Map.entry("lava", 8)
    );

    public static void registerAll() {
        // ---------------- Анализ ----------------

        SpellModules.register(new SpellModuleType(SpellModules.id("analysis_block"), ENT, C_SCAN) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 0; }
            @Override public PortType[] inputPorts() { return new PortType[]{POINT}; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                if (!(ctx.caster() instanceof net.minecraft.world.entity.player.Player player)) {
                    throw new SpellContext.SpellException("cast.not_player");
                }
                BlockPos pos = in.get(0).asBlockPos();
                if (!(ctx.level() instanceof ServerLevel server)) {
                    throw new SpellContext.SpellException("cast.server_only");
                }
                // сканировать имеет смысл только то, до чего можно дотянуться:
                // блок в любом чанке мира — это не «наблюдение», а свободный
                // доступ к модулям со всей карты
                ctx.requireInRange(pos);
                BlockState state = server.getBlockState(pos);
                if (state.isAir()) {
                    throw new SpellContext.SpellException("cast.analysis_empty");
                }
                String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .getKey(state.getBlock()).toString();
                out.add(SpellValue.of(
                        com.github.fireprime5.spellcraft.spell.KnowledgeHooks.scan(player, id, ctx)));
            }
        });

        SpellModules.register(new SpellModuleType(SpellModules.id("analysis_mob"), ENT, C_SCAN) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 0; }
            @Override public PortType[] inputPorts() { return new PortType[]{ENTITY}; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                if (!(ctx.caster() instanceof net.minecraft.world.entity.player.Player player)) {
                    throw new SpellContext.SpellException("cast.not_player");
                }
                Entity target = in.get(0).asEntity();
                if (target instanceof net.minecraft.world.entity.player.Player) {
                    throw new SpellContext.SpellException("cast.analysis_player");
                }
                ctx.requireEntityInRange(target);
                String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                        .getKey(target.getType()).toString();
                out.add(SpellValue.of(
                        com.github.fireprime5.spellcraft.spell.KnowledgeHooks.scan(player, id, ctx)));
            }
        });

        // «Анализ себя»: выдаёт личный модуль игрока.
        //
        // Отдельный модуль, а не флаг у analysis_mob, потому что сканировать
        // игрока запрещено (см. analysis_mob ниже): тот модуль отдаёт награды за
        // блоки и мобов из общей таблицы, а тут нужен доступ к самому игроку.
        SpellModules.register(new SpellModuleType(SpellModules.id("analysis_self"), ENT, C_SCAN) {
            @Override public int inputs() { return 0; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 0; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                if (!(ctx.caster() instanceof net.minecraft.world.entity.player.Player player)) {
                    throw new SpellContext.SpellException("cast.not_player");
                }
                if (!(ctx.caster().level() instanceof ServerLevel)) {
                    throw new SpellContext.SpellException("cast.not_player");
                }
                String opened = com.github.fireprime5.spellcraft.data.PlayerKnowledge
                        .discoverUnique(player);
                if (opened.isEmpty()) {
                    // личный модуль ровно один и открывается он один раз:
                    // повторный анализ должен сказать об этом, а не молча
                    // открыть второй
                    throw new SpellContext.SpellException("cast.analysis_self");
                }
                ctx.trace("self analysis -> " + opened);
                // Говорим игроку, ЧТО он нашёл. Раньше успешный каст не писал в чат
                // ничего («успех ничем не шумит»), и открытый модуль был виден только
                // тому, кто догадался перейти в другую вкладку палитры: выглядело так,
                // будто «Анализ себя» не сработал.
                var openedType = SpellModules.get(
                        com.github.fireprime5.spellcraft.Spellcraft.id(opened));
                var found = net.minecraft.network.chat.Component.translatable(
                        "message.spellcraft.unique_found",
                        openedType == null
                                ? net.minecraft.network.chat.Component.literal(opened)
                                : net.minecraft.network.chat.Component.translatable(openedType.nameKey()));
                player.sendSystemMessage(found);
                player.displayClientMessage(found, true);
                // на выходе — личная подпись: она у каждого своя, и по ней видно,
                // что «Анализ себя» отработал именно для этого игрока
                out.add(SpellValue.of(
                        UniqueModules.signatureOf(player)));
            }
        });

        // ---------------- Извлечение ----------------

        SpellModules.register(new SpellModuleType(SpellModules.id("extract_block"), ENT, C_EXTRACT) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 0; }
            @Override public PortType[] inputPorts() { return new PortType[]{POINT}; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                if (!(ctx.caster() instanceof net.minecraft.world.entity.player.Player player)) {
                    throw new SpellContext.SpellException("cast.not_player");
                }
                BlockPos pos = in.get(0).asBlockPos();
                if (!(ctx.level() instanceof ServerLevel server)) {
                    throw new SpellContext.SpellException("cast.server_only");
                }
                // иначе энергию бездны можно было бы вытягивать из ancient_debris (400)
                // в любой точке карты, не доходя до неё
                ctx.requireInRange(pos);
                BlockState state = server.getBlockState(pos);
                if (state.isAir()) {
                    throw new SpellContext.SpellException("cast.extract_empty");
                }
                FluidState fluid = state.getFluidState();
                String drainKey = "b:" + pos.toShortString();
                if (!fluid.isEmpty() && fluid.isSource()) {
                    int amount = drainMana(player, 8, drainKey);
                    ctx.trace("extract fluid +" + amount);
                    out.add(SpellValue.of(amount));
                    return;
                }
                int value = manaValueOf(state, net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .getKey(state.getBlock()).toString());
                if (value <= 0) {
                    throw new SpellContext.SpellException("cast.extract_nothing",
                            net.minecraft.core.registries.BuiltInRegistries.BLOCK
                                    .getKey(state.getBlock()).toString());
                }
                if (ctx.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            8, 0.3, 0.3, 0.3, 0.02);
                }
                int amount = drainMana(player, value, drainKey);
                ctx.trace("extract block +" + amount + (amount < value
                        ? " (повтор: " + amount * 100 / value + "%)" : ""));
                out.add(SpellValue.of(amount));
            }
        });

        SpellModules.register(new SpellModuleType(SpellModules.id("extract_mob"), ENT, C_EXTRACT) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 0; }
            @Override public PortType[] inputPorts() { return new PortType[]{ENTITY}; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                if (!(ctx.caster() instanceof net.minecraft.world.entity.player.Player player)) {
                    throw new SpellContext.SpellException("cast.not_player");
                }
                Entity target = in.get(0).asEntity();
                if (target instanceof net.minecraft.world.entity.player.Player) {
                    throw new SpellContext.SpellException("cast.extract_player");
                }
                ctx.requireEntityInRange(target);
                int value = manaValueOf(target);
                if (value <= 0) {
                    throw new SpellContext.SpellException("cast.extract_nothing",
                            net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                                    .getKey(target.getType()).toString());
                }
                String drainKey = "e:" + target.getUUID();
                int amount = drainMana(player, value, drainKey);
                ctx.trace("extract mob +" + amount + (amount < value
                        ? " (повтор: " + amount * 100 / value + "%)" : ""));
                out.add(SpellValue.of(amount));
            }
        });
    }

    /** Короткий id: из "minecraft:stone" делаем "stone". */
    public static String shortId(String full) {
        int colon = full.indexOf(':');
        return colon < 0 ? full : full.substring(colon + 1);
    }

    /**
     * Забирает энергию бездны, но не больше потолка.
     *
     * <p>Каждая следующая добыча с той же цели даёт меньше энергии бездны
     * (100%, 50%, 33%, ... до 10%), поэтому один блок нельзя «выдоить»
     * и держать на нём всю энергию бездны.
     */
    private static int drainMana(net.minecraft.world.entity.player.Player player, int amount,
                                String targetKey) {
        int times = com.github.fireprime5.spellcraft.data.PlayerKnowledge.drainCount(player, targetKey);
        int percent = times < com.github.fireprime5.spellcraft.data.PlayerKnowledge.DRAIN_FALLOFF.length
                ? com.github.fireprime5.spellcraft.data.PlayerKnowledge.DRAIN_FALLOFF[times]
                : 10;
        // без нижней единицы: раньше здесь стоял Math.max(1, ...), и каждая
        // из 512 целей навсегда оставалась краном хотя бы в одну энергию бездны — то есть
        // любой каст «Анализ + Извлечение» медленно наполнял кошелёк
        int scaled = amount * percent / 100;
        if (scaled <= 0) {
            return 0;
        }
        com.github.fireprime5.spellcraft.data.PlayerKnowledge.recordDrain(player, targetKey);
        int added = com.github.fireprime5.spellcraft.energy.AbyssEnergy.add(player, scaled);
        return Math.max(0, added);
    }

    // -------------------------------------------------------------- цены

    public static int manaValueOf(BlockState state, String id) {
        Integer custom = BLOCK_MANA.get(shortId(id));
        if (custom != null) {
            return custom;
        }
        float hardness = state.getBlock().defaultDestroyTime();
        return (int) Math.max(4, Math.min(120, 6 + hardness * 6));
    }

    public static int manaValueOf(Entity entity) {
        if (entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss) {
            return 600;
        }
        if (entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon) {
            return 900;
        }
        if (entity instanceof Creeper) {
            return 70;
        }
        if (entity instanceof EnderMan) {
            return 160;
        }
        if (entity instanceof Slime slime) {
            return 30 + slime.getSize() * 20;
        }
        if (entity instanceof Witch) {
            return 110;
        }
        if (entity instanceof Spider) {
            return 80;
        }
        if (entity instanceof Zombie) {
            return 60;
        }
        if (entity instanceof Skeleton) {
            return 70;
        }
        if (entity instanceof ItemEntity) {
            return 10;
        }
        if (entity instanceof LivingEntity living) {
            return (int) Math.max(20, Math.min(400, living.getMaxHealth() * 4));
        }
        return 15;
    }
}