package com.github.fireprime5.spellcraft.network;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * Доступ к сетке машины с проверкой прав.
 *
 * <p>Единственное место, где сервер решает, можно ли игроку трогать эту машину.
 * И запрос, и сохранение идут через него — иначе правила разъедутся: запрос
 * проверяет расстояние, а сохранение проходит мимо.
 */
public final class MachineAccess {

    /** Максимум, на котором игрок вообще может открыть панель машины. */
    public static final double MAX_DISTANCE = 8.0;

    private MachineAccess() {
    }

    /**
     * Сетка машины, если блок её действительно имеет и игрок рядом.
     *
     * @return null — нельзя: далеко, не та машина или чужой тип
     */
    public static com.github.fireprime5.spellcraft.machine.MachineGrid gridAt(
            ServerPlayer player, BlockPos pos, String kind) {
        if (!isNear(player, pos) || !isKnownKind(kind) || !player.level().hasChunkAt(pos)) {
            return null;
        }
        if ("smelter".equals(kind)
                && player.level().getBlockEntity(pos)
                instanceof com.github.fireprime5.spellcraft.energy.SmelterBlock.SmelterBlockEntity
                smelter) {
            return smelter.grid();
        }
        return null;
    }

    /** Сохраняет схему в машину. */
    public static boolean save(ServerPlayer player, BlockPos pos, String kind,
                               com.github.fireprime5.spellcraft.spell.SpellGraph graph) {
        var grid = gridAt(player, pos, kind);
        if (grid == null) {
            return false;
        }
        // load() внутри чистит неразрешённые модули и рвёт повисшие связи,
        // поэтому схема с клиента попадает в блок уже безопасной
        grid.load(graph.save());
        return true;
    }

    private static boolean isNear(ServerPlayer player, BlockPos pos) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                <= MAX_DISTANCE * MAX_DISTANCE;
    }

    private static boolean isKnownKind(String kind) {
        // Keep the wire value an explicit protocol enum; do not let the
        // machine policy's default branch turn arbitrary strings into tanks.
        return "smelter".equals(kind);
    }
}
