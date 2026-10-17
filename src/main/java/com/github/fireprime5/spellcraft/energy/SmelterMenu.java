package com.github.fireprime5.spellcraft.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Меню плавильни: вход, выход и инвентарь игрока.
 *
 * <p>Слоты блока берутся из блочной сущности напрямую, а не через capability:
 * так перетаскивание мышью работает без лишнего слоя, а сервер всё равно
 * проверяет всё сам.
 */
public class SmelterMenu extends AbstractContainerMenu {

    /** Плавильня за этим меню. */
    private final SmelterBlock.SmelterBlockEntity smelter;

    /** Доступ к миру: нужен для проверки рецепта и для живучести меню. */
    private final ContainerLevelAccess access;

    public SmelterMenu(int id, Inventory playerInventory, BlockPos pos) {
        this(id, playerInventory, pos, ContainerLevelAccess.create(
                playerInventory.player.level(), pos));
    }

    /** Конструктор для клиента: блок и позиция на клиенте неизвестны. */
    public SmelterMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, BlockPos.ZERO, ContainerLevelAccess.NULL);
    }

    public SmelterMenu(int id, Inventory playerInventory, BlockPos pos,
                       ContainerLevelAccess access) {
        super(com.github.fireprime5.spellcraft.registry.ModMenus.SMELTER.get(), id);
        this.access = access;
        this.smelter = playerInventory.player.level().getBlockEntity(pos)
                instanceof SmelterBlock.SmelterBlockEntity block
                ? block
                : null;

        // На клиенте блочной сущности нет: меню собирается без мира (см.
        // ModMenus, pos = BlockPos.ZERO), поэтому smelter == null. Slot с
        // null-контейнером ронял бы экран на getItem() ещё до отрисовки, —
        // поэтому слоты всегда получают настоящий контейнер: на сервере это
        // сама плавильня, на клиенте — пустая заглушка (содержимое слотам
        // приходит синхронизацией меню).
        net.minecraft.world.Container slots = smelter != null
                ? smelter
                : new net.minecraft.world.SimpleContainer(SmelterBlock.SLOTS);

        // вход сверху, выход снизу — как у обычной печи, порядок привычен
        addSlot(new Slot(slots, SmelterBlock.SLOT_INPUT, 56, 17) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                // только то, что вообще переплавляется: иначе игрок забьёт
                // вход рудой без рецепта и будет гадать, почему ничего нет
                return SmelterMenu.this.smelter != null
                        && maySmelt(stack);
            }
        });
        // выход не принимает ничего: забрать результат можно только мышью
        addSlot(new Slot(slots, SmelterBlock.SLOT_OUTPUT, 56, 53) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        // инвентарь игрока: три ряда по девять плюс горячие слоты
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    /** Плавильня за меню: нужна экрану для подсказки результата. */
    public SmelterBlock.SmelterBlockEntity smelter() {
        return smelter;
    }

    /** Можно ли положить этот предмет во вход: есть ли у него рецепт плавки. */
    private boolean maySmelt(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        // уровень берётся через evaluate: ContainerLevelAccess в 1.21 не
        // имеет getLevel(), только функциональный доступ
        return access.evaluate((level, pos) ->
                SmelterBlock.recipeFor(level, stack).isPresent(), false);
    }

    /** Что вообще получится из текущего содержимого входа. */
    public ItemStack getResultFor() {
        if (smelter == null) {
            return ItemStack.EMPTY;
        }
        ItemStack input = smelter.getStackInSlot(SmelterBlock.SLOT_INPUT);
        if (input.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return access.evaluate((level, pos) ->
                SmelterBlock.recipeFor(level, input)
                        .map(recipe -> recipe.getResultItem(level.registryAccess()))
                        .orElse(ItemStack.EMPTY), ItemStack.EMPTY);
    }

    /** Насколько переплавлен текущий предмет, 0..COOK_TICKS. */
    public int getProgress() {
        return smelter == null ? 0 : smelter.progress();
    }

    /**
     * Быстрый перенос предметов shift-кликом.
     *
     * <p>Выход плавильни выбрасывать нельзя: игрок забирал бы результат мимо
     * переплава и плавильня работала бы вхолостую.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < SmelterBlock.SLOTS) {
            if (!moveItemStackTo(stack, SmelterBlock.SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // из инвентаря — только во вход, и только то, что переплавляется
            if (!moveItemStackTo(stack, SmelterBlock.SLOT_INPUT,
                    SmelterBlock.SLOT_INPUT + 1, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    /**
     * Кнопка «Модули» в панели плавильни.
     *
     * <p>Открывает модульную сетку машины. Пакет уходит на сервер: клиент не
     * знает ни позиции машины, ни того, кто ею владеет, а проверять права
     * должен сервер, а не окно.
     */
    public void openMachineGrid(net.minecraft.world.entity.player.Player player) {
        if (smelter == null) {
            return;
        }
        // Запрос уходит на сервер, а не команда «открыть экран»: клиент не знает,
        // что за блок стоит по этому адресу и имеет ли игрок на него право.
        // Проверка расстояния и типа блока — на сервере (MachineAccess).
        com.github.fireprime5.spellcraft.network.RequestMachineGridPayload.send(
                smelter.getBlockPos(), "smelter");
    }

    /** При закрытии панели предметы возвращаются игроку. */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (smelter == null) {
            return;
        }
        // вход и выход возвращаются в инвентарь, а не дропаются в мир: иначе
        // предметы разлетались бы по полу при каждом закрытии панели
        giveBack(player, SmelterBlock.SLOT_INPUT);
        giveBack(player, SmelterBlock.SLOT_OUTPUT);
    }

    private void giveBack(Player player, int slot) {
        if (smelter == null) {
            return;
        }
        ItemStack stack = smelter.getStackInSlot(slot);
        if (stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack)) {
            // инвентарь полон: отдаём в руки, а если и они заняты — в мир
            player.drop(stack, false);
        }
        smelter.setStackInSlot(slot, ItemStack.EMPTY);
    }

    @Override
    public boolean stillValid(Player player) {
        return smelter != null && stillValid(access, player,
                com.github.fireprime5.spellcraft.registry.ModBlocks.SMELTER.get());
    }
}