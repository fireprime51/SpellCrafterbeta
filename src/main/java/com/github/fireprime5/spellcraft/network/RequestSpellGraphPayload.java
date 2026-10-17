package com.github.fireprime5.spellcraft.network;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Клиент -> Сервер: «открыл построитель» — сервер один раз присылает
 * сохранённый граф и энергию бездны, а потом клиент просто шлёт изменения.
 *
 * Периодического запроса графа нет специально: пока идёт обмен пакетами,
 * правка игрока могла откатываться старой версией ответа (заметная задержка).
 * Для энергии бездны есть отдельный лёгкий запрос {@link RequestAbyssEnergyPayload}.
 */
public record RequestSpellGraphPayload(int unused) implements CustomPacketPayload {

    public static final Type<RequestSpellGraphPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("spellcraft", "request_spell_graph"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestSpellGraphPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT,
                    RequestSpellGraphPayload::unused, RequestSpellGraphPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send() {
        PacketDistributor.sendToServer(new RequestSpellGraphPayload(0));
    }

    public static void handle(RequestSpellGraphPayload payload, ServerPlayer player) {
        if (!rateLimit().allow(player.getGameProfile().getId())) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new SyncSpellGraphPayload(
                com.github.fireprime5.spellcraft.data.PlayerSpellStore.getCopy(player)));
        AbyssEnergySyncPayload.sendTo(player);
        PacketDistributor.sendToPlayer(player, new OpenBuilderPayload(0));
    }

    private static final PacketRateLimiter RATE_LIMIT = new PacketRateLimiter(5);

    private static PacketRateLimiter rateLimit() {
        return RATE_LIMIT;
    }

    public static void cleanup(java.util.UUID playerId) {
        RATE_LIMIT.cleanup(playerId);
    }
}