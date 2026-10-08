package com.github.fireprime5.spellcraft.energy;

import net.minecraft.world.entity.player.Player;

/**
 * ЗАГЛУШКА энергосистемы.
 *
 * <p>Важно: мана НЕ накапливается сама — её добывают модулем «Извлечение».
 * Потолок маны фиксированный (позже появится «сосуд», который его поднимет).
 *
 * <p>Хранилище маны игрока остаётся в persistentData сущности ради совместимости
 * с уже сохранёнными мирами; потолок берётся из конфига.
 */
public final class ManaCapability {

    /** Потолок маны по умолчанию (используется, если конфиг ещё не загружен). */
    public static final int DEFAULT_MAX_MANA = 1000;

    private static final String KEY = "spellcraft_mana";
    private static final String INFINITE_KEY = "spellcraft_infinite_mana";

    private ManaCapability() {
    }

    /**
     * Включена ли бесконечная мана.
     *
     * <p>Отдельный флаг, а не «очень много маны»: потолок берётся из конфига и
     * мог бы оказаться меньше стоимости заклинания, и тогда подделать дешёвое
     * заклинание было бы нечем. Флаг влияет только на проверку и списание:
     * сама мана остаётся в потолке, поэтому «Извлечение» работает как обычно.
     */
    public static boolean isInfinite(Player player) {
        return player.getPersistentData().getBoolean(INFINITE_KEY);
    }

    /** Включает или выключает бесконечную ману. */
    public static void setInfinite(Player player, boolean enabled) {
        if (enabled) {
            player.getPersistentData().putBoolean(INFINITE_KEY, true);
        } else {
            // ключ удаляется, а не пишется false: в persistentData
            // безвредных значений не должно копиться при каждой смене режима
            player.getPersistentData().remove(INFINITE_KEY);
        }
    }

    /** Переключает режим и возвращает новое состояние. */
    public static boolean toggleInfinite(Player player) {
        setInfinite(player, !isInfinite(player));
        return isInfinite(player);
    }

    /** Текущий потолок маны игрока (из конфига). */
    public static int capacity() {
        return com.github.fireprime5.spellcraft.config.SpellConfig.get().playerEnergyCapacity;
    }

    public static int getMana(Player player) {
        return player.getPersistentData().getInt(KEY);
    }

    public static void setMana(Player player, int amount) {
        player.getPersistentData().putInt(KEY, Math.clamp(amount, 0, capacity()));
    }

    /** Возвращает, сколько маны реально удалось добыть (с учётом потолка). */
    public static int addMana(Player player, int amount) {
        int before = getMana(player);
        setMana(player, before + amount);
        return getMana(player) - before;
    }

    /**
     * Тратит ману.
 *
 * <p>При включённой бесконечной мана просто не уходит: заклинание остаётся
     * бесплатным, а полоска не показывает расход — иначе игрок видел бы, как
     * каст стоит 40 маны, и удивлялся бы, что ничего не изменилось.
     */
    public static void consumeMana(Player player, int amount) {
        if (isInfinite(player)) {
            return;
        }
        setMana(player, getMana(player) - amount);
    }

    /**
     * Сколько маны списать с игрока.
     *
     * <p>Списания возвращается в трёх местах, и они должны считаться одинаково:
     * сам каст в {@code SpellExecutor}, добор маны за блоки при мгновенной
     * добыче и покупка модификатора. Считать это в каждом месте вручную —
     * значит рано или поздно забыть одно, и заклинание станет частично
     * платным без всяких видимых причин.
     */
    public static int spentAmount(Player player, int amount) {
        return isInfinite(player) ? 0 : amount;
    }

    /**
     * Хватает ли маны.
     *
     * <p>Бесконечная мана проходит любую проверку. Это единственное место,
     * где решается, может ли игрок кастануть, поэтому режим включается
     * именно здесь, а не подменой значения маны.
     */
    public static boolean has(Player player, int amount) {
        return isInfinite(player) || getMana(player) >= amount;
    }

    public static boolean isFull(Player player) {
        return getMana(player) >= capacity();
    }

    /**
     * При первом входе мана остаётся нулевой: добывать её нужно модулем.
     * Если конфиг уменьшил потолок, значение подрезается.
     */
    public static void init(Player player) {
        if (!player.getPersistentData().contains(KEY)) {
            setMana(player, 0);
        } else if (getMana(player) > capacity()) {
            setMana(player, capacity());
        }
    }

    /**
     * Переносит ману на нового игрока при респауне.
     * Мана хранится в persistentData сущности, который при смерти не
     * переносится автоматически (в отличие от вложений с copyOnDeath),
     * поэтому без этого копирования мана обнулялась бы при смерти.
     */
    public static void copyMana(Player from, Player to) {
        if (from == null || to == null || from == to) {
            return;
        }
        var data = from.getPersistentData();
        if (data.contains(KEY)) {
            setMana(to, data.getInt(KEY));
        }
    }

    /**
 * Переносит режим бесконечной маны на нового игрока при респауне.
 *
 * <p>Флаг лежит в persistentData сущности, который при смерти не переносится,
 * поэтому без этого смерть молча возвращала бы обычный режим.
     */
    public static void copyInfinite(Player from, Player to) {
        if (from == null || to == null || from == to) {
            return;
        }
        setInfinite(to, isInfinite(from));
    }

    /** Пассивного регена нет — мана идёт только из «Извлечения». */
    public static void tickRegen(Player player) {
    }
}