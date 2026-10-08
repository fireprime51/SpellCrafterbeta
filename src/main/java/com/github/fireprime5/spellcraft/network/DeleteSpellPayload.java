package com.github.fireprime5.spellcraft.network;

import com.github.fireprime5.spellcraft.data.SpellLibrary;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Клиент -> Сервер: удалить заклинание из библиотеки.
 */
public record DeleteSpellPayload(int index) implements CustomPacketPayload {

    public static final Type<DeleteSpellPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("spellcraft", "delete_spell"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeleteSpellPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, DeleteSpellPayload::index,
                    DeleteSpellPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(int index) {
        PacketDistributor.sendToServer(new DeleteSpellPayload(index));
    }

    public static void handle(DeleteSpellPayload payload, ServerPlayer player) {
        SpellLibrary.delete(player, payload.index());
        LibrarySyncPayload.sendTo(player);
    }
}
