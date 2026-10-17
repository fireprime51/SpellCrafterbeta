package com.github.fireprime5.spellcraft.spell;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Подразделы модулей внутри категории.
 *
 * <p>Категория отвечает на вопрос «о чём модуль» (число, сущность, действие,
 * логика), а подраздел — «что он делает с тем, что пришло». Без деления список
 * в палитре выглядел сплошной полосой из сорока с лишним названий: чтобы найти
 * «Взять из стека», приходилось прокручивать весь «Логику».
 *
 * <p>Таблица вынесена сюда, а не в конструктор модуля, по двум причинам:
 * перечисление стоит отдельно от кода регистрации (его видно целиком и можно
 * перегруппировать, не трогая модули), и добавление подраздела не требует
 * пересборки всех регистраций модулей.
 *
 * <p>Идентификатор подраздела — ключ перевода {@code sub.spellcraft.<key>}.
 * Модуль без записи здесь попадает в {@link #OTHER}, чтобы в списке не было
 * безымянной каши из хвоста.
 */
public final class SpellSubsections {

    private SpellSubsections() {
    }

    /** Модуль без подраздела. */
    public static final String OTHER = "other";

    /** Подразделы по ключу, в том порядке, в котором они идут в палитре. */
    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    /** Подраздел каждого модуля: id модуля → ключ подраздела. */
    private static final Map<String, String> BY_MODULE = new LinkedHashMap<>();

    // ------------------------------------------------------- числа

    public static final String CONSTANTS = "constants";
    public static final String ARITHMETIC = "arithmetic";
    public static final String ROUNDING = "rounding";
    public static final String COMPARE = "compare";
    public static final String RANDOM = "random";
    public static final String VECTORS = "vectors";
    public static final String POINTS = "points";

    // --------------------------------------------------- сущности

    public static final String SELF = "self";
    public static final String TARGETING = "targeting";
    public static final String READ_ENTITY = "read_entity";
    public static final String SCAN = "scan";

    // ---------------------------------------------------- действия

    public static final String DAMAGE = "damage";
    public static final String HEALING = "healing";
    public static final String AFFECT = "affect";
    public static final String MINING = "mining";
    public static final String BUILD = "build";
    public static final String PROJECTILES = "projectiles";
    public static final String VISUALS = "visuals";

    // ------------------------------------------------------- логика

    public static final String PICK = "pick";
    public static final String BOOLEAN = "boolean";
    public static final String STACK = "stack";
    public static final String CELLS = "cells";
    public static final String FLOW = "flow";

    // ------------------------------------------------------- мир

    /** Чтение мира: свет, время, воздух. */
    public static final String WORLD_READ = "world_read";

    // ------------------------------------------------------- эффекты

    /** Улучшения: то, что игрок накладывает на себя или на союзника. */
    public static final String BUFFS = "buffs";
    /** Вредные эффекты: то, что игрок накладывает на цель. */
    public static final String HARMFUL = "harmful";
    /** Зрительные: не меняют состояние, только показывают. */
    public static final String LOOK = "look";

    /** Адреса: точка вместе с измерением. */
    public static final String ADDRESS = "address";
    /** Сами порталы. */
    public static final String PORTALS = "portals";

    /** То, что лежит в руках. */
    public static final String HAND = "hand";
    /** То, что лежит в инвентаре. */
    public static final String INVENTORY = "inventory";
    public static final String ARMOR = "armor";

    static {
        label(CONSTANTS, "Константы", "Constants");
        label(ARITHMETIC, "Арифметика", "Arithmetic");
        label(ROUNDING, "Округление и знаки", "Rounding and signs");
        label(COMPARE, "Сравнения", "Comparisons");
        label(RANDOM, "Случайность", "Randomness");
        label(VECTORS, "Векторы", "Vectors");
        label(POINTS, "Точки", "Points");
        label(SELF, "Кастер", "Caster");
        label(TARGETING, "Поиск цели", "Targeting");
        label(READ_ENTITY, "Чтение сущности", "Reading entities");
        label(SCAN, "Анализ и извлечение", "Scan and extract");
        label(DAMAGE, "Урон", "Damage");
        label(HEALING, "Лечение", "Healing");
        label(AFFECT, "Воздействие", "Affecting");
        label(MINING, "Добыча", "Mining");
        label(BUILD, "Постройка и блоки", "Building and blocks");
        label(PROJECTILES, "Снаряды и взрывы", "Projectiles and blasts");
        label(VISUALS, "Эффекты и звук", "Visuals and sound");
        label(PICK, "Выбор значения", "Picking a value");
        label(BOOLEAN, "Условия", "Conditions");
        label(STACK, "Стек", "Stack");
        label(CELLS, "Ячейки", "Cells");
        label(FLOW, "Ход каста", "Cast flow");
        label(WORLD_READ, "Чтение мира", "Reading the world");
        label(BUFFS, "Улучшения", "Buffs");
        label(HARMFUL, "Вредные", "Harmful");
        label(LOOK, "Зрительные", "Visual");
        label(ADDRESS, "Адреса", "Addresses");
        label(PORTALS, "Порталы", "Portals");
        label(HAND, "В руках", "In hand");
        label(ARMOR, "Броня", "Armor");
        label(INVENTORY, "В инвентаре", "In inventory");
        label(OTHER, "Прочее", "Other");

        // --- числа ---
        of(CONSTANTS, "const_zero", "const_one", "const_five", "const_ten", "const_neg_one");
        of(ARITHMETIC, "add", "subtract", "multiply", "divide", "power", "negate",
                "percent_of", "modulo");
        of(ROUNDING, "abs", "floor", "ceil", "round", "sqrt", "sign", "min", "max", "clamp");
        of(RANDOM, "random", "random_range", "lerp");
        of(VECTORS, "vector_distance", "vector_lerp", "vector_from_coords", "vector_length", "vector_normalize", "vector_flat",
                "vector_scale", "vector_add", "vector_subtract", "vector_dot", "vector_cross",
                "vector_angle", "vector_rotate_yaw", "vector_rotate_pitch", "vector_cone",
                "wrap_angle", "sin", "cos");
        of(POINTS, "point_from_coords", "point_offset", "point_distance", "point_direction",
                "point_midpoint", "line_blocks", "coord_x", "coord_y", "coord_z");

        // --- сущности ---
        of(SELF, "caster_entity", "entity_look", "entity_position", "self_position");
        of(TARGETING, "raycast_entity", "raycast_block", "entity_under_crosshair",
                "block_under_crosshair", "nearest_entity", "entities_nearby",
                "entities_in_radius", "entities_count_in_radius");
        of(READ_ENTITY, "entity_health", "entity_max_health", "entity_distance",
                "entity_velocity", "is_player", "is_mob", "is_item", "is_projectile",
                "is_vehicle", "is_alive");
        of(SCAN, "analysis_block", "analysis_mob", "analysis_self",
                "extract_block", "extract_mob");

        // --- порталы ---
        of(ADDRESS, "location_here", "location_overworld", "location_nether", "location_end");
        of(PORTALS, "portal_gate", "portal_passage");

        // --- предметы: только чтение, и живут в своих подразделах ---
        of(HAND, "held_item", "held_slot");
        of(INVENTORY, "item_count");

        // --- мир ---
        of(WORLD_READ, "is_air", "block_light", "time_of_day", "world_spawn");

        // --- действия ---
        of(DAMAGE, "damage");
        of(HEALING, "heal", "heal_percent");
        of(AFFECT, "ignite", "impulse", "teleport", "shield", "barrier",
                "freeze", "unfreeze");
        of(MINING, "break_block", "drain_liquids");
        of(BUILD, "place_held_block", "place_block", "fill_region", "create_water",
                "create_lava", "create_fire");
        of(MINING, "fission");
        of(PROJECTILES, "arrow", "fireball", "explode", "lightning");
        of(VISUALS, "sparkle", "sound", "sound_at");

        // --- логика ---
        of(PICK, "pick_first", "pick_second", "select", "select_random", "swap", "duplicate");
        of(BOOLEAN, "compare", "and", "or", "not", "equals", "not_equals",
                "greater", "less", "greater_or_equal", "less_or_equal");
        of(STACK, "stack_push", "delay_push", "delay_pop", "stack_pop", "stack_dup", "stack_drop", "stack_drop_n",
                "stack_clear", "stack_size", "stack_repeat", "stack_swap", "stack_pick");
        of(CELLS, "stack_store", "stack_load", "stack_slots_used", "stack_clear_slots");
        of(FLOW, "stack_exit", "stack_skip");

        // --- эффекты: три группы, иначе тридцать семь названий подряд ---
        of(BUFFS, "effect_absorption", "effect_fire_resistance", "effect_water_breathing",
                "effect_speed", "effect_slowness", "effect_jump_boost", "effect_strength",
                "effect_health_boost", "effect_regeneration", "effect_resistance",
                "effect_invisibility", "effect_night_vision", "effect_haste",
                "effect_mining_fatigue", "effect_luck", "effect_unluck",
                "effect_dolphins_grace", "effect_conduit_power", "effect_hero_of_the_village");
        of(HARMFUL, "effect_instant_damage", "effect_instant_health", "effect_poison",
                "effect_wither", "effect_hunger", "effect_weakness", "effect_levitation",
                "effect_nausea", "effect_blindness", "effect_darkness", "effect_slow_falling",
                "effect_oozing", "effect_wind_charged", "effect_infested",
                "effect_weaving", "effect_trial_omen", "effect_raid_omen", "effect_bad_omen");
        of(LOOK, "effect_glowing", "effect_saturation");

        // ------------------------------------------------------- броня
        // Отдельный подраздел, а не «эффекты»: модули брони не кастуются, а
        // висят на нагруднике. В общей полосе они выглядели бы обычными
        // заклинаниями, и игрок искал бы их там, где их нет.
        of(ARMOR, "trigger_on_hit", "trigger_on_hurt", "creative_flight",
                "modifier_speed", "modifier_damage", "modifier_protection");

        // ------------------------------------------------------- личные
        // Таблица пополняется самим UniqueModules: набор уникальных модулей
        // задаётся там же, где они регистрируются, иначе список и реестр
        // разошлись бы при первом же добавлении модуля.
    }

    /** Все идентификаторы личных модулей — их набор задаёт {@link UniqueModules}. */
    private static final java.util.Set<String> UNIQUE_IDS = new java.util.LinkedHashSet<>();

    static void registerUnique(String... modulePaths) {
        for (String path : modulePaths) {
            UNIQUE_IDS.add(path);
        }
    }

    /**
     * Личный модуль, который живёт в броне.
     *
     * <p>«Вечность» — одновременно личный модуль (выпадает из «Анализа себя») и
     * модуль брони (висит на нагруднике). Раздел «броня» нужен ей, чтобы её не
     *льзя было положить в обычное заклинание; личность от раздела не зависит и
     * хранится в {@link #UNIQUE_IDS}.
     */
    static void registerUniqueArmor(String modulePath) {
        UNIQUE_IDS.add(modulePath);
        of(ARMOR, modulePath);
    }

    /** Модуль брони: в обычном заклинании его поставить нельзя. */
    public static boolean isArmor(SpellModuleType type) {
        return ARMOR.equals(of(type));
    }

    /**
     * Личный модуль игрока, выдаваемый «Анализом себя».
     *
     * <p>Смотрим в {@link #UNIQUE_IDS}, а не в подраздел: у «Вечности» подраздел
     * «броня», но она остаётся личным модулем и не выдаётся ни сканированием, ни
     * командой «выучить всё».
     */
    public static boolean isUnique(SpellModuleType type) {
        return UNIQUE_IDS.contains(type.id().getPath());
    }

    /** Все идентификаторы личных модулей — их набор задаёт {@link UniqueModules}. */
    public static java.util.List<String> uniqueModules() {
        return java.util.List.copyOf(UNIQUE_IDS);
    }

    private static void label(String key, String ru, String en) {
        LABELS.put(key, ru + "\u0000" + en);
    }

    private static void of(String sub, String... modules) {
        for (String module : modules) {
            BY_MODULE.put(module, sub);
        }
    }

    /** Подраздел модуля (по id) или {@link #OTHER}, если его нет в таблице. */
    public static String of(SpellModuleType type) {
        return of(type.id().getPath());
    }

    public static String of(String modulePath) {
        return BY_MODULE.getOrDefault(modulePath, OTHER);
    }

    /** Ключ перевода названия подраздела. */
    public static String nameKey(String sub) {
        return "sub.spellcraft." + sub;
    }

    public static boolean isKnown(String sub) {
        return LABELS.containsKey(sub);
    }

    /** Подразделы, реально встречающиеся у этих модулей, в порядке таблицы. */
    public static java.util.List<String> presentIn(Iterable<SpellModuleType> types) {
        Set<String> used = new java.util.LinkedHashSet<>();
        for (SpellModuleType type : types) {
            used.add(of(type));
        }
        return LABELS.keySet().stream().filter(used::contains).toList();
    }

    /**
     * Порядок подразделов внутри категории — таблица одна на все категории.
     *
     * <p>Список кэшируется: его спрашивает сортировка каждого найденного
     * модуля, а раньше копия ключей создавалась на каждый вызов.
     */
    public static java.util.List<String> order() {
        if (ORDER == null) {
            ORDER = Collections.unmodifiableList(java.util.List.copyOf(LABELS.keySet()));
        }
        return ORDER;
    }

    private static java.util.List<String> ORDER;

    /** Есть ли русское название (для проверок согласованности переводов). */
    public static String labelRu(String sub) {
        String raw = LABELS.get(sub);
        return raw == null ? null : raw.split("\u0000")[0];
    }

    public static String labelEn(String sub) {
        String raw = LABELS.get(sub);
        return raw == null ? null : raw.split("\u0000")[1];
    }
}