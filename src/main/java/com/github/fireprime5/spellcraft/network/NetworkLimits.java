package com.github.fireprime5.spellcraft.network;

/** Bounds for attacker-controlled packet fields (independent of graph node policy). */
final class NetworkLimits {
    static final int MAX_GRAPH_BYTES = 2 * 1024 * 1024;
    static final int MAX_KIND_LENGTH = 32;

    private NetworkLimits() {
    }
}
