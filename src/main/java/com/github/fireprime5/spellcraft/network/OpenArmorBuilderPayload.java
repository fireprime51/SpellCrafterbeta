package com.github.fireprime5.spellcraft.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

/**
 * Сервер -> Клиент: открыть конструктор модулей брони.
 *
 * <p>Рука едет вместе с пакетом, чтобы экран знал, куда потом сохранять схему.
 */
public record OpenArmorBuilderPayload(InteractionHand hand) implements CustomPacketPayload {

    public static final Type<OpenArmorBuilderPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("spellcraft", "open_armor_builder"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenArmorBuilderPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeVarInt(payload.hand().ordinal()),
                    buf -> new OpenArmorBuilderPayload(
                            buf.readVarInt() == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
