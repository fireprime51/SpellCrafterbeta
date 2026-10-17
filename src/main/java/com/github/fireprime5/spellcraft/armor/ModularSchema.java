package com.github.fireprime5.spellcraft.armor;

import com.github.fireprime5.spellcraft.spell.SpellGraph;
import com.github.fireprime5.spellcraft.spell.SpellModules;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;

/**
 * Схема модулей брони: тот же граф, что и у заклинания, но со своим набором.
 *
 * <p>Набор разрешённых модулей у брони шире, чем у машины, и в другую сторону:
 * броня не варит и не добывает, зато встаёт между игроком и уроном. Отсюда
 * триггеры и полёт, которых нет ни в одной машине.
 *
 * <p>Схема неизменяема: нагрудник лежит в инвентаре, а конструктор открыт в
 * другом экране. Если бы методы меняли граф на месте, отмена правки в
 * конструкторе уже не вернула бы броню к прежнему виду.
 */
public final class ModularSchema {

    /** Сколько модулей разрешено на броне. */
    public static final int MAX_NODES = 32;

    /** Разрешённые модули: id без пространства имён. */
    private static final java.util.Set<String> ALLOWED = java.util.Set.of(
            "const_zero", "const_one", "const_five", "const_ten", "const_neg_one",
            "add", "subtract", "multiply", "divide", "percent_of",
            "min", "max", "clamp", "round",
            "entity_health", "entity_max_health",
            ArmorModuleIds.TRIGGER_ON_HIT, ArmorModuleIds.TRIGGER_ON_HURT,
            ArmorModuleIds.CREATIVE_FLIGHT,
            ArmorModuleIds.MODIFIER_SPEED, ArmorModuleIds.MODIFIER_DAMAGE,
            ArmorModuleIds.MODIFIER_PROTECTION,
            com.github.fireprime5.spellcraft.spell.UniqueModules.ETERNITY,
            com.github.fireprime5.spellcraft.spell.ShieldModules.BARRIER,
            // Барьер ставится и в заклинание, и в нагрудник. Раздел у него
            // остаётся «Действия», поэтому в обычной палитре он виден, а в
            // палитре брони проходит по белому списку этого контейнера.
            "stack_push", "stack_pop", "stack_dup", "stack_size",
            "stack_store", "stack_load", "stack_exit");

    /** Пустая схема: броня без модулей не добавляет поведения. */
    public static final ModularSchema EMPTY = new ModularSchema(new SpellGraph());

    public static final com.github.fireprime5.spellcraft.spell.validation.SchemaPolicy POLICY =
            com.github.fireprime5.spellcraft.spell.validation.SchemaPolicy.restricted(MAX_NODES, ALLOWED);

    private final SpellGraph graph;

    /**
     * Хэш содержимого, посчитанный один раз при создании.
     *
     * <p>Схема неизменяема: {@link #graph()} отдаёт копию, а {@link #view()} —
     * живой граф только для чтения и исполнения, узлы никто не добавляет. Поэтому
     * хэш можно посчитать заранее, а не на каждом вызове: значение компонента
     * данных попадает в хеш-таблицы предметов, и пересборка NBT на каждый
     * {@code hashCode} стоила бы заметно.
     */
    private final int contentHash;

    private ModularSchema(SpellGraph graph) {
        this.graph = graph;
        this.contentHash = graph.save().hashCode();
    }

    /**
     * Схема лежит в компоненте данных предмета, а Minecraft требует от значения
     * компонента реализовать {@code equals} и {@code hashCode} — иначе любое
     * сохранение падает с
     * «Data components must implement equals and hashCode. Problematic class:
     * ModularSchema», и модули в нагруднике не сохраняются вовсе.
     *
     * <p>Оба метента считаются от одного и того же — сериализованного NBT, — иначе
     * они могут разойтись: {@link #sameAs} сравнивает граф структурно и не считает
     * хвостовые «-1» в связях, тогда как NBT их хранит.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof ModularSchema schema && save().equals(schema.save());
    }

    @Override
    public int hashCode() {
        return contentHash;
    }

    /** Граф для конструктора: копия, чтобы правка не била по надетой броне. */
    public SpellGraph graph() {
        return graph.copy();
    }

    /** Граф для исполнения. */
    public SpellGraph view() {
        return graph;
    }

    public boolean isEmpty() {
        return graph.nodes.isEmpty();
    }

    public int size() {
        return graph.nodes.size();
    }

    /** Разрешён ли модуль. */
    public static boolean allows(String path) {
        return ALLOWED.contains(path);
    }

    public static java.util.Set<String> allowed() {
        return ALLOWED;
    }

    /**
     * Ставит модуль.
     *
     * @return false — модуль не разрешён или броня полна
     */
    public static ModularSchema with(SpellGraph updated) {
        if (updated == null) {
            return EMPTY;
        }
        if (!updated.validateForSave(null, POLICY).isEmpty()) {
            return EMPTY;
        }
        return new ModularSchema(updated.copy());
    }

    /** Id модулей в порядке узлов: для подсказок и проверки триггеров. */
    public List<String> modules() {
        List<String> ids = new ArrayList<>(graph.nodes.size());
        for (var node : graph.nodes) {
            ids.add(node.moduleId.getPath());
        }
        return ids;
    }

    /**
     * Модификаторы узла с этим модулем.
     *
     * <p>Нужны, чтобы эффект, зависящий от галочки на модуле, работал и в броне:
     * там схема исполняется как есть, и без чтения узла модуль не узнал бы, что
     * игрок включил «только снаряды».
     */
    public java.util.Set<String> modifiersOf(String path) {
        for (var node : graph.nodes) {
            if (node.moduleId.getPath().equals(path)) {
                return new java.util.LinkedHashSet<>(node.modifiers);
            }
        }
        return java.util.Set.of();
    }

    /** Есть ли в схеме модуль с таким id. */
    public boolean has(String path) {
        for (var node : graph.nodes) {
            if (node.moduleId.getPath().equals(path)) {
                return true;
            }
        }
        return false;
    }

    /** Сколько бездны броня требует за тик. */
    public int abyssDemand() {
        return (int) Math.round(armorUpkeepPerSecond() * 20);
    }

    /** Облагается ли узел постоянным налогом. */
    private static boolean taxable(String path) {
        // «Вечность» исключена намеренно: налог платит за пассивное действие, а
        // она в покое не делает ничего и платит за каждый удар отдельно. С
        // облаганием бездна таяла бы сама, без единого удара.
        return !com.github.fireprime5.spellcraft.spell.UniqueModules.ETERNITY.equals(path);
    }

    /**
     * Расход всей брони, энергий бездны в секунду.
     *
     * <p>Складывается из расходов отдельных модулей, а не из числа узлов. Полёт
     * стоит пол-единицы в секунду, барьер — тридцать, и разница между ними не
     * «в тридцать раз больше модулей», а «в шестьдесят раз дороже». Считать
     * поштучно было нельзя: арифметика «одна единица за узел за 40 тиков»
     * разъезжалась с любым модулем дороже полёта.
     *
     * <p>Схема только из «Вечности» даёт {@code 0}: налога нет, и нагрудник
     * включается сразу. Проверять бездну всё равно придётся — на самом ударе.
     */
    public double armorUpkeepPerSecond() {
        double всего = 0;
        for (var node : graph.nodes) {
            var type = com.github.fireprime5.spellcraft.spell.SpellModules.get(node.moduleId);
            if (type == null || !taxable(node.moduleId.getPath())) {
                continue;
            }
            всего += Math.max(0, type.armorUpkeepPerSecond());
        }
        return всего;
    }

    public CompoundTag save() {
        return graph.save();
    }

    /** Загрузка из NBT: неразрешённые и битые узлы выбрасываются. */
    public static ModularSchema load(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return EMPTY;
        }
        SpellGraph loaded = SpellGraph.load(tag);
        for (int i = loaded.nodes.size() - 1; i >= 0; i--) {
            var node = loaded.nodes.get(i);
            if (!POLICY.allows(node.moduleId) || SpellModules.get(node.moduleId) == null) {
                loaded.removeNode(i);
            }
        }
        return with(loaded);
    }

    public boolean sameAs(ModularSchema other) {
        return other != null && graph.sameAs(other.graph);
    }
}
