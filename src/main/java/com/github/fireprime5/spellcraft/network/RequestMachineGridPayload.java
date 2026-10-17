package com.github.fireprime5.spellcraft.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Клиент -> Сервер: «хочу настроить сетку машины».
 *
 * <p>Клиент присылает позицию и тип, но не схему: он не знает ни что за блок
 * там стоит, ни имеет ли игрок на него право. Проверяет всё сервер.
 */
public record RequestMachineGridPayload(BlockPos pos, String kind) implements CustomPacketPayload {

    public static final Type<RequestMachineGridPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("spellcraft", "request_machine_grid"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestMachineGridPayload> STREAM_CODEC =
            StreamCodec.of(RequestMachineGridPayload::write, RequestMachineGridPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, RequestMachineGridPayload payload) {
        buf.writeBlockPos(payload.pos());
        buf.writeUtf(payload.kind(), 32);
    }

    private static RequestMachineGridPayload read(RegistryFriendlyByteBuf buf) {
        return new RequestMachineGridPayload(buf.readBlockPos(), buf.readUtf(32));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(BlockPos pos, String kind) {
        PacketDistributor.sendToServer(new RequestMachineGridPayload(pos, kind));
    }

    public static void handle(RequestMachineGridPayload payload, ServerPlayer player) {
        var machine = MachineAccess.gridAt(player, payload.pos(), payload.kind());
        if (machine == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player,
                new SyncMachineGridPayload(payload.pos(), payload.kind(), machine.save()));
        PacketDistributor.sendToPlayer(player,
                new OpenMachineGridPayload(payload.pos(), payload.kind()));
    }
}