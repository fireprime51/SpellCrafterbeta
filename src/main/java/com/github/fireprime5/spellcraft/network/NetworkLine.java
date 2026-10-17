package com.github.fireprime5.spellcraft.network;

import com.github.fireprime5.spellcraft.fluid.AbyssPort;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.EnumSet;
import java.util.Set;

/**
 * Одна линия внутри блока сети: провод одного цвета или труба бездны.
 *
 * <p>Линий в блоке до трёх, у каждой свой слот, свой цвет и свой набор
 * подключений. Именно это и даёт то, ради чего всё затевалось: в одну грань
 * машины входят три линии разных цветов и труба — они стоят в разных точках
 * грани и не сливаются.
 *
 * <p>Подключения хранятся битовой маской из шести бит, а не свойствами блока:
 * свойств было бы восемнадцать (три линии по шесть сторон) плюс цвета, и ни один
 * blockstate такое не переживает. Маска живёт в NBT блок-сущности.
 *
 * <p>Сама линия — и хранилище энергии, и порт бездны. Иначе переносу пришлось бы
 * отдельно знать про блок-сущности соседей, а соседом линии может быть и бак, и
 * машина, и вторая линия в том же блоке.
 */
public final class NetworkLine implements IEnergyStorage, AbyssPort {

    private static final String KIND = "kind";
    private static final String COLOR = "color";
    private static final String MASK = "mask";
    private static final String STORED = "stored";

    private final LineKind kind;
    private final DyeColor color;
    private int mask;
    private int stored;

    /**
     * @param kind вид линии: провод или труба бездны
     * @param color цвет провода; для трубы бездны игнорируется
     * @param mask битовая маска подключений по шести сторонам
     * @param stored запас энергии или бездны
     */
    public NetworkLine(LineKind kind, DyeColor color, int mask, int stored) {
        this.kind = kind;
        this.color = kind == LineKind.ABYSS ? DyeColor.WHITE : color;
        this.mask = mask & NetworkGeometry.ALL_SIDES;
        this.stored = Math.max(0, stored);
    }

    /** Новая линия без подключений и без запаса. */
    public static NetworkLine empty(LineKind kind, DyeColor color) {
        return new NetworkLine(kind, color, 0, 0);
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
        return другой != null && kind == другой.kind && color == другой.color;
    }

    /** Записать линию в NBT. */
    public CompoundTag save(CompoundTag tag) {
        tag.putString(KIND, kind.id());
        tag.putString(COLOR, color.getSerializedName());
        tag.putInt(MASK, mask);
        tag.putInt(STORED, stored);
        return tag;
    }

    /** Прочитать линию из NBT. Битое имя цвета читается как белый. */
    public static NetworkLine load(CompoundTag tag) {
        LineKind kind = LineKind.byId(tag.getString(KIND));
        DyeColor color = DyeColor.byName(tag.getString(COLOR), DyeColor.WHITE);
        return new NetworkLine(kind, color, tag.getInt(MASK), tag.getInt(STORED));
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

    // --- бездна -----------------------------------------------------------

    @Override
    public int abyssStored() {
        return stored;
    }

    @Override
    public int abyssCapacity() {
        return NetworkEnergy.CAPACITY;
    }

    @Override
    public int abyssReceive(int amount) {
        if (kind != LineKind.ABYSS || amount <= 0) {
            return 0;
        }
        int room = abyssCapacity() - stored;
        int добавлено = Math.min(room, amount);
        stored += Math.max(0, добавлено);
        return Math.max(0, добавлено);
    }

    @Override
    public int abyssExtract(int amount) {
        if (kind != LineKind.ABYSS || amount <= 0) {
            return 0;
        }
        int взято = Math.min(stored, amount);
        stored -= взято;
        return Math.max(0, взято);
    }

    @Override
    public boolean abyssAccepts() {
        return kind == LineKind.ABYSS && stored < abyssCapacity();
    }

    @Override
    public boolean abyssProvides() {
        return kind == LineKind.ABYSS && stored > 0;
    }

    @Override
    public String toString() {
        return kind + "/" + color.getSerializedName() + "/" + Integer.toHexString(mask);
    }
}
