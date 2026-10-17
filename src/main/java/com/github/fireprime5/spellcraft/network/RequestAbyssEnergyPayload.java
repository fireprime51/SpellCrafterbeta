package com.github.fireprime5.spellcraft.network;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Клиент -> Сервер: просто обновить количество энергии бездны (для полоски в GUI).
 * Граф при этом не передаётся, поэтому правки игрока не могут откатиться.
 */
public record RequestAbyssEnergyPayload(int unused) implements CustomPacketPayload {

    public static final Type<RequestAbyssEnergyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("spellcraft", "request_mana"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestAbyssEnergyPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, RequestAbyssEnergyPayload::unused,
                    RequestAbyssEnergyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send() {
        PacketDistributor.sendToServer(new RequestAbyssEnergyPayload(0));
    }

    public static void handle(RequestAbyssEnergyPayload payload, ServerPlayer player) {
        AbyssEnergySyncPayload.sendTo(player);
    }
}