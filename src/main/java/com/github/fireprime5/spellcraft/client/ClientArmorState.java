package com.github.fireprime5.spellcraft.client;

import com.github.fireprime5.spellcraft.spell.SpellGraph;
import net.minecraft.world.InteractionHand;

/**
 * Схема брони, ждущая правки на клиенте.
 *
 * <p>Отдельное состояние, а не общее с заклинанием: иначе конструктор брони
 * открылся бы поверх заклинания игрока и записал модули не туда.
 */
public final class ClientArmorState {

    private static InteractionHand hand;
    private static SpellGraph graph = new SpellGraph();

    private ClientArmorState() {
    }

    public static void set(InteractionHand newHand, SpellGraph newGraph) {
        hand = newHand;
        graph = newGraph != null ? newGraph : new SpellGraph();
    }

    public static InteractionHand hand() {
        return hand;
    }

    public static SpellGraph graph() {
        return graph;
    }

    public static void clear() {
        hand = null;
        graph = new SpellGraph();
    }
}
