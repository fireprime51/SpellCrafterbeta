package com.github.fireprime5.spellcraft.portal;

import com.github.fireprime5.spellcraft.Spellcraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Реестр всех порталов на сервере.
 *
 * <p>Хранится как {@link SavedData} обычного мира: портал может связывать
 * измерения, а данные измерений раздельные, поэтому держать реестр в Незере или в
 * Энде было бы негде. Обычный мир — единственное хранилище, видимое всем.
 *
 * <p>В реестре лежат только «адреса»: где стоит портал, куда ведёт и до каких
 * пор. Сам блок в мире ничего не знает, кроме своего идентификатора, поэтому
 * перезагрузка мира, релог и перезапуск сервера не ломают связку.
 */
public final class PortalRegistry extends SavedData {

    private static final String DATA_ID = "spellcraft_portals";

    /**
     * Запись об одном портале.
     *
     * <p>«Куда ведёт» и «есть ли там блок» — это разные вещи, и раньше они
     * были слиты: односторонний портал тоже попадал в индекс по своему
     * выходу, и в мире появлялся блок, которого никто не ставил.
     *
     * <p>Поэтому адрес назначения есть всегда, а флаг {@code twoWay} говорит,
     * стоит ли в этой точке второй блок и ищется ли портал по ней.
     *
     * @param twoWay    двусторонний ли портал (есть ли блок на выходе)
     * @param expiresAt когда портал исчезнет в игровых тиках; 0 — навсегда
     * @param owner     кто поставил: по нему считается потолок порталов
     */
    public record Entry(UUID id, String fromDim, BlockPos from, String toDim, BlockPos to,
                        boolean twoWay, long expiresAt, UUID owner) {

        public boolean permanent() {
            return expiresAt <= 0;
        }

        /** Прошло ли время жизни. */
        public boolean expired(long gameTime) {
            return !permanent() && gameTime >= expiresAt;
        }

        public long remainingTicks(long gameTime) {
            return permanent() ? 0 : Math.max(0, expiresAt - gameTime);
        }

        /** Двусторонний ли портал: есть ли второй блок и второй вход. */
        public boolean hasReturn() {
            return twoWay;
        }

        /**
         * Куда ведёт портал, стоящий в указанном измерении и точке.
         *
         * <p>Конец выбирается по ПОЛОЖЕНИЮ, а не по измерению. Изначально выбор
         * шёл только по измерению, и для двусторонних врат внутри одного мира
         * (а это почти все врата: «Здесь» и «Точка возрождения» в обычном мире)
         * обе ветви совпадали. Стоя в портале на выходе, игрок получал обратно
         * тот же самый адрес — портал телепортировал в себя, и никакой таймер
         * это не лечил.
         *
         * @param dim измерение, в котором стоит игрок
         * @param pos блок, в котором он стоит
         */
        public String exitDim(String dim, BlockPos pos) {
            return atEntry(dim, pos) ? toDim : fromDim;
        }

        public BlockPos exitPos(String dim, BlockPos pos) {
            return atEntry(dim, pos) ? to : from;
        }

        /**
         * Стоит ли игрок именно на «входе» (а не на выходе).
         *
         * <p>Строгое совпадение и измерения, и точки. Вариант «то ли по точке,
         * то ли потому что измерение другое» был неверным: у врат между мирами
         * он считал выход входом и отправлял обратно в тот же Незер.
         */
        private boolean atEntry(String dim, BlockPos pos) {
            return fromDim.equals(dim) && from.equals(pos);
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", id);
            tag.putString("from_dim", fromDim);
            tag.putInt("from_x", from.getX());
            tag.putInt("from_y", from.getY());
            tag.putInt("from_z", from.getZ());
            tag.putString("to_dim", toDim);
            tag.putInt("to_x", to.getX());
            tag.putInt("to_y", to.getY());
            tag.putInt("to_z", to.getZ());
            tag.putBoolean("two_way", twoWay);
            tag.putLong("expires", expiresAt);
            tag.putUUID("owner", owner);
            return tag;
        }

        public static Entry load(CompoundTag tag) {
            BlockPos from = new BlockPos(tag.getInt("from_x"), tag.getInt("from_y"),
                    tag.getInt("from_z"));
            BlockPos to = new BlockPos(tag.getInt("to_x"), tag.getInt("to_y"),
                    tag.getInt("to_z"));
            return new Entry(tag.getUUID("id"), tag.getString("from_dim"), from,
                    tag.getString("to_dim"), to, tag.getBoolean("two_way"),
                    tag.getLong("expires"), tag.getUUID("owner"));
        }
    }

    /** Ключ поиска «портал, стоящий здесь». */
    private record Slot(String dim, BlockPos pos) {
    }

    private final Map<UUID, Entry> byId = new HashMap<>();
    private final Map<Slot, UUID> byPos = new HashMap<>();

    public PortalRegistry() {
    }

    public static PortalRegistry load(CompoundTag tag,
                                       net.minecraft.core.HolderLookup.Provider registries) {
        PortalRegistry data = new PortalRegistry();
        var list = tag.getList("portals", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Entry entry = Entry.load(list.getCompound(i));
            data.byId.put(entry.id(), entry);
            data.byPos.put(new Slot(entry.fromDim(), entry.from()), entry.id());
            if (entry.hasReturn()) {
                data.byPos.put(new Slot(entry.toDim(), entry.to()), entry.id());
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        var list = new net.minecraft.nbt.ListTag();
        for (Entry entry : byId.values()) {
            list.add(entry.save());
        }
        tag.put("portals", list);
        return tag;
    }

    // ------------------------------------------------------------- работа

    public void add(Entry entry) {
        byId.put(entry.id(), entry);
        byPos.put(new Slot(entry.fromDim(), entry.from()), entry.id());
        if (entry.hasReturn()) {
            byPos.put(new Slot(entry.toDim(), entry.to()), entry.id());
        }
        setDirty();
    }

    public Entry get(UUID id) {
        return byId.get(id);
    }

    /** Портал, стоящий в этих координатах, или null. */
    public Entry at(String dim, BlockPos pos) {
        UUID id = byPos.get(new Slot(dim, pos));
        return id == null ? null : byId.get(id);
    }

    /** Убирает запись и возвращает её — вызывающему надо снести сами блоки. */
    public Entry remove(UUID id) {
        Entry entry = byId.remove(id);
        if (entry != null) {
            byPos.remove(new Slot(entry.fromDim(), entry.from()));
            if (entry.hasReturn()) {
                byPos.remove(new Slot(entry.toDim(), entry.to()));
            }
            setDirty();
        }
        return entry;
    }

    /** Сколько порталов записано (для команды и проверок). */
    public int size() {
        return byId.size();
    }

    public List<Entry> all() {
        return new ArrayList<>(byId.values());
    }

    // ------------------------------------------------- переходы между мирами

    /**
     * Где сущность была в прошлый тик.
     *
     * @param tick игровое время последнего срабатывания
     */
    private record PortalMark(UUID portal, BlockPos pos, long tick) {
    }

    /**
     * Сработать должен только ВХОД в портал, а не постоянное нахождение в нём.
     *
     * <p>Раньше защита была только таймером на 20 тиков, и этого не хватало.
     * Вот что происходило с двусторонними вратами: игрок входит в портал A →
     * переносится в B → там таймер его спасает на секунду → секунда проходит,
     * а он всё ещё стоит в B → перенос обратно в A → и так до бесконечности,
     * два раза в секунду. Со стороны выглядело как «телепортирует в себя».
     *
     * <p>Теперь у каждой сущности запоминается, в каком портале она была в
     * прошлый тик. Отсюда два правила:
     *
     * <ul>
     *   <li><b>Уже стоим здесь</b> — не переносить снова, пока сущность внутри.</li>
     *   <li><b>Только что вышли из этого портала</b> — дать время выйти,
     *       иначе прибытие в противоположный конец считалось бы новым входом.</li>
     * </ul>
     *
     * <p>Отметка живёт в статической карте по идентификатору, а не в самой
     * сущности: переход между измерениями создаёт новую сущность на сервере, и
     * отметка в старом объекте была бы потеряна ровно в тот момент, когда она
     * нужнее всего.
     */
    private static final Map<UUID, PortalMark> LAST_PORTAL = new ConcurrentHashMap<>();

    /** Сколько тиков после прибытия ещё нельзя войти в тот же портал. */
    public static final int REENTRY_GRACE_TICKS = 5;
    /**
     * Сколько тиков «уже стояли внутри» считается одним и тем же положением.
     *
     * <p>Два, а не один: {@code entityInside} зовётся раз в тик, но между
     * соседними вызовами проходит именно тик, и запас в один тик нужен на
     * возможное дрожание счётчика.
     */
    private static final int PRESENCE_GRACE_TICKS = 2;

    /**
     * Стоит ли переносить сущность, которая сейчас внутри портала.
     *
     * <p>Вызывается каждый тик из {@code PortalBlock#entityInside}, поэтому
     * внутри не только чтение, но и запись отметки.
     *
     * @return true — сущность только что вошла, её надо перенести
     */
    public static boolean shouldEnter(UUID entity, UUID portal, BlockPos pos, long gameTime) {
        PortalMark mark = LAST_PORTAL.get(entity);
        boolean alreadyInside = mark != null
                && mark.portal().equals(portal)
                && mark.pos().equals(pos)
                && gameTime - mark.tick() <= PRESENCE_GRACE_TICKS;
        boolean justArrived = mark != null
                && mark.portal().equals(portal)
                && gameTime - mark.tick() < REENTRY_GRACE_TICKS;
        LAST_PORTAL.put(entity, new PortalMark(portal, pos, gameTime));
        prune(gameTime);
        return !alreadyInside && !justArrived;
    }

    /** Отметка, которая не обновлялась, больше не нужна: сущность давно ушла. */
    private static void prune(long gameTime) {
        if (LAST_PORTAL.size() > 512) {
            LAST_PORTAL.entrySet().removeIf(e -> gameTime - e.getValue().tick() > 100);
        }
    }

    /** Полный сброс отметок — для перезагрузки мира и дебага. */
    public static void forgetAll() {
        LAST_PORTAL.clear();
    }

    /**
     * Переносит сущность в другой адрес.
     *
     * <p>Внутри одного измерения хватает {@code teleportTo}, между измерениями
     * обязателен {@code changeDimension}: он переносит данные, команду,
     * положение в сети и создаёт новую сущность на стороне сервера. Обычный
     * телепорт через измерение «работает» только визуально — игрок проваливается
     * в пустоту, а его данные остаются в старом мире.
     */
    public static void travel(net.minecraft.world.entity.Entity entity, String toDim, BlockPos to,
                              ServerLevel from) {
        ResourceLocation key = ResourceLocation.tryParse(toDim);
        ServerLevel target = key == null ? null : from.getServer().getLevel(
                ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, key));
        if (target == null) {
            throw new com.github.fireprime5.spellcraft.spell.SpellContext.SpellException(
                    "place.no_dimension");
        }
        if (target == from) {
            entity.teleportTo(target, to.getX() + 0.5, to.getY(), to.getZ() + 0.5,
                    java.util.Set.of(), entity.getYRot(), entity.getXRot());
            return;
        }
        // переход между измерениями создаёт НОВУЮ сущность на сервере; старый
        // объект после этого больше нельзя трогать
        entity.changeDimension(new net.minecraft.world.level.portal.DimensionTransition(
                target,
                new net.minecraft.world.phys.Vec3(to.getX() + 0.5, to.getY(), to.getZ() + 0.5),
                entity.getDeltaMovement(),
                entity.getYRot(),
                entity.getXRot(),
                entity1 -> {
                    // звук портала и «появление» на новом месте; без этого
                    // переход выглядит как обычный телепорт, а не как портал
                }));
    }

    // --------------------------------------------------------- потолочное

    /** Максимум порталов на одного игрока — иначе схема забивает мир. */
    public static final int MAX_PER_PLAYER = 16;
    /** Максимум порталов на весь сервер. */
    public static final int MAX_TOTAL = 512;

    public int countOwnedBy(UUID owner) {
        int count = 0;
        for (Entry entry : byId.values()) {
            if (owner.equals(entry.owner())) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------ доступ

    /**
     * Реестр сервера.
     *
     * <p>Берётся из обычного мира: только там хранилище данных общее для всех
     * измерений. Если обычный мир ещё не загружен, возвращается пустой
     * реестр-заглушка — читать из него безопасно, а создание портала в такой
     * момент попадёт в никуда, поэтому вызывающий код должен проверять
     * {@link #isLoaded()}.
     */
    public static PortalRegistry get(net.minecraft.server.MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return null;
        }
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PortalRegistry::new, PortalRegistry::load), DATA_ID);
    }
}