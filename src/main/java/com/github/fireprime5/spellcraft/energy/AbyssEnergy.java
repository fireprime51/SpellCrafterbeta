package com.github.fireprime5.spellcraft.energy;

import net.minecraft.world.entity.player.Player;

/**
 * Кошелёк энергии бездны игрока.
 *
 * <p>Важно: энергия бездны НЕ накапливается сама — её добывают модулем «Извлечение».
 * Потолок кошелька задаёт конфиг, а сосуды рядом с игроком добавляют к нему ёмкость
 * и запас (см. {@link AbyssPool}).
 *
 * <p>Хранилище энергии бездны игрока остаётся в persistentData сущности ради совместимости
 * с уже сохранёнными мирами; потолок берётся из конфига.
 */
public final class AbyssEnergy {

    /** Потолок энергии бездны по умолчанию (используется, если конфиг ещё не загружен). */
    public static final int DEFAULT_MAX_ABYSS = 1000;

    /**
     * Энергия бездны при первом появлении в мире.
     *
     * <p>Не ноль: без единицы в руках игрок не может кастнуть вообще ничего —
     * самого дешёвого заклинания на старте может не хватить, и первые минуты
     * игры проходят в невозможности что-либо сделать. Десяти единиц хватает на
     * несколько попыток и всё ещё мало для серьёзного заклинания.
     */
    public static final int STARTING_ABYSS = 10;

    /**
     * Энергия бездны после воскрешения.
     *
     * <p>Одна единица — намеренно мало. Воскресший не начинает заново, он
     * еле выкарабкивается: это же делает нулевую энергию бездны опасной, иначе смерть
     * была бы просто способом отдохнуть с полным кошельком.
     */
    public static final int RESPAWN_ABYSS = 1;

    /** Ключ запаса в persistentData игрока. */
    /**
     * Скидка личного модуля «Глаз»: платишь десятую часть.
     *
     * <p>Действует на всё, что берётся из бездны, — касты, модификаторы, налог
     * брони. Не действует на «Вечность»: там плата идёт за перехваченный удар,
     * а не за действие, и дешёвая бессмертность попросту вышла бы из строя.
     */
    public static final double EYE_DISCOUNT = 0.1;

    private static final String KEY = "spellcraft_abyss_energy";
    /** Ключ режима бесконечной энергии. */
    private static final String INFINITE_KEY = "spellcraft_infinite_abyss_energy";

    /**
     * Старые ключи, до переименования в энергию бездны.
     *
     * <p>Значение в persistentData лежит в файле сохранения игрока, и новый ключ
     * после переименования просто не нашёлся бы: игрок пришёл бы с нулём и
     * полным ощущением, что его обокрали. Поэтому старый ключ читается и
     * переносится один раз, а не просто игнорируется.
     */
    private static final String LEGACY_KEY = "spellcraft_mana";
    private static final String LEGACY_INFINITE_KEY = "spellcraft_infinite_mana";

    /** Переносит старое сохранение на новые ключи. Вызывается при входе. */
    private static void migrate(Player player) {
        var data = player.getPersistentData();
        if (data.contains(LEGACY_KEY) && !data.contains(KEY)) {
            data.putInt(KEY, data.getInt(LEGACY_KEY));
            data.remove(LEGACY_KEY);
        }
        if (data.contains(LEGACY_INFINITE_KEY) && !data.contains(INFINITE_KEY)) {
            data.putBoolean(INFINITE_KEY, data.getBoolean(LEGACY_INFINITE_KEY));
            data.remove(LEGACY_INFINITE_KEY);
        }
    }

    private AbyssEnergy() {
    }

    /**
     * Включена ли бесконечная энергия бездны.
     *
     * <p>Отдельный флаг, а не «очень много энергии бездны»: потолок берётся из конфига и
     * мог бы оказаться меньше стоимости заклинания, и тогда подделать дешёвое
     * заклинание было бы нечем. Флаг влияет только на проверку и списание:
     * сама энергия бездны остаётся в потолке, поэтому «Извлечение» работает как обычно.
     */
    public static boolean isInfinite(Player player) {
        return player.getPersistentData().getBoolean(INFINITE_KEY);
    }

    /** Включает или выключает бесконечную энергию бездны. */
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

    /** Текущий потолок энергии бездны игрока (из конфига). */
    public static int capacity() {
        return com.github.fireprime5.spellcraft.config.SpellConfig.get().playerEnergyCapacity;
    }

    public static int get(Player player) {
        return player.getPersistentData().getInt(KEY);
    }

    public static void set(Player player, int amount) {
        player.getPersistentData().putInt(KEY, Math.clamp(amount, 0, capacity()));
    }

    /**
     * Возвращает, сколько энергии бездны реально удалось добыть.
     *
     * <p>С учётом сосудов: сначала пополняется кошелёк, излишки падают в
     * сосуды рядом. Раньше излишек просто пропадал, и извлекатель в фабрике
     * был бесполезен — копить было некуда.
     *
     * <p>Пока у игрока работает «Вечность», бездна не восстанавливается: метод
     * возвращает ноль. Проверка стоит здесь, а не в каждом источнике, потому что
     * источники добавляют энергию только через этот метод — извлекатель, сосуды и
     * модули-заклинания. Новое место, забытое про правило, не появится само.
     */
    public static int add(Player player, int amount) {
        if (com.github.fireprime5.spellcraft.armor.Eternity.isActive(player)) {
            return 0;
        }
        return AbyssPool.add(player, amount);
    }

    /**
     * Добавление без учёта сосудов: только свой кошелёк.
     *
     * <p>Метод для {@link AbyssPool} и только для него. Публичный {@link
     * #addMana} ходит в пул, а пул обязан звать этот: если пул позовёт
     * {@code addMana}, тот снова позовёт пул, и любое добавление энергии бездны уронит
     * сервер переполнением стека.
     */
    public static int addToWallet(Player player, int amount) {
        int before = get(player);
        set(player, before + amount);
        return get(player) - before;
    }

    /**
     * Тратит энергию бездны.
 *
 * <p>При включённой бесконечной энергия бездны просто не уходит: заклинание остаётся
     * бесплатным, а полоска не показывает расход — иначе игрок видел бы, как
     * каст стоит 40 энергии бездны, и удивлялся бы, что ничего не изменилось.
     */
    public static void consume(Player player, int amount) {
        consumeRaw(player, discounted(player, amount));
    }

    /**
     * Списать по полной цене, без скидки «Глаза».
     *
     * <p>Проверка и списание обязаны идти одной и той же формулой: если
     * «хватает» спрашивает со скидкой, а списывается без неё, игрок увидит
     * «не хватает бездны» при полном кошельке.
     */
    public static void consumeRaw(Player player, int amount) {
        if (isInfinite(player)) {
            return;
        }
        AbyssPool.take(player, amount);
    }

    /**
     * Сколько энергии бездны списать с игрока.
     *
     * <p>Списания возвращается в трёх местах, и они должны считаться одинаково:
     * сам каст в {@code SpellExecutor}, добор энергии бездны за блоки при мгновенной
     * добыче и покупка модификатора. Считать это в каждом месте вручную —
     * значит рано или поздно забыть одно, и заклинание станет частично
     * платным без всяких видимых причин.
     */
    public static int spentAmount(Player player, int amount) {
        // ровно столько же, сколько реально ушло: панель показывает «каст за N»,
        // и при скидке «Глаза» это должно быть четыре, а не сорок
        return isInfinite(player) ? 0 : discounted(player, amount);
    }

    /**
     * Есть ли у игрока «Глаз» — личный модуль, снижающий все расходы.
     *
     * <p>Признак один: модуль открыт. Отдельного «включения» нет и не нужно —
     * нашёл, значит работает. Иначе пришлось бы хранить второе состояние рядом с
     * «изучено», и они разошлись бы при первом же переносе мира.
     */
    public static boolean hasEye(Player player) {
        return com.github.fireprime5.spellcraft.data.PlayerKnowledge.knows(player,
                com.github.fireprime5.spellcraft.Spellcraft
                        .id(com.github.fireprime5.spellcraft.spell.UniqueModules.EYE));
    }

    /**
     * Сколько на самом деле списывается за операцию этой ценой.
     *
     * <p>Вниз, без пола в единицу: «минус 90%» — это минус 90%, и дешёвая операция
     * за 1–9 энергии при «Глазе» обходится в ноль. Пол в единицу сделал бы
     * скидку фиктивной для всего, что дешевле десятка, а именно на дешёвых
     * заклинаниях игрок и заметил бы разницу в первую очередь.
     */
    static int discounted(Player player, int amount) {
        return applyRate(amount, hasEye(player));
    }

    /**
     * Сама формула скидки — без игрока.
     *
     * <p>Отдельно от {@link #discounted}, чтобы её можно было проверить обычным
     * тестом: проверка «есть ли у игрока „Глаз“» ходит в знания, а для неё нужен
     * живой {@code Player}. Здесь чистая арифметика, и она же работает в игре.
     */
    static int applyRate(int amount, boolean глазЕсть) {
        if (amount <= 0 || !глазЕсть) {
            return amount;
        }
        return (int) Math.floor(amount * EYE_DISCOUNT);
    }

    /** Хватает ли бездны с учётом «Глаза». */
    public static boolean has(Player player, int amount) {
        return hasRaw(player, discounted(player, amount));
    }

    /**
     * Хватает ли бездны по полной цене, без скидки.
     *
     * <p>Отдельно от {@link #has} нужен «Вечности»: её плата за удар не
     * скидывается, и проверять надо ровно ту же сумму, которая списывается.
     */
    public static boolean hasRaw(Player player, int amount) {
        if (amount <= 0) {
            return true;
        }
        return isInfinite(player) || AbyssPool.has(player, amount);
    }

    /**
     * Хватает ли энергии бездны.
     *
     * <p>Бесконечная энергия бездны проходит любую проверку. Это единственное место,
     * где решается, может ли игрок кастануть, поэтому режим включается
     * именно здесь, а не подменой значения энергии бездны.
     */
    /**
     * Полон ли кошелёк вместе с сосудами.
     *
     * <p>Проверяется через пул, а не через одно число: полный кошелёк при
     * непустом сосуде — это не «полно», и извлекатель в этом случае продолжал бы
     * выдавать энергию бездны в пустоту.
     */
    public static boolean isFull(Player player) {
        return AbyssPool.isFull(player);
    }

    /** Полная ёмкость игрока: кошелёк плюс сосуды рядом. */
    public static int capacityOf(Player player) {
        return AbyssPool.capacity(player);
    }

    /**
     * Начальная энергия бездны при первом входе в мир.
     *
     * <p>Только если значения ещё нет: повторный заход на существующего игрока
     * не должен съедать накопленную энергию бездны, поэтому ключ проверяется, а не
     * значение.
     *
     * <p>Если конфиг уменьшил потолок ниже стартовой энергии бездны, значение
     * подрезается — иначе игрок получил бы энергию бездны, которую нельзя потратить.
     */
    public static void init(Player player) {
        // миграция идёт до проверки: старый мир обязан увидеть свой запас,
        // иначе игрок пришёл бы с нулём и не понял бы почему
        migrate(player);
        if (!player.getPersistentData().contains(KEY)) {
            set(player, STARTING_ABYSS);
        } else if (get(player) > capacity()) {
            set(player, capacity());
        }
    }

    /**
     * Воскрешение: энергия бездны начинается с одной единицы.
     *
     * <p>Вызывается только после смерти, а не при переходе порталом: смена
     * измерения через портал смертью не является, и сбрасывать там кошелёк
     * означало бы, что портал обнуляет энергию бездны.
     */
    public static void onRespawn(Player player) {
        set(player, RESPAWN_ABYSS);
        // таймер пустоты сбрасывается: после смерти он и так не должен
        // продолжаться, иначе новый игрок умер бы через оставшиеся секунды
        AbyssDecay.reset(player);
    }

    /**
     * Переносит энергию бездны на нового игрока при респауне.
     * Энергия бездны хранится в persistentData сущности, который при смерти не
     * переносится автоматически (в отличие от вложений с copyOnDeath),
     * поэтому без этого копирования энергия бездны обнулялась бы при смерти.
     */
    public static void copyAbyss(Player from, Player to) {
        if (from == null || to == null || from == to) {
            return;
        }
        var data = from.getPersistentData();
        if (data.contains(KEY)) {
            set(to, data.getInt(KEY));
        }
    }

    /**
 * Переносит режим бесконечной энергии бездны на нового игрока при респауне.
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

    /** Пассивного регена нет — энергия бездны идёт только из «Извлечения». */
    public static void tickRegen(Player player) {
    }
}