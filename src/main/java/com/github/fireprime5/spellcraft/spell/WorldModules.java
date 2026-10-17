package com.github.fireprime5.spellcraft.spell;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

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
 * Модули добычи и работы с блоками.
 *
 * <p>У всех модулей добычи есть НЕОБЯЗАТЕЛЬНЫЙ вход «область» (число):
 * без него модуль работает с одним блоком, а с подключённым числом — с
 * областью вокруг точки (радиус = значение).
 *
 * <p>Все модули, меняющие мир, обязаны позвать {@link SpellContext#requireWritable}
 * перед записью и {@link SpellContext#requireInRange} перед чтением. Точка в
 * схеме может быть получена арифметикой от любых чисел, поэтому без общей
 * проверки «Добыча» ломала бы блоки по всей карте, а «Создать лаву» — заливала
 * чужие постройки и заставляла сервер генерировать незагруженные чанки.
 */
public final class WorldModules {

    private WorldModules() {
    }

    /** Максимум блоков за один каст мгновенной добычи. */
    public static final int MAX_VEIN_BLOCKS = 32;
    /** Стоимость энергии бездны за каждый сломанный блок в мгновенной добыче. */
    public static final int MANA_PER_VEIN_BLOCK = 2;
    /** Максимальный радиус области (защита от лага). */
    public static final int MAX_AREA_RADIUS = 6;
    /** Максимум блоков, которые «Постройка из руки» ставит за один каст. */
    public static final int MAX_PLACED_BLOCKS = 16;
    /** Максимум блоков жидкости, снимаемых «Осушением» за один каст. */
    public static final int MAX_DRAINED_BLOCKS = 32;

    private static final SpellModuleType.Category ACT = SpellModuleType.Category.ACTIONS;
    private static final SpellModuleType.Category NUM = SpellModuleType.Category.NUMBERS;
    private static final SpellModuleType.Category EFF =
            SpellModuleType.Category.EFFECTS;

    private static final int C_ACT = 0xFFE86060;
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
                ctx.requireWritable(start);
                BlockState startState = server.getBlockState(start);
                if (startState.isAir()) {
                    throw new SpellContext.SpellException("cast.nothing_to_break");
                }
                // бедрок, барьер и рамку портала «Добыча» не берёт: см. подробное
                // объяснение в SpellContext#canBreak. Молча пропускать нельзя —
                // игрок поймёт, что «сломать не вышло», только по тишине
                ctx.requireBreakable(start);

                // число на втором входе: радиус области или уровень удачи
                double param = in.get(1) == null ? 1 : in.get(1).asNumber();
                boolean silk = ctx.hasModifier(SpellModifiers.SILK);
                boolean fortune = ctx.hasModifier(SpellModifiers.FORTUNE);
                boolean area = ctx.hasModifier(SpellModifiers.AREA);

                if (ctx.hasModifier(SpellModifiers.INSTANT)) {
                    int broken = breakVein(ctx, start);
                    if (broken == 0) {
                        ctx.trace("vein: nothing breakable at " + start);
                        return;
                    }
                    ctx.trace("vein: " + broken + " blocks, abyss " + ctx.manaSpent());
                    return;
                }

                int radius = area ? (int) Math.clamp(param, 1, MAX_AREA_RADIUS) : 0;
                int fortuneLevel = fortune ? (int) Math.clamp(param, 1, 5) : 0;
                int broken = breakArea(ctx, start, radius, silk, fortuneLevel);
                if (broken == 0) {
                    ctx.trace("nothing breakable at " + start);
                    return;
                }
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
                if (!(ctx.level() instanceof ServerLevel server)) {
                    throw new SpellContext.SpellException("cast.server_only");
                }
                ctx.requireWritable(center);
                int radius = radius(in.get(1));
                int drained = 0;
                for (BlockPos pos : sphere(center, radius)) {
                    if (drained >= MAX_DRAINED_BLOCKS) {
                        ctx.trace("drain limit reached (" + MAX_DRAINED_BLOCKS + ")");
                        break;
                    }
                    ctx.requireLoaded(pos);
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
                // координаты в мире не ограничены, а точка из них — вполне:
                // проверка здесь, а не в каждом модуле, не даёт «Создать лаву»
                // упереться в стену ошибок из всех модулей сразу
                ctx.requireInRange(new net.minecraft.world.phys.Vec3(
                        in.get(0).asNumber(), in.get(1).asNumber(), in.get(2).asNumber()));
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

        // --- постройка: блок берётся из руки кастующего ---
        //
        // Раньше здесь был ещё и «Постройка» с предметом на входе: она брала
        // предмет из инвентаря кастующего, а предмет в схему попадал из
        // модулей «Блок на месте»/«Предмет в руке», которые копировали стопку
        // из мира, ничего не убирая. Вместе это давало бесконечные блоки за
        // несколько энергии бездны, поэтому вся ветка удалена, а постройка осталась
        // только из того, что игрок и так держит в руке.
        SpellModules.register(new SpellModuleType(SpellModules.id("place_held_block"), ACT, C_ACT) {
            @Override public int inputs() { return 2; }
            @Override public int outputs() { return 1; }
            @Override public int optionalInputs() { return 1; }
            @Override public int manaCost() { return 4; }
            @Override public PortType[] inputPorts() { return new PortType[]{POINT, NUMBER}; }
            @Override public PortType[] outputPorts() { return new PortType[]{POINT}; }
            @Override public String inputHint(int port, java.util.Set<String> enabled) {
                return port == 1
                        ? Component.translatable("hint.spellcraft.place_radius").getString()
                        : "";
            }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                if (!(ctx.caster() instanceof net.minecraft.world.entity.player.Player player)) {
                    throw new SpellContext.SpellException("cast.not_living");
                }
                if (!(ctx.level() instanceof ServerLevel server)) {
                    throw new SpellContext.SpellException("cast.server_only");
                }
                ItemStack hand = player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND);
                BlockState state = blockStateOf(hand);
                if (state == null) {
                    throw new SpellContext.SpellException("cast.not_a_block");
                }
                BlockPos center = in.get(0).asBlockPos();
                ctx.requireWritable(center);
                int placed = 0;
                for (BlockPos pos : sphere(center, radius(in.get(1)))) {
                    if (placed >= MAX_PLACED_BLOCKS) {
                        ctx.trace("place limit reached (" + MAX_PLACED_BLOCKS + ")");
                        break;
                    }
                    ctx.requireWritable(pos);
                    var current = server.getBlockState(pos);
                    if (!current.isAir() && !current.canBeReplaced()) {
                        continue;
                    }
                    server.setBlockAndUpdate(pos, state);
                    placed++;
                }
                if (!player.getAbilities().instabuild && hand.isDamageableItem()) {
                    hand.hurtAndBreak(1, player,
                            net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                }
                ctx.trace("placed " + placed + " held blocks");
                out.add(SpellValue.of(Vec3.atCenterOf(center), SpellValue.Kind.POSITION));
            }
        });

        // --- постройка предметом из инвентаря ---
        //
        // Отличие от «Постройки из руки»: блок берётся из любой ячейки инвентаря,
        // поэтому предмет на входе — это «поставить вот этот блок». Блоки
        // списываются поштучно, так что модуль остаётся обычной постройкой, а не
        // источником блоков. Источников, копирующих предмет из мира или инвентаря
        // без списания (такие модули и давали бесконечные блоки), здесь нет:
        // единственное, откуда берётся ITEM — то, что уже у игрока в руках.
        SpellModules.register(new SpellModuleType(SpellModules.id("place_block"), ACT, C_ACT) {
            @Override public int inputs() { return 3; }
            @Override public int outputs() { return 1; }
            @Override public int optionalInputs() { return 1; }
            @Override public int manaCost() { return 6; }
            @Override public PortType[] inputPorts() {
                return new PortType[]{POINT, ITEM, NUMBER};
            }
            @Override public PortType[] outputPorts() { return new PortType[]{POINT}; }
            @Override public String inputHint(int port, java.util.Set<String> enabled) {
                return port == 2
                        ? Component.translatable("hint.spellcraft.place_radius").getString()
                        : "";
            }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                if (!(ctx.level() instanceof ServerLevel server)) {
                    throw new SpellContext.SpellException("cast.server_only");
                }
                ItemStack stack = in.get(1) == null ? ItemStack.EMPTY : in.get(1).asItem();
                BlockState state = blockStateOf(stack);
                if (state == null) {
                    throw new SpellContext.SpellException("cast.not_a_block");
                }
                BlockPos center = in.get(0).asBlockPos();
                ctx.requireWritable(center);
                Inventory inventory = ctx.caster() instanceof Player player
                        ? player.getInventory() : null;
                int placed = 0;
                for (BlockPos pos : sphere(center, radius(in.get(2)))) {
                    if (placed >= MAX_PLACED_BLOCKS) {
                        ctx.trace("place limit reached (" + MAX_PLACED_BLOCKS + ")");
                        break;
                    }
                    ctx.requireWritable(pos);
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
                out.add(SpellValue.of(Vec3.atCenterOf(center), SpellValue.Kind.POSITION));
            }
        });

        /**
         * Залить область между двумя точками.
         *
         * <p>Прямоугольник задаётся двумя противоположными углами, а не радиусом:
         * так можно построить стену или пол точно там, где нужно. Блоки берутся
         * из инвентаря поштучно, поэтому область не становится источником блоков.
         */
        SpellModules.register(new SpellModuleType(SpellModules.id("fill_region"), ACT, C_ACT) {
            @Override public int inputs() { return 3; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 24; }
            @Override public PortType[] inputPorts() {
                return new PortType[]{POINT, POINT, ITEM};
            }
            @Override public PortType[] outputPorts() { return new PortType[]{POINT}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                BlockPos corner = in.get(0).asBlockPos();
                BlockPos other = in.get(1).asBlockPos();
                ItemStack stack = in.get(2) == null ? ItemStack.EMPTY : in.get(2).asItem();
                BlockState state = blockStateOf(stack);
                if (state == null) {
                    throw new SpellContext.SpellException("cast.not_a_block");
                }
                ServerLevel server = serverLevel(ctx);
                BlockPos min = new BlockPos(Math.min(corner.getX(), other.getX()),
                        Math.min(corner.getY(), other.getY()),
                        Math.min(corner.getZ(), other.getZ()));
                BlockPos max = new BlockPos(Math.max(corner.getX(), other.getX()),
                        Math.max(corner.getY(), other.getY()),
                        Math.max(corner.getZ(), other.getZ()));
                long volume = (long) (max.getX() - min.getX() + 1)
                        * (max.getY() - min.getY() + 1)
                        * (max.getZ() - min.getZ() + 1);
                // объём считается ДО постройки: иначе игрок увидел бы частично
                // залитую область и не понял бы, где предел
                if (volume > MAX_FILL_BLOCKS) {
                    throw new SpellContext.SpellException("cast.region_too_big", volume);
                }
                ctx.requireWritable(min);
                ctx.requireWritable(max);
                Inventory inventory = ctx.caster() instanceof Player player
                        ? player.getInventory() : null;
                int placed = 0;
                for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                    ctx.requireWritable(pos);
                    var current = server.getBlockState(pos);
                    if (!current.isAir() && !current.canBeReplaced()) {
                        continue;
                    }
                    if (!takeBlock(inventory, stack)) {
                        ctx.trace("out of blocks at " + placed);
                        break;
                    }
                    server.setBlockAndUpdate(pos, state);
                    placed++;
                }
                ctx.trace("filled " + placed + " blocks of " + stack.getHoverName().getString());
                out.add(SpellValue.of(Vec3.atCenterOf(max), SpellValue.Kind.POSITION));
            }
        });

        /**
         * Расщепление: убрать блок вместе с его содержимым, не роняя добычу.
         *
         * <p>Цена — весь кошелёк целиком. Модуль убирает любой блок в радиусе
         * досягаемости, включая содержимое сундуков и печей, поэтому за обычную
         * цену он был бы инструментом для сноса чужой постройки; сжигание всей
         * энергии бездны делает его разовым решением «надо любой ценой».
         *
         * <p>Цена считается в {@link #castCost(int)}, а не в {@link #manaCost()},
         * потому что она равна текущему кошельку игрока: и сервер при проверке,
         * и панель показывают настоящее число, а не фиксированную заглушку.
         *
         * <p><b>Способ добычи пока не придуман</b>, поэтому модуль не приписан ни
         * к одной цели сканирования и недостижим в игре — см. {@link ScanRewards}.
         */
        SpellModules.register(new SpellModuleType(SpellModules.id("fission"), ACT, C_ACT) {
            @Override public int inputs() { return 2; }
            @Override public int outputs() { return 1; }
            @Override public int optionalInputs() { return 1; }
            @Override public int manaCost() { return 0; }
            @Override public int castCost(int manaCapacity) {
                return manaCapacity > 0 ? manaCapacity : super.manaCost();
            }
            @Override public PortType[] inputPorts() { return new PortType[]{POINT, NUMBER}; }
            @Override public PortType[] outputPorts() { return new PortType[]{POINT}; }
            @Override public String inputHint(int port, java.util.Set<String> enabled) {
                return port == 1
                        ? Component.translatable("hint.spellcraft.fission_radius").getString()
                        : "";
            }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                ServerLevel server = serverLevel(ctx);
                BlockPos center = in.get(0).asBlockPos();
                ctx.requireWritable(center);
                int radius = radius(in.get(1));
                int removed = 0;
                for (BlockPos pos : sphere(center, radius)) {
                    if (!SpellContext.canBreak(server, server.getBlockState(pos), pos)) {
                        continue;
                    }
                    // false: блок исчезает вместе с содержимым и ничего не роняет
                    if (server.destroyBlock(pos, false)) {
                        removed++;
                    }
                }
                ctx.trace("fission: " + removed + " blocks consumed with their contents");
                out.add(SpellValue.of(Vec3.atCenterOf(center), SpellValue.Kind.POSITION));
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

    /** Уровень сервера: клиент модули не исполняет, но класс общий. */
    private static ServerLevel serverLevel(SpellContext ctx) {
        if (ctx.level() instanceof ServerLevel server) {
            return server;
        }
        throw new SpellContext.SpellException("cast.server_only");
    }

    /** В пределах ли досягаемости (мягкая проверка для циклов по области). */
    private static boolean withinRange(SpellContext ctx, BlockPos pos) {
        try {
            ctx.requireInRange(pos);
            return true;
        } catch (SpellContext.SpellException e) {
            return false;
        }
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
     *
     * <p>Сколько блоков можно сломать, решается ДО обхода — по остатку энергии бездны
     * ({@link SpellContext#remainingManaBudget()}). Раньше проверка стояла
     * внутри цикла в виде {@code hasMana(...)}: значение в кошельке до конца
     * каста не меняется, поэтому проверка либо проходила все итерации, либо
     * падала на первой, и игрок с двумя манами выкапывал жилу целиком.
     *
     * <p>Первый блок уже оплачен стоимостью самого модуля, дальше — по
     * {@link #MANA_PER_VEIN_BLOCK} за блок.
     *
     * <p>Неразрушимые блоки пропускаются (см. {@link SpellContext#canBreak}):
     * обход идёт по связанным блокам одного типа, а жила бедрока под миром
     * обошла бы кастеру бесплатную добычу всего остального.
     *
     * @return сколько блоков действительно вынуто
     */
    private static int breakVein(SpellContext ctx, BlockPos start) {
        if (!(ctx.level() instanceof ServerLevel server)) {
            throw new SpellContext.SpellException("cast.server_only");
        }
        Block startBlock = server.getBlockState(start).getBlock();
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);

        int budget = Math.min(MAX_VEIN_BLOCKS,
                1 + ctx.remainingManaBudget() / MANA_PER_VEIN_BLOCK);
        int broken = 0;
        while (!queue.isEmpty() && broken < budget) {
            BlockPos pos = queue.poll();
            if (!visited.add(pos)) {
                continue;
            }
            BlockState state = server.getBlockState(pos);
            if (!state.getBlock().equals(startBlock)) {
                continue;
            }
            // неразрушимое пропускаем: жила бедрока под миром иначе обошла бы
            // кастеру бесплатную добычу всего, что вокруг неё
            if (!SpellContext.canBreak(server, state, pos)) {
                continue;
            }
            // жила растёт от проверенной точки, но её край всё равно мог бы
            // вылезти за пределы досягаемости
            ctx.requireInRange(pos);
            if (server.destroyBlock(pos, true)) {
                broken++;
                if (broken > 1) {
                    ctx.spendMana(MANA_PER_VEIN_BLOCK);
                }
            }
            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                if (!visited.contains(next) && server.isLoaded(next)) {
                    queue.add(next);
                }
            }
        }
        if (broken >= budget && !queue.isEmpty()) {
            // ограничили, а не сломали: это не ошибка каста, блоки-то вынуты
            ctx.trace("vein stopped: abyss or block limit (" + budget + ")");
        }
        return broken;
    }

    /**
     * Ломает область: silkDrop — ронять сам блок, fortune >= 0 — добавлять удачу.
     *
     * <p>Главное здесь — порядок действий. Блок сначала ЛОМАЕТСЯ, и только если
     * он действительно исчез, роняется добыча. Раньше было наоборот: предметы
     * выпадали безусловно, а результат {@code destroyBlock} не смотрели вовсе.
     * Вместе с отсутствием проверки неразрушимости это давало бесплатный
     * бесконечный бедрок: блок оставался на месте, а в мире появлялись
     * бедроковые кучи.
     *
     * @return сколько блоков действительно вынуто
     */
    private static int breakArea(SpellContext ctx, BlockPos center, int radius, boolean silkDrop,
                                 int fortune) {
        if (!(ctx.level() instanceof ServerLevel server)) {
            throw new SpellContext.SpellException("cast.server_only");
        }
        int broken = 0;
        int skipped = 0;
        var tool = ctx.caster() instanceof net.minecraft.world.entity.LivingEntity living
                ? living.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND)
                : ItemStack.EMPTY;

        for (BlockPos pos : sphere(center, radius)) {
            // центр проверен при входе, а край сферы на радиусе 6 ещё выходит
            // за пределы досягаемости, если центр стоял ровно на границе
            if (!server.isLoaded(pos) || !withinRange(ctx, pos)) {
                continue;
            }
            var state = server.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (!SpellContext.canBreak(server, state, pos)) {
                skipped++;
                continue;
            }
            // добыча считается ДО разрушения: блок ещё на месте, и getDrops
            // видит корректное окружение (соседние блоки, воду)
            var blockEntity = server.getBlockEntity(pos);
            var drops = silkDrop
                    ? silkDrops(server, pos, state, blockEntity, ctx.caster(), tool)
                    : Block.getDrops(state, server, pos, blockEntity, ctx.caster(), tool);
            if (!server.destroyBlock(pos, false)) {
                // блок устоял (например, его защитил другой мод): добычу не роняем
                skipped++;
                continue;
            }
            for (ItemStack drop : drops) {
                if (fortune > 0 && drop.getCount() > 0) {
                    drop.grow(server.random.nextInt(fortune + 1));
                }
                Block.popResource(server, pos, drop);
            }
            broken++;
        }
        if (skipped > 0) {
            // честно говорим, что часть области прошла мимо, иначе игрок
            // недоумевает, почему «по области» вынуто меньше, чем показано
            ctx.trace(skipped + " blocks skipped (not breakable or protected)");
        }
        return broken;
    }

    /**
     * Добыча «Шёлкового касания»: сам блок вместо его содержимого.
     *
     * <p>Пустой список, если блок требует правильного инструмента: ванилла
     * отдаёт обсидиан только с алмазной киркой, и «Добыча» не должна это
     * обходить, иначе она просто выдавала бы любой блок с таблицей добычи.
     */
    private static java.util.List<ItemStack> silkDrops(ServerLevel server, BlockPos pos,
                                                       BlockState state,
                                                       net.minecraft.world.level.block.entity.BlockEntity be,
                                                       net.minecraft.world.entity.Entity breaker,
                                                       ItemStack tool) {
        if (state.requiresCorrectToolForDrops()
                && !(breaker instanceof net.minecraft.world.entity.player.Player player
                && state.canHarvestBlock(server, pos, player))) {
            return java.util.List.of();
        }
        return java.util.List.of(new ItemStack(state.getBlock()));
    }

    private static BlockState blockStateOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        var block = net.minecraft.world.level.block.Block.byItem(stack.getItem());
        return block == null ? null : block.defaultBlockState();
    }

    /**
     * Убирает один блок из инвентаря кастающего.
     *
     * <p>Без этого «Постройка» превращалась бы в бесконечный источник блоков:
     * предмет на входе приходит из рук игрока, а не из мира.
     *
     * @return false — блоков в инвентаре больше нет
     */
    private static boolean takeBlock(Inventory inventory, ItemStack wanted) {
        if (inventory == null || wanted.isEmpty()) {
            // нет инвентаря (каст от моба) — тратить нечего
            return false;
        }
        for (ItemStack stack : inventory.items) {
            if (ItemStack.isSameItem(stack, wanted) && stack.getCount() > 0) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    /** Потолок объёма «Залить область»: защита от прямоугольника в миллион блоков. */
    public static final int MAX_FILL_BLOCKS = 256;

    /** Базовая цена модуля эффекта; остальное начисляется за время и уровень. */
    public static final int EFFECT_BASE_MANA = 6;

    // ------------------------------------------------------------- эффекты

    /** Потолок времени эффекта в секундах: пять минут. */
    public static final int MAX_EFFECT_SECONDS = 300;
    /** Нижняя граница: эффект короче секунды смысла не имеет. */
    public static final int MIN_EFFECT_SECONDS = 1;
    /** Потолок уровня эффекта: Vanilla V. Выше в игре уже ничего не работает. */
    public static final int MAX_EFFECT_AMPLIFIER = 4;

    /** Энергия бездны за каждую секунду действия эффекта. */
    public static final int EFFECT_ABYSS_PER_SECOND = 2;
    /** Энергия бездны за каждую ступень уровня. */
    public static final int EFFECT_MANA_PER_AMPLIFIER = 6;

    /**
     * Стоимость применения эффекта.
     *
     * <p>Растёт вместе со временем и уровнем: иначе «два часа левитации
     * четвёртого уровня» стоил бы столько же, сколько одна секунда.
     */
    public static int effectManaCost(int seconds, int amplifier) {
        return EFFECT_BASE_MANA
                + Math.clamp(seconds, MIN_EFFECT_SECONDS, MAX_EFFECT_SECONDS)
                * EFFECT_ABYSS_PER_SECOND
                + Math.clamp(amplifier, 0, MAX_EFFECT_AMPLIFIER)
                * EFFECT_MANA_PER_AMPLIFIER;
    }

    /**
     * Все эффекты Minecraft как отдельные модули.
     *
     * <p>Список id задан явно (реестр Minecraft доступен только в игре), сам
     * эффект ищется в реестре в момент каста.
     *
     * <p>Модули вернулись после удаления, но с тремя ограничениями, которых
     * раньше не было и которые делали их инструментом гриферства:
     *
     * <ul>
     *   <li><b>Время ограничено</b> пятью минутами — «навечно наложить
     *       левитацию» больше нельзя, эффект приходится поддерживать кастами.</li>
     *   <li><b>Уровень ограничен</b> Vanilla V — при 255 эффект выходил за
     *       любые игровые механики.</li>
     *   <li><b>Цена растёт</b> со временем и уровнем, поэтому постоянные
     *       дорогие эффекты не окупаются, а дешёвые остаются доступны.</li>
     * </ul>
     *
     * <p>Себе эффект наложить можно всегда, другим — только если на то есть
     * право: проверка та же, что у урона (см. {@link SpellContext#requireCanHarm}).
     */
    public static void registerEffectModules() {
        for (String path : EFFECT_IDS) {
            registerEffect(path);
        }
    }

    private static void registerEffect(String path) {
        SpellModules.register(new SpellModuleType(
                SpellModules.id("effect_" + path), EFF, C_EFFECT) {
            @Override public int inputs() { return 3; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return EFFECT_BASE_MANA; }
            @Override public PortType[] inputPorts() {
                return new PortType[]{ENTITY, NUMBER, NUMBER};
            }
            @Override public PortType[] outputPorts() { return new PortType[]{ENTITY}; }

            @Override public String nameKey() {
                // название эффекта из ванильных переводов
                return "effect.minecraft." + path;
            }

            @Override public String descriptionKey() {
                // у всех эффектов одинаковое устройство, поэтому и описание одно;
                // название эффекта подставляет description() ниже
                return "module.spellcraft.effect_desc";
            }

            @Override public Component description() {
                // Component.translatable(key) без аргументов на строке с %s
                // показывает %s буквально, поэтому название эффекта передаём явно
                return Component.translatable(descriptionKey(), label());
            }

            @Override public String inputHint(int port, java.util.Set<String> enabled) {
                if (port == 1) {
                    return Component.translatable("hint.spellcraft.effect_seconds").getString();
                }
                if (port == 2) {
                    return Component.translatable("hint.spellcraft.effect_amplifier").getString();
                }
                return "";
            }

            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                Entity target = in.get(0).asEntity();
                if (!(target instanceof net.minecraft.world.entity.LivingEntity living)) {
                    throw new SpellContext.SpellException("cast.not_living");
                }
                // себе можно всегда; на остальных — обычные правила PvP и
                // плагинов защиты, иначе эффект был бы обходом их
                if (target != ctx.caster()) {
                    ctx.requireCanHarm(target);
                } else {
                    ctx.requireEntityInRange(target);
                }

                var effect = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
                        .get(net.minecraft.resources.ResourceLocation.withDefaultNamespace(path));
                if (effect == null) {
                    throw new SpellContext.SpellException("cast.unknown_effect", path);
                }

                int seconds = (int) Math.clamp(Math.round(in.get(1).asNumber()),
                        MIN_EFFECT_SECONDS, MAX_EFFECT_SECONDS);
                int amplifier = (int) Math.clamp(Math.round(in.get(2).asNumber()),
                        0, MAX_EFFECT_AMPLIFIER);

                // цена платная, поэтому применяем эффект только если хватает
                // именно на него, а не «на каст в целом»: при нуле энергии бездны на
                // пятиминутной левитации каст прошёл бы проверку на 6 энергии бездны
                // базовой цены и ушёл бы в долг
                int price = effectManaCost(seconds, amplifier);
                if (price > ctx.remainingManaBudget()) {
                    throw new SpellContext.SpellException("abyss.insufficient_effect", price);
                }
                ctx.spendMana(price);

                living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
                                .wrapAsHolder(effect),
                        seconds * 20, amplifier, false, true, true));
                ctx.trace("effect " + path + " level " + amplifier + " for " + seconds
                        + "s (" + price + " abyss)");
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
