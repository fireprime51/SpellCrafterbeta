package com.github.fireprime5.spellcraft.spell;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Замороженные сущности: кто стоит на месте и почему.
 *
 * <p>Заморозка — не эффект зелья, а принудительное обнуление движения каждый
 * тик. Зельем её не сделать: слизость и прыгучесть — это модификаторы, а моби
 * двигается всё равно. Приходится брать движение под контроль напрямую.
 *
 * <p>Живучесть держится в карте, а не в данных сущности, потому что срок
 * кончается сам и повторно накладываться должен свободно — как у щита.
 */
public final class Frozen {

    /** Толчок, который снимается заморозкой, тиков. */
    private static final int SHAKE_SLACK = 12;

    /** Когда сущность перестаёт быть замороженной. */
    private static final Map<UUID, Long> UNTIL = new ConcurrentHashMap<>();

    private Frozen() {
    }

    /** Заморозить до этого тика. */
    public static void freeze(UUID id, long until) {
        UNTIL.put(id, until);
    }

    /** Действует ли заморозка на сущность прямо сейчас. */
    public static boolean frozen(Entity entity, long now) {
        return entity != null && frozen(entity.getUUID(), now);
    }

    /**
     * Ядро проверки — по идентификатору.
     *
     * <p>Отдельная перегрузка не для красоты: {@code Entity} в тестах не создаётся,
     * а всё решение принимается по идентификатору и тику. Без неё проверка срока
     * жила бы только внутри игрового тика, то есть её было бы нечем проверить.
     */
    public static boolean frozen(UUID id, long now) {
        Long срок = UNTIL.get(id);
        if (срок == null) {
            return false;
        }
        if (now > срок) {
            UNTIL.remove(id, срок);
            return false;
        }
        return true;
    }

    /**
     * Держать сущность на месте — вызывается каждый тик для всех замороженных.
     *
     * <p>Движение гасится целиком, включая падение: замороженный моб не должен
     * переместиться и от удара, иначе «заморозка» окажется просто замедлением.
     * Верхушка слегка подпрыгивает, иначе живность стояла бы мёртвой стеной и
     * игрок не понял бы, что она работает.
     */
    public static void hold(Entity entity, long now) {
        if (!frozen(entity, now)) {
            return;
        }
        entity.setDeltaMovement(Vec3.ZERO);
        entity.hasImpulse = true;
        if (entity instanceof LivingEntity живой) {
            живой.setDeltaMovement(Vec3.ZERO);
            // толчок после заморозки должен пройти: иначе перезаморозить
            // замороженную сущность нельзя было бы сдвинуть с места вообще
            живой.hurtMarked = true;
        }
    }

    /** Снять заморозку досрочно. */
    public static void thaw(UUID id) {
        UNTIL.remove(id);
    }

    /** Только для тестов. */
    public static void reset() {
        UNTIL.clear();
    }
}