package com.github.fireprime5.spellcraft.spell;

import com.github.fireprime5.spellcraft.Spellcraft;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Стартовые модули: доступны с самого начала, без изучения.
 *
 * Это вся цепочка, которой игрок добывает первую ману и первые знания:
 * Кастер → Взгляд → Луч → Анализ / Извлечение. Всё бесплатно.
 */
public final class StartModules {

    private StartModules() {
    }

    private static final Set<String> START = new LinkedHashSet<>(java.util.List.of(
            "caster_entity", "entity_look", "raycast_entity", "raycast_block",
            "analysis_block", "analysis_mob", "extract_block", "extract_mob"));

    public static boolean isStartModule(ResourceLocation id) {
        return id.getNamespace().equals(Spellcraft.MODID) && START.contains(id.getPath());
    }

    public static Set<String> all() {
        return Collections.unmodifiableSet(START);
    }
}