package com.github.fireprime5.spellcraft.armor;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Optional;

/**
 * Что щит и барьер делают, пока подняты.
 *
 * <p>Три вещи: погасить урон из-под защиты, отдать снаряд обратно и нарисовать
 * контур. Контур — частицами по кромке, а не заливкой: заливка в дугу съела бы
 * треть экрана, а игроку и нужно знать одно — где граница.
 *
 * <p>Всё это живёт на сервере и рисуется через {@code sendParticles}, как и весь
 * остальной мод: частицы в этом моде не отдельная сущность, а вызов с сервера.
 */
public final class ShieldEvents {

    /** Как часто перерисовывать контур. Чаще — заметно, реже — выглядит рваным. */
    private static final int OUTLINE_INTERVAL = 3;

    /** Сколько точек на кромке. */
    private static final int OUTLINE_POINTS = 26;

    /** Сколько тиков защита держится после последнего подъёма в броне. */
    private static final int ARMOR_GRACE = 12;

    /**
     * Потолок отражения.
     *
     * <p>Обмен уроном не должен быть способом накрутить себе же: без потолка
     * отражённый удар от удачного ответа равнялся бы удару и урон отражался бы
     * по кругу.
     */
    private static final float MAX_REFLECT = 20.0F;

    private ShieldEvents() {
    }

    public static void register(IEventBus bus) {
        bus.register(ShieldEvents.class);
    }

    /**
     * Урон из-под защиты.
     *
     * <p>Проверка стоит до всего остального: если защита держит удар, ни блок брони,
     * ни триггер «при уроне» работать не должны.
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isCanceled()) {
            return;
        }
        Shield.Active защита = Shield.active(player.getUUID(),
                player.level().getGameTime()).orElse(null);
        if (защита == null) {
            return;
        }
        DamageSource источник = event.getSource();

        if (защита.projectilesOnly() && !Shield.isProjectile(источник)) {
            // режим «только снаряды»: ближний удар защита не держит, и врать
            // игроку, что держала, нельзя — он ждёт отражения и не получает его
            return;
        }
        Vec3 откуда = Shield.originOf(источник);
        if (!Shield.blocks(защита.shape(), player.getEyePosition(),
                player.getLookAngle(), откуда)) {
            return;
        }

        float урон = event.getAmount();
        if (урон <= 0) {
            return;
        }

        // снаряд отбивается физически: он меняет хозяина и улетает обратно,
        // иначе стрела просто исчезнет и выглядит как ошибка
        if (источник.getDirectEntity() instanceof Projectile снаряд) {
            отбить(снаряд, player);
        } else {
            LivingEntity атакующий = Shield.attackerOf(источник);
            if (атакующий != null) {
                // отражение ближнего удара: столько же, сколько он сам прислал.
                // Ограничено тем же потолком, что и урон заклинаниями, иначе
                // отражение становится способом убить моба его же уроном
                атакующий.hurt(player.level().damageSources().playerAttack(player),
                        (float) Math.min(урон, MAX_REFLECT));
            }
        }
        event.setCanceled(true);
    }

    /** Отбить снаряд: хозяин меняется на защитника, направление — на противоположное. */
    private static void отбить(Projectile снаряд, ServerPlayer защитник) {
        снаряд.setOwner(защитник);
        Vec3 назад = снаряд.position().subtract(защитник.getEyePosition());
        if (назад.lengthSqr() < 1.0E-6) {
            назад = защитник.getLookAngle();
        }
        снаряд.setDeltaMovement(назад.normalize().scale(1.2)
                .add(0, назад.normalize().y * 0.35, 0));
        снаряд.hurtMarked = true;
    }

    /**
     * Тик игрока: рисуем кромку и поддерживаем защиту, поднятую бронёй.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        // Именно instanceof, а не слепой каст: событие тика приходит и на клиенте,
        // где игрок — LocalPlayer. Приведение падало на первом же кадре, и игра не
        // грузилась вовсе.
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        long now = player.level().getGameTime();
        Optional<Shield.Active> защита = Shield.active(player.getUUID(), now);
        if (защита.isEmpty()) {
            return;
        }
        if (now % OUTLINE_INTERVAL == 0) {
            нарисоватьКонтур(player, защита.get());
        }
    }

    /**
     * Контур частицами: только кромка, без заливки.
     *
     * <p>Точки идут по дуге или по поясу сферы — то, что игрок видит как границу
     * защищённой области. Заливка внутри сделала бы щит непрозрачным ровно настолько,
     * насколько велик радиус, а кромка читается с одного взгляда.
     */
    private static void нарисоватьКонтур(ServerPlayer player, Shield.Active защита) {
        if (!(player.level() instanceof ServerLevel уровень)) {
            return;
        }
        Vec3 центр = player.getEyePosition();
        double радиус = защита.shape().radius();
        Vec3 взгляд = player.getLookAngle();

        // вектор вбок от взгляда, строго горизонтальный: иначе дуга встала бы
        // вокруг произвольной мировой оси и игрок не понял бы, что она перед ним
        Vec3 вбок = new Vec3(-взгляд.z, 0, взгляд.x);
        вбок = вбок.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : вбок.normalize();

        for (int i = 0; i < OUTLINE_POINTS; i++) {
            double доля = (double) i / OUTLINE_POINTS;
            Vec3 точка;
            if (защита.shape() == Shield.Shape.SPHERE) {
                // кольцо вокруг игрока: весь круг, барьер держит урон отовсюду
                double угол = доля * Math.PI * 2;
                точка = центр.add(взгляд.scale(Math.cos(угол) * радиус))
                        .add(вбок.scale(Math.sin(угол) * радиус));
            } else {
                // дуга перед взглядом, шириной ровно в сектор защиты
                double угол = (доля - 0.5) * 2 * Shield.HALF_ANGLE;
                точка = центр.add(взгляд.scale(Math.cos(угол) * радиус))
                        .add(вбок.scale(Math.sin(угол) * радиус));
            }
            уровень.sendParticles(ParticleTypes.ENCHANT,
                    точка.x, точка.y, точка.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Выход и смерть — защита не должна пережить игрока на сервере. */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Shield.clear(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.Clone event) {
        Shield.clear(event.getEntity().getUUID());
    }

    /**
     * Поддержка защиты, поднятой бронёй.
     *
     * <p>Вызывается из тика брони, пока нагрудник работает: срок каждый раз
     * продлевается, а по снятию брони {@link #clearExpiring} снимает её через
     * несколько тиков — модуль не должен висеть после того, как игрок его снял.
     */
    public static void refreshFromArmor(ServerPlayer player, Shield.Shape shape,
                                        boolean projectilesOnly) {
        Shield.raiseWhileEquipped(new Shield.PlayerView(player.getUUID(),
                player.getEyePosition(), player.getLookAngle()),
                shape, projectilesOnly, player.level().getGameTime());
    }

    /** Снять защиту, если её не продлили за последние {@link #ARMOR_GRACE} тиков. */
    public static void clearExpiring(ServerPlayer player, long lastRaisedTick) {
        long now = player.level().getGameTime();
        if (now - lastRaisedTick <= ARMOR_GRACE) {
            return;
        }
        Shield.Active активная = Shield.active(player.getUUID(), now).orElse(null);
        if (активная != null && активная.until() > now) {
            Shield.clear(player.getUUID());
        }
    }
}