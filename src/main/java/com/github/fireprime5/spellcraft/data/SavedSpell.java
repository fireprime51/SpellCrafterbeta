package com.github.fireprime5.spellcraft.data;

import com.github.fireprime5.spellcraft.spell.SpellGraph;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Сохранённое заклинание: имя + граф. Вынесено отдельно от реестра вложений,
 * чтобы кодек можно было использовать и проверять в тестах.
 */
public record SavedSpell(String name, SpellGraph graph) {

    public static final Codec<SavedSpell> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("name").forGetter(SavedSpell::name),
            SpellGraph.CODEC.fieldOf("graph").forGetter(SavedSpell::graph)
    ).apply(inst, SavedSpell::new));

    public static final Codec<List<SavedSpell>> LIST_CODEC = CODEC.listOf();
}
