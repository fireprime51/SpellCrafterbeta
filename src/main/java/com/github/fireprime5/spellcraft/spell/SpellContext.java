package com.github.fireprime5.spellcraft.spell;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Контекст выполнения заклинания: стек значений, ошибки, трасса, мана.
 */
public class SpellContext {

    private final Level level;
    private final LivingEntity caster;
    private final Vec3 origin;

    private final Deque<SpellValue> stack = new ArrayDeque<>();
    private final List<SpellIssue> errors = new ArrayList<>();
    private final List<String> trace = new ArrayList<>();

    private Entity targetEntity;
    private BlockPos targetBlock;
    private Vec3 targetPos;

    private int manaSpent;
    /** Узел, который выполняется сейчас (модули сети берут из него цель). */
    private SpellNode currentNode;
    /** Модификаторы узла, который выполняется сейчас. */
    private java.util.Set<String> currentModifiers = java.util.Set.of();

    /**
     * Ячейки для хранения значений между модулями («Зарядить»/«Взять»).
     *
     * <p>Отдельное от стека хранилище нужно для значений, которые переживают
     * цикл: стек при повторении расходуется, а содержимое ячейки остаётся.
     */
    private final SpellValue[] slots = new SpellValue[SLOTS];

    /**
     * Каст досрочно остановлен модулем вроде «Выход» — не ошибка, а обычное
     * завершение. Исполнитель прекращает обход узлов, но результат остаётся
     * успешным: иначе игрок увидит «заклинание не сработало» там, где всё
     * отработало как задумано.
     */
    private boolean halted;

    /** Сколько ячеек хранения доступно заклинанию. */
    public static final int SLOTS = 8;

    /** Потолок стека: защита от модулей, размножающих значение (см. «Повтор»). */
    public static final int MAX_STACK = 64;

    public SpellContext(Level level, LivingEntity caster, Vec3 origin) {
        this.level = level;
        this.caster = caster;
        this.origin = origin;
        this.targetPos = origin;
    }

    public Level level() {
        return level;
    }

    public LivingEntity caster() {
        return caster;
    }

    public Vec3 origin() {
        return origin;
    }

    public Entity targetEntity() {
        return targetEntity;
    }

    public BlockPos targetBlock() {
        return targetBlock;
    }

    public Vec3 targetPos() {
        return targetPos;
    }

    public void setTargetEntity(Entity e) {
        this.targetEntity = e;
        this.targetPos = e.position();
        this.targetBlock = e.blockPosition();
    }

    public void setTargetBlock(BlockPos pos) {
        this.targetBlock = pos;
        this.targetPos = Vec3.atCenterOf(pos);
        this.targetEntity = null;
    }

    public void setTargetPos(Vec3 pos) {
        this.targetPos = pos;
        this.targetEntity = null;
        this.targetBlock = BlockPos.containing(pos);
    }

    public SpellNode currentNode() {
        return currentNode;
    }

    public void setCurrentNode(SpellNode node) {
        this.currentNode = node;
    }

    /** Параметр текущего узла (пустая строка, если параметра нет). */
    public String currentParam() {
        return currentNode == null || currentNode.param == null ? "" : currentNode.param;
    }

    // --- Стек ---

    /**
     * Кладёт значение на стек.
     *
     * <p>Стек ограничен: модуль «Повтор» кладёт сразу N копий значения, и без
     * потолка заклинание вида «Взять → Повтор 5 → ... » могло бы забить память
     * сервера. При переполнении лишнее просто не кладётся, а модуль узнаёт об
     * этом через {@link #stackIsFull()}.
     */
    public void push(SpellValue value) {
        if (value != null && stack.size() < MAX_STACK) {
            stack.push(value);
        }
    }

    /** Стек заполнен: новое значение уже не поместится. */
    public boolean stackIsFull() {
        return stack.size() >= MAX_STACK;
    }

    /** Кладёт значение, только если в стеке есть место. */
    public boolean tryPush(SpellValue value) {
        if (value == null || stackIsFull()) {
            return false;
        }
        stack.push(value);
        return true;
    }

    public SpellValue pop() {
        if (stack.isEmpty()) {
            throw new SpellException("stack.underflow");
        }
        return stack.pop();
    }

    public SpellValue peek() {
        if (stack.isEmpty()) {
            throw new SpellException("stack.underflow");
        }
        return stack.peek();
    }

    public int stackSize() {
        return stack.size();
    }

    public boolean stackIsEmpty() {
        return stack.isEmpty();
    }

    public List<SpellValue> stackSnapshot() {
        return new ArrayList<>(stack);
    }

    public String stackString() {
        if (stack.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (SpellValue v : stack) {
            sb.append(v.describe().getString()).append(", ");
        }
        sb.setLength(sb.length() - 2);
        return sb.append(']').toString();
    }

    /**
     * Кладёт значение в ячейку.
     *
     * @return false — ячейки нет или она занята; перезапись запрещена
     */
    public boolean store(int slot, SpellValue value) {
        if (!validSlot(slot) || slots[slot] != null) {
            return false;
        }
        slots[slot] = value;
        return true;
    }

    /** Значение из ячейки (или null, если ячейка пуста). */
    public SpellValue load(int slot) {
        return validSlot(slot) ? slots[slot] : null;
    }

    /** Освобождает ячейку: значение возвращается на стек. */
    public SpellValue take(int slot) {
        SpellValue value = load(slot);
        slots[slot] = null;
        return value;
    }

    /** Пуста ли ячейка. */
    public boolean slotFree(int slot) {
        return validSlot(slot) && slots[slot] == null;
    }

    /** Сколько ячеек занято. */
    public int slotsUsed() {
        int used = 0;
        for (SpellValue value : slots) {
            if (value != null) {
                used++;
            }
        }
        return used;
    }

    /**
     * Номер ячейки из числа на входе.
     *
     * <p>Индексы приходят из заклинания, поэтому приводятся к целому и
     * зажимаются в границы: «-1» или «999» не должны ни падать, ни писать
     * мимо массива.
     */
    public int slotIndex(SpellValue value) {
        if (value == null) {
            throw new SpellException("slot.no_number");
        }
        double number = value.asNumber();
        if (!Double.isFinite(number)) {
            throw new SpellException("slot.no_number");
        }
        return Math.clamp((int) Math.round(number), 0, SLOTS - 1);
    }

    private boolean validSlot(int slot) {
        return slot >= 0 && slot < slots.length;
    }

    /** Прекращает каст: оставшиеся узлы не выполняются. */
    public void halt() {
        this.halted = true;
    }

    public boolean halted() {
        return halted;
    }

    // --- Ошибки / трасса ---

    public void error(SpellIssue issue) {
        errors.add(issue);
    }

    public List<SpellIssue> errors() {
        return errors;
    }

    public void trace(String line) {
        trace.add(line);
    }

    public List<String> trace() {
        return trace;
    }

    public boolean failed() {
        return !errors.isEmpty();
    }

    // --- Мана (заглушка энергосистемы) ---

    public int manaSpent() {
        return manaSpent;
    }

    public void setModifiers(java.util.Collection<String> modifiers) {
        this.currentModifiers = new java.util.HashSet<>(modifiers);
    }

    public java.util.Set<String> modifiers() {
        return currentModifiers;
    }

    public boolean hasModifier(SpellModifier modifier) {
        return currentModifiers.contains(modifier.id());
    }

    /**
     * Учитывает потраченную ману.
     *
     * <p>При бесконечной мане в счётчик идёт ноль, а не номинал: по счётчику
     * потом считается, сколько игрок реально потратил, и без этого панель
     * сообщала бы «каст за 40 маны», хотя ничего не списалось.
     */
    public void spendMana(int amount) {
        manaSpent += caster instanceof net.minecraft.world.entity.player.Player p
                ? com.github.fireprime5.spellcraft.energy.ManaCapability.spentAmount(p, amount)
                : amount;
    }

    /**
     * Хватает ли маны на каст.
     *
     * <p>Проверка идёт через {@code ManaCapability.has}, а не через сравнение
     * с текущим значением: иначе бесконечная мана проходила бы проверку в
     * {@link com.github.fireprime5.spellcraft.energy.PlayerManaBuffer}, но не
     * здесь, и заклинание всё равно падало бы с «не хватает маны».
     */
    public boolean hasMana(int amount) {
        return caster instanceof net.minecraft.world.entity.player.Player p
                && com.github.fireprime5.spellcraft.energy.ManaCapability.has(p, amount);
    }

    public void consumeMana(int amount) {
        if (caster instanceof net.minecraft.world.entity.player.Player p) {
            com.github.fireprime5.spellcraft.energy.ManaCapability.consumeMana(p, amount);
        }
    }

    /**
     * Ошибка исполнения: ключ перевода + аргумент.
     */
    public static class SpellException extends RuntimeException {
        private final String key;
        private final String arg;

        public SpellException(String key) {
            this(key, "");
        }

        public SpellException(String key, Object arg) {
            super(key);
            this.key = key;
            this.arg = String.valueOf(arg);
        }

        public String getKey() {
            return key;
        }

        public SpellIssue toIssue() {
            return new SpellIssue(key, arg);
        }
    }
}