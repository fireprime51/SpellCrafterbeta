package com.github.fireprime5.spellcraft.network;

import com.github.fireprime5.spellcraft.client.ClientBridge;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

/**
 * Сервер -> Клиент: схема модулей нагрудника для показа в конструкторе.
 *
 * <p>Идёт перед {@link OpenArmorBuilderPayload}: клиент сначала получает схему,
 * потом команду открыть экран, иначе конструктор открылся бы пустым.
 *
 * <p>Разбор пакета и запись в состояние брони делает клиентский код через
 * {@link ClientBridge} — обработчики пакетов приходят не из основного потока,
 * а состояние конструктора читает уже главный поток.
 */
public record SyncArmorGraphPayload(InteractionHand hand, CompoundTag tag)
        implements CustomPacketPayload {

    public static final Type<SyncArmorGraphPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("spellcraft", "sync_armor_graph"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncArmorGraphPayload> STREAM_CODEC =
            StreamCodec.of(SyncArmorGraphPayload::write, SyncArmorGraphPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, SyncArmorGraphPayload payload) {
        buf.writeVarInt(payload.hand().ordinal());
        buf.writeNbt(payload.tag());
    }

    private static SyncArmorGraphPayload read(RegistryFriendlyByteBuf buf) {
        var hand = buf.readVarInt() == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        var tag = buf.readNbt();
        return new SyncArmorGraphPayload(hand, tag == null ? new CompoundTag() : tag);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}