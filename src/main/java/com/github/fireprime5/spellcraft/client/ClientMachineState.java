package com.github.fireprime5.spellcraft.client;

import com.github.fireprime5.spellcraft.spell.SpellGraph;
import net.minecraft.core.BlockPos;

/**
 * Сетка машины, ждущая правки на клиенте.
 *
 * <p>Отдельное состояние от заклинания и от брони: три схемы живут в трёх
 * разных местах, и общая переменная для них означала бы, что правка одной
 * затирает другую.
 */
public final class ClientMachineState {

    private static BlockPos pos;
    private static String kind;
    private static SpellGraph graph = new SpellGraph();

    private ClientMachineState() {
    }

    public static void set(BlockPos newPos, String newKind, SpellGraph newGraph) {
        pos = newPos;
        kind = newKind;
        graph = newGraph != null ? newGraph : new SpellGraph();
    }

    public static BlockPos pos() {
        return pos;
    }

    public static String kind() {
        return kind;
    }

    public static SpellGraph graph() {
        return graph;
    }

    public static boolean has(BlockPos target) {
        return pos != null && pos.equals(target);
    }

    public static void clear() {
        pos = null;
        kind = null;
        graph = new SpellGraph();
    }
}