package com.github.fireprime5.spellcraft.client;

import com.github.fireprime5.spellcraft.network.SpellResultPayload;
import com.github.fireprime5.spellcraft.spell.SpellGraph;

/**
 * Клиентское зеркало состояния заклинаний: текущий граф, результат каста, мана.
 * Класс не содержит клиентских зависимостей, поэтому его безопасно читать
 * из общих классов (например, из тикера для синхронизации маны).
 */
public final class ClientSpellState {

    private static SpellGraph graph;
    private static SpellResultPayload lastResult;
    private static int mana;
    /** Бесконечная мана включена: панель рисует «∞» вместо числа. */
    private static boolean infiniteMana;
    private static java.util.List<String> known = java.util.List.of();
    private static java.util.List<String> knownMods = java.util.List.of();
    private static int total;
    private static boolean builderOpen;
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

    public static int getMana() {
        return mana;
    }

    public static void setMana(int value) {
        mana = value;
    }

    public static void setMana(int value, java.util.List<String> knownModules,
                               java.util.List<String> knownModifiers, int totalCount) {
        mana = value;
        known = knownModules;
        knownMods = knownModifiers;
        total = totalCount;
    }

    public static boolean isInfiniteMana() {
        return infiniteMana;
    }

    public static void setInfiniteMana(boolean value) {
        infiniteMana = value;
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

    public static boolean isBuilderOpen() {
        return builderOpen;
    }

    public static void setBuilderOpen(boolean value) {
        builderOpen = value;
    }
}