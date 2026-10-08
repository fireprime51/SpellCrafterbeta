package com.github.fireprime5.spellcraft.data;

import com.github.fireprime5.spellcraft.Spellcraft;
import com.github.fireprime5.spellcraft.spell.SpellGraph;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Хранилище графа заклинания игрока.
 *
 * Используется NeoForge Attachment с сериализацией в NBT: граф переживает
 * перезаход в мир, смену измерения и смерть (раньше он хранился в
 * persistentData сущности и терялся при пересоздании игрока).
 */
public final class PlayerSpellStore {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries
                    .ATTACHMENT_TYPES, Spellcraft.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SpellGraph>> SPELL =
            ATTACHMENTS.register("spell_graph", () -> AttachmentType.builder(SpellGraph::new)
                    // сериализация в NBT игрока: переживает релог и смену мира
                    .serialize(SpellGraph.CODEC)
                    .copyOnDeath()
                    .build());

    public static AttachmentType<SpellGraph> type() {
        return SPELL.get();
    }

    private PlayerSpellStore() {
    }

    public static SpellGraph get(Player player) {
        return player.getData(type());
    }

    public static void set(Player player, SpellGraph graph) {
        player.setData(type(), graph);
    }

    public static SpellGraph getCopy(Player player) {
        return player.getData(type()).copy();
    }
}