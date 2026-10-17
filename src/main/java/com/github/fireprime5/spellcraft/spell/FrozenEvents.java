package com.github.fireprime5.spellcraft.spell;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Держит замороженные сущности на месте.
 *
 * <p>Гасить движение один раз при наложении нельзя: через тик моб снова
 * набирает скорость и ползёт. Поэтому заморозка это не «эффект», а требование,
 * выполняемое каждый тик, пока держит срок.
 *
 * <p>Порядок нужен именно такой: сначала гасим движение, потом пересчитываем
 * физику. Иначе тик движка успевает применить скорость и сущность за тик
 * съезжает на шаг в сторону.
 */
public final class FrozenEvents {

    private FrozenEvents() {
    }

    public static void register(IEventBus bus) {
        bus.register(FrozenEvents.class);
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        Frozen.hold(event.getEntity(), event.getEntity().level().getGameTime());
    }

    /**
     * Ушедшая с уровня сущность больше не появится, и её запись в карте срока
     * была бы вечной. Сама по себе она безобидна, но карта растёт, а проверить
     * её некому.
     */
    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        Frozen.thaw(event.getEntity().getUUID());
    }
}