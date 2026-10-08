package com.github.fireprime5.spellcraft.network;

import com.github.fireprime5.spellcraft.data.SavedSpell;
import com.github.fireprime5.spellcraft.data.SpellLibrary;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Сервер -> Клиент: список заклинаний в библиотеке (только имена и числа модулей).
 */
public record LibrarySyncPayload(List<String> names, List<Integer> sizes)
        implements CustomPacketPayload {

    public static final Type<LibrarySyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("spellcraft", "library_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LibrarySyncPayload> STREAM_CODEC =
            StreamCodec.of(LibrarySyncPayload::write, LibrarySyncPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, LibrarySyncPayload payload) {
        buf.writeVarInt(payload.names().size());
        for (int i = 0; i < payload.names().size(); i++) {
            buf.writeUtf(payload.names().get(i), 64);
            buf.writeVarInt(payload.sizes().get(i));
        }
    }

    private static LibrarySyncPayload read(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<String> names = new ArrayList<>();
        List<Integer> sizes = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            names.add(buf.readUtf(64));
            sizes.add(buf.readVarInt());
        }
        return new LibrarySyncPayload(names, sizes);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendTo(ServerPlayer player) {
        List<SavedSpell> spells = SpellLibrary.get(player);
        List<String> names = new ArrayList<>();
        List<Integer> sizes = new ArrayList<>();
        for (SavedSpell spell : spells) {
            names.add(spell.name());
            sizes.add(spell.graph().nodes.size());
        }
        PacketDistributor.sendToPlayer(player, new LibrarySyncPayload(names, sizes));
    }
}
