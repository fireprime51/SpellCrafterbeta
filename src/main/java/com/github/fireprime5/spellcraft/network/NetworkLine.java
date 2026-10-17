package com.github.fireprime5.spellcraft.network;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

import java.util.EnumSet;
import java.util.Set;

/**
 * Одна линия внутри блока сети: провод одного цвета или труба бездны.
 *
 * <p>Линий в блоке до трёх, у каждой свой слот и свой набор подключений. Именно
 * это и даёт то, ради чего всё затевалось: в одну грань машины входят три линии —
 * энергетические трубы разных цветов и жидкостная, — и они стоят в разных точках
 * грани и не сливаются.
 *
 * <p>Подключения хранятся битовой маской из шести бит, а не свойствами блока:
 * свойств было бы восемнадцать (три линии по шесть сторон) плюс цвета, и ни один
 * blockstate такое не переживает. Маска живёт в NBT блок-сущности.
 *
 * <p>Сама линия — и хранилище энергии, и хранилище жидкости. Иначе переносу
 * пришлось бы отдельно знать про блок-сущности соседей, а соседом линии может
 * быть и бак, и машина, и вторая линия в том же блоке.
 */
public final class NetworkLine implements IEnergyStorage, IFluidHandler {

    private static final String KIND = "kind";
    private static final String COLOR = "color";
    private static final String MASK = "mask";
    private static final String STORED = "stored";
    private static final String FLUID = "fluid";

    private final LineKind kind;
    private final DyeColor color;
    /** Жидкость трубы; у остальных видов линии всегда {@code null}. */
    private ResourceLocation fluid;
    private int mask;
    /** Запас энергии, бездны или жидкости в миллиб buckets. */
    private int stored;

    /**
     * @param kind вид линии: провод или труба бездны
     * @param color цвет провода; для трубы бездны игнорируется
     * @param mask битовая маска подключений по шести сторонам
     * @param stored запас энергии или бездны
     */
    public NetworkLine(LineKind kind, DyeColor color, int mask, int stored) {
        this.kind = kind;
        this.color = color;
        this.mask = mask & NetworkGeometry.ALL_SIDES;
        this.stored = Math.max(0, stored);
    }

    /**
     * Линия с жидкостью: для трубы.
     *
     * @param fluid что за жидкость; {@code null}, пока труба ни к чему не подключена
     */
    public NetworkLine(LineKind kind, DyeColor color, int mask, int stored,
                       ResourceLocation fluid) {
        this(kind, color, mask, stored);
        this.fluid = kind == LineKind.FLUID ? fluid : null;
    }

    /** Новая линия без подключений и без запаса. */
    public static NetworkLine empty(LineKind kind, DyeColor color) {
        return new NetworkLine(kind, color, 0, 0);
    }

    /** Новая пустая труба: жидкость ещё неизвестна, она берётся у соседа. */
    public static NetworkLine emptyFluid() {
        return new NetworkLine(LineKind.FLUID, DyeColor.WHITE, 0, 0, null);
    }

    /** Жидкость трубы или {@code null}: у остальных видов её нет. */
    public ResourceLocation fluid() {
        return fluid;
    }

    /**
     * Запомнить, что именно по этой трубе течёт.
     *
     * <p>Пока жидкость неизвестна, труба стыкуется с любой: узнать она может
     * только от соседа. Разным жидкостям труба не подходит — иначе вода и лава
     * оказались бы в одной линии, а это две разные сети в одном блоке.
     */
    public void fluid(ResourceLocation fluid) {
        if (kind != LineKind.FLUID) {
            return;
        }
        if (stored > 0 && this.fluid != null && fluid != null && !this.fluid.equals(fluid)) {
            // в трубе уже что-то есть: сменить жидкость на полной можно только
            // вылив её, иначе она пропала бы молча
            return;
        }
        this.fluid = fluid;
    }

    public LineKind kind() {
        return kind;
    }

    public DyeColor color() {
        return color;
    }

    /** Битовая маска подключений: бит {@code i} — сторона {@code Direction.values()[i]}. */
    public int mask() {
        return mask;
    }

    /** Новая маска. Лишние биты отсекаются: иначе в NBT попадёт мусор. */
    public void mask(int mask) {
        this.mask = mask & NetworkGeometry.ALL_SIDES;
    }

    public int stored() {
        return stored;
    }

    public void stored(int stored) {
        this.stored = Math.max(0, stored);
    }

    /** Подключена ли линия в эту сторону. */
    public boolean connected(Direction direction) {
        return (mask & (1 << direction.get3DDataValue())) != 0;
    }

    /** Подключает или отключает линию в одну сторону. */
    public void connect(Direction direction, boolean value) {
        int бит = 1 << direction.get3DDataValue();
        mask = value ? mask | бит : mask & ~бит;
    }

    /** Все стороны, в которые линия смотрит. */
    public Set<Direction> sides() {
        Set<Direction> стороны = EnumSet.noneOf(Direction.class);
        for (Direction direction : Direction.values()) {
            if (connected(direction)) {
                стороны.add(direction);
            }
        }
        return стороны;
    }

    /** Задаёт стороны сразу: то, что пересчитал блок. */
    public void sides(Set<Direction> стороны) {
        int mask = 0;
        for (Direction direction : стороны) {
            mask |= 1 << direction.get3DDataValue();
        }
        this.mask = mask;
    }

    /** Есть ли хоть одно подключение: висячий конец сети ни о чём. */
    public boolean connected() {
        return mask != 0;
    }

    /**
     * Такая же линия, но не она сама: два блока с такой линией соединяются.
     *
     * <p>Вид и цвет — это идентичность сети. Две линии одного вида и цвета
     * свариваются в одну сеть, разного цвета — остаются разными: цвет и есть
     * то, чем линии различаются.
     */
    public boolean sameNetwork(NetworkLine другой) {
        if (другой == null || kind != другой.kind) {
            return false;
        }
        if (kind == LineKind.FLUID) {
            // неизвестная жидкость подходит к любой: узнать её можно только от
            // соседа, и иначе две трубы ни к чему не сойдутся
            return fluid == null || другой.fluid == null || fluid.equals(другой.fluid);
        }
        return color == другой.color;
    }

    /** Записать линию в NBT. */
    public CompoundTag save(CompoundTag tag) {
        tag.putString(KIND, kind.id());
        tag.putString(COLOR, color.getSerializedName());
        tag.putInt(MASK, mask);
        tag.putInt(STORED, stored);
        if (kind == LineKind.FLUID && fluid != null) {
            tag.putString(FLUID, fluid.toString());
        }
        return tag;
    }

    /** Прочитать линию из NBT. Битое имя цвета читается как белый. */
    public static NetworkLine load(CompoundTag tag) {
        LineKind kind = LineKind.byId(tag.getString(KIND));
        DyeColor color = DyeColor.byName(tag.getString(COLOR), DyeColor.WHITE);
        ResourceLocation fluid = tag.contains(FLUID)
                ? ResourceLocation.tryParse(tag.getString(FLUID)) : null;
        return new NetworkLine(kind, color, tag.getInt(MASK), tag.getInt(STORED), fluid);
    }

    // --- жидкость ---------------------------------------------------------

    /** Труба как хранилище жидкости: одна ёмкость на всю линию. */
    @Override
    public int getTanks() {
        return 1;
    }

    /** Что сейчас в трубе: пусто, если она пуста или жидкость ещё неизвестна. */
    @Override
    public FluidStack getFluidInTank(int tank) {
        if (kind != LineKind.FLUID || fluid == null || stored <= 0 || tank != 0) {
            return FluidStack.EMPTY;
        }
        Fluid жидкость = BuiltInRegistries.FLUID.get(fluid);
        return жидкость == null ? FluidStack.EMPTY : new FluidStack(жидкость, stored);
    }

    @Override
    public int getTankCapacity(int tank) {
        return NetworkFluids.CAPACITY;
    }

    /** Труба принимает только ту жидкость, с какой уже связана или пуста. */
    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return !stack.isEmpty()
                && NetworkFluids.принимает(this, BuiltInRegistries.FLUID.getKey(stack.getFluid()));
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return NetworkFluids.fill(this, resource, action.simulate());
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || !isFluidValid(0, resource)) {
            return FluidStack.EMPTY;
        }
        return NetworkFluids.drain(this, Math.min(resource.getAmount(), stored),
                action.simulate());
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return NetworkFluids.drain(this, maxDrain, action.simulate());
    }

    // --- Forge Energy -----------------------------------------------------

    @Override
    public int getEnergyStored() {
        return stored;
    }

    @Override
    public int getMaxEnergyStored() {
        return NetworkEnergy.CAPACITY;
    }

    @Override
    public boolean canExtract() {
        return kind == LineKind.ENERGY;
    }

    @Override
    public boolean canReceive() {
        return kind == LineKind.ENERGY;
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        if (!canReceive() || toReceive <= 0) {
            return 0;
        }
        int принято = Math.min(getMaxEnergyStored() - stored, toReceive);
        if (!simulate) {
            stored += Math.max(0, принято);
        }
        return Math.max(0, принято);
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        if (!canExtract() || toExtract <= 0) {
            return 0;
        }
        int взято = Math.min(stored, toExtract);
        if (!simulate) {
            stored -= взято;
        }
        return взято;
    }


}
