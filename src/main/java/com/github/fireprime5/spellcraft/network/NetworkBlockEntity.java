package com.github.fireprime5.spellcraft.network;

import com.github.fireprime5.spellcraft.fluid.AbyssPort;
import com.github.fireprime5.spellcraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.energy.IEnergyStorage;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Блок-сущность сети: до трёх линий, у каждой свой слот и свои подключения.
 *
 * <p>Раньше провод был сам себе и блоком, и хранилищем: подключения лежали в
 * свойствах блока, а энергия — в блок-сущности. С несколькими линиями так не
 * выйдет (свойств потребовалось бы восемнадцать плюс цвета, и blockstate такой
 * набор не переживает), поэтому подключения уехали в NBT, как в Modern
 * Industrialization, а блок-сущность стала хранилищем всех линий сразу.
 *
 * <p>Клиенту изменения уходят обычным {@code getUpdateTag}: при пересчёте
 * подключений блок дёргает {@code sendBlockUpdated}, и рендерер видит те же
 * линии, что и сервер.
 */
public class NetworkBlockEntity extends BlockEntity {

    /** Линии блока: правило «сколько их может быть» живёт в {@link NetworkLines}. */
    private final NetworkLines lines = new NetworkLines();

    /** Тик, до которого тянуть сквозь сеть рано. */
    private long nextPullTick = Long.MIN_VALUE;

    public NetworkBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.WIRE_ENTITY.get(), pos, state);
    }

    // --- линии ------------------------------------------------------------

    /** Линии блока. Индекс в списке — номер слота. */
    public List<NetworkLine> lines() {
        return lines.all();
    }

    /** Правило слотов: сколько линий помещается и что уже лежит. */
    public NetworkLines container() {
        return lines;
    }

    /** Линия в слоте или {@code null}, если слот пуст. */
    public NetworkLine line(int slot) {
        return lines.line(slot);
    }

    /** Линия такого вида и цвета в этом блоке или {@code null}. */
    public NetworkLine line(LineKind kind, DyeColor color) {
        return lines.line(kind, color);
    }

    /** Есть ли в блоке свободный слот. */
    public boolean hasFreeSlot() {
        return lines.hasFreeSlot();
    }

    /** Кладёт линию в свободный слот. */
    public boolean add(LineKind kind, DyeColor color) {
        return lines.add(kind, color);
    }

    /** Убирает линию из слота. */
    public NetworkLine remove(int slot) {
        return lines.remove(slot);
    }

    /** Пуст ли блок: пустой блок сети снимается целиком. */
    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /**
     * Энергия наружу для труб других модов.
     *
     * <p>Отдаётся первая энергетическая линия: блок сети для чужого мода — это
     * одно хранилище, а не три, иначе каждый сосед тянул бы из всех подряд.
     */
    public IEnergyStorage energyStorage() {
        for (NetworkLine линия : lines.all()) {
            if (линия.kind() == LineKind.ENERGY) {
                return линия;
            }
        }
        return null;
    }

    // --- соединения -------------------------------------------------------

    /**
     * Пересчитать блок и все блоки сети вокруг него.
     *
     * <p>Соседи пересчитываются отдельно от постановки: сначала блок встаёт со
     * своими линиями, потом обновляются провода вокруг. Иначе новый провод
     * посмотрел бы на старые подключения соседей и не показал бы к ним лапу —
     * сеть выглядела бы разорванной на один блок.
     */
    public static void refresh(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof NetworkBlockEntity be)) {
            return;
        }
        be.reconnect(level, pos);
        for (Direction d : Direction.values()) {
            BlockPos сосед = pos.relative(d);
            if (level.getBlockEntity(сосед) instanceof NetworkBlockEntity рядом) {
                рядом.reconnect(level, сосед);
            }
        }
    }

    /**
     * Отправить линии клиенту.
     *
     * <p>Отдельно от пересчёта подключений: добавленная или снятая линия сама по
     * себе подключений не меняет, а клиент без этого продолжил бы рисовать старый
     * блок — или пустой.
     */
    public void sync(Level level, BlockPos pos) {
        setChanged();
        level.sendBlockUpdated(pos, getBlockState(), getBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        // форма блока — это линии, а Minecraft кэширует её и раздаёт соседям
        // только по событиям блока. Без этого пересчёта клик по отросшему концу
        // линии не попадал бы в блок, и hitSlot отдавал бы -1
        getBlockState().updateNeighbourShapes(level, pos,
                net.minecraft.world.level.block.Block.UPDATE_ALL);
    }

    /** Пересчитать подключения этого блока по соседям. */
    public void reconnect(Level level, BlockPos pos) {
        boolean изменилось = выровнятьСлоты(level, pos);
        for (NetworkLine линия : lines.all()) {
            if (линия == null) {
                continue;
            }
            int было = линия.mask();
            for (Direction d : Direction.values()) {
                линия.connect(d, connects(level, pos, d, линия.kind(), линия.color())
                        && слотыСовпали(level, pos, d, линия));
            }
            изменилось |= было != линия.mask();
        }
        if (изменилось) {
            sync(level, pos);
        }
    }

    /**
     * Свести слоты линий с соседями.
     *
     * <p>Слот — это номер геометрии, то есть точка входа в грань: 5/16, 8/16 или
     * 11/16. Сосед узнаёт линию по виду и цвету, а стоит она в своём слоте. Если
     * слоты разошлись — в одном блоке красная первая, в соседнем второй, — оба
     * тянутся навстречу друг другу из разных точек: сеть формально соединена, а
     * на стыке ступенька в три пикселя, и линии разных цветов выглядят
     * перепутанными.
     *
     * <p>Сосед важнее: его слот блок принимает и подстраивается под него, если
     * слот свободен. Занятый слот — обмен местами, когда сосед держит на нём как
     * раз ту линию, которая у нас стоит в её слоте. Не вышло — блок просто не
     * соединится с этим соседом: разрыв лучше ступеньки.
     *
     * @return сдвинулась ли хоть одна линия
     */
    private boolean выровнятьСлоты(Level level, BlockPos pos) {
        boolean изменилось = false;
        for (int проход = 0; проход < NetworkGeometry.SLOTS; проход++) {
            boolean сдвинули = false;
            for (Direction d : Direction.values()) {
                BlockPos сосед = pos.relative(d);
                if (!(level.getBlockEntity(сосед) instanceof NetworkBlockEntity be)) {
                    continue;
                }
                for (NetworkLine линия : lines.all()) {
                    if (линия == null) {
                        continue;
                    }
                    NetworkLine рядом = be.line(линия.kind(), линия.color());
                    if (рядом == null) {
                        continue;
                    }
                    int мой = lines.slot(линия.kind(), линия.color());
                    int его = be.lines.slot(рядом.kind(), рядом.color());
                    if (мой < 0 || его < 0 || мой == его || be.lines.line(его) == null) {
                        continue;
                    }
                    NetworkLine занятая = lines.line(его);
                    if (занятая == null) {
                        сдвинули |= lines.move(мой, его);
                    } else if (be.lines.slot(занятая.kind(), занятая.color()) == мой) {
                        // сосед держит на нашем слоте ровно ту линию, которая
                        // сейчас стоит у нас: обе встанут на место обменом
                        сдвинули |= lines.swap(мой, его);
                    }
                    if (сдвинули) {
                        изменилось = true;
                    }
                }
            }
            if (!сдвинули) {
                break;
            }
        }
        return изменилось;
    }

    /**
     * Совпадает ли слот линии с её слотом у соседа.
     *
     * <p>Только для блоков сети: у машины своего слота нет, и линия входит в её
     * грань откуда угодно.
     */
    private boolean слотыСовпали(Level level, BlockPos pos, Direction d, NetworkLine линия) {
        BlockPos сосед = pos.relative(d);
        if (!(level.getBlockState(сосед).getBlock() instanceof WireBlock)) {
            return true;
        }
        if (!(level.getBlockEntity(сосед) instanceof NetworkBlockEntity be)) {
            return false;
        }
        NetworkLine рядом = be.line(линия.kind(), линия.color());
        return рядом != null
                && be.lines.slot(рядом.kind(), рядом.color())
                == lines.slot(линия.kind(), линия.color());
    }

    /**
     * Соединяется ли линия с блоком в этом направлении.
     *
     * <p>Три случая: такая же линия в соседнем блоке сети, машина, принимающая
     * этот вид ресурса, и — для энергии — любой узел сети: провод питает машину
     * независимо от её цвета.
     */
    public static boolean connects(LevelReader level, BlockPos pos, Direction d,
                                   LineKind kind, DyeColor color) {
        BlockPos сосед = pos.relative(d);
        var блок = level.getBlockState(сосед).getBlock();
        if (блок instanceof WireBlock) {
            // блок сети проверяется по блоку, а не по наличию блок-сущности:
            // пустой блок сети тоже помечен как узел, и линия стыковалась бы с
            // ним — сеть текла бы сквозь блоки, в которых её нет
            if (!(level.getBlockEntity(сосед) instanceof NetworkBlockEntity be)) {
                return false;
            }
            // только наличие такой же линии у соседа. Прежняя проверка «а он уже
            // смотрит сюда?» глушила сеть с обеих сторон: первый блок про второй
            // ещё не знает, второй смотрит на первого и видит у него маску 0 — и
            // никто не делает шаг первым, два соседних провода не соединяются
            // никогда. Наличие линии и задаёт тождество сети, а симметрия
            // получается сама, когда сосед пересчитается
            return be.line(kind, color) != null;
        }
        if (kind == LineKind.ENERGY) {
            return блок instanceof NetworkNode;
        }
        return level.getBlockEntity(сосед) instanceof AbyssPort;
    }

    // --- форма ------------------------------------------------------------

    /**
     * Форма всех линий блока: по ней выделяется и по ней кликают.
     *
     * <p>Пустой блок тоже получает форму — кубик сердцевины. Иначе он
     * невидим и невыбираем: клик в него не попадает, предмет не может положить
     * в него линию, и блок остаётся пустым навсегда. Пользователь видит место,
     * где ничего нет, и ничем не может это исправить.
     */
    public VoxelShape shape() {
        VoxelShape форма = NetworkGeometry.shapeAll(lines.all());
        return форма.isEmpty() ? NetworkGeometry.shape(0, java.util.Set.of()) : форма;
    }

    /**
     * Какая линия попала под курсор.
     *
     * <p>Нужна, чтобы снять или добавить именно ту линию, которую игрок видит: в
     * блоке их до трёх, и снять «не ту» — значит снять чужую сеть.
     */
    public NetworkLine hit(Vec3 точка) {
        for (int slot = 0; slot < lines.size(); slot++) {
            NetworkLine линия = lines.line(slot);
            if (линия == null) {
                continue;
            }
            if (NetworkGeometry.inside(slot, линия.sides(), точка)) {
                return линия;
            }
        }
        return null;
    }

    /** Номер слота линии под курсором или -1. */
    public int hitSlot(Vec3 точка) {
        NetworkLine попал = hit(точка);
        for (int slot = 0; slot < lines.size(); slot++) {
            if (lines.line(slot) == попал) {
                return slot;
            }
        }
        return -1;
    }

    // --- тик --------------------------------------------------------------

    public static <T extends BlockEntity> BlockEntityTicker<T> ticker() {
        return (level, pos, state, be) -> {
            if (level.isClientSide || !(be instanceof NetworkBlockEntity сеть)) {
                return;
            }
            сеть.work(level, pos);
        };
    }

    /** Перенос по всем линиям блока за один тик. */
    public void work(Level level, BlockPos pos) {
        for (NetworkLine линия : lines.all()) {
            if (линия.kind() == LineKind.ABYSS) {
                NetworkEnergy.transferAbyss(level, pos, линия);
                continue;
            }
            NetworkEnergy.transferEnergy(level, pos, линия);
            long now = level.getGameTime();
            if (now >= nextPullTick) {
                nextPullTick = now + NetworkEnergy.PULL_INTERVAL;
                NetworkEnergy.pullThrough(level, pos, линия);
            }
        }
    }

    // --- NBT --------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        lines.save(tag);
    }

    /**
     * Данные для клиента при загрузке чанка.
     *
     * <p>В ванилле умолчание возвращает пустой тег. Клиент тогда получает блок
     * вообще без линий и молча рисует пустоту, пока сервер считает, что линии
     * есть: блок виден, линии на месте, а на экране ничего.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    /**
     * Данные для клиента при точечном обновлении блока.
     *
     * <p>В ванилле умолчание возвращает {@code null}, и {@code ChunkHolder} не
     * отправляет пакет вовсе: без этого метода {@code sendBlockUpdated} доставит
     * клиенту только состояние блока, а блочные данные — никогда. Отсюда были
     * «линий 0» на клиенте при полной сети на сервере.
     */
    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        load(tag);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        load(tag);
    }

    /**
     * Читает линии из NBT.
     *
     * <p>Формат списка и раскладку по слотам держит сам контейнер: блок-сущности
     * нечего знать про то, как линия лежит в NBT.
     */
    private void load(CompoundTag tag) {
        lines.load(tag);
        setChanged();
    }

}
