package com.github.fireprime5.spellcraft.spell;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * Граф заклинания: набор узлов и соединений между ними.
 */
public class SpellGraph {

    /** Кодек графа: список узлов вместе со связями (используется для сохранения). */
    public static final com.mojang.serialization.Codec<SpellGraph> CODEC =
            SpellNode.FULL_CODEC.listOf().xmap(SpellGraph::fromNodes, g -> g.nodes);

    /** Максимум модулей в одном заклинании (защита от мусорных пакетов). */
    public static final int MAX_NODES = 512;

    public final List<SpellNode> nodes = new ArrayList<>();

    public static SpellGraph fromNodes(List<SpellNode> nodes) {
        SpellGraph graph = new SpellGraph();
        graph.nodes.addAll(nodes);
        graph.dropBrokenConnections();
        return graph;
    }

    public SpellNode addNode(ResourceLocation moduleId, int gridX, int gridY) {
        SpellNode node = new SpellNode(moduleId, gridX, gridY);
        nodes.add(node);
        return node;
    }

    public void removeNode(int index) {
        if (index < 0 || index >= nodes.size()) {
            return;
        }
        nodes.remove(index);
        for (SpellNode n : nodes) {
            n.inputConnections.replaceAll(i -> i == index ? -1 : (i > index ? i - 1 : i));
        }
    }

    public boolean connect(int fromNode, int fromPort, int toNode, int toPort) {
        if (fromNode == toNode || fromNode < 0 || toNode < 0) {
            return false;
        }
        if (fromNode >= nodes.size() || toNode >= nodes.size()) {
            return false;
        }
        SpellModuleType from = SpellModules.get(nodes.get(fromNode).moduleId);
        SpellModuleType to = SpellModules.get(nodes.get(toNode).moduleId);
        if (from == null || to == null) {
            return false;
        }
        if (fromPort < 0 || fromPort >= from.outputs()) {
            return false;
        }
        if (toPort < 0 || toPort >= to.inputs()) {
            return false;
        }
        if (nodes.get(toNode).inputConnections.size() >= SpellNode.MAX_CONNECTIONS) {
            return false;
        }
        while (nodes.get(toNode).inputConnections.size() <= toPort) {
            nodes.get(toNode).inputConnections.add(-1);
        }
        nodes.get(toNode).inputConnections.set(toPort, fromNode);
        return true;
    }

    public void disconnect(int toNode, int toPort) {
        if (toNode >= 0 && toNode < nodes.size() && toPort >= 0
                && toPort < nodes.get(toNode).inputConnections.size()) {
            nodes.get(toNode).inputConnections.set(toPort, -1);
        }
    }

    /**
     * Топологическая сортировка + детект циклов. Возвращает порядок выполнения
     * или null, если граф содержит цикл.
     */
    public List<Integer> topologicalOrder(List<SpellIssue> issues) {
        int n = nodes.size();
        int[] state = new int[n]; // 0 = new, 1 = visiting, 2 = done
        List<Integer> order = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            if (state[i] == 0 && !dfs(i, state, order, issues)) {
                return null;
            }
        }
        return order;
    }

    private boolean dfs(int i, int[] state, List<Integer> order, List<SpellIssue> issues) {
        state[i] = 1;
        SpellNode node = nodes.get(i);
        for (int dep : node.inputConnections) {
            if (dep < 0) {
                continue;
            }
            if (dep >= state.length) {
                // битая связь (например, из чужого/старого сохранения):
                // validate() уже сообщит о ней, здесь просто пропускаем
                continue;
            }
            if (state[dep] == 1) {
                issues.add(SpellIssue.of("graph.cycle"));
                return false;
            }
            if (state[dep] == 0 && !dfs(dep, state, order, issues)) {
                return false;
            }
        }
        state[i] = 2;
        order.add(i);
        return true;
    }

    /**
     * Полная проверка графа перед кастом.
     */
    public List<SpellIssue> validate() {
        return validate(null);
    }

    /**
     * Проверка с учётом прогресса игрока: закрытые модули использовать нельзя.
     *
     * @param player игрок (null — проверка без ограничений)
     */
    public List<SpellIssue> validate(net.minecraft.world.entity.player.Player player) {
        List<SpellIssue> issues = new ArrayList<>();
        Map<ResourceLocation, Integer> counts = new HashMap<>();

        for (int i = 0; i < nodes.size(); i++) {
            SpellNode node = nodes.get(i);
            SpellModuleType type = SpellModules.get(node.moduleId);
            if (type == null) {
                issues.add(SpellIssue.of("module.unknown", node.moduleId.toString()));
                continue;
            }
            counts.merge(type.id(), 1, Integer::sum);

            for (int port = 0; port < type.inputs(); port++) {
                int conn = port < node.inputConnections.size() ? node.inputConnections.get(port) : -1;
                if (conn < 0 || conn >= nodes.size()) {
                    if (!type.isOptional(port)) {
                        issues.add(SpellIssue.of("graph.missing_input",
                                SpellModules.shortName(type) + " #" + (i + 1)
                                        + " вход " + (port + 1)));
                    }
                    continue;
                }
                // Проверяем, что тип источника подходит к типу приёмника.
                //
                // Связь в графе хранит только индекс узла-источника, выход не
                // запоминается: какое именно значение придёт, решает порт
                // ПРИЁМНИКА (см. SpellExecutor — srcOutputs.get(port)). Поэтому
                // здесь тоже сверяется выход 0, иначе проверка спорила бы с
                // исполнителем. Для модулей с несколькими выходами это значит,
                // что типы всех выходов обязаны совпадать: сейчас таковы
                // duplicate и swap (оба ANY). Модуль с разными типами выходов
                // потребует хранить пару (узел, порт) — это ломает формат
                // сохранения графа.
                SpellModuleType srcType = SpellModules.get(nodes.get(conn).moduleId);
                if (srcType != null && srcType.outputs() > 0) {
                    PortType out = srcType.outputType(0);
                    PortType in = type.inputType(port);
                    if (!in.accepts(out)) {
                        issues.add(SpellIssue.of("graph.type_mismatch",
                                SpellModules.shortName(srcType) + " (" + out.label().getString()
                                        + ") → " + SpellModules.shortName(type)
                                        + " вход " + (port + 1) + " (" + in.label().getString() + ")"));
                    }
                }
            }
        }

        for (var e : counts.entrySet()) {
            SpellModuleType type = SpellModules.get(e.getKey());
            if (type != null && type.maxInstances() >= 0 && e.getValue() > type.maxInstances()) {
                issues.add(SpellIssue.of("graph.too_many", SpellModules.shortName(type)));
            }
        }

        if (nodes.isEmpty()) {
            issues.add(SpellIssue.of("graph.empty"));
        }

        // конфликтующие модификаторы одного модуля
        for (int i = 0; i < nodes.size(); i++) {
            SpellNode node = nodes.get(i);
            SpellModuleType type = SpellModules.get(node.moduleId);
            if (type == null || node.modifiers.isEmpty()) {
                continue;
            }
            for (String id : node.modifiers) {
                SpellModifier modifier = SpellModifiers.byId(id);
                if (modifier == null) {
                    issues.add(SpellIssue.of("graph.modifier_unknown", id));
                    continue;
                }
                if (!type.modifiers().contains(modifier)) {
                    issues.add(SpellIssue.of("graph.modifier_not_allowed",
                            SpellModules.shortName(type) + ": " + id));
                }
                // конфликт внутри группы
                for (String otherId : node.modifiers) {
                    SpellModifier other = SpellModifiers.byId(otherId);
                    if (other != null && other != modifier && modifier.conflictsWith(other)) {
                        issues.add(SpellIssue.of("graph.modifier_conflict",
                                modifier.label().getString() + " × "
                                        + other.label().getString()));
                    }
                }
            }
        }

        if (player != null) {
            for (int i = 0; i < nodes.size(); i++) {
                SpellModuleType type = SpellModules.get(nodes.get(i).moduleId);
                if (type == null) {
                    continue;
                }
                if (!com.github.fireprime5.spellcraft.data.PlayerKnowledge.knows(player, type.id())) {
                    issues.add(SpellIssue.of("graph.module_locked",
                            SpellModules.shortName(type)));
                    continue;
                }
                var need = type.requiresModule();
                if (need != null && !com.github.fireprime5.spellcraft.data.PlayerKnowledge
                        .knows(player, need)) {
                    SpellModuleType required = SpellModules.get(need);
                    issues.add(SpellIssue.of("graph.module_requires",
                            SpellModules.shortName(type) + " → "
                                    + (required == null ? need.toString()
                                    : SpellModules.shortName(required))));
                }
                // модификатор нельзя включить, пока он не изучен
                for (String id : nodes.get(i).modifiers) {
                    var modifier = SpellModifiers.byId(id);
                    if (modifier == null || !type.modifiers().contains(modifier)) {
                        continue;
                    }
                    if (!com.github.fireprime5.spellcraft.data.PlayerKnowledge
                            .knowsModifier(player, id)) {
                        issues.add(SpellIssue.of("graph.modifier_locked",
                                modifier.label().getString()));
                    }
                }
            }
        }

        topologicalOrder(issues);
        return issues;
    }

    /**
     * Убирает "читерские" модификаторы: неизвестные, не разрешённые модулю,
     * конфликтующие и ещё не изученные игроком. Вызывается на сервере перед
     * сохранением, чтобы в схему не попало то, что игрок не мог открыть.
     *
     * @return true — если что-то изменилось
     */
    public boolean sanitizeModifiers(
            net.minecraft.world.entity.player.Player player) {
        boolean changed = false;
        for (SpellNode node : nodes) {
            SpellModuleType type = SpellModules.get(node.moduleId);
            if (node.modifiers.isEmpty()) {
                continue;
            }
            if (type == null) {
                node.modifiers.clear();
                changed = true;
                continue;
            }
            var allowed = new java.util.LinkedHashSet<String>();
            for (String id : node.modifiers) {
                SpellModifier modifier = SpellModifiers.byId(id);
                if (modifier == null || !type.modifiers().contains(modifier)) {
                    changed = true;
                    continue;
                }
                if (player != null && !com.github.fireprime5.spellcraft.data.PlayerKnowledge
                        .knowsModifier(player, id)) {
                    changed = true;
                    continue;
                }
                boolean conflict = false;
                for (String kept : allowed) {
                    SpellModifier other = SpellModifiers.byId(kept);
                    if (other != null && other.conflictsWith(modifier)) {
                        conflict = true;
                        break;
                    }
                }
                if (conflict) {
                    changed = true;
                    continue;
                }
                allowed.add(id);
            }
            if (allowed.size() != node.modifiers.size()) {
                node.modifiers.clear();
                node.modifiers.addAll(allowed);
            }
        }
        return changed;
    }

    /** Границы графа по клеткам — для бесконечной сетки не ограничены. */
    public int maxX() {
        int max = 0;
        for (SpellNode n : nodes) {
            max = Math.max(max, n.gridX);
        }
        return max;
    }

    public int totalManaCost() {
        int total = 0;
        for (SpellNode node : nodes) {
            SpellModuleType type = SpellModules.get(node.moduleId);
            if (type != null) {
                total += type.manaCost() + SpellModifiers.totalCastCost(
                        new java.util.HashSet<>(node.modifiers));
            }
        }
        return total;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (SpellNode n : nodes) {
            list.add(n.save());
        }
        tag.put("nodes", list);
        return tag;
    }

    /**
     * Чтение графа из NBT (пакеты, буфер обмена, файлы). Данные приходят от
     * клиента, поэтому читаются «мягко»: неизвестные узлы и битые индексы связей
     * не приводят к исключению, а количество модулей ограничено.
     */
    public static SpellGraph load(CompoundTag tag) {
        SpellGraph graph = new SpellGraph();
        ListTag list = tag.getList("nodes", Tag.TAG_COMPOUND);
        int count = Math.min(list.size(), MAX_NODES);
        for (int i = 0; i < count; i++) {
            graph.nodes.add(SpellNode.load(list.getCompound(i)));
        }
        graph.dropBrokenConnections();
        return graph;
    }

    /** Убирает связи, ведущие за пределы графа (или сами на себя). */
    public void dropBrokenConnections() {
        int size = nodes.size();
        for (SpellNode node : nodes) {
            node.sanitize();
            node.inputConnections.replaceAll(conn -> conn < 0 || conn >= size ? -1 : conn);
        }
    }

    public SpellGraph copy() {
        return load(save());
    }
}
