package com.github.fireprime5.spellcraft.spell;

import com.github.fireprime5.spellcraft.Spellcraft;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Стартовые модули: доступны с самого начала, без изучения.
 *
 * Это вся цепочка, которой игрок добывает первую энергию бездны и первые знания:
 * Кастер → Взгляд → Луч → Анализ / Извлечение. Всё бесплатно.
 */
public final class StartModules {

    private StartModules() {
    }

    private static final Set<String> START = new LinkedHashSet<>(java.util.List.of(
            "caster_entity", "entity_look", "raycast_entity", "raycast_block",
            "analysis_block", "analysis_mob", "extract_block", "extract_mob",
            // «Здесь» и «Адрес: обычный мир» — то, из чего собирается первый
            // портал. Без них адрес нечем построить, а остальные адреса
            // (Незер, Энд) открываются посещением, то есть позже
            "location_here", "location_overworld",
            // «Здесь» — самая простая точка: без неё не собрать первое
            // заклинание, которое что-то делает с собой
            "self_position",
            // константы: чтобы узнать, что такое «0» и «1», сканировать
            // блок не нужно. Это подножие, а не умение
            "const_zero", "const_one", "const_five", "const_ten", "const_neg_one",
            // «Анализ себя» — вторая точка входа после книги: он открывает
            // личный модуль игрока. Бесплатный и доступный сразу, иначе игрок
            // до него не доберётся, а без него прогрессия осталась бы общей
            // на всех и личные модули не имели бы смысла
            "analysis_self"));

    public static boolean isStartModule(ResourceLocation id) {
        return id.getNamespace().equals(Spellcraft.MODID) && START.contains(id.getPath());
    }

    public static Set<String> all() {
        return Collections.unmodifiableSet(START);
    }
}