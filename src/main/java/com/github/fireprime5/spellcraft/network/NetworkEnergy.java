package com.github.fireprime5.spellcraft.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Перенос энергии по линиям блока сети.
 *
 * <p>Главное отличие от прежней версии: линий в блоке до трёх, и у соседа нужно
 * спрашивать <b>все</b> линии того же вида и цвета. Раньше в блоке жил ровно один
 * провод, поэтому спрашивался один; с несколькими линиями вторая и третья были бы
 * не видны переносу, и сеть «теряла» бы цвет на стыке.
 *
 * <p>Правило обмена прежнее и симметричное: с линией энергия идёт только от более
 * заряженной к менее заряженной. Равные не обмениваются ничем, и линия замирает
 * устойчиво вместо того, чтобы дрожать.
 */
public final class NetworkEnergy {

    /** Запас линии: хватает на несколько переносов подряд, но не на батарею. */
    public static final int CAPACITY = 1000;

    /** Сколько энергии линия берёт либо отдаёт за один тик с одного соседа. */
    public static final int TRANSFER = 50;

    /** Как часто линия вправе тянуть сквозь сеть. */
    public static final int PULL_INTERVAL = 10;

    /** Предохранитель обхода: длиннее сеть всё равно не имеет смысла. */
    public static final int MAX_SCAN = 1024;

    private NetworkEnergy() {
    }

    /**
     * Тик энергетической линии: собрать у соседей, раздать соседям.
     *
     * @param уровень мир
     * @param позиция где стоит блок
     * @param линия сама линия: её запас пополняется и тратится
     */
    public static void transferEnergy(Level уровень, BlockPos позиция, NetworkLine линия) {
        int buffer = Math.clamp(линия.getEnergyStored(), 0, CAPACITY);

        // 1. набор: сперва у таких же линий того же цвета, потом у чужих хранилищ
        for (Direction d : линия.sides()) {
            int room = CAPACITY - buffer;
            if (room <= 0) {
                break;
            }
            NetworkLine рядом = neighborLine(уровень, позиция, линия, d);
            if (рядом != null) {
                // от соседней линии берём, только если она богаче: иначе две линии
                // одного цвета обмениваются зарядами впустую
                if (рядом.getEnergyStored() <= buffer) {
                    continue;
                }
                buffer += рядом.extractEnergy(Math.min(TRANSFER, room), false);
                continue;
            }
            IEnergyStorage чужой = capability(уровень, позиция.relative(d), d);
            if (чужой == null || !чужой.canExtract()) {
                continue;
            }
            buffer += Math.max(0, чужой.extractEnergy(Math.min(TRANSFER, room), false));
        }
        линия.stored(buffer);

        // 2. раздача: сначала чужим хранилищам, потом менее заряженным линиям
        for (Direction d : линия.sides()) {
            if (линия.getEnergyStored() <= 0) {
                break;
            }
            NetworkLine рядом = neighborLine(уровень, позиция, линия, d);
            if (рядом != null) {
                if (рядом.getEnergyStored() >= линия.getEnergyStored()) {
                    continue;
                }
                int room = рядом.getMaxEnergyStored() - рядом.getEnergyStored();
                int принято = Math.max(0, рядом.receiveEnergy(
                        Math.min(TRANSFER, Math.min(room, линия.getEnergyStored())), false));
                линия.stored(Math.max(0, линия.getEnergyStored() - принято));
                continue;
            }
            IEnergyStorage чужой = capability(уровень, позиция.relative(d), d);
            if (чужой == null || !чужой.canReceive()) {
                continue;
            }
            int принято = чужой.receiveEnergy(
                    Math.min(TRANSFER, линия.getEnergyStored()), false);
            линия.stored(Math.max(0, линия.getEnergyStored() - Math.max(0, принято)));
        }
    }

    /**
     * Забрать из сети, если рядом ничего нет.
     *
     * <p>Потребитель тянет сквозь всю сразу: на hundred-метровой трассе энергия
     * шла бы двести тиков, а с блоками посередине вязла совсем. Обход не каждый
     * тик — сеть из тысячи линий обходится целиком, и делать это двадцать раз в
     * секунду дорого.
     */
    public static void pullThrough(Level уровень, BlockPos начало, NetworkLine линия) {
        if (линия.getEnergyStored() >= CAPACITY) {
            return;
        }
        for (IEnergyStorage источник : linesThrough(уровень, начало, линия)) {
            int room = CAPACITY - линия.getEnergyStored();
            if (room <= 0) {
                return;
            }
            int взято = Math.max(0, источник.extractEnergy(room, false));
            if (взято > 0) {
                линия.stored(линия.getEnergyStored() + взято);
                return;
            }
        }
    }

    /**
     * Источники, видимые сквозь цепочку таких же линий.
     *
     * <p>Обход в ширину по связанным линиям того же вида и цвета. Сами линии
     * источниками не считаются: иначе цепочка питала бы саму себя.
     */
    public static List<IEnergyStorage> linesThrough(Level уровень, BlockPos начало,
                                                    NetworkLine линия) {
        List<IEnergyStorage> источники = new ArrayList<>();
        Set<BlockPos> виденные = new HashSet<>();
        Deque<BlockPos> очередь = new ArrayDeque<>();
        очередь.add(начало.immutable());
        виденные.add(начало.immutable());
        int посещено = 0;
        while (!очередь.isEmpty() && посещено < MAX_SCAN) {
            BlockPos pos = очередь.poll();
            посещено++;
            for (Direction d : линия.sides()) {
                BlockPos сосед = pos.relative(d);
                if (!виденные.add(сосед.immutable())) {
                    continue;
                }
                // незагруженный соседний чанк не грузим ради сети: обход не
                // должен превращаться в принудительную загрузку мира
                if (!уровень.hasChunkAt(сосед)) {
                    continue;
                }
                NetworkLine рядом = lineAt(уровень, сосед, d, линия);
                if (рядом == null) {
                    IEnergyStorage чужой = capability(уровень, сосед, d);
                    if (чужой != null && чужой.canExtract() && чужой.getEnergyStored() > 0) {
                        источники.add(new ForeignLine(чужой));
                    }
                    continue;
                }
                источники.add(рядом);
                очередь.add(сосед.immutable());
            }
        }
        return источники;
    }

    /** Линия того же вида и цвета в этом блоке, смотрящая в нашу сторону. */
    private static NetworkLine neighborLine(Level уровень, BlockPos позиция,
                                            NetworkLine линия, Direction d) {
        return lineAt(уровень, позиция.relative(d), d, линия);
    }

    private static NetworkLine lineAt(Level уровень, BlockPos позиция, Direction d,
                                      NetworkLine линия) {
        if (уровень.getBlockEntity(позиция) instanceof NetworkBlockEntity сосед) {
            NetworkLine найденная = сосед.line(линия.kind(), линия.color());
            return найденная != null && найденная.connected(d.getOpposite()) ? найденная : null;
        }
        return null;
    }

    /** Порт бездны в соседе: линия из блока сети или порт машины. */


    /**
     * Энергия чужого блока.
     *
     * <p>Сначала спрашивается без стороны: моды по-разному понимают этот аргумент,
     * и из-за одного блока с чужим модом линия вела бы себя случайно.
     */
    private static IEnergyStorage capability(Level уровень, BlockPos позиция,
                                             Direction d) {
        if (уровень.getBlockState(позиция).isAir()) {
            return null;
        }
        IEnergyStorage any = уровень.getCapability(Capabilities.EnergyStorage.BLOCK,
                позиция, null);
        if (any != null) {
            return any;
        }
        return уровень.getCapability(Capabilities.EnergyStorage.BLOCK, позиция,
                d.getOpposite());
    }

    /** Чужое хранилище, обёрнутое линией: чтобы список источников был одного типа. */
    private record ForeignLine(IEnergyStorage storage) implements IEnergyStorage {

        @Override
        public int getEnergyStored() {
            return storage.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            return storage.getMaxEnergyStored();
        }

        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return storage.extractEnergy(toExtract, simulate);
        }

        @Override
        public boolean canExtract() {
            return storage.canExtract();
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    }
}
