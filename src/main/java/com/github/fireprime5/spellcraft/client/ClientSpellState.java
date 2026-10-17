package com.github.fireprime5.spellcraft.client;

import com.github.fireprime5.spellcraft.network.SpellResultPayload;
import com.github.fireprime5.spellcraft.spell.SpellGraph;

/**
 * Клиентское зеркало состояния заклинаний: текущий граф, результат каста, энергия бездны.
 * Класс не содержит клиентских зависимостей, поэтому его безопасно читать
 * из общих классов (например, из тикера для синхронизации энергии бездны).
 */
public final class ClientSpellState {

    private static SpellGraph graph;
    private static SpellResultPayload lastResult;
    private static int abyss;
    private static int maxAbyss;
    /** Бесконечная энергия бездны включена: панель рисует «∞» вместо числа. */
    private static boolean infiniteAbyss;
    private static java.util.List<String> known = java.util.List.of();
    private static java.util.List<String> knownMods = java.util.List.of();
    private static int total;
    private static java.util.List<String> libraryNames = java.util.List.of();
    private static java.util.List<Integer> librarySizes = java.util.List.of();
    /** Индексы модулей, на которых произошла ошибка в последнем касте. */
    private static java.util.Set<Integer> errorNodes = java.util.Set.of();

    private ClientSpellState() {
    }

    public static SpellGraph getGraph() {
        return graph;
    }

    public static void setGraph(SpellGraph g) {
        graph = g;
    }

    public static SpellResultPayload getLastResult() {
        return lastResult;
    }

    public static void setLastResult(SpellResultPayload result) {
        lastResult = result;
    }

    public static int getAbyss() {
        return abyss;
    }

    public static void setAbyss(int value) {
        abyss = value;
    }

    /**
     * Потолок энергии бездны, пришедший с сервера.
     *
     * <p>Нужен панели для показа настоящей цены каста: у части модулей она
     * зависит от кошелька игрока («Расщепление» стоит ровно всю энергию бездны), и без
     * потолка на клиенте нечем посчитать её для показа.
     */
    public static int getMaxAbyss() {
        return maxAbyss;
    }

    public static void setAbyss(int value, int max, java.util.List<String> knownModules,
                               java.util.List<String> knownModifiers, int totalCount) {
        abyss = value;
        maxAbyss = max;
        known = knownModules;
        knownMods = knownModifiers;
        total = totalCount;
    }

    public static boolean isInfiniteAbyss() {
        return infiniteAbyss;
    }

    public static void setInfiniteAbyss(boolean value) {
        infiniteAbyss = value;
    }

    public static java.util.List<String> knownModifiers() {
        return knownMods;
    }

    public static java.util.List<String> knownModules() {
        return known;
    }

    public static int total() {
        return total;
    }

    public static java.util.List<String> libraryNames() {
        return libraryNames;
    }

    public static java.util.List<Integer> librarySizes() {
        return librarySizes;
    }

    public static void setLibrary(java.util.List<String> names, java.util.List<Integer> sizes) {
        libraryNames = names;
        librarySizes = sizes;
    }

    public static java.util.Set<Integer> errorNodes() {
        return errorNodes;
    }

    public static void setErrorNodes(java.util.Set<Integer> nodes) {
        errorNodes = nodes == null ? java.util.Set.of() : nodes;
    }
}