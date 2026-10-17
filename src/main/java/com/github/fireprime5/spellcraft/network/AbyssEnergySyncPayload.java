package com.github.fireprime5.spellcraft.network;

import com.github.fireprime5.spellcraft.energy.AbyssEnergy;
import com.github.fireprime5.spellcraft.energy.AbyssPool;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Сервер -> Клиент: энергия бездны игрока и прогресс анализа (для панели и палитры).
 *
 * @param infinite включена ли бесконечная энергия бездны — панель показывает «∞» вместо
 *                 числа, иначе пришлось бы объяснять, почему каст бесплатный,
 *                 а полоска показывает ноль
 */
public record AbyssEnergySyncPayload(int abyss, int maxAbyss, java.util.List<String> knownModules,
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
                        // размер должен читаться РОВНО столько, сколько записано:
                        // иначе при size > 512 остаток буфера уезжает в следующие
                        // поля пакета и весь кадр рассинхронизируется. Поэтому
                        // размер не зажимается, а отвергается, если он вне ряда.
                        if (size < 0 || size > 512) {
                            throw new io.netty.handler.codec.DecoderException(
                                    "bad name list size: " + size);
                        }
                        java.util.List<String> out = new java.util.ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            out.add(buf.readUtf(128));
                        }
                        return out;
                    });

    public static final Type<AbyssEnergySyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("spellcraft", "mana_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AbyssEnergySyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, AbyssEnergySyncPayload::abyss,
                    ByteBufCodecs.VAR_INT, AbyssEnergySyncPayload::maxAbyss,
                    NAMES_CODEC, AbyssEnergySyncPayload::knownModules,
                    NAMES_CODEC, AbyssEnergySyncPayload::knownModifiers,
                    ByteBufCodecs.VAR_INT, AbyssEnergySyncPayload::total,
                    ByteBufCodecs.BOOL, AbyssEnergySyncPayload::infinite,
                    AbyssEnergySyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new AbyssEnergySyncPayload(
                AbyssPool.total(player),
                AbyssEnergy.capacityOf(player),
                com.github.fireprime5.spellcraft.data.PlayerKnowledge.knownModules(player),
                java.util.List.copyOf(
                        com.github.fireprime5.spellcraft.data.PlayerKnowledge.knownModifiers(player)),
                com.github.fireprime5.spellcraft.spell.SpellModules.all().size(),
                AbyssEnergy.isInfinite(player)));
    }
}
