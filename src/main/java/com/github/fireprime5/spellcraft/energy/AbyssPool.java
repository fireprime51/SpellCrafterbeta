package com.github.fireprime5.spellcraft.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Пул энергии бездны игрока: кошелёк плюс сосуды рядом.
 *
 * <p>Сосуды в радиусе {@link #RADIUS} суммируются и работают как один кошелёк:
 * игрок тратит энергию бездны из сосудов, когда своего не хватает, и излишки добычи
 * падают в сосуд, а не пропадают.
 *
 * <p>Порядок один и тот же в обе стороны — сначала кошелёк, потом сосуды.
 * Он выбран один раз и используется везде, иначе «потратить энергию бездны» и «добыть
 * энергию бездны» считали бы по-разному, и игрок увидел бы, что каст съел больше, чем
 * показал полоской.
 *
 * <p>Сосудов рядом ищут перебором куба. Это дешевле, чем поддерживать
 * индексы сосудов на игрока: сосудов рядом единицы, а чанк — нет.
 */
public final class AbyssPool {

    /**
     * Радиус связи сосуда с игроком.
     *
     * <p>Четыре блока — это один-два шага от сосуда. Ближе нельзя: игрок
     * вынужден стоять вплотную и энергии бездныт с соседом. Дальше — сосуд начинает
     * кормить через полкомнаты, и «пришёл к фабрике» перестаёт что-то значить.
     */
    public static final double RADIUS = 4.0;

    private AbyssPool() {
    }

    /**
     * Сосуды, доступные игроку.
     *
     * <p>Пустой список на клиенте и у существ без уровня: пул считается только
     * на сервере, где сосуды и есть.
     */
    public static List<AbyssVesselBlock.VesselBlockEntity> vessels(Player player) {
        List<AbyssVesselBlock.VesselBlockEntity> found = new ArrayList<>(4);
        Level level = player.level();
        if (level.isClientSide) {
            return found;
        }
        AABB box = player.getBoundingBox().inflate(RADIUS);
        int minX = (int) Math.floor(box.minX);
        int minY = (int) Math.floor(box.minY);
        int minZ = (int) Math.floor(box.minZ);
        int maxX = (int) Math.floor(box.maxX);
        int maxY = (int) Math.floor(box.maxY);
        int maxZ = (int) Math.floor(box.maxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (level.getBlockEntity(new BlockPos(x, y, z))
                            instanceof AbyssVesselBlock.VesselBlockEntity vessel) {
                        found.add(vessel);
                    }
                }
            }
        }
        return found;
    }

    /** Дополнительная ёмкость от сосудов рядом. */
    public static int extraCapacity(Player player) {
        int extra = 0;
        for (AbyssVesselBlock.VesselBlockEntity vessel : vessels(player)) {
            extra += AbyssVesselBlock.CAPACITY;
        }
        return extra;
    }

    /** Сколько энергии бездны есть суммарно: кошелёк плюс сосуды. */
    public static int total(Player player) {
        int total = AbyssEnergy.get(player);
        for (AbyssVesselBlock.VesselBlockEntity vessel : vessels(player)) {
            total += vessel.getAbyss();
        }
        return total;
    }

    /** Полная ёмкость игрока: свой кошелёк плюс сосуды рядом. */
    public static int capacity(Player player) {
        return AbyssEnergy.capacity() + extraCapacity(player);
    }

    /**
     * Добавляет энергию бездны: сначала в кошелёк, излишки — в сосуды.
     *
     * @return сколько реально удалось положить
     */
    public static int add(Player player, int amount) {
        if (amount <= 0) {
            return 0;
        }
        // Свободное место в кошельке меряется по ёмкости САМОГО кошелька, а не
        // по ёмкости пула. Это и была причина, по которой сосуд не наполнялся
        // никогда: при одном сосуде ёмкость пула равна 4000, кошелёк полон при
        // 1000, и «свободного места» выходило 3000. Энергию бездны уходило в кошелёк,
        // кошелёк её зажимал и отдавал ноль, а остаток считался от запланированного
        // «одна единица» — то есть ноль. До сосуда дело не доходило никогда.
        int walletCapacity = AbyssEnergy.capacity();
        int roomInWallet = Math.max(0, walletCapacity - AbyssEnergy.get(player));
        int toWallet = Math.min(roomInWallet, amount);
        int added = toWallet > 0 ? AbyssEnergy.addToWallet(player, toWallet) : 0;

        // остаток считаем от ФАКТИЧЕСКОГО добавления, а не от намерения: если
        // кошелёк обрежет запись, излишек должен уйти в сосуд, а не потеряться
        int rest = amount - added;
        for (AbyssVesselBlock.VesselBlockEntity vessel : vessels(player)) {
            if (rest <= 0) {
                break;
            }
            rest -= vessel.add(rest);
        }
        return added + (amount - rest);
    }

    /**
     * Забирает энергию бездны: сначала из кошелька, потом из сосудов.
     *
     * @return сколько реально удалось снять
     */
    public static int take(Player player, int amount) {
        if (amount <= 0) {
            return 0;
        }
        int taken = 0;
        int inWallet = AbyssEnergy.get(player);
        int fromWallet = Math.min(inWallet, amount);
        if (fromWallet > 0) {
            AbyssEnergy.set(player, inWallet - fromWallet);
            taken += fromWallet;
        }
        int rest = amount - fromWallet;
        for (AbyssVesselBlock.VesselBlockEntity vessel : vessels(player)) {
            if (rest <= 0) {
                break;
            }
            rest -= vessel.take(rest);
        }
        return taken + (amount - fromWallet - rest);
    }

    /** Хватает ли энергии бездны с учётом сосудов. */
    public static boolean has(Player player, int amount) {
        if (amount <= 0) {
            return true;
        }
        return total(player) >= amount;
    }

    /**
     * Есть ли куда складывать энергию бездны: кошелёк полон и все сосуды полны.
     *
     * <p>Кошелёк сравнивается со своей ёмкостью, а не с ёмкостью пула. Со
     * сравнением по пулу при наличии любого сосуда «полно» не наступало никогда,
     * и извлекатель продолжал работать впустую.
     */
    public static boolean isFull(Player player) {
        if (AbyssEnergy.get(player) < AbyssEnergy.capacity()) {
            return false;
        }
        for (AbyssVesselBlock.VesselBlockEntity vessel : vessels(player)) {
            if (vessel.hasRoom()) {
                return false;
            }
        }
        return true;
    }
}