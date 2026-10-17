package com.github.fireprime5.spellcraft.network;

import com.github.fireprime5.spellcraft.spell.SpellGraph;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Клиент -> Сервер: сохранить схему модулей нагрудника.
 *
 * <p>Схема кладётся прямо в стек на сервере, а не в отдельное хранилище
 * игрока. Иначе нагрудник, снятый и переданный другому игроку, принёс бы чужие
 * модули с собой — а это ровно то, чего броня делать не должна.
 */
public record SaveArmorGraphPayload(InteractionHand hand, SpellGraph graph)
        implements CustomPacketPayload {

    public static final Type<SaveArmorGraphPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("spellcraft", "save_armor_graph"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveArmorGraphPayload> STREAM_CODEC =
            StreamCodec.of(SaveArmorGraphPayload::write, SaveArmorGraphPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, SaveArmorGraphPayload payload) {
        buf.writeVarInt(payload.hand().ordinal());
        buf.writeNbt(payload.graph.save());
    }

    private static SaveArmorGraphPayload read(RegistryFriendlyByteBuf buf) {
        if (buf.readableBytes() > NetworkLimits.MAX_GRAPH_BYTES + 5) {
            throw new IllegalArgumentException("armor graph payload is too large");
        }
        int handId = buf.readVarInt();
        if (handId < 0 || handId > 1) {
            throw new IllegalArgumentException("invalid interaction hand");
        }
        var hand = handId == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        var tag = buf.readNbt();
        return new SaveArmorGraphPayload(hand, tag == null ? new SpellGraph() : SpellGraph.load(tag));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(InteractionHand hand, SpellGraph graph) {
        PacketDistributor.sendToServer(new SaveArmorGraphPayload(hand, graph));
    }

    public static void handle(SaveArmorGraphPayload payload, ServerPlayer player) {
        var stack = player.getItemInHand(payload.hand());
        if (!(stack.getItem()
                instanceof com.github.fireprime5.spellcraft.armor.ModularChestplateItem)) {
            return;
        }
        var graph = payload.graph().copy();
        graph.sanitizeModifiers(player);
        // Validate the contextual armor policy before putting anything in the item.
        // Invalid submissions consistently clear the existing schema.
        var schema = graph.validateForSave(player,
                com.github.fireprime5.spellcraft.armor.ModularSchema.POLICY).isEmpty()
                ? com.github.fireprime5.spellcraft.armor.ModularSchema.with(graph)
                : com.github.fireprime5.spellcraft.armor.ModularSchema.EMPTY;
        if (schema.isEmpty()) {
            com.github.fireprime5.spellcraft.armor.ModularChestplateItem.setSchema(stack,
                    com.github.fireprime5.spellcraft.armor.ModularSchema.EMPTY);
            return;
        }
        com.github.fireprime5.spellcraft.armor.ModularChestplateItem.setSchema(stack, schema);
    }
}
