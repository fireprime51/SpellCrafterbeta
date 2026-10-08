package com.github.fireprime5.spellcraft.network;

import com.github.fireprime5.spellcraft.energy.ManaCapability;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Клиент -> Сервер: «открыл построитель» — сервер один раз присылает
 * сохранённый граф и ману, а потом клиент просто шлёт изменения.
 *
 * Периодического запроса графа нет специально: пока идёт обмен пакетами,
 * правка игрока могла откатываться старой версией ответа (заметная задержка).
 * Для маны есть отдельный лёгкий запрос {@link RequestManaPayload}.
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
        PacketDistributor.sendToPlayer(player, new SyncSpellGraphPayload(
                com.github.fireprime5.spellcraft.data.PlayerSpellStore.getCopy(player)));
        ManaSyncPayload.sendTo(player);
        PacketDistributor.sendToPlayer(player, new OpenBuilderPayload(0));
    }
}