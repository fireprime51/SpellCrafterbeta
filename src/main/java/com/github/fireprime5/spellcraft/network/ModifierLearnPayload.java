package com.github.fireprime5.spellcraft.network;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Клиент -> Сервер: попытка изучить модификатор за ману (со шансом).
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

    public static void handle(ModifierLearnPayload payload, ServerPlayer player) {
        var ctx = new com.github.fireprime5.spellcraft.spell.SpellContext(player.level(), player,
                player.getEyePosition());
        try {
            com.github.fireprime5.spellcraft.spell.KnowledgeHooks.learnModifier(player,
                    payload.modifierId(), ctx);
        } catch (RuntimeException e) {
            com.github.fireprime5.spellcraft.Spellcraft.LOGGER.debug("modifier learn failed: " + e);
        }
        ManaSyncPayload.sendTo(player);
    }
}
