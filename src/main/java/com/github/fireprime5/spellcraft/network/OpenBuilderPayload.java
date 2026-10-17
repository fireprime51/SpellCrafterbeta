package com.github.fireprime5.spellcraft.network;

import com.github.fireprime5.spellcraft.client.ClientBridge;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Сервер -> Клиент: открыть построитель заклинаний.
 * Сам экран открывается в клиентском обработчике (см. {@link ClientBridge}).
 */
public record OpenBuilderPayload(int unused) implements CustomPacketPayload {

    public static final Type<OpenBuilderPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("spellcraft", "open_builder"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenBuilderPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, OpenBuilderPayload::unused,
                    OpenBuilderPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}