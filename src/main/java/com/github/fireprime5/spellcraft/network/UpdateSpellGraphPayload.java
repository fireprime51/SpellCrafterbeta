package com.github.fireprime5.spellcraft.network;

import com.github.fireprime5.spellcraft.Spellcraft;
import com.github.fireprime5.spellcraft.data.PlayerSpellStore;
import com.github.fireprime5.spellcraft.spell.SpellGraph;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Клиент -> Сервер: сохранить/обновить граф заклинания.
 */
public record UpdateSpellGraphPayload(SpellGraph graph) implements CustomPacketPayload {

    public static final Type<UpdateSpellGraphPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("spellcraft", "update_spell_graph"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateSpellGraphPayload> STREAM_CODEC =
            StreamCodec.of(UpdateSpellGraphPayload::write, UpdateSpellGraphPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, UpdateSpellGraphPayload payload) {
        buf.writeNbt(payload.graph.save());
    }

    private static UpdateSpellGraphPayload read(RegistryFriendlyByteBuf buf) {
        if (buf.readableBytes() > NetworkLimits.MAX_GRAPH_BYTES) {
            throw new IllegalArgumentException("spell graph payload is too large");
        }
        var tag = buf.readNbt();
        return new UpdateSpellGraphPayload(tag == null ? new SpellGraph() : SpellGraph.load(tag));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UpdateSpellGraphPayload payload, ServerPlayer player) {
        // граф приходит от клиента: обрезаем по размеру и чистим битые связи
        SpellGraph graph = payload.graph();
        if (!graph.nodes.isEmpty() && !rateLimit().allow(player.getGameProfile().getId())) {
            Spellcraft.LOGGER.warn("Player {} spammed spell graph updates, dropping one",
                    player.getName().getString());
            return;
        }
        graph.dropBrokenConnections();
        // сохраняем копию, чтобы клиентский объект не был общим с серверным
        graph = graph.copy();
        // Ordinary spells deliberately have no gameplay node cap, but unknown
        // modules, malformed connections and cycles must not enter server state.
        if (!graph.validateForSave(player,
                com.github.fireprime5.spellcraft.spell.validation.SchemaPolicy.SPELL).isEmpty()) {
            return;
        }
        // выкидываем модификаторы, которые игрок не изучал или которые конфликтуют
        if (graph.sanitizeModifiers(player)) {
            SyncSpellGraphPayload.sendTo(player, graph);
        }
        PlayerSpellStore.set(player, graph);
    }

    /**
     * Сколько раз в секунду игрок может прислать граф.
     *
     * <p>Раньше проверка была {@code nodes.size() > MAX_NODES}, но она была
     * мёртвой: {@link SpellGraph#load} уже обрезал список, так что условие
     * не срабатывало никогда. Реальной защиты не было — пакет с большим
     * графом принимался сколько угодно раз в секунду, и каждый раз
     * пересобирал вложение игрока.
     *
     * <p>Правка схемы шлётся на каждое движение мыши с нажатой кнопкой, так
     * что 10 пакетов в секунду — с запасом для живого редактирования.
     */
    private static final PacketRateLimiter RATE_LIMIT = new PacketRateLimiter(10);

    public static void cleanup(UUID playerId) {
        RATE_LIMIT.cleanup(playerId);
    }

    private static PacketRateLimiter rateLimit() {
        return RATE_LIMIT;
    }
}
