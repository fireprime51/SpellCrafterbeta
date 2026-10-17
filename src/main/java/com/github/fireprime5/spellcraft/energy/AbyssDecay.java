package com.github.fireprime5.spellcraft.energy;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * Что происходит с игроком, у которого кончилась энергия бездны.
 *
 * <p>Энергия бездны — не просто ресурс заклинаний, а то, чем игрок держится за мир.
 * Нулевая энергия бездны означает, что он больше не принадлежит этому измерению, и тело
 * начинает рассыпаться: накладываются слабость и голод, а через пять минут
 * непрерывной пустоты игрок умирает.
 *
 * <p>Счётчик идёт только пока энергия бездны равна ровно нулю. Получил единицу — таймер
 * сбрасывается сразу, потому что «устал, но выбился» и «умирает» должны быть
 * разными состояниями, а не одной шкалой с разными значениями.
 *
 * <p>Смерть здесь не наказание и не способ умереть от голода обычным путём: она
 * единственная, что отличает пустую энергию бездны от любой другой нехватки ресурсов.
 * Поэтому урон от слабости здесь смягчён — иначе игрок умирал бы на второй
 * минуте от падения, а не через пять минут по замыслу.
 */
public final class AbyssDecay {

    /** Тиков до смерти при непрерывной нулевой энергии бездны: пять минут. */
    public static final int TICKS_TO_DEATH = 5 * 60 * 20;

    /** Ключ счётчика в persistentData игрока. */
    private static final String KEY = "spellcraft_abyss_decay";

    /** Сколько тиков держатся наложенные эффекты. */
    private static final int EFFECT_DURATION = 40;

    /**
     * Сила слабости.
     *
     * <p>Уровень 0 — «слегка слабый»: −4% урона в ближнем бою. Достаточно,
     * чтобы почувствовать, и недостаточно, чтобы убить за две минуты.
     */
    private static final int WEAKNESS_AMPLIFIER = 0;

    /** Голод: уровень 1 замедляет трату сытости вдвое. */
    private static final int HUNGER_AMPLIFIER = 1;

    private AbyssDecay() {
    }

    /**
     * Тик игрока: проверяет пустую энергию бездны и ведёт таймер.
     *
     * <p>Бесконечная энергия бездны режимом отладки считается полной: иначе отладка схем
     * стоила бы пяти минут жизни.
     */
    public static void tick(Player player) {
        if (player.level().isClientSide || player.isSpectator()) {
            return;
        }
        // именно пул, а не кошелёк: у игрока с сосудом кошелёк может быть
        // пустым, а энергия бездны — лежать рядом. Считать по кошельку значило бы убить
        // игрока, который стоит у полного сосуда
        if (AbyssEnergy.isInfinite(player) || AbyssPool.total(player) > 0) {
            reset(player);
            return;
        }

        int elapsed = nextTick(player);
        applyEffects(player);

        if (elapsed >= TICKS_TO_DEATH) {
            kill(player);
        }
    }

    /** Счётчик тиков нулевой энергии бездны. */
    private static int nextTick(Player player) {
        int elapsed = getTicks(player) + 1;
        setTicks(player, elapsed);
        return elapsed;
    }

    /**
     * Накладывает слабость и голод.
     *
     * <p>Эффекты накладываются на 40 тиков и продлеваются каждым тиком, а не
     * ставятся с большой длительностью: так они исчезают сами, стоит игроку
     * добыть одну единицу энергии бездны. Постоянный эффект пришлось бы снимать вручную,
     * и игрок уходил бы со смертельным голодом, забыв про него.
     */
    private static void applyEffects(Player player) {
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EFFECT_DURATION,
                WEAKNESS_AMPLIFIER, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.HUNGER, EFFECT_DURATION,
                HUNGER_AMPLIFIER, false, false, true));
    }

    /**
     * Смерть от пустоты.
     *
     * <p>{@code kill()} вместо {@code hurt}: урон шёл бы через защиту, броню и
     * PvP-правила, и игрок с полным зачаром мог бы не умереть вовсе — тогда как
     * замысел именно в том, что нулевая энергия бездны убивает гарантированно.
     */
    private static void kill(Player player) {
        player.kill();
    }

    /** Сбрасывает таймер: энергия бездны появилась или включён бесконечный режим. */
    public static void reset(Player player) {
        if (player.getPersistentData().contains(KEY)) {
            player.getPersistentData().remove(KEY);
        }
    }

    /** Сколько тиков игрок уже провёл без энергии бездны. */
    public static int getTicks(Player player) {
        return player.getPersistentData().getInt(KEY);
    }

    private static void setTicks(Player player, int value) {
        player.getPersistentData().putInt(KEY, Math.clamp(value, 0, TICKS_TO_DEATH));
    }
}
