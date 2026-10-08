package com.github.fireprime5.spellcraft.spell;

import com.github.fireprime5.spellcraft.Spellcraft;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Реестр всех доступных типов модулей + поиск для палитры.
 */
public final class SpellModules {

    private static final Map<ResourceLocation, SpellModuleType> MODULES = new HashMap<>();

    /**
     * Дополнительные синонимы для поиска (русские), чтобы поиск работал
     * независимо от выбранного языка игры.
     */
    private static final Map<String, String> ALIASES = new HashMap<>();

    private static void alias(String modulePath, String words) {
        ALIASES.put(modulePath, words);
    }

    static {
        alias("damage", "урон атака ударить удар");
        alias("heal", "лечение лечить здоровье хил");
        alias("explode", "взрыв взорвать бомба");
        alias("fireball", "огненный шар огонь пламя снаряд");
        alias("impulse", "импульс толкать оттолкнуть");
        alias("sparkle", "искры частицы эффект блеск");
        alias("sound", "звук слышно");
        alias("raycast_entity", "луч сущность цель поражение");
        alias("raycast_block", "луч блок точка стена");
        alias("caster_entity", "кастер игрок я");
        alias("entity_look", "взгляд направление взглянуть");
        alias("entity_position", "позиция координаты где");
        alias("add", "плюс сложить сумма");
        alias("subtract", "минус вычесть разность");
        alias("multiply", "умножить произведение");
        alias("divide", "делить разделить");
        alias("power", "степень возвести");
        alias("negate", "минус знак");
        alias("swap", "поменять обменять");
        alias("pick_first", "первый выбор");
        alias("pick_second", "второй выбор");

        alias("random", "случайное рандом");
        alias("random_range", "случайное диапазон");
        alias("abs", "модуль");
        alias("floor", "округление вниз");
        alias("ceil", "округление вверх");
        alias("round", "округление");
        alias("sqrt", "корень");
        alias("sign", "знак плюс минус");
        alias("min", "минимум меньше");
        alias("max", "максимум больше");
        alias("percent_of", "процент");
        alias("modulo", "остаток");
        alias("clamp", "ограничить");
        alias("nearest_entity", "ближайшая цель рядом");
        alias("entity_under_crosshair", "прицел цель смотреть");
        alias("self_position", "своя позиция я");
        alias("entity_health", "здоровье хп");
        alias("entity_max_health", "макс здоровье");
        alias("entity_distance", "расстояние дистанция");
        alias("entity_velocity", "скорость движение");
        alias("is_player", "игрок ли");
        alias("entities_nearby", "сколько сущностей рядом толпа");
        alias("block_under_crosshair", "блок под прицелом смотреть");
        alias("is_air", "воздух пусто");
        alias("block_light", "свет освещение");
        alias("time_of_day", "время суток день ночь");
        alias("world_spawn", "спавн возрождение");
        alias("held_item", "предмет рука");
        alias("item_count", "количество предметов инвентарь");
        alias("compare", "сравнить сравнение");
        alias("select", "выбор условие если");
        alias("duplicate", "дублировать копия раздвоить");
        alias("not", "не отрицание");
        alias("and", "и логика");
        alias("or", "или логика");
        alias("select_random", "случайный выбор рандом");
        alias("set_health", "здоровье установить");
        alias("heal_percent", "лечение процент хил");
        alias("ignite", "поджечь огонь гореть");
        alias("effect_speed", "скорость эффект зелье бег");
        alias("teleport", "телепорт перенести");
        alias("lightning", "молния разряд");
        alias("arrow", "стрела снаряд");
        alias("sound_at", "звук");
        alias("create_water", "вода жидкость залить");
        alias("create_lava", "лава жидкость");
        alias("create_fire", "огонь пламя поджечь развести");
        alias("break_block", "добыча сломать блок раскопки копать");
        alias("drain_liquids", "осушение осушить вода лава жидкость");
        alias("point_from_coords", "координаты точка xyz");
        alias("coord_x", "координата x");
        alias("coord_y", "координата y");
        alias("coord_z", "координата z");
        alias("place_block", "постройка поставить блок");
        alias("place_held_block", "построить блок в руке");
        alias("stack_push", "стек положить push");
        alias("stack_pop", "стек взять pop");
        alias("stack_dup", "стек дублировать");
        alias("stack_drop", "стек сбросить");
        alias("stack_clear", "стек очистить");
        alias("stack_size", "стек размер сколько");

        // циклы и ячейки
        alias("stack_repeat", "цикл повтор размножить копии повторить");
        alias("stack_swap", "сдвинуть поменять местами стек обменять");
        alias("stack_pick", "выбор взять копия стек достать");
        alias("stack_drop_n", "отбросить снять стек убрать");
        alias("stack_exit", "выход остановить прекратить каст");
        alias("stack_skip", "пропустить остановить каст условие");
        alias("stack_store", "ячейка сохранить запомнить положить");
        alias("stack_load", "ячейка взять достать загрузить");
        alias("stack_slots_used", "ячейки занято сколько");
        alias("stack_clear_slots", "ячейки очистить освободить сбросить");

        // предметы и блоки
        alias("block_at", "блок прочитать взять из мира что за блок предмет");
        alias("fluid_at", "жидкость ведро вода лава жидкость взять");
        alias("held_slot", "слот хотбар ячейка быстрого доступа предмет");
        alias("empty_hand", "пустая рука рука ничего нет");
        alias("offhand_item", "вторая рука вторая_рука");
        alias("swap_blocks", "поменять местами обменять блоки переставить");
        alias("fill_region", "залить область прямоугольник заполнить область");
        alias("clear_block", "убрать блок очистить удалить без добычи");
        alias("give_item", "выдать предмет дать в инвентарь получить");
        alias("drop_item", "выбросить предмет выронить на землю дропнуть");
        alias("consume_item", "забрать предмет изъять из инвентаря снять");
        alias("modifier_fortune", "модификатор удача фортуна добыча");
        alias("modifier_silk", "модификатор шёлковое касание добыча");
        alias("modifier_area", "модификатор по области добыча");
        alias("modifier_instant", "модификатор мгновенная добыча жила");
        alias("modifier_no_blocks", "модификатор взрыв без блоков не ломать");
        alias("modifier_wide", "модификатор взрыв широкий радиус");
        alias("modifier_fire", "модификатор взрыв поджиг огонь");
        alias("modifier_shove", "модификатор взрыв отбрасывание откидывает");
        alias("modifier_powerful", "модификатор взрыв усиленный мощный");
        alias("analysis_block", "анализ блок изучить исследовать");
        alias("analysis_mob", "анализ моб существо изучить исследовать");
        alias("extract_block", "извлечение блок мана добыча");
        alias("extract_mob", "извлечение моб мана добыча");

        // векторы и геометрия
        alias("vector_from_coords", "вектор по координатам собрать направление xyz");
        alias("vector_length", "длина вектора длина насколько далеко");
        alias("vector_normalize", "нормировать вектор единичный длина");
        alias("vector_flat", "сплющить вектор горизонтальный разброс по земле");
        alias("vector_scale", "масштаб вектора умножить вектор растянуть");
        alias("vector_add", "сложить вектора сумма вектор");
        alias("vector_subtract", "вычесть вектора разность вектор");
        alias("vector_dot", "скалярное произведение вектора");
        alias("vector_cross", "векторное произведение вектора перпендикулярный");
        alias("vector_angle", "угол между векторами градусы насколько повернут");
        alias("vector_rotate_yaw", "поворот вектора влево вправо рыскание развернуть");
        alias("vector_rotate_pitch", "наклон вектора вверх вниз тангаж навести");
        alias("vector_cone", "разброс случайный конус веер");
        alias("wrap_angle", "угол нормализовать градусы круг 360");
        alias("point_distance", "расстояние между точками дистанция");
        alias("point_direction", "направление на точку от точки к точке");
        alias("point_midpoint", "середина точка отрезка центр между");
        alias("point_offset", "сдвинуть точку сместить сдвиг");
        alias("line_blocks", "блоков в линии длина луча луч до дальность");
        alias("sin", "синус");
        alias("cos", "косинус");
        alias("lerp", "плавно интерполяция между плавный");
    }
    private static List<SpellModuleType> sortedCache = null;

    private SpellModules() {
    }

    public static void register(SpellModuleType type) {
        MODULES.put(type.id(), type);
        sortedCache = null;
    }

    public static SpellModuleType get(ResourceLocation id) {
        return MODULES.get(id);
    }

    public static List<SpellModuleType> all() {
        if (sortedCache == null) {
            sortedCache = new ArrayList<>(MODULES.values());
            sortedCache.sort(Comparator.comparing(SpellModules::sortKey));
        }
        return Collections.unmodifiableList(sortedCache);
    }

    private static String sortKey(SpellModuleType t) {
        return t.category().ordinal() + "_" + t.id().getPath();
    }

    public static String shortName(SpellModuleType type) {
        return type.id().getPath();
    }

    /**
     * Поиск по локализованному названию, id и категории.
     * Ищет и по русскому названию («урон», «взрыв»), и по английскому id («damage»).
     */
    public static List<SpellModuleType> search(String query, SpellModuleType.Category category) {
        String q = normalize(query);
        List<SpellModuleType> result = new ArrayList<>();
        for (SpellModuleType t : MODULES.values()) {
            if (category != null && t.category() != category) {
                continue;
            }
            if (q.isEmpty() || matches(t, q)) {
                result.add(t);
            }
        }
        result.sort(Comparator.comparing(SpellModules::sortKey));
        return result;
    }

    private static boolean matches(SpellModuleType type, String q) {
        String path = normalize(type.id().getPath());
        String ns = normalize(type.id().getNamespace());
        String name = normalize(net.minecraft.network.chat.Component
                .translatable(type.nameKey()).getString());
        String category = normalize(net.minecraft.network.chat.Component
                .translatable(type.category().translationKey()).getString());
        String alias = normalize(ALIASES.get(type.id().getPath()));
        return path.contains(q) || ns.contains(q) || name.contains(q) || category.contains(q)
                || (!alias.isEmpty() && alias.contains(q));
    }

    /**
     * Убирает пробелы/подчёркивания и переводит в нижний регистр.
     * Пробелы внутри слова тоже убираются, чтобы «по области» и «пообласти»
     * находили одно и то же.
     */
    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        String lower = s.toLowerCase();
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c != ' ' && c != '_') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static void init() {
        BuiltinModules.registerAll();
        ExtraModules.registerAll();
        AnalysisModules.registerAll();
        StackModules.registerAll();
        WorldModules.registerAll();
        GeometryModules.registerAll();
        ItemModules.registerAll();
        WorldModules.registerEffectModules();
    }

    public static ResourceLocation id(String path) {
        return Spellcraft.id(path);
    }
}