package com.github.fireprime5.spellcraft.spell;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Предпосылки модулей: что нужно знать, чтобы открылось следующее.
 *
 * <p>Сканирование открывает модули, но модулей больше, чем целей, которые
 * игрок встречает. Раньше это означало, что 51 модуль из 127 был недостижим
 * никогда — включая базовую арифметику: её нельзя было получить ни сканированием,
 * ни командой (команда только у оператора).
 *
 * <p>Поле {@link SpellModuleType#requiresModule()} для этого и написано, но не
 * было переопределено ни одним модулем. Здесь оно оживает: модуль, у которого
 * есть предпосылка, открывается вместе с ней, как только игрок узнал
 * предпосылку.
 *
 * <p>Таблица вынесена отдельно от регистрации по той же причине, что и
 * {@link SpellSubsections}: видно целиком, можно перегруппировать, и перенос
 * модуля не ломает его цепочку.
 *
 * <p>Схема выстроена по уровням: числа → математика → условия → чтение мира →
 * чтение сущностей. Внутри уровня порядок неважен.
 */
public final class SpellPrerequisites {

    private SpellPrerequisites() {
    }

    /** Модуль, который не открывается в принципе (способ добычи не придуман). */
    public static final String UNREACHABLE = "fission";

    /** Модуль → модуль, который нужно знать первым. */
    private static final Map<String, String> REQUIRES = new HashMap<>();

    /** Модуль → список модулей, которые он открывает. Считается при разботе. */
    private static final Map<String, Set<String>> UNLOCKS = new LinkedHashMap<>();

    private static void chain(String after, String... first) {
        for (String module : first) {
            REQUIRES.put(module, after);
        }
    }

    static {
        // --- математика от сложения ---
        // «−2, +4 = 2» читается естественнее, чем голое умножение, поэтому
        // сложение — начало всей ветки
        // subtract/multiply/divide имеют собственные награды за сканирование,
        // поэтому предпосылкой для них делать нечего
        chain("add",
                "percent_of", "min", "max", "clamp",
                "abs", "floor", "ceil", "round");
        chain("multiply",
                "power", "modulo");
        // знак и корень — про «отрицательное и обратное», поэтому от вычитания
        chain("subtract", "sign", "sqrt");

        // --- условия от сравнения ---
        chain("compare", "and", "or", "not");
        // «Дублировать» открывает разветвление на два потока
        chain("duplicate", "pick_first", "pick_second", "swap");

        // --- координаты ---
        chain("point_from_coords", "coord_x", "coord_y", "coord_z");

        // --- сущности ---
        chain("entity_position",
                "entity_health", "entity_max_health", "entity_velocity");
        chain("entity_distance", "entities_nearby");
        // «Ближайшая цель» достаётся сканированием скулька — значит, и модули
        // поиска по радиусу не надо выдавать отдельной наградой
        chain("nearest_entity", "entities_in_radius", "entities_count_in_radius");
        // щит и барьер — продолжение «Ближайшей цели»: сначала научился искать,
        // потом научился закрываться
        chain("nearest_entity", "shield", "barrier");
        // условия — после «сравнить», сортировка сущностей — после «это игрок»
        chain("compare", "equals", "not_equals", "greater", "less",
                "greater_or_equal", "less_or_equal");
        chain("is_player", "is_mob", "is_item", "is_projectile", "is_vehicle",
                "is_alive");
        chain("vector_length", "vector_distance", "vector_lerp");
        chain("stack_push", "delay_push", "delay_pop");
        // заморозка — продолжение «толчка»: сначала научился двигать, потом стопорить
        chain("impulse", "freeze", "unfreeze");

        // --- стек ---
        // stack_exit («выдать всё и остановиться») — конец любой постройки,
        // поэтому он должен быть сразу: без него заклинание нельзя завершить
        chain("stack_push", "stack_exit");
        chain("stack_pop", "stack_drop", "stack_pick");

        // --- случайность ---
        chain("random", "random_range");

        // --- модульная броня ---
        // Триггеры идут после арифметики: игрок уже умеет считать, иначе
        // «скорость» и «урон» в броне были бы просто переключателями без
        // смысла. Порядок важен — каждый следующий модуль открывается после
        // предыдущего, поэтому полёт уже не собрать без триггеров.
        chain("add", "trigger_on_hit", "trigger_on_hurt");
        chain("trigger_on_hit", "modifier_speed", "modifier_damage",
                "modifier_protection");
        chain("modifier_speed", "creative_flight");


        // обратные ссылки для быстрой проверки «что открывает этот модуль»
        REQUIRES.forEach((module, after) ->
                UNLOCKS.computeIfAbsent(after, key -> new TreeSet<>()).add(module));
        // семейства в UNLOCKS не попадают: их предпосылки вычисляются по имени,
        // и таблица «что открывает этот модуль» остаётся точной для явных
    }

    /**
     * Предпосылка по умолчанию для целых семейств модулей.
     *
     * <p>Эффектов 39, и перечислять их по именам — значит поддерживать список
     * на 39 строк, который молча разъедется с {@code EFFECT_IDS}. Поэтому
     * семейство получает общее правило: сначала научись находить сущность,
     * потом влиять на неё. Исключения — два мгновенных эффекта, которые по
     * смыслу являются лечением и уроном, а не «изменением сущности».
     */
    private static String defaultPrerequisite(String modulePath) {
        // у эффектов с собственной наградой за сканирование предпосылка
        // не нужна: иначе игрок получает модуль, который всё равно заперт
        if (modulePath.startsWith("effect_") && !ScanRewards.isReward(modulePath)) {
            return switch (modulePath) {
                case "effect_instant_health" -> "heal";
                case "effect_instant_damage" -> "damage";
                default -> "entity_position";
            };
        }
        return null;
    }

    /** Предпосылка модуля или null, если её нет. */
    public static String of(String modulePath) {
        String explicit = REQUIRES.get(modulePath);
        return explicit != null ? explicit : defaultPrerequisite(modulePath);
    }

    public static String of(SpellModuleType type) {
        return of(type.id().getPath());
    }

    /** Модули, которые открывает знание этого. */
    public static Set<String> unlocks(String modulePath) {
        return UNLOCKS.getOrDefault(modulePath, Set.of());
    }

    /** Есть ли у модуля предпосылка. */
    public static boolean hasPrerequisite(String modulePath) {
        return of(modulePath) != null;
    }

    /** Модуль, который нельзя получить в игре никак. */
    public static boolean isUnreachable(String modulePath) {
        return UNREACHABLE.equals(modulePath);
    }

    /** Вся цепочка предпосылок: модуль → предпосылка. Для тестов и отладки. */
    public static Map<String, String> all() {
        return java.util.Collections.unmodifiableMap(REQUIRES);
    }
}