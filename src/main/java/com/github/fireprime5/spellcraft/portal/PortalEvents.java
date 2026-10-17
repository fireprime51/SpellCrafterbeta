package com.github.fireprime5.spellcraft.portal;

import com.github.fireprime5.spellcraft.Spellcraft;
import com.github.fireprime5.spellcraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Работа порталов: переход по входу и уборка истёкших.
 *
 * <p>Сам переход живёт в блоке ({@code PortalBlock#entityInside}), а сюда
 * вынесена только уборка истёкших порталов — раз в секунду по
 * {@code ServerTickEvent.Post}.
 *
 * <p>Почему уборка не на своём {@code tick()} блока: блок находится в своём
 * измерении, а реестр — в обычном мире, и истёкший портал двустороннего типа
 * надо снесть в ДРУГОМ измерении. Серверный тик видит оба.
 */
public final class PortalEvents {

    /** Как часто проверяется срок жизни порталов, в тиках. */
    private static final int SWEEP_INTERVAL_TICKS = 20;

    private PortalEvents() {
    }

    public static void register(net.neoforged.bus.api.IEventBus neoForgeBus) {
        neoForgeBus.addListener(PortalEvents::onServerTick);
    }

    /**
     * Раз в секунду снимает истёкшие порталы.
     *
     * <p>Пассивного ожидания нет: если бы портал сгорал только при входе, то
     * одиноко стоящий истёкший портал висел бы в мире вечно и при этом уже не
     * работал бы.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % SWEEP_INTERVAL_TICKS != 0) {
            return;
        }
        PortalRegistry registry = PortalRegistry.get(server);
        if (registry == null) {
            return;
        }
        List<PortalRegistry.Entry> expired = new ArrayList<>();
        long time = server.overworld().getGameTime();
        for (PortalRegistry.Entry entry : registry.all()) {
            if (entry.expired(time)) {
                expired.add(entry);
            }
        }
        for (PortalRegistry.Entry entry : expired) {
            registry.remove(entry.id());
            destroyEnds(server, entry);
        }
    }

    /** Сносит оба конца портала в их измерениях. */
    private static void destroyEnds(MinecraftServer server, PortalRegistry.Entry entry) {
        destroyEnd(server, entry.fromDim(), entry.from());
        if (entry.hasReturn()) {
            destroyEnd(server, entry.toDim(), entry.to());
        }
    }

    private static void destroyEnd(MinecraftServer server, String dim, BlockPos pos) {
        ResourceLocation key = ResourceLocation.tryParse(dim);
        if (key == null) {
            return;
        }
        ServerLevel level = server.getLevel(
                net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION, key));
        if (level == null) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.is(ModBlocks.PORTAL.get())) {
            // false: содержимое сгоревшего портала выбрасывать не нужно
            level.destroyBlock(pos, false);
        }
    }

    /** Ключ измерения строкой: реестр хранит адреса текстом. */
    public static String dimensionKey(ServerLevel level) {
        return level.dimension().location().toString();
    }
}