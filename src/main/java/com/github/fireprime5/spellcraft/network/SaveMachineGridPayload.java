package com.github.fireprime5.spellcraft.network;

import com.github.fireprime5.spellcraft.spell.SpellGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Клиент -> Сервер: сохранить схему модулей машины.
 *
 * <p>Схема пришла с клиента, поэтому сервер проверяет её целиком: и расстояние
 * до машины, и то, что там вообще машина, и что все модули разрешены её
 * набором. Иначе игрок отправил бы в блок что угодно, включая модули, которых
 * в плавильне быть не должно.
 */
public record SaveMachineGridPayload(BlockPos pos, String kind, SpellGraph graph)
        implements CustomPacketPayload {

    public static final Type<SaveMachineGridPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("spellcraft", "save_machine_grid"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveMachineGridPayload> STREAM_CODEC =
            StreamCodec.of(SaveMachineGridPayload::write, SaveMachineGridPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, SaveMachineGridPayload payload) {
        buf.writeBlockPos(payload.pos());
        buf.writeUtf(payload.kind(), 32);
        buf.writeNbt(payload.graph.save());
    }

    private static SaveMachineGridPayload read(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String kind = buf.readUtf(32);
        if (buf.readableBytes() > NetworkLimits.MAX_GRAPH_BYTES) {
            throw new IllegalArgumentException("machine graph payload is too large");
        }
        var tag = buf.readNbt();
        return new SaveMachineGridPayload(pos, kind,
                tag == null ? new SpellGraph() : SpellGraph.load(tag));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(BlockPos pos, String kind, SpellGraph graph) {
        PacketDistributor.sendToServer(new SaveMachineGridPayload(pos, kind, graph));
    }

    public static void handle(SaveMachineGridPayload payload, ServerPlayer player) {
        // MachineAccess проверяет расстояние, тип блока и чистит неразрешённые
        // модули: клиенту доверять здесь нельзя
        MachineAccess.save(player, payload.pos(), payload.kind(), payload.graph());
    }
}
