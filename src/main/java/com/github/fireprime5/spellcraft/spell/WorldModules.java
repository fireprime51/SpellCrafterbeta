package com.github.fireprime5.spellcraft.spell;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.github.fireprime5.spellcraft.spell.PortType.ANY;
import static com.github.fireprime5.spellcraft.spell.PortType.ENTITY;
import static com.github.fireprime5.spellcraft.spell.PortType.ITEM;
import static com.github.fireprime5.spellcraft.spell.PortType.NUMBER;
import static com.github.fireprime5.spellcraft.spell.PortType.POINT;

/**
 * Модули добычи, постройки и работы с блоками.
 *
 * У всех модулей добычи/постройки есть НЕОБЯЗАТЕЛЬНЫЙ вход «область» (число):
 * без него модуль работает с одним блоком, а с подключённым модулем
 * «область» — с целой областью (радиус = значение).
 */
public final class WorldModules {

    private WorldModules() {
    }

    /** Максимум блоков за один каст мгновенной добычи. */
    public static final int MAX_VEIN_BLOCKS = 32;
    /** Стоимость маны за каждый сломанный блок в мгновенной добыче. */
    public static final int MANA_PER_VEIN_BLOCK = 2;
    /** Максимальный радиус области (защита от лага). */
    public static final int MAX_AREA_RADIUS = 6;

    private static final SpellModuleType.Category ACT = SpellModuleType.Category.ACTIONS;
    private static final SpellModuleType.Category LOG = SpellModuleType.Category.LOGIC;
    private static final SpellModuleType.Category NUM = SpellModuleType.Category.NUMBERS;

    private static final int C_ACT = 0xFFE86060;
    private static final int C_LOG = 0xFF9285F0;
    private static final int C_NUM = 0xFFE2A44E;
    private static final int C_EFFECT = 0xFF3FA6A6;

    public static void registerAll() {
        // --- Добыча: один модуль, варианты включаются галочками (ПКМ по модулю) ---
        SpellModules.register(new SpellModuleType(SpellModules.id("break_block"), ACT, C_ACT) {
            @Override public int inputs() { return 2; }
            @Override public int outputs() { return 0; }
            @Override public int optionalInputs() { return 1; }
            @Override public int manaCost() { return 8; }
            @Override public PortType[] inputPorts() { return new PortType[]{POINT, NUMBER}; }
            @Override public java.util.List<SpellModifier> modifiers() {
                return SpellModifiers.mining();
            }
            @Override public String inputHint(int port, java.util.Set<String> enabled) {
                if (port != 1) {
                    return "";
                }
                boolean area = enabled.contains(SpellModifiers.AREA.id());
                boolean fortune = enabled.contains(SpellModifiers.FORTUNE.id());
                if (area && fortune) {
                    return Component.translatable("hint.spellcraft.break_area_fortune").getString();
                }
                if (area) {
                    return Component.translatable("hint.spellcraft.break_area").getString();
                }
                if (fortune) {
                    return Component.translatable("hint.spellcraft.break_fortune").getString();
                }
                return Component.translatable("hint.spellcraft.break_plain").getString();
            }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                BlockPos start = in.get(0).asBlockPos();
                if (!(ctx.level() instanceof ServerLevel server)) {
                    throw new SpellContext.SpellException("cast.server_only");
                }
                if (server.getBlockState(start).isAir()) {
                    throw new SpellContext.SpellException("cast.nothing_to_break");
                }

                // число на втором входе: радиус области или уровень удачи
                double param = in.get(1) == null ? 1 : in.get(1).asNumber();
                boolean silk = ctx.hasModifier(SpellModifiers.SILK);
                boolean fortune = ctx.hasModifier(SpellModifiers.FORTUNE);
                boolean area = ctx.hasModifier(SpellModifiers.AREA);
                boolean instant = ctx.hasModifier(SpellModifiers.INSTANT);

                if (instant) {
                    int broken = breakVein(ctx, start);
                    ctx.trace("vein: " + broken + " blocks, mana " + ctx.manaSpent());
                    return;
                }

                int radius = area ? (int) Math.clamp(param, 1, WorldModules.MAX_AREA_RADIUS) : 0;
                int fortuneLevel = fortune ? (int) Math.clamp(param, 1, 5) : 0;
                int broken = breakArea(ctx, start, radius, silk, fortuneLevel);
                ctx.trace("broken " + broken + " blocks"
                        + (silk ? " (silk)" : "") + (fortune ? " (fortune " + fortuneLevel + ")" : ""));
            }
        });

        // --- осушение жидкостей ---
        SpellModules.register(new SpellModuleType(SpellModules.id("drain_liquids"), ACT, C_ACT) {
            @Override public int inputs() { return 2; }
            @Override public int outputs() { return 0; }
            @Override public int optionalInputs() { return 1; }
            @Override public int manaCost() { return 10; }
            @Override public PortType[] inputPorts() { return new PortType[]{POINT, NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                BlockPos center = in.get(0).asBlockPos();
                int radius = radius(in.get(1));
                int drained = 0;
                if (!(ctx.level() instanceof ServerLevel server)) {
                    throw new SpellContext.SpellException("cast.server_only");
                }
                for (BlockPos pos : sphere(center, radius)) {
                    FluidState fluid = server.getFluidState(pos);
                    if (!fluid.isEmpty() && fluid.isSource()) {
                        server.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR
                                .defaultBlockState());
                        drained++;
                    }
                }
                ctx.trace("drained " + drained + " liquid blocks");
            }
        });

        // --- координаты ---
        SpellModules.register(new SpellModuleType(SpellModules.id("point_from_coords"), NUM, C_NUM) {
            @Override public int inputs() { return 3; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 3; }
            @Override public PortType[] inputPorts() {
                return new PortType[]{NUMBER, NUMBER, NUMBER};
            }
            @Override public PortType[] outputPorts() { return new PortType[]{POINT}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                out.add(SpellValue.of(new net.minecraft.world.phys.Vec3(
                        in.get(0).asNumber(), in.get(1).asNumber(), in.get(2).asNumber()),
                        SpellValue.Kind.POSITION));
            }
        });

        // Оси координат принимают и точку, и число. Число проходит насквозь:
        // без этого до блока, заданного арифметикой, было бы не дотянуться —
        // «Точка по координатам» собирает точку из чисел, а на вход каждой оси
        // можно подать только готовую точку, и числа обратно не вернуть.
        for (String axis : new String[]{"x", "y", "z"}) {
            SpellModules.register(new SpellModuleType(SpellModules.id("coord_" + axis), NUM, C_NUM) {
                @Override public int inputs() { return 1; }
                @Override public int outputs() { return 1; }
                @Override public int manaCost() { return 2; }
                @Override public PortType[] inputPorts() { return new PortType[]{ANY}; }
                @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
                @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                              List<SpellValue> out) {
                    SpellValue value = in.get(0);
                    if (value == null || value.kind() == SpellValue.Kind.NUMBER) {
                        // число или пустой вход — просто пропускаем дальше
                        out.add(value);
                        return;
                    }
                    var pos = value.asBlockPos();
                    double coordinate = switch (axis) {
                        case "x" -> pos.getX();
                        case "y" -> pos.getY();
                        default -> pos.getZ();
                    };
                    out.add(SpellValue.of(coordinate));
                }
            });
        }

        // --- постройка: один блок или область ---
        SpellModules.register(new SpellModuleType(SpellModules.id("place_block"), ACT, C_ACT) {
            @Override public int inputs() { return 3; }
            @Override public int outputs() { return 1; }
            @Override public int optionalInputs() { return 1; }
            @Override public int manaCost() { return 6; }
            @Override public PortType[] inputPorts() {
                return new PortType[]{POINT, ITEM, NUMBER};
            }
            @Override public PortType[] outputPorts() { return new PortType[]{POINT}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                ItemStack stack = in.get(1).asItem();
                if (!(ctx.level() instanceof ServerLevel server)) {
                    throw new SpellContext.SpellException("cast.server_only");
                }
                BlockState state = blockStateOf(stack);
                if (state == null) {
                    throw new SpellContext.SpellException("cast.not_a_block");
                }
                int radius = radius(in.get(2));
                var inventory = ctx.caster() instanceof net.minecraft.world.entity.player.Player p
                        ? p.getInventory() : null;
                int placed = 0;
                for (BlockPos pos : sphere(in.get(0).asBlockPos(), radius)) {
                    var current = server.getBlockState(pos);
                    if (!current.isAir() && !current.canBeReplaced()) {
                        // место занято — блок из инвентаря не тратим
                        continue;
                    }
                    if (!takeBlock(inventory, stack)) {
                        ctx.trace("no blocks left in inventory");
                        break;
                    }
                    server.setBlockAndUpdate(pos, state);
                    placed++;
                }
                ctx.trace("placed " + placed + " blocks of " + stack.getHoverName().getString());
                out.add(in.get(0));
            }
        });

        SpellModules.register(new SpellModuleType(SpellModules.id("place_held_block"), ACT, C_ACT) {
            @Override public int inputs() { return 2; }
            @Override public int outputs() { return 1; }
            @Override public int optionalInputs() { return 1; }
            @Override public int manaCost() { return 4; }
            @Override public PortType[] inputPorts() { return new PortType[]{POINT, NUMBER}; }
            @Override public PortType[] outputPorts() { return new PortType[]{POINT}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                if (!(ctx.caster() instanceof net.minecraft.world.entity.player.Player player)) {
                    throw new SpellContext.SpellException("cast.not_living");
                }
                ItemStack hand = player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND);
                if (!(ctx.level() instanceof ServerLevel server)) {
                    throw new SpellContext.SpellException("cast.server_only");
                }
                BlockState state = blockStateOf(hand);
                if (state == null) {
                    throw new SpellContext.SpellException("cast.not_a_block");
                }
                int placed = 0;
                for (BlockPos pos : sphere(in.get(0).asBlockPos(), radius(in.get(1)))) {
                    var current = server.getBlockState(pos);
                    if (current.isAir() || current.canBeReplaced()) {
                        server.setBlockAndUpdate(pos, state);
                        placed++;
                    }
                }
                if (!player.getAbilities().instabuild && hand.isDamageableItem()) {
                    hand.hurtAndBreak(1, player,
                            net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                }
                ctx.trace("placed " + placed + " held blocks");
                out.add(in.get(0));
            }
        });
    }

    // ------------------------------------------------------------- утилиты

    /** Радиус области: без подключения — 0 (один блок). */
    private static int radius(SpellValue value) {
        if (value == null) {
            return 0;
        }
        return (int) Math.clamp(value.asNumber(), 0, MAX_AREA_RADIUS);
    }

    /** Все позиции сферы заданного радиуса (включая центр). */
    private static List<BlockPos> sphere(BlockPos center, int radius) {
        List<BlockPos> result = new ArrayList<>();
        if (radius <= 0) {
            result.add(center);
            return result;
        }
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) {
                        continue;
                    }
                    result.add(center.offset(dx, dy, dz));
                }
            }
        }
        return result;
    }

    /**
     * Мгновенная добыча: обход связного блока того же типа.
     * Не больше MAX_VEIN_BLOCKS и не больше, чем есть маны.
     */
    private static int breakVein(SpellContext ctx, BlockPos start) {
        if (!(ctx.level() instanceof ServerLevel server)) {
            throw new SpellContext.SpellException("cast.server_only");
        }
        Block startBlock = server.getBlockState(start).getBlock();
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);

        int broken = 0;
        while (!queue.isEmpty() && broken < MAX_VEIN_BLOCKS) {
            BlockPos pos = queue.poll();
            if (!visited.add(pos)) {
                continue;
            }
            if (!server.getBlockState(pos).getBlock().equals(startBlock)) {
                continue;
            }
            if (broken > 0 && !ctx.hasMana(MANA_PER_VEIN_BLOCK)) {
                ctx.error(SpellIssue.of("mana.insufficient_vein", MANA_PER_VEIN_BLOCK));
                break;
            }
            if (server.destroyBlock(pos, true)) {
                broken++;
                ctx.spendMana(MANA_PER_VEIN_BLOCK);
            }
            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                if (!visited.contains(next) && server.isLoaded(next)) {
                    queue.add(next);
                }
            }
        }
        return broken;
    }

    /** Ломает область: silkDrop — ронять сам блок, fortune >= 0 — добавлять удачу. */
    private static int breakArea(SpellContext ctx, BlockPos center, int radius, boolean silkDrop,
                                 int fortune) {
        if (!(ctx.level() instanceof ServerLevel server)) {
            throw new SpellContext.SpellException("cast.server_only");
        }
        int broken = 0;
        var tool = ctx.caster() instanceof net.minecraft.world.entity.LivingEntity living
                ? living.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND)
                : ItemStack.EMPTY;

        for (BlockPos pos : sphere(center, radius)) {
            if (!server.isLoaded(pos)) {
                continue;
            }
            var state = server.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (silkDrop) {
                server.destroyBlock(pos, false);
                Block.popResource(server, pos, new ItemStack(state.getBlock()));
            } else {
                var blockEntity = server.getBlockEntity(pos);
                var drops = Block.getDrops(state, server, pos, blockEntity, ctx.caster(), tool);
                server.destroyBlock(pos, false);
                for (ItemStack drop : drops) {
                    if (fortune > 0 && drop.getCount() > 0) {
                        drop.grow(server.random.nextInt(fortune + 1));
                    }
                    Block.popResource(server, pos, drop);
                }
            }
            broken++;
        }
        return broken;
    }

    private static BlockState blockStateOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        var block = net.minecraft.world.level.block.Block.byItem(stack.getItem());
        return block == null ? null : block.defaultBlockState();
    }

    /**
     * Убирает один блок из инвентаря кастающего (как при обычной постройке).
     * Без этого «Постройка» превращалась в бесконечный источник блоков.
     *
     * @return false — блоков в инвентаре больше нет
     */
    private static boolean takeBlock(net.minecraft.world.entity.player.Inventory inventory,
                                     ItemStack wanted) {
        if (inventory == null) {
            // нет инвентаря (например, каст от моба) — тратить нечего
            return true;
        }
        for (int i = 0; i < inventory.items.size(); i++) {
            ItemStack stack = inventory.items.get(i);
            if (ItemStack.isSameItem(stack, wanted) && stack.getCount() > 0) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    /**
     * Все эффекты Minecraft как отдельные модули.
     * Список id задан явно (реестр Minecraft доступен только в игре),
     * сам эффект ищется в реестре в момент каста.
     */
    public static void registerEffectModules() {
        for (String path : EFFECT_IDS) {
            registerEffect(path);
        }
    }

    private static void registerEffect(String path) {
        SpellModules.register(new SpellModuleType(
                SpellModules.id("effect_" + path), ACT, C_EFFECT) {
            @Override public int inputs() { return 3; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 18; }
            @Override public PortType[] inputPorts() {
                return new PortType[]{ENTITY, NUMBER, NUMBER};
            }
            @Override public PortType[] outputPorts() { return new PortType[]{ENTITY}; }

            @Override public String nameKey() {
                // название эффекта из ванильных переводов
                return "effect.minecraft." + path;
            }

            @Override public String descriptionKey() {
                // у всех эффектов одинаковое устройство, поэтому и описание одно
                // (без %s — ключ используется как Component.translatable(key))
                return "module.spellcraft.effect_desc_plain";
            }

            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                Entity target = in.get(0).asEntity();
                if (!(target instanceof net.minecraft.world.entity.LivingEntity living)) {
                    throw new SpellContext.SpellException("cast.not_living");
                }
                var effect = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
                        .get(net.minecraft.resources.ResourceLocation.withDefaultNamespace(path));
                if (effect == null) {
                    throw new SpellContext.SpellException("cast.unknown_effect", path);
                }
                int ticks = (int) Math.clamp(in.get(1).asNumber() * 20.0, 20, 20 * 60 * 60);
                int amplifier = (int) Math.clamp(in.get(2).asNumber(), 0, 255);
                living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect),
                        ticks, amplifier, false, true, true));
                ctx.trace("effect " + path + " amplifier " + amplifier
                        + " for " + (ticks / 20) + "s");
                out.add(in.get(0));
            }
        });
    }

    /** id всех эффектов Minecraft 1.21.1. */
    private static final String[] EFFECT_IDS = {
            "absorption", "bad_omen", "blindness", "conduit_power", "darkness",
            "dolphins_grace", "fire_resistance", "glowing", "haste", "health_boost",
            "hero_of_the_village", "hunger", "infested", "instant_damage", "instant_health",
            "invisibility", "jump_boost", "levitation", "luck", "mining_fatigue", "nausea",
            "night_vision", "oozing", "poison", "raid_omen", "regeneration", "resistance",
            "saturation", "slow_falling", "slowness", "speed", "strength", "trial_omen",
            "unluck", "water_breathing", "weakness", "weaving", "wind_charged", "wither",
    };
}
