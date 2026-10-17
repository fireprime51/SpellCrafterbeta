package com.github.fireprime5.spellcraft.network;

import com.github.fireprime5.spellcraft.armor.ModularChestplateItem;
import com.github.fireprime5.spellcraft.armor.ModularSchema;
import com.github.fireprime5.spellcraft.client.ClientArmorState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Клиент -> Сервер: «правый клик по модульному нагруднику».
 *
 * <p>Сервер отвечает схемой и командой открыть конструктор. Клиент не присылает
 * свою схему: она лежит в компоненте предмета, и единственный честный источник
 * правды — сервер, который видит настоящий стек.
 */
public record RequestArmorGraphPayload(InteractionHand hand) implements CustomPacketPayload {

    public static final Type<RequestArmorGraphPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("spellcraft", "request_armor_graph"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestArmorGraphPayload> STREAM_CODEC =
            StreamCodec.of(
                    (RegistryFriendlyByteBuf buf, RequestArmorGraphPayload payload) ->
                            buf.writeVarInt(payload.hand().ordinal()),
                    (RegistryFriendlyByteBuf buf) -> new RequestArmorGraphPayload(
                            buf.readVarInt() == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(InteractionHand hand) {
        PacketDistributor.sendToServer(new RequestArmorGraphPayload(hand));
    }

    public static void handle(RequestArmorGraphPayload payload, ServerPlayer player) {
        var stack = player.getItemInHand(payload.hand());
        // не нагрудник — просто игнорируем: возможно, игрок успел переключиться
        if (!(stack.getItem() instanceof ModularChestplateItem)) {
            return;
        }
        ModularSchema schema = ModularChestplateItem.schemaOf(stack);
        PacketDistributor.sendToPlayer(player, new SyncArmorGraphPayload(
                payload.hand(), schema.isEmpty() ? new com.github.fireprime5.spellcraft
                        .spell.SpellGraph().save() : schema.save()));
        PacketDistributor.sendToPlayer(player, new OpenArmorBuilderPayload(payload.hand()));
    }
}