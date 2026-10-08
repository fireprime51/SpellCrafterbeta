package com.github.fireprime5.spellcraft.network;

import com.github.fireprime5.spellcraft.data.SpellLibrary;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Клиент -> Сервер: загрузить заклинание из библиотеки в текущий редактор.
 */
public record LoadSpellPayload(int index) implements CustomPacketPayload {

    public static final Type<LoadSpellPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("spellcraft", "load_spell"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LoadSpellPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, LoadSpellPayload::index,
                    LoadSpellPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(int index) {
        PacketDistributor.sendToServer(new LoadSpellPayload(index));
    }

    public static void handle(LoadSpellPayload payload, ServerPlayer player) {
        SpellLibrary.load(player, payload.index);
        LibrarySyncPayload.sendTo(player);
        SyncSpellGraphPayload.sendTo(player,
                com.github.fireprime5.spellcraft.data.PlayerSpellStore.getCopy(player));
    }
}
