package com.github.fireprime5.spellcraft.spell;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.github.fireprime5.spellcraft.spell.PortType.ITEM;
import static com.github.fireprime5.spellcraft.spell.PortType.NUMBER;
import static com.github.fireprime5.spellcraft.spell.PortType.POINT;

/**
 * Модули предметов и блоков.
 *
 * <p>До этого файла предмет был почти мёртвым типом: его давал только модуль
 * «Предмет в руке», а читался только счётчиком в инвентаре. Из-за этого
 * «Постройка», которой нужен предмет на входе, умела строить исключительно тем,
 * что уже лежит в руке, — а значит и взять блок из мира, и выдать предмет,
 * и забрать предмет обратно было нечем.
 *
 * <p>Здесь закрывается вся эта дыра: блок читается из мира, предмет выдаётся,
 * выбрасывается и забирается обратно, два блока меняются местами.
 *
 * <p>Ни один модуль здесь не создаёт предметы из ничего: «Выдать» кладёт в
 * инвентарь ровно столько, сколько пришло на вход, а «Забрать» — наоборот.
 * Единственный источник предметов остаётся добыча.
 */
public final class ItemModules {

    private ItemModules() {
    }

    private static final SpellModuleType.Category ACT = SpellModuleType.Category.ACTIONS;
    private static final SpellModuleType.Category ENT = SpellModuleType.Category.ENTITIES;
    private static final SpellModuleType.Category NUM = SpellModuleType.Category.NUMBERS;

    private static final int C_ITEM = 0xFFFF8FA3;
    private static final int C_BLOCK = 0xFF7FC8A9;
    private static final int C_NUM = 0xFFE2A44E;

    /** Потолок предметов за один каст: защита от забивания инвентаря. */
    public static final int MAX_ITEM_COUNT = 64;
    /** Сколько блоков «Залить область» кладёт за один каст. */
    public static final int MAX_FILL_BLOCKS = 256;

    public static void registerAll() {
        reading();
        blocks();
        items();
    }

    // ----------------------------------------------------------- чтение

    private static void reading() {
        /** Прочитать блок в точке как предмет — без разрушения. */
        SpellModules.register(new SpellModuleType(SpellModules.id("block_at"),
                ENT, C_BLOCK) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 3; }
            @Override public PortType[] inputPorts() { return new PortType[]{POINT}; }
            @Override public PortType[] outputPorts() { return new PortType[]{ITEM}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                BlockPos pos = in.get(0).asBlockPos();
                BlockState state = serverState(ctx, pos);
                ItemStack stack = new ItemStack(state.getBlock());
                if (stack.isEmpty()) {
                    // воздух не предмет: вернулась бы пустая стопка, и дальше
                    // по графу пошло бы «это блок», который на самом деле ничем
                    throw new SpellContext.SpellException("cast.nothing_there");
                }
                ctx.trace("block at " + pos + " is " + stack.getHoverName().getString());
                out.add(SpellValue.of(stack));
            }
        });

        /** Жидкость в точке как предмет — ведро воды, а не сам блок. */
        SpellModules.register(new SpellModuleType(SpellModules.id("fluid_at"),
                ENT, C_BLOCK) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 3; }
            @Override public PortType[] inputPorts() { return new PortType[]{POINT}; }
            @Override public PortType[] outputPorts() { return new PortType[]{ITEM}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                BlockPos pos = in.get(0).asBlockPos();
                var fluid = serverState(ctx, pos).getFluidState();
                if (fluid.isEmpty()) {
                    throw new SpellContext.SpellException("cast.no_fluid_there");
                }
                // ведро, а не сам блок: жидкость в мире — это блок, но как предмет
                // она существует только в ведре
                ItemStack bucket = new ItemStack(fluid.getType().getBucket());
                if (bucket.isEmpty()) {
                    throw new SpellContext.SpellException("cast.no_bucket_for",
                            BuiltInRegistries.FLUID.getKey(fluid.getType()).toString());
                }
                out.add(SpellValue.of(bucket));
            }
        });

        /** Предмет из слота хотбара: 1 — самая рука, 9 — крайняя ячейка. */
        SpellModules.register(new SpellModuleType(SpellModules.id("held_slot"),
                ENT, C_ITEM) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 2; }
            @Override public PortType[] inputPorts() { return new PortType[]{NUMBER}; }
            @Override public PortType[] outputPorts() { return new PortType[]{ITEM}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                Inventory inventory = playerInventory(ctx, "cast.inv_read");
                // слот приходит из заклинания, поэтому приводится к целому и
                // зажимается: «0» и «99» не должны ни падать, ни читать чужое
                int slot = (int) Math.round(in.get(0).asNumber());
                if (slot < 1 || slot > 9) {
                    throw new SpellContext.SpellException("cast.no_such_slot", slot);
                }
                ItemStack stack = inventory.items.get(slot - 1);
                if (stack.isEmpty()) {
                    throw new SpellContext.SpellException("cast.slot_empty", slot);
                }
                out.add(SpellValue.of(stack.copy()));
            }
        });

        /** 1 — рука пуста, 0 — нет. */
        SpellModules.register(new SpellModuleType(SpellModules.id("empty_hand"),
                NUM, C_NUM) {
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 1; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                Inventory inventory = playerInventory(ctx, "cast.inv_read");
                out.add(SpellValue.of(inventory.getItem(inventory.selected).isEmpty()
                        ? 1 : 0));
            }
        });

        /** Предмет, который кастующий держит в руке, — только чтение, без копии мира. */
        SpellModules.register(new SpellModuleType(SpellModules.id("offhand_item"),
                ENT, C_ITEM) {
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 2; }
            @Override public PortType[] outputPorts() { return new PortType[]{ITEM}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                ItemStack stack = playerInventory(ctx, "cast.inv_read").offhand.get(0);
                if (stack.isEmpty()) {
                    throw new SpellContext.SpellException("cast.offhand_empty");
                }
                out.add(SpellValue.of(stack.copy()));
            }
        });
    }

    // ------------------------------------------------------------ блоки

    private static void blocks() {
        /** Поменять два блока местами, вместе с добычей каждого. */
        SpellModules.register(new SpellModuleType(SpellModules.id("swap_blocks"),
                ACT, C_BLOCK) {
            @Override public int inputs() { return 2; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 12; }
            @Override public PortType[] inputPorts() { return new PortType[]{POINT, POINT}; }
            @Override public PortType[] outputPorts() { return new PortType[]{POINT}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                BlockPos from = in.get(0).asBlockPos();
                BlockPos to = in.get(1).asBlockPos();
                if (from.equals(to)) {
                    throw new SpellContext.SpellException("cast.same_block");
                }
                ServerLevel server = serverLevel(ctx);
                if (!server.isLoaded(from) || !server.isLoaded(to)) {
                    throw new SpellContext.SpellException("cast.chunk_unloaded");
                }
                BlockState first = server.getBlockState(from);
                BlockState second = server.getBlockState(to);
                if (first.isAir() || second.isAir()) {
                    throw new SpellContext.SpellException("cast.swap_air");
                }
                // добыча считается до перестановки: блок с Bedrock-подобными
                // свойствами всё равно не ломается, а без этого проверки
                // воздушный блок забрасывал бы игроку воздушные предметы
                var firstDrops = Block.getDrops(first, server, from, server.getBlockEntity(from),
                        ctx.caster(), hand(ctx));
                var secondDrops = Block.getDrops(second, server, to, server.getBlockEntity(to),
                        ctx.caster(), hand(ctx));
                server.destroyBlock(from, false);
                server.destroyBlock(to, false);
                server.setBlockAndUpdate(to, first);
                server.setBlockAndUpdate(from, second);
                for (ItemStack drop : firstDrops) {
                    Block.popResource(server, to, drop);
                }
                for (ItemStack drop : secondDrops) {
                    Block.popResource(server, from, drop);
                }
                ctx.trace("swapped " + from + " <-> " + to);
                out.add(SpellValue.of(Vec3.atCenterOf(to), SpellValue.Kind.POSITION));
            }
        });

        /**
         * Залить область между двумя точками.
         *
         * <p>Прямоугольник задаётся двумя противоположными углами, а не радиусом:
         * так можно построить стену или пол точно там, где нужно. Предметы
         * берутся из инвентаря, поэтому область не превращается в источник
         * блоков — на сколько хватило, на столько и залито.
         */
        SpellModules.register(new SpellModuleType(SpellModules.id("fill_region"),
                ACT, C_BLOCK) {
            @Override public int inputs() { return 3; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 20; }
            @Override public PortType[] inputPorts() {
                return new PortType[]{POINT, POINT, ITEM};
            }
            @Override public PortType[] outputPorts() { return new PortType[]{POINT}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                BlockPos corner = in.get(0).asBlockPos();
                BlockPos other = in.get(1).asBlockPos();
                ItemStack stack = in.get(2).asItem();
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
                if (volume > MAX_FILL_BLOCKS) {
                    // объём считается ДО постройки: иначе игрок увидел бы
                    // частично залитую область и не понял бы, где предел
                    // ключ ошибки принимает один аргумент (так устроен
                    // SpellIssue), поэтому предел не подставляется: он виден
                    // в подсказке модуля
                    throw new SpellContext.SpellException("cast.region_too_big", volume);
                }
                Inventory inventory = inventory(ctx);
                int placed = 0;
                for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                    if (!server.isLoaded(pos)) {
                        continue;
                    }
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

        /** Убрать блок, не роняя его: воздух на выходе. */
        SpellModules.register(new SpellModuleType(SpellModules.id("clear_block"),
                ACT, C_BLOCK) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 6; }
            @Override public PortType[] inputPorts() { return new PortType[]{POINT}; }
            @Override public PortType[] outputPorts() { return new PortType[]{POINT}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                BlockPos pos = in.get(0).asBlockPos();
                ServerLevel server = serverLevel(ctx);
                if (server.getBlockState(pos).isAir()) {
                    throw new SpellContext.SpellException("cast.nothing_there");
                }
                // false вместо true: блок должен исчезнуть, но не выпасть
                // предметом — иначе это второй способ добычи в обход ман
                server.destroyBlock(pos, false);
                out.add(SpellValue.of(Vec3.atCenterOf(pos), SpellValue.Kind.POSITION));
            }
        });
    }

    // ---------------------------------------------------------- предметы

    private static void items() {
        /** Положить предмет в инвентарь игрока. */
        SpellModules.register(new SpellModuleType(SpellModules.id("give_item"),
                ACT, C_ITEM) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 8; }
            @Override public PortType[] inputPorts() { return new PortType[]{ITEM}; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                ItemStack stack = in.get(0).asItem();
                if (stack.isEmpty()) {
                    throw new SpellContext.SpellException("cast.empty_item");
                }
                Inventory inventory = playerInventory(ctx, "cast.inv_read");
                int room = stack.getMaxStackSize();
                for (ItemStack slot : inventory.items) {
                    if (ItemStack.isSameItem(slot, stack)) {
                        room += stack.getMaxStackSize() - slot.getCount();
                    }
                }
                for (ItemStack slot : inventory.items) {
                    if (slot.isEmpty()) {
                        room += stack.getMaxStackSize();
                    }
                }
                if (room <= 0) {
                    throw new SpellContext.SpellException("cast.inv_full");
                }
                int given = Math.min(stack.getCount(), room);
                given = Math.min(given, MAX_ITEM_COUNT);
                ItemStack copy = stack.copy();
                copy.setCount(given);
                inventory.add(copy);
                ctx.trace("gave " + given + " x " + stack.getHoverName().getString()
                        + (given < stack.getCount() ? " (inventory full)" : ""));
                out.add(SpellValue.of(given));
            }
        });

        /** Выбросить предмет в точку. */
        SpellModules.register(new SpellModuleType(SpellModules.id("drop_item"),
                ACT, C_ITEM) {
            @Override public int inputs() { return 2; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 8; }
            @Override public PortType[] inputPorts() {
                return new PortType[]{ITEM, POINT};
            }
            @Override public PortType[] outputPorts() { return new PortType[]{POINT}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                ItemStack stack = in.get(0).asItem();
                if (stack.isEmpty()) {
                    throw new SpellContext.SpellException("cast.empty_item");
                }
                Vec3 pos = in.get(1).asVec();
                ServerLevel server = serverLevel(ctx);
                ItemStack copy = stack.copy();
                copy.setCount(Math.min(copy.getCount(), MAX_ITEM_COUNT));
                ItemEntity dropped = new ItemEntity(server, pos.x, pos.y, pos.z, copy);
                dropped.setDefaultPickUpDelay();
                server.addFreshEntity(dropped);
                ctx.trace("dropped " + copy.getCount() + " x "
                        + copy.getHoverName().getString());
                out.add(SpellValue.of(pos, SpellValue.Kind.POSITION));
            }
        });

        /**
         * Забрать предмет из инвентаря игрока.
         *
         * <p>Зеркало «Выдать»: вместе с предметом уходит и его значение, дальше
         * по графу идёт уже снятая стопка. Так заклинание может разбирать и
         * собирать чужой инвентарь, не создавая предметов.
         */
        SpellModules.register(new SpellModuleType(SpellModules.id("consume_item"),
                ACT, C_ITEM) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 8; }
            @Override public PortType[] inputPorts() { return new PortType[]{ITEM}; }
            @Override public PortType[] outputPorts() { return new PortType[]{ITEM}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                ItemStack wanted = in.get(0).asItem();
                if (wanted.isEmpty()) {
                    throw new SpellContext.SpellException("cast.empty_item");
                }
                Inventory inventory = playerInventory(ctx, "cast.inv_read");
                int taken = 0;
                ItemStack takenStack = ItemStack.EMPTY;
                for (int i = 0; i < inventory.items.size() && taken < wanted.getCount(); i++) {
                    ItemStack slot = inventory.items.get(i);
                    if (!ItemStack.isSameItem(slot, wanted) || slot.getCount() <= 0) {
                        continue;
                    }
                    int take = Math.min(slot.getCount(), wanted.getCount() - taken);
                    if (takenStack.isEmpty()) {
                        takenStack = slot.copy();
                        takenStack.setCount(take);
                    } else {
                        takenStack.grow(take);
                    }
                    slot.shrink(take);
                    taken += take;
                }
                if (taken == 0) {
                    throw new SpellContext.SpellException("cast.no_such_item",
                            wanted.getHoverName().getString());
                }
                ctx.trace("took " + taken + " x " + wanted.getHoverName().getString());
                out.add(SpellValue.of(takenStack));
            }
        });
    }

    // ------------------------------------------------------------ хелперы

    private static ServerLevel serverLevel(SpellContext ctx) {
        if (!(ctx.level() instanceof ServerLevel server)) {
            throw new SpellContext.SpellException("cast.server_only");
        }
        return server;
    }

    private static BlockState serverState(SpellContext ctx, BlockPos pos) {
        return serverLevel(ctx).getBlockState(pos);
    }

    /** Инвентарь игрока, если кастует игрок. */
    private static Inventory playerInventory(SpellContext ctx, String key) {
        if (!(ctx.caster() instanceof Player player)) {
            throw new SpellContext.SpellException(key);
        }
        return player.getInventory();
    }

    /** Инвентарь или null: для постройки, которой тратить нечего. */
    private static Inventory inventory(SpellContext ctx) {
        return ctx.caster() instanceof Player player ? player.getInventory() : null;
    }

    private static ItemStack hand(SpellContext ctx) {
        return ctx.caster().getItemInHand(
                net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    private static BlockState blockStateOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Block block = Block.byItem(stack.getItem());
        return block == null ? null : block.defaultBlockState();
    }

    /**
     * Убирает один блок из инвентаря кастающего.
     *
     * <p>Копия правила из {@code WorldModules}: без этого «Залить область»
     * превращалась бы в бесконечный источник блоков.
     *
     * @return false — блоков в инвентаре больше нет
     */
    private static boolean takeBlock(Inventory inventory, ItemStack wanted) {
        if (inventory == null) {
            return true;
        }
        for (ItemStack stack : inventory.items) {
            if (ItemStack.isSameItem(stack, wanted) && stack.getCount() > 0) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }
}
