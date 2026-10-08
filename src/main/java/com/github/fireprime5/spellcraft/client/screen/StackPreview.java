package com.github.fireprime5.spellcraft.client.screen;

import com.github.fireprime5.spellcraft.spell.SpellGraph;
import com.github.fireprime5.spellcraft.spell.SpellModuleType;
import com.github.fireprime5.spellcraft.spell.SpellModules;
import com.github.fireprime5.spellcraft.spell.SpellNode;
import com.github.fireprime5.spellcraft.spell.SpellValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Предпросмотр стека на клиенте — считается без каста, только по «чистым»
 * модулям (константы и арифметика). Позволяет видеть, что получится,
 * ещё до применения заклинания. Модули, зависящие от мира, дают null.
 */
public final class StackPreview {

    private StackPreview() {
    }

    /** Строка стека: значение (null — «не вычислить на клиенте») или его подпись. */
    public record Entry(String label, SpellValue.Kind kind, boolean known) {
    }

    public static List<Entry> compute(SpellGraph graph) {
        List<Integer> order = graph.topologicalOrder(new ArrayList<>());
        if (order == null) {
            return List.of();
        }
        Map<Integer, List<SpellValue>> results = new HashMap<>();
        for (int idx : order) {
            SpellNode node = graph.nodes.get(idx);
            SpellModuleType type = SpellModules.get(node.moduleId);
            if (type == null) {
                continue;
            }
            List<SpellValue> inputs = new ArrayList<>();
            for (int port = 0; port < type.inputs(); port++) {
                int conn = port < node.inputConnections.size()
                        ? node.inputConnections.get(port) : -1;
                List<SpellValue> src = conn >= 0 ? results.get(conn) : null;
                inputs.add(src == null || src.isEmpty() ? null : src.get(0));
            }
            SpellValue value = evaluate(type, inputs);
            results.put(idx, value == null ? List.of() : List.of(value));
        }

        List<Entry> stack = new ArrayList<>();
        for (int idx : order) {
            List<SpellValue> out = results.get(idx);
            if (out == null || out.isEmpty()) {
                continue;
            }
            SpellValue v = out.get(0);
            stack.add(new Entry(v.describe().getString(), v.kind(), true));
        }
        // модули без результата, но с входами — показываем как неизвестные
        for (int idx : order) {
            SpellNode node = graph.nodes.get(idx);
            SpellModuleType type = SpellModules.get(node.moduleId);
            if (type == null) {
                continue;
            }
            List<SpellValue> out = results.get(idx);
            if (type.inputs() > 0 && (out == null || out.isEmpty())) {
                stack.add(new Entry("?", SpellValue.Kind.NUMBER, false));
            }
        }
        return stack;
    }

    /** Считает только модули без побочных эффектов. */
    private static SpellValue evaluate(SpellModuleType type, List<SpellValue> in) {
        String path = type.id().getPath();
        for (SpellValue v : in) {
            if (v == null) {
                return null;
            }
        }
        try {
            Double a = in.isEmpty() ? null : in.get(0).asNumber();
            Double b = in.size() > 1 ? in.get(1).asNumber() : null;
            double result = switch (path) {
                case "const_zero" -> 0;
                case "const_one" -> 1;
                case "const_five" -> 5;
                case "const_ten" -> 10;
                case "const_neg_one" -> -1;
                case "add" -> a + b;
                case "subtract" -> a - b;
                case "multiply" -> a * b;
                case "divide" -> b == 0 ? Double.NaN : a / b;
                case "power" -> Math.pow(a, b);
                case "negate" -> -a;
                default -> Double.NaN;
            };
            if (Double.isNaN(result)) {
                return null;
            }
            return SpellValue.of(result);
        } catch (RuntimeException e) {
            return null;
        }
    }
}