package com.github.fireprime5.spellcraft.network;

import com.github.fireprime5.spellcraft.data.PlayerSpellStore;
import com.github.fireprime5.spellcraft.spell.SpellGraph;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Клиент -> Сервер: сохранить/обновить граф заклинания.
 */
public record UpdateSpellGraphPayload(SpellGraph graph) implements CustomPacketPayload {

    public static final Type<UpdateSpellGraphPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("spellcraft", "update_spell_graph"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateSpellGraphPayload> STREAM_CODEC =
            StreamCodec.of(UpdateSpellGraphPayload::write, UpdateSpellGraphPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, UpdateSpellGraphPayload payload) {
        buf.writeNbt(payload.graph.save());
    }

    private static UpdateSpellGraphPayload read(RegistryFriendlyByteBuf buf) {
        var tag = buf.readNbt();
        return new UpdateSpellGraphPayload(tag == null ? new SpellGraph() : SpellGraph.load(tag));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UpdateSpellGraphPayload payload, ServerPlayer player) {
        // граф приходит от клиента, поэтому ограничиваем размер и чистим битые связи
        SpellGraph graph = payload.graph();
        if (graph.nodes.size() > SpellGraph.MAX_NODES) {
            com.github.fireprime5.spellcraft.Spellcraft.LOGGER.warn(
                    "Player {} sent a spell with {} nodes (limit {})",
                    player.getName().getString(), graph.nodes.size(), SpellGraph.MAX_NODES);
            return;
        }
        graph.dropBrokenConnections();
        // сохраняем копию, чтобы клиентский объект не был общим с серверным
        graph = graph.copy();
        // выкидываем модификаторы, которые игрок не изучал или которые конфликтуют
        if (graph.sanitizeModifiers(player)) {
            SyncSpellGraphPayload.sendTo(player, graph);
        }
        PlayerSpellStore.set(player, graph);
    }
}
