package com.github.fireprime5.spellcraft.machine;

import com.github.fireprime5.spellcraft.spell.SpellGraph;
import com.github.fireprime5.spellcraft.spell.SpellModules;
import com.github.fireprime5.spellcraft.spell.SpellModuleType;
import com.github.fireprime5.spellcraft.spell.SpellNode;
import net.minecraft.nbt.CompoundTag;

import java.util.List;
import java.util.Set;

/**
 * Модульная сетка машины: какие модули в неё разрешено ставить.
 *
 * <p>У каждой машины своя сетка — ограниченный набор модулей. Плавильне
 * нужны вещи, которыми управляют огнём, извлекателю — теми, чем наполняют.
 * Общая сетка означала бы, что в плавильне можно поставить телепорт, и это
 * было бы нелепо.
 *
 * <p>Граф машины — тот же {@link SpellGraph}, что и у заклинания: та же
 * проверка циклов, те же типы портов, тот же код исполнения. Отличается
 * только список допустимых модулей.
 *
 * <p>Узлы стоят на сетке конструктора (gridX/gridY), а не в мире: конструктор
 * рисует схему, и машина показывает её так же.
 *
 * <p>Налог: пока сетка пуста, машина работает на одной энергии. Стоит включить
 * хоть один модуль — машина начинает требовать ещё и бездну из трубы.
 */
public class MachineGrid {

    /** Сколько модулей разрешено в одной машине. */
    public static final int MAX_NODES = 24;

    /** Бездна за тик на один включённый модуль. */
    public static final int ABYSS_PER_MODULE = 2;

    /** Набор разрешённых модулей: id без пространства имён. */
    private final Set<String> allowed;
    private final com.github.fireprime5.spellcraft.spell.validation.SchemaPolicy policy;

    private final SpellGraph graph = new SpellGraph();

    public MachineGrid(Set<String> allowed) {
        this.allowed = Set.copyOf(allowed);
        this.policy = com.github.fireprime5.spellcraft.spell.validation.SchemaPolicy.restricted(MAX_NODES, allowed);
    }

    public com.github.fireprime5.spellcraft.spell.validation.SchemaPolicy policy() {
        return policy;
    }

    /** Разрешён ли модуль. */
    public boolean allows(SpellModuleType type) {
        return type != null && policy.allows(type.id());
    }

    /** Разрешён ли модуль по id. */
    public boolean allows(String path) {
        return allowed.contains(path);
    }

    /** Разрешённые модули: для палитры конструктора машины. */
    public Set<String> allowed() {
        return allowed;
    }

    /** Граф машины. */
    public SpellGraph graph() {
        return graph;
    }

    /**
     * Ставит модуль.
     *
     * @return false — модуль не разрешён или сетка полна
     */
    public boolean add(SpellModuleType type, int gridX, int gridY) {
        if (!allows(type)) {
            return false;
        }
        SpellGraph candidate = graph.copy();
        int existing = indexAt(gridX, gridY);
        if (existing >= 0) {
            candidate.removeNode(existing);
        }
        candidate.addNode(type.id(), gridX, gridY);
        if (!candidate.validateForSave(null, policy).isEmpty()) {
            return false;
        }
        // узел на занятом месте заменяется: иначе две вставки в одну клетку
        // оставили бы в графе два узла и сломали бы расчёт порядка
        remove(gridX, gridY);
        // addNode возвращает void, поэтому успех проверяем по списку:
        // узел мог не добавиться, если его там уже был
        graph.addNode(type.id(), gridX, gridY);
        return indexAt(gridX, gridY) >= 0;
    }

    /** Убирает узел по клетке. */
    public boolean remove(int gridX, int gridY) {
        int index = indexAt(gridX, gridY);
        if (index < 0) {
            return false;
        }
        graph.removeNode(index);
        return true;
    }

    /** Индекс узла в клетке или -1. */
    public int indexAt(int gridX, int gridY) {
        List<SpellNode> nodes = graph.nodes;
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).gridX == gridX && nodes.get(i).gridY == gridY) {
                return i;
            }
        }
        return -1;
    }

    /** Сколько модулей включено. */
    public int moduleCount() {
        return graph.nodes.size();
    }

    public boolean isEmpty() {
        return graph.nodes.isEmpty();
    }

    /**
     * Сколько бездны машине нужно за тик.
     *
     * <p>Ноль при пустой сетке: требовать бездну за пустую машину было бы
     * наказанием за то, что игрок ничего не настроил.
     */
    public int abyssDemand() {
        return graph.nodes.size() * ABYSS_PER_MODULE;
    }

    /**
     * Выбрасывает узлы, которых больше нет в реестре или в наборе.
     *
     * <p>Модули исчезают при смене версии мода, и сохранённый граф сосался бы
     * в пустоту. Чистим при загрузке, а не при использовании: иначе машина
     * ломалась бы в момент работы.
     */
    public void prune() {
        // Идём с конца и удаляем через removeNode: он сам пересобирает индексы
        // связей. removeIf обрывал бы связи, а «оставить один узел» стирало бы
        // машину, у которой было больше одного модуля.
        for (int i = graph.nodes.size() - 1; i >= 0; i--) {
            SpellModuleType type = SpellModules.get(graph.nodes.get(i).moduleId);
            if (type == null || !allows(type)) {
                graph.removeNode(i);
            }
        }
    }

    public CompoundTag save() {
        return graph.save();
    }

    /** Загрузка: готовый разбор графа, он и так мягкий к битым данным. */
    public void load(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return;
        }
        SpellGraph loaded = SpellGraph.load(tag);
        graph.nodes.clear();
        graph.nodes.addAll(loaded.nodes);
        prune();
        if (!graph.validateForSave(null, policy).isEmpty()) {
            graph.nodes.clear();
        }
    }

    /** Наборы модулей по типу машины. */
    public static final class Sets {

        private Sets() {
        }

        /**
         * Плавильня: огонь, магия и время.
         *
         * <p>Ничего, что двигает мир снаружи блока: плавильня не выбрасывает
         * предметы в сторону и не телепортирует.
         */
        public static Set<String> smelter() {
            return Set.of(
                    "const_zero", "const_one", "const_five", "const_ten", "const_neg_one",
                    "add", "subtract", "multiply", "divide", "percent_of",
                    "min", "max", "clamp", "round", "floor", "ceil",
                    "time_of_day", "block_under_crosshair",
                    "heal", "damage", "ignite",
                    "stack_push", "stack_pop", "stack_dup", "stack_size",
                    "stack_store", "stack_load", "stack_exit");
        }

        /**
         * Извлекатель: добыча и счёт.
         *
         * <p>Ничего, что жжёт энергию или двигает блоки: извлекатель — источник,
         * и он не должен уметь вредить миру.
         */
        public static Set<String> extractor() {
            return Set.of(
                    "const_zero", "const_one", "const_five", "const_ten", "const_neg_one",
                    "add", "subtract", "multiply", "divide", "percent_of",
                    "block_under_crosshair", "time_of_day",
                    "analysis_block", "analysis_mob", "extract_block", "extract_mob",
                    "entity_health", "entity_max_health",
                    "stack_push", "stack_pop", "stack_dup", "stack_size",
                    "stack_store", "stack_load", "stack_exit");
        }

        /** Бак: арифметика и счёт, он ничего не делает. */
        public static Set<String> tank() {
            return Set.of(
                    "const_zero", "const_one", "const_five", "const_ten", "const_neg_one",
                    "add", "subtract", "multiply", "divide",
                    "stack_push", "stack_pop", "stack_dup", "stack_size", "stack_exit");
        }

        /** Сетка по типу машины. */
        public static Set<String> forMachine(String type) {
            return switch (type) {
                case "smelter" -> smelter();
                case "extractor" -> extractor();
                default -> tank();
            };
        }
    }
}
