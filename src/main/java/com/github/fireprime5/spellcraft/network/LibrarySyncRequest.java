package com.github.fireprime5.spellcraft.network;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Клиент -> Сервер: просто запросить список заклинаний библиотеки.
 */
public record LibrarySyncRequest(int unused) implements CustomPacketPayload {

    public static final Type<LibrarySyncRequest> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("spellcraft", "library_sync_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LibrarySyncRequest> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, LibrarySyncRequest::unused,
                    LibrarySyncRequest::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send() {
        PacketDistributor.sendToServer(new LibrarySyncRequest(0));
    }

    public static void handle(LibrarySyncRequest payload, ServerPlayer player) {
        LibrarySyncPayload.sendTo(player);
    }
}
