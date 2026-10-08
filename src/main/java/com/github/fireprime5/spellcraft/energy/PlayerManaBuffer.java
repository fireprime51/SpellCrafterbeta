package com.github.fireprime5.spellcraft.energy;

import net.minecraft.world.entity.player.Player;

/**
 * Мост между кошельком игрока (старая мана) и новым интерфейсом энергии.
 *
 * <p>Так генератор воздуха или станция могут отдавать игроку энергию, а
 * модуль «Извлечение» — забирать её из робота, не затрагивая остальной код.
 */
public final class PlayerManaBuffer implements IEnergyBuffer {

    private final Player player;

    public PlayerManaBuffer(Player player) {
        this.player = player;
    }

    public static PlayerManaBuffer of(Player player) {
        return new PlayerManaBuffer(player);
    }

    @Override
    public int capacity() {
        return ManaCapability.capacity();
    }

    @Override
    public int stored() {
        return ManaCapability.getMana(player);
    }

    @Override
    public int setStored(int amount) {
        ManaCapability.setMana(player, amount);
        return stored();
    }

    @Override
    public int receive(int amount) {
        int before = stored();
        ManaCapability.addMana(player, amount);
        return stored() - before;
    }

    @Override
    public int extract(int amount) {
        int taken = Math.min(amount, stored());
        if (taken > 0) {
            ManaCapability.consumeMana(player, taken);
        }
        return taken;
    }
}