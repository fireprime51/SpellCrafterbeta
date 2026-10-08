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
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
 * модули. Извлечение: забирает ману из блока или существа (у каждого своя цена).
 * Оба модуля бесплатны по мане — иначе нельзя было бы начать.
 */
public final class AnalysisModules {

    private AnalysisModules() {
    }

    private static final SpellModuleType.Category ENT = SpellModuleType.Category.ENTITIES;
    private static final int C_SCAN = 0xFF6FB3D2;
    private static final int C_EXTRACT = 0xFFD2A65A;

    /** Сколько маны даёт тот или иной блок/моб (таблицы по id). */
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
                String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                        .getKey(target.getType()).toString();
                out.add(SpellValue.of(
                        com.github.fireprime5.spellcraft.spell.KnowledgeHooks.scan(player, id, ctx)));
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
     * Забирает ману, но не больше потолка.
     *
     * <p>Каждая следующая добыча с той же цели даёт меньше маны
     * (100%, 50%, 33%, ... до 10%), поэтому один блок нельзя «выдоить»
     * и держать на нём всю ману.
     */
    private static int drainMana(net.minecraft.world.entity.player.Player player, int amount,
                                String targetKey) {
        int times = com.github.fireprime5.spellcraft.data.PlayerKnowledge.drainCount(player, targetKey);
        int percent = times < com.github.fireprime5.spellcraft.data.PlayerKnowledge.DRAIN_FALLOFF.length
                ? com.github.fireprime5.spellcraft.data.PlayerKnowledge.DRAIN_FALLOFF[times]
                : 10;
        int scaled = Math.max(1, amount * percent / 100);
        com.github.fireprime5.spellcraft.data.PlayerKnowledge.recordDrain(player, targetKey);
        int added = com.github.fireprime5.spellcraft.energy.ManaCapability.addMana(player, scaled);
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