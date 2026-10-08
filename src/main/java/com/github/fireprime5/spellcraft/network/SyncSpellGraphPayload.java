package com.github.fireprime5.spellcraft.network;

import com.github.fireprime5.spellcraft.spell.SpellGraph;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Сервер -> Клиент: полная копия графа заклинания игрока (при открытии GUI).
 */
public record SyncSpellGraphPayload(SpellGraph graph) implements CustomPacketPayload {

    public static final Type<SyncSpellGraphPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("spellcraft", "sync_spell_graph"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncSpellGraphPayload> STREAM_CODEC =
            StreamCodec.of(SyncSpellGraphPayload::write, SyncSpellGraphPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, SyncSpellGraphPayload payload) {
        buf.writeNbt(payload.graph.save());
    }

    private static SyncSpellGraphPayload read(RegistryFriendlyByteBuf buf) {
        var tag = buf.readNbt();
        return new SyncSpellGraphPayload(tag == null ? new SpellGraph() : SpellGraph.load(tag));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendTo(net.minecraft.server.level.ServerPlayer player, SpellGraph graph) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new SyncSpellGraphPayload(graph));
    }
}
