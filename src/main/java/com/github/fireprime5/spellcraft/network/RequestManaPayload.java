package com.github.fireprime5.spellcraft.network;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Клиент -> Сервер: просто обновить количество маны (для полоски в GUI).
 * Граф при этом не передаётся, поэтому правки игрока не могут откатиться.
 */
public record RequestManaPayload(int unused) implements CustomPacketPayload {

    public static final Type<RequestManaPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("spellcraft", "request_mana"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestManaPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, RequestManaPayload::unused,
                    RequestManaPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send() {
        PacketDistributor.sendToServer(new RequestManaPayload(0));
    }

    public static void handle(RequestManaPayload payload, ServerPlayer player) {
        ManaSyncPayload.sendTo(player);
    }
}