package com.github.fireprime5.spellcraft.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Клиент -> Сервер: попытка изучить модификатор за энергию бездны (со шансом).
 */
public record ModifierLearnPayload(String modifierId) implements CustomPacketPayload {

    public static final Type<ModifierLearnPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("spellcraft", "modifier_learn"));

    private static final StreamCodec<net.minecraft.network.FriendlyByteBuf, String> NAME_CODEC =
            StreamCodec.of((buf, value) -> buf.writeUtf(value, 64), buf -> buf.readUtf(64));

    public static final StreamCodec<RegistryFriendlyByteBuf, ModifierLearnPayload> STREAM_CODEC =
            StreamCodec.composite(NAME_CODEC, ModifierLearnPayload::modifierId,
                    ModifierLearnPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(String modifierId) {
        PacketDistributor.sendToServer(new ModifierLearnPayload(modifierId));
    }

    /** Пакет приходит от клиента, поэтому попыток должно быть немного. */
    private static final long MIN_LEARN_INTERVAL_MS = 500L;
    private static final Map<java.util.UUID, Long> LAST_ATTEMPT = new ConcurrentHashMap<>();

    public static void cleanup(java.util.UUID playerId) {
        LAST_ATTEMPT.remove(playerId);
    }

    public static void handle(ModifierLearnPayload payload, ServerPlayer player) {
        long now = System.currentTimeMillis();
        Long last = LAST_ATTEMPT.get(player.getGameProfile().getId());
        if (last != null && now - last < MIN_LEARN_INTERVAL_MS) {
            return;
        }
        LAST_ATTEMPT.put(player.getGameProfile().getId(), now);
        var ctx = new com.github.fireprime5.spellcraft.spell.SpellContext(player.level(), player,
                player.getEyePosition());
        try {
            com.github.fireprime5.spellcraft.spell.KnowledgeHooks.learnModifier(player,
                    payload.modifierId(), ctx);
        } catch (RuntimeException e) {
            com.github.fireprime5.spellcraft.Spellcraft.LOGGER.debug("modifier learn failed: " + e);
        }
        AbyssEnergySyncPayload.sendTo(player);
    }
}
