package com.github.fireprime5.spellcraft.network;

import com.github.fireprime5.spellcraft.data.SpellLibrary;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Клиент -> Сервер: сохранить заклинание в библиотеку под именем.
 */
public record SaveSpellPayload(String name) implements CustomPacketPayload {

    public static final Type<SaveSpellPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("spellcraft", "save_spell"));

    private static final StreamCodec<net.minecraft.network.FriendlyByteBuf, String> NAME_CODEC =
            StreamCodec.of((buf, value) -> buf.writeUtf(value, 64), buf -> buf.readUtf(64));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveSpellPayload> STREAM_CODEC =
            StreamCodec.composite(NAME_CODEC, SaveSpellPayload::name, SaveSpellPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(String name) {
        PacketDistributor.sendToServer(new SaveSpellPayload(name));
    }

    public static void handle(SaveSpellPayload payload, ServerPlayer player) {
        boolean saved = SpellLibrary.save(player, payload.name(),
                com.github.fireprime5.spellcraft.data.PlayerSpellStore.getCopy(player));
        if (saved) {
            LibrarySyncPayload.sendTo(player);
        } else {
            // все слоты заняты — сообщаем игроку, чтобы он не ждал результата
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "message.spellcraft.library_full", SpellLibrary.MAX_SPELLS));
        }
    }
}