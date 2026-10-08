package com.github.fireprime5.spellcraft.client.screen;

import com.github.fireprime5.spellcraft.spell.SpellGraph;
import com.github.fireprime5.spellcraft.spell.SpellModuleType;
import com.github.fireprime5.spellcraft.spell.SpellModules;
import com.github.fireprime5.spellcraft.spell.SpellNode;

import java.util.HashSet;

/**
 * Подсказка для входного порта открытого модуля.
 *
 * <p>Отдельный класс, а не метод экрана, потому что здесь ловится падение из
 * чужого багрепорта: удаление модуля добычи роняло экран с
 * {@code IndexOutOfBoundsException}. Меню модификаторов живо, пока его не
 * закрыли, и каждый кадр спрашивает подсказку у узла по <em>индексу</em>.
 * Индекс протухает на каждом шагу:
 *
 * <ul>
 *   <li>удаление узла сжимает {@code graph.nodes} — индекс уезжает за конец;</li>
 *   <li>ответ сервера, отмена и вставка пересоздают граф целиком, и старый
 *       индекс начинает указывать на другой узел или ни на какой;</li>
 * </ul>
 *
 * <p>Экран наследуется от {@code Screen} и в тестах не поднимается, поэтому
 * метод намеренно статический и без UI-зависимостей: иначе причину падения
 * было бы нечем проверить, кроме как чтением исходника глазами.
 */
public final class ModifierHint {

    private ModifierHint() {
    }

    /**
     * Модификаторы узла на момент открытия меню.
     *
     * <p>Тот же протухший индекс, что и в {@link #forNode}: если узел исчез
     * между кликом и открытием, {@code get} уронил бы экран. Пустой набор
     * честнее — меню покажет, что галочек нет, и не уронит ничего.
     */
    public static java.util.Set<String> enabledOn(SpellGraph graph, int node) {
        if (graph == null || node < 0 || node >= graph.nodes.size()) {
            return new HashSet<>();
        }
        SpellNode target = graph.nodes.get(node);
        if (target == null) {
            return new HashSet<>();
        }
        return new HashSet<>(target.modifiers);
    }

    /**
     * Подсказка для порта узла или пустая строка, если узла больше нет.
     *
     * @param node индекс узла в {@code graph.nodes}; устаревший индекс не считается
     *        ошибкой — он просто ничего не описывает
     */
    public static String forNode(SpellGraph graph, int node, int port) {
        if (graph == null || node < 0 || node >= graph.nodes.size()) {
            return "";
        }
        SpellNode target = graph.nodes.get(node);
        if (target == null || target.moduleId == null) {
            return "";
        }
        SpellModuleType type = SpellModules.get(target.moduleId);
        if (type == null) {
            return "";
        }
        String hint = type.inputHint(port, new HashSet<>(target.modifiers));
        return hint == null ? "" : hint;
    }
}