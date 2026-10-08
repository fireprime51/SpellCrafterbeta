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
 * Сервер -> Клиент: мана игрока и прогресс анализа (для панели и палитры).
 */
/**
 * Сервер -> Клиент: мана игрока и прогресс анализа (для панели и палитры).
 *
 * @param infinite включена ли бесконечная мана — панель показывает «∞» вместо
 *                 числа, иначе пришлось бы объяснять, почему каст бесплатный,
 *                 а полоска показывает ноль
 */
public record ManaSyncPayload(int mana, int maxMana, java.util.List<String> knownModules,
                              java.util.List<String> knownModifiers, int total,
                              boolean infinite)
        implements CustomPacketPayload {

    /** Список строк (id модулей) в буфере. */
    private static final StreamCodec<net.minecraft.network.FriendlyByteBuf, java.util.List<String>>
            NAMES_CODEC = StreamCodec.of(
                    (buf, list) -> {
                        buf.writeVarInt(list.size());
                        for (String s : list) {
                            buf.writeUtf(s, 128);
                        }
                    },
                    buf -> {
                        int size = buf.readVarInt();
                        java.util.List<String> out = new java.util.ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            out.add(buf.readUtf(128));
                        }
                        return out;
                    });

    public static final Type<ManaSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("spellcraft", "mana_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ManaSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ManaSyncPayload::mana,
                    ByteBufCodecs.VAR_INT, ManaSyncPayload::maxMana,
                    NAMES_CODEC, ManaSyncPayload::knownModules,
                    NAMES_CODEC, ManaSyncPayload::knownModifiers,
                    ByteBufCodecs.VAR_INT, ManaSyncPayload::total,
                    ByteBufCodecs.BOOL, ManaSyncPayload::infinite,
                    ManaSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new ManaSyncPayload(
                ManaCapability.getMana(player),
                ManaCapability.capacity(),
                com.github.fireprime5.spellcraft.data.PlayerKnowledge.knownModules(player),
                java.util.List.copyOf(
                        com.github.fireprime5.spellcraft.data.PlayerKnowledge.knownModifiers(player)),
                com.github.fireprime5.spellcraft.spell.SpellModules.all().size(),
                ManaCapability.isInfinite(player)));
    }
}
