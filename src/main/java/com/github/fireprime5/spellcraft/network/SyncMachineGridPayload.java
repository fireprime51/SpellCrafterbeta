package com.github.fireprime5.spellcraft.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Сервер -> Клиент: схема модулей машины для показа в конструкторе.
 *
 * <p>Идёт перед {@link OpenMachineGridPayload}: клиент сначала получает схему,
 * потом команду открыть экран, иначе конструктор открылся бы пустым.
 */
public record SyncMachineGridPayload(BlockPos pos, String kind, CompoundTag tag)
        implements CustomPacketPayload {

    public static final Type<SyncMachineGridPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("spellcraft", "sync_machine_grid"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncMachineGridPayload> STREAM_CODEC =
            StreamCodec.of(SyncMachineGridPayload::write, SyncMachineGridPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, SyncMachineGridPayload payload) {
        buf.writeBlockPos(payload.pos());
        buf.writeUtf(payload.kind(), 32);
        buf.writeNbt(payload.tag());
    }

    private static SyncMachineGridPayload read(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String kind = buf.readUtf(32);
        CompoundTag tag = buf.readNbt();
        return new SyncMachineGridPayload(pos, kind, tag == null ? new CompoundTag() : tag);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}