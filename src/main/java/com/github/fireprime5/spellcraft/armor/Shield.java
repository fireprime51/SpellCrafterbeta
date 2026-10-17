package com.github.fireprime5.spellcraft.armor;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Щит и барьер: защита, которую игрок поднимает вокруг себя.
 *
 * <p>Оба работают одинаково — просто разной формы. Щит вырастает дугой там, куда
 * игрок смотрит, и держит удар только спереди, как настоящий щит: если атакующий
 * стоит за спиной, щит не поможет. Барьер вырастает сферой вокруг игрока и
 * держит удар отовсюду.
 *
 * <p>Геометрия вынесена в статические методы без единого обращения к миру: её
 * можно проверить обычным тестом, тогда как {@code ServerLevel} в тестах не
 * создаётся.
 *
 * <p>Отдельно важно: у урона от заклинаний этого мода в источнике <b>нет
 * позиции</b>. {@code damage} бьёт через {@code damageSources().playerAttack},
 * где записано только «кто ударил». Поэтому «откуда прилетело» определяется по
 * позиции атакующей сущности, а не по источнику урона.
 */
public final class Shield {

    /** Радиус щита перед игроком. */
    public static final double SHIELD_RADIUS = 2.5;

    /** Радиус барьера вокруг игрока. */
    public static final double BARRIER_RADIUS = 3.5;

    /** Половина сектора щита: 60° в каждую сторону, всего 120°. */
    public static final double HALF_ANGLE = Math.toRadians(60);

    /** Сколько держится щит по умолчанию, в тиках. */
    public static final int SHIELD_TICKS = 200;

    /** Сколько держится барьер по умолчанию, в тиках. */
    public static final int BARRIER_TICKS = 300;

    /** Форма защиты. */
    public enum Shape {
        /** Дуга перед взглядом. */
        ARC,
        /** Сфера вокруг игрока. */
        SPHERE;

        public double radius() {
            return this == ARC ? SHIELD_RADIUS : BARRIER_RADIUS;
        }

        public int defaultTicks() {
            return this == ARC ? SHIELD_TICKS : BARRIER_TICKS;
        }
    }

    /**
     * Поднятая защита одного игрока.
     *
     * @param until тик, до которого защита держится; {@link Long#MAX_VALUE} —
     *              пока не снимешь, то есть режим брони
     * @param projectilesOnly только снаряды: ближние удары проходят
     */
    public record Active(Shape shape, long until, boolean projectilesOnly) {

        /** Жив ли щит в этот тик. */
        public boolean aliveAt(long now) {
            return now <= until;
        }
    }

    private static final Map<UUID, Active> ACTIVE = new ConcurrentHashMap<>();

    private Shield() {
    }

    // ------------------------------------------------------------- состояние

    /** Поднять защиту. Повторный вызов продлевает, а не заменяет: барьер в броне
     *  вызывается каждый тик и обязан обновлять время, а не сбрасывать его. */
    public static void raise(PlayerView view, Shape shape, long ticks, boolean projectilesOnly,
                            long now) {
        long until = ticks <= 0 ? now : now + ticks;
        ACTIVE.put(view.id(), new Active(shape, until, projectilesOnly));
    }

    /** Поднять защиту до бесконечности — режим брони. */
    public static void raiseWhileEquipped(PlayerView view, Shape shape,
                                          boolean projectilesOnly, long now) {
        ACTIVE.put(view.id(), new Active(shape, Long.MAX_VALUE, projectilesOnly));
    }

    /** Защита игрока в этот тик или пусто, если её нет. */
    public static Optional<Active> active(UUID id, long now) {
        Active активная = ACTIVE.get(id);
        if (активная == null) {
            return Optional.empty();
        }
        if (!активная.aliveAt(now)) {
            ACTIVE.remove(id, активная);
            return Optional.empty();
        }
        return Optional.of(активная);
    }

    public static void clear(UUID id) {
        ACTIVE.remove(id);
    }

    /** Только для тестов. */
    public static void reset() {
        ACTIVE.clear();
    }

    // ------------------------------------------------------------- геометрия

    /**
     * Держит ли защита урон, пришедший из этой точки.
     *
     * @param eye откуда смотрит игрок — центр защиты берётся от глаз, а не от ног:
     *            иначе цель над головой или под ногами оказывалась бы вне щита
     * @param look куда игрок смотрит
     * @param origin откуда пришёл удар; {@code null} — неизвестно, и защита
     *               молчит: гадать хуже, чем пропустить
     */
    public static boolean blocks(Shape shape, Vec3 eye, Vec3 look, Vec3 origin) {
        if (origin == null) {
            return false;
        }
        Vec3 отГлаз = origin.subtract(eye);
        double distance = отГлаз.length();
        if (distance > shape.radius()) {
            return false;
        }
        if (shape == Shape.SPHERE) {
            return true;
        }
        // ровно в глаза: направление не определено, но удар в упор защита держать
        // обязана — иначе подойти вплотную значит выбить щит
        if (distance < 1.0E-4) {
            return true;
        }
        return отГлаз.normalize().dot(look.normalize()) >= Math.cos(HALF_ANGLE);
    }

    /**
     * Откуда пришёл удар, или {@code null}, если определить нечем.
     *
     * <p>Сначала сам снаряд: у стрелы есть позиция, и по ней видно, с какой
     * стороны она прилетела. Иначе — атакующая сущность. Иначе — никак: у
     * урона от заклинаний позиции нет, и «щит спереди» про такую атаку ничего
     * не скажет.
     */
    public static Vec3 originOf(net.minecraft.world.damagesource.DamageSource source) {
        if (source == null) {
            return null;
        }
        Entity прямое = source.getDirectEntity();
        if (прямое instanceof Projectile снаряд) {
            return снаряд.position();
        }
        Entity виновник = source.getEntity();
        if (виновник != null) {
            return виновник.position();
        }
        return null;
    }

    /** Снаряд ли это — такие отбиваются физически, а не просто гасятся. */
    public static boolean isProjectile(net.minecraft.world.damagesource.DamageSource source) {
        return source != null && source.getDirectEntity() instanceof Projectile;
    }

    /** Кому возвращать удар при отражении. */
    public static LivingEntity attackerOf(net.minecraft.world.damagesource.DamageSource source) {
        if (source == null) {
            return null;
        }
        return source.getEntity() instanceof LivingEntity живой ? живой : null;
    }

    /** Минимальный набор данных об игроке: идентификатор и точка, от которой он смотрит. */
    public record PlayerView(UUID id, Vec3 eye, Vec3 look) {
    }
}