package com.github.fireprime5.spellcraft.energy;

import com.github.fireprime5.spellcraft.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.List;
import java.util.Optional;

/**
 * Плавильня: переплавляет предметы, тратя энергию.
 *
 * <p>Смысл машины простой — показать, что линия проводов обслуживает не одну
 * машину. До плавильни в моде был только извлекатель, и провод нельзя было
 * проверить на чём-то, кроме «светится ли полоска энергии».
 *
 * <p>Рецепты берутся из игры: {@link SmeltingRecipe} — это ровно то, чем
 * пользуется обычная печь. Ни одной своей таблицы, и любой мод, добавивший
 * рецепт плавки, работает здесь без участия Spellcraft.
 *
 * <p>Два слота, как у печи: вход и выход. Воронки подключаются обычным
 * {@link IItemHandler}, машина встаёт в линию без ручной переклади.
 */
public class SmelterBlock extends BaseEntityBlock {

    public static final MapCodec<SmelterBlock> CODEC = simpleCodec(SmelterBlock::new);

    /** Тики на один предмет при полной энергии: вдвое быстрее обычной печи. */
    public static final int COOK_TICKS = 100;

    /** Энергия на один переплавленный предмет. */
    public static final int ENERGY_PER_ITEM = 800;

    /** Энергия за тик: столько уходит на протяжении всего переплава. */
    public static final int ENERGY_PER_TICK = ENERGY_PER_ITEM / COOK_TICKS;

    /** Слот входа. */
    public static final int SLOT_INPUT = 0;
    /** Слот выхода. */
    public static final int SLOT_OUTPUT = 1;
    /** Всего слотов: два. */
    public static final int SLOTS = 2;

    public SmelterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SmelterBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        // тикер общий из MachineBlockEntity: свой здесь конфликтовал бы с ним
        // по сигнатуре после стирания обобщений
        return createTickerHelper(type, ModBlocks.SMELTER_ENTITY.get(),
                MachineEnergy.MachineBlockEntity.ticker());
    }

    /**
     * Форма блока: корпус 14×14 и 13 пикселей высотой.
     *
     * <p>Не 16×16: полноблочный куб выглядел бы болванкой и занимал бы место
     * зря. Чуть меньше полного блока он читается как печь, и на его грани
     * остаётся рамка, по которой видно, где блок закончился.
     */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return SHAPE;
    }

    private static final VoxelShape SHAPE = Shapes.box(1, 0, 1, 15, 13, 15);

    /**
     * ПКМ без предмета: показать прогресс.
     *
     * <p>Полоса прогресса в мире не рисуется — это потребовало бы отдельной
     * анимации, — поэтому состояние видно только по подсказке оператору и по
     * тому, что из блока выпадает результат.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        // интерфейс открывается всем: класть предметы должен и обычный игрок
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (menuId, inventory, p) -> new SmelterMenu(menuId, inventory, pos),
                Component.translatable("block.spellcraft.smelter")), pos);
        return InteractionResult.SUCCESS;
    }

    /**
     * Рецепт плавки для стека.
     *
     * <p>Вход подставляется ванильным {@link SingleRecipeInput}: рецепт плавки
     * объявлен именно на него, и свой класс-заглушка не подошёл бы по типам.
     */
    public static Optional<SmeltingRecipe> recipeFor(Level level, ItemStack input) {
        return level.getRecipeManager().getRecipeFor(
                        RecipeType.SMELTING, new SingleRecipeInput(input), level)
                .map(holder -> holder.value())
                .map(SmeltingRecipe.class::cast);
    }

    /** Блок-сущность плавильни: два слота, прогресс и энергия. */
    public static class SmelterBlockEntity extends MachineEnergy.MachineBlockEntity
            implements IItemHandlerModifiable, net.minecraft.world.Container,
            com.github.fireprime5.spellcraft.fluid.AbyssPort {

        private final NonNullList<ItemStack> items =
                NonNullList.withSize(SLOTS, ItemStack.EMPTY);

        /** Сколько тиков уже идёт переплавление текущего предмета. */
        private int progress;

        /**
         * Модульная сетка плавильни.
         *
         * <p>Пустая по умолчанию: машина работает на одной энергии, пока
         * игрок ничего в неё не поставил. С модулями начинает требовать бездну.
         */
        private com.github.fireprime5.spellcraft.machine.MachineGrid grid =
                new com.github.fireprime5.spellcraft.machine.MachineGrid(
                        com.github.fireprime5.spellcraft.machine.MachineGrid.Sets.smelter());

        public SmelterBlockEntity(BlockPos pos, BlockState state) {
            super(ModBlocks.SMELTER_ENTITY.get(), pos, state,
                    MachineEnergy.DEFAULT_CAPACITY);
        }

        /** Один шаг переплава: тик, трата энергии, выдача результата. */
        public void work(Level level, BlockPos pos) {
            ItemStack input = items.get(SLOT_INPUT);
            if (input.isEmpty()) {
                progress = 0;
                return;
            }
            ItemStack output = items.get(SLOT_OUTPUT);
            Optional<SmeltingRecipe> found = recipeFor(level, input);
            if (found.isEmpty()) {
                progress = 0;
                return;
            }
            // getResultItem, а не result: в 1.21 рецепты отдают результат
            // по реестру именно этим именем
            ItemStack result = found.get().getResultItem(level.registryAccess());
            // выход занят не тем, что получится: переплавивать некуда
            if (!output.isEmpty() && (!ItemStack.isSameItemSameComponents(output, result)
                    || output.getCount() + result.getCount() > output.getMaxStackSize())) {
                progress = 0;
                return;
            }
            // энергия проверяется до тика: недоплаченный тик не начинается,
            // иначе предмет переплавился бы «в долг»
            if (!energy().consume(ENERGY_PER_TICK)) {
                progress = 0;
                return;
            }
            // модули заряжают машину бездной: без единого модуля налога нет,
            // и пустая плавильня работает на одной энергии
            if (grid.abyssDemand() > 0 && !consumeAbyss(grid.abyssDemand())) {
                progress = 0;
                return;
            }
            progress++;
            if (progress < COOK_TICKS) {
                setChanged();
                return;
            }
            progress = 0;
            input.shrink(1);
            if (output.isEmpty()) {
                items.set(SLOT_OUTPUT, result.copy());
            } else {
                output.grow(result.getCount());
            }
            setChanged();
        }

        public int progress() {
            return progress;
        }

        /** Модульная сетка плавильни. */
        public com.github.fireprime5.spellcraft.machine.MachineGrid grid() {
            return grid;
        }

        /** Бездна, набранная трубой за последний тик. */
        private int abyssBuffer;

        /**
         * Съедает бездну из запаса, который налили трубы.
         *
         * <p>Бездна в трубе и бездна в кошельке игрока — разные ресурсы,
         * смешивать их нельзя: иначе игрок набил бы машину из своего кошелька
         * и обошёл бы трубы целиком.
         *
         * @return true — хватило; false — работа встаёт
         */
        private boolean consumeAbyss(int need) {
            if (need <= 0) {
                return true;
            }
            if (abyssBuffer < need) {
                // нехватка не добирается частично: недоплаченный тик
                // не начинается, иначе модуль работал бы «в долг»
                return false;
            }
            abyssBuffer -= need;
            return true;
        }

        @Override
        public int abyssStored() {
            return abyssBuffer;
        }

        @Override
        public int abyssCapacity() {
            // запас машины: труба заливает заранее, иначе модуль с требованием
            // в два тика работал бы через раз
            return 256;
        }

        @Override
        public int abyssReceive(int amount) {
            if (amount <= 0) {
                return 0;
            }
            int room = Math.max(0, abyssCapacity() - abyssBuffer);
            int added = Math.min(room, amount);
            abyssBuffer += added;
            return added;
        }

        @Override
        public int abyssExtract(int amount) {
            if (amount <= 0) {
                return 0;
            }
            int taken = Math.min(abyssBuffer, amount);
            abyssBuffer -= taken;
            return taken;
        }

        @Override
        public int abyssDemand() {
            return grid.abyssDemand();
        }

        // ------------------------------------------------- предметы

        // ----------------------------------------------- Container
        //
        // Слоты меню создаются через Slot(blockEntity, ...), а тот требует
        // именно Container. Поэтому сущность его реализует — так же, как это
        // делает обычная печь. Через capability слот не создаётся: нужен и
        // быстрый перенос предметов, и серверная проверка.

        @Override
        public int getContainerSize() {
            return SLOTS;
        }

        @Override
        public boolean isEmpty() {
            return items.get(SLOT_INPUT).isEmpty() && items.get(SLOT_OUTPUT).isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return slot >= 0 && slot < SLOTS ? items.get(slot) : ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (slot >= 0 && slot < SLOTS) {
                items.set(slot, stack);
                setChanged();
            }
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            if (slot < 0 || slot >= SLOTS) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = items.get(slot);
            ItemStack taken = stack.split(Math.min(count, stack.getCount()));
            setChanged();
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack stack = items.get(slot);
            items.set(slot, ItemStack.EMPTY);
            setChanged();
            return stack;
        }

        @Override
        public void clearContent() {
            items.clear();
            setChanged();
        }

        @Override
        public boolean stillValid(net.minecraft.world.entity.player.Player player) {
            return true;
        }

        @Override
        public int getSlots() {
            return SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot >= 0 && slot < SLOTS ? items.get(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != SLOT_INPUT) {
                return stack;
            }
            ItemStack existing = items.get(SLOT_INPUT);
            // вход ровно на один предмет: иначе плавильня становилась бы
            // накопителем сырья, а это уже сосуд
            if (!existing.isEmpty()) {
                return stack;
            }
            // контракт IItemHandler: при simulate НЕЛЬЗЯ ни менять переданный
            // стек, ни писать в инвентарь. Раньше здесь звали split(1) (что
            // мутировало стек вызывающего) и items.set даже в симуляции — из-за
            // этого воронка «принимала» предмет и тут же удаляла его из мира.
            if (simulate) {
                return stack.getCount() > 1 ? stack.copyWithCount(stack.getCount() - 1) : ItemStack.EMPTY;
            }
            ItemStack single = stack.split(1);
            items.set(SLOT_INPUT, single);
            setChanged();
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != SLOT_OUTPUT) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = items.get(SLOT_OUTPUT);
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int taken = Math.min(amount, stack.getCount());
            if (simulate) {
                return stack.copyWithCount(taken);
            }
            ItemStack result = stack.split(taken);
            setChanged();
            return result;
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            if (slot >= 0 && slot < SLOTS) {
                items.set(slot, stack);
                setChanged();
            }
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == SLOT_INPUT ? 1 : 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == SLOT_INPUT && !stack.isEmpty();
        }

        // ------------------------------------------------- сохранение

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.putInt("progress", progress);
            tag.putInt("abyss", abyssBuffer);
            tag.put("grid", grid.save());
            // слоты сохраняются поимённо: иначе переставить их можно было бы
            // сменой версии мода, и плавильня молча теряла бы содержимое
            // saveOptional, а не save: save на пустом стеке бросает
            // «Cannot encode empty ItemStack». Так и случилось — игра падала
            // при ЛЮБОМ сохранении блока, то есть при разрушении плавильни
            // и при каждой записи чанка
            tag.put("input", items.get(SLOT_INPUT).saveOptional(registries));
            tag.put("output", items.get(SLOT_OUTPUT).saveOptional(registries));
        }

        @Override
        public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            progress = Math.clamp(tag.getInt("progress"), 0, COOK_TICKS);
            abyssBuffer = Math.clamp(tag.getInt("abyss"), 0, abyssCapacity());
            grid.load(tag.getCompound("grid"));
            items.set(SLOT_INPUT, readStack(tag, "input", registries));
            items.set(SLOT_OUTPUT, readStack(tag, "output", registries));
        }

        /** Чтение слота: отсутствующий или битый тег даёт пустой стек. */
        private static ItemStack readStack(CompoundTag tag, String key,
                                           HolderLookup.Provider registries) {
            if (!tag.contains(key, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                return ItemStack.EMPTY;
            }
            // parse возвращает Optional: битый тег даёт пустой стек, а не стек
            // с нулевым количеством, который воронки считают настоящим
            return ItemStack.parse(registries, tag.getCompound(key))
                    .filter(stack -> !stack.isEmpty())
                    .orElse(ItemStack.EMPTY);
        }
    }
}
