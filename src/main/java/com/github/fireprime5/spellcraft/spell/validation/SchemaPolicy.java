package com.github.fireprime5.spellcraft.spell.validation;

import com.github.fireprime5.spellcraft.spell.SpellModules;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/** Contextual restrictions; ordinary spells have no gameplay node limit. */
public record SchemaPolicy(int maxNodes, Set<ResourceLocation> allowedModules) {
    public static final SchemaPolicy SPELL = new SchemaPolicy(-1, null);

    public SchemaPolicy {
        if (allowedModules != null) {
            allowedModules = Set.copyOf(allowedModules);
        }
    }

    public static SchemaPolicy restricted(int maxNodes, Set<String> paths) {
        return new SchemaPolicy(maxNodes, paths.stream().map(SpellModules::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }

    public boolean allows(ResourceLocation id) {
        return id != null && (allowedModules == null || allowedModules.contains(id));
    }
}
