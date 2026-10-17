package com.github.fireprime5.spellcraft.spell;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Контекст выполнения заклинания: стек значений, ошибки, трасса, энергия бездны.
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
    /**
     * Сколько энергии бездны исполнитель уже списал до обхода графа.
     *
     * <p>Нужен, чтобы отличить «потрачено модулем» от «потрачено заранее»:
     * см. {@link #remainingManaBudget()}.
     */
    private int prepaidMana;
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

    /**
     * Максимальная дальность воздействия заклинания, в блоках.
     *
     * <p>Заклинание — это схема, полностью заданная игроком, поэтому точку
     * воздействия можно получить арифметикой от любых чисел, а координаты в
     * мире не ограничены. Без этого предела модуль «Создать лаву» или «Убрать
     * блок» писал бы куда угодно на карте: за чужой постройкой, в чужом регионе
     * и за пределами загруженных чанков.
     *
     * <p>Предел общий для всех модулей и проверяется на сервере в одном месте
     * ({@link #requireInRange(Vec3)}), поэтому новый модуль не может «забыть»
     * про дальность: он не сможет позвать точку напрямую.
     */
    public static final double MAX_RANGE = 24.0;

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

    // ------------------------------------------------------- границы мира

    /**
     * Проверяет, что точка достижима кастом, и бросает иначе.
     *
     * <p>Общий предел для всех модулей, трогающих мир: см. {@link #MAX_RANGE}.
     * Считается от позиции сущности, а не от глаза, иначе «дальность рук» и
     * «дальность взгляда» отличались бы на два блока и правило пришлось бы
     * угадывать.
     */
    public void requireInRange(Vec3 pos) {
        if (pos == null || !Double.isFinite(pos.x) || !Double.isFinite(pos.y)
                || !Double.isFinite(pos.z)) {
            throw new SpellException("cast.bad_position");
        }
        if (caster == null) {
            // кастера нет только в вызовах вне игры (тесты считают сами
            // модули). В касте исполнителя кастер всегда есть, так что
            // пропуск тут ничего не открывает
            return;
        }
        requireWithin(caster.position(), pos);
    }

    /**
     * Чистая проверка дальности: точка не дальше {@link #MAX_RANGE} от точки.
     *
     * <p>Отдельный метод без кастующего, чтобы правило можно было проверить
     * без Minecraft и не дублировать его в каждом модуле.
     *
     * @throws SpellContext.SpellException если точка вне досягаемости
     */
    public static void requireWithin(Vec3 from, Vec3 to) {
        if (from.distanceToSqr(to) > MAX_RANGE * MAX_RANGE) {
            throw new SpellException("cast.too_far", (int) MAX_RANGE);
        }
    }

    /** То же, что {@link #requireInRange(Vec3)}, для координаты блока. */
    public void requireInRange(BlockPos pos) {
        requireInRange(Vec3.atCenterOf(pos));
    }

    /**
     * Проверяет, что цель не дальше {@link #MAX_RANGE}.
     *
     * <p>Отдельно от блоков: цель могла прийти из {@code entity_under_crosshair}
     * (тогда она и так близко) или из чужой схемы, где сущность стоит рядом, а
     * кастует кто-то другой — тогда «близко к кастеру» и не значит «можно бить».
     */
    public void requireEntityInRange(Entity target) {
        requireInRange(target.position());
    }

    /**
     * Чанк должен быть загружен: иначе {@code setBlockAndUpdate} не просто
     * меняет блок, а заставляет сервер сгенерировать чанк. Игрок, кастующий
     * один раз, заставил бы сервер генерировать мир по произвольным координатам.
     */
    public void requireLoaded(BlockPos pos) {
        if (level instanceof net.minecraft.server.level.ServerLevel server
                && !server.isLoaded(pos)) {
            throw new SpellException("cast.chunk_unloaded");
        }
    }

    /**
     * Разрушим ли блок вообще.
     *
     * <p>Проверка обязательна, потому что {@code Level.destroyBlock} НЕ смотрит на
     * прочность: он безусловно ставит на место воздух. Неразрушимость в игре
     * держится в двух других местах — в цикле копания ({@code getDestroyProgress})
     * и в проверке добычи, — и ни одну из них этот метод не проходит. Поэтому
     * «Добыча» сносила бедрок, барьеры и рамки портала, а «Шёлковое касание»
     * при этом ещё и роняло предмет: код добычи не смотрел, удалился ли блок
     * вообще.
     *
     * <p>Признак неразрушимости ровно один и он же у ваниллы: у бедрока,
     * барьера, {@code end_portal_frame}, {@code structure_void} задан
     * {@code strength(-1.0F)}, поэтому {@link net.minecraft.world.level.block.state.BlockState#getDestroySpeed}
     * отдаёт −1.
     *
     * <p>Позиция передаётся настоящая, а не заглушка: лёд и глиняные блоки
     * переопределяют {@code getDestroySpeed} и читают окружение.
     */
    public boolean canBreak(BlockPos pos) {
        if (!(level instanceof net.minecraft.world.level.LevelAccessor accessor)) {
            // без мира судить не о чем: проверку делает сервер
            return true;
        }
        return canBreak(accessor, accessor.getBlockState(pos), pos);
    }

    /** То же для уже прочитанного состояния — обходы читают блок один раз. */
    public static boolean canBreak(net.minecraft.world.level.LevelAccessor level,
                                   net.minecraft.world.level.block.state.BlockState state,
                                   BlockPos pos) {
        return !state.isAir() && state.getDestroySpeed(level, pos) >= 0;
    }

    /**
     * Как {@link #canBreak(BlockPos)}, но с внятной ошибкой вместо молчаливого
     * пропуска. Ставится на точку, по которой кликнули: игрок должен увидеть
     * «этот блок нельзя сломать», а не «не сломалось ничего».
     */
    public void requireBreakable(BlockPos pos) {
        if (!canBreak(pos)) {
            throw new SpellException("cast.undestroyable", blockName(pos));
        }
    }

    private String blockName(BlockPos pos) {
        if (level == null) {
            return "";
        }
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(level.getBlockState(pos).getBlock()).getPath();
    }

    /** Дальность + загруженность: обязательный минимум для записи в мир. */
    public void requireWritable(BlockPos pos) {
        requireInRange(pos);
        requireLoaded(pos);
    }

    /**
     * Проверяет, что кастер вообще имеет право навредить этой цели.
     *
     * <p>Нужна модулям урона, лечения и отбрасывания. Их обычный
     * {@code LivingEntity.hurt()} идёт мимо {@code Player.attack()}, а значит
     * мимо и PvP-правил сервера, и плагинов защиты, которые вешаются на
     * {@code AttackEntityEvent}. Без этой проверки заклинание было бы способом
     * бить игроков там, где сервер запрещает.
     *
     * <p>Для игрока используется тот же хук NeoForge, что и в обычной атаке
     * ({@code CommonHooks.onPlayerAttackTarget}), поэтому правила совпадают с
     * ванилльными и срабатывают те же отмены от плагинов.
     */
    public void requireCanHarm(Entity target) {
        requireEntityInRange(target);
        if (!(caster instanceof LivingEntity attacker)) {
            return;
        }
        // «мирная» сложность, неуязвимость цели, зритель
        if (!attacker.canAttack((LivingEntity) target)) {
            throw new SpellException("cast.cant_harm");
        }
        if (target instanceof net.minecraft.world.entity.player.Player victim
                && attacker instanceof net.minecraft.world.entity.player.Player duelist
                && !net.neoforged.neoforge.common.CommonHooks
                .onPlayerAttackTarget(duelist, victim)) {
            throw new SpellException("cast.cant_harm");
        }
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

    /**
     * Освобождает ячейку и возвращает её значение.
     *
     * <p>Индекс проверяется здесь же, а не полагается на {@link #load(int)}:
     * {@code load} для неверного индекса возвращает {@code null}, и запись в
     * массив следом упала бы с {@code ArrayIndexOutOfBounds}.
     *
     * @return значение ячейки или null, если индекс неверный или ячейка пуста
     */
    public SpellValue take(int slot) {
        if (!validSlot(slot)) {
            return null;
        }
        SpellValue value = slots[slot];
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

    // --- Энергия бездны (заглушка энергосистемы) ---

    public int manaSpent() {
        return manaSpent;
    }

    /** Вызывается исполнителем сразу после списания полной стоимости графа. */
    public void markPrepaid(int amount) {
        this.prepaidMana = amount;
    }

    /**
     * Сколько энергии бездны ещё можно добрать модулями прямо сейчас.
     *
     * <p>Исполнитель снимает полную стоимость графа до обхода, а модули в
     * мгновенную сдачу только учитывают свои траты в счётчике — реальное
     * списание случится в конце каста. Поэтому «остаток» здесь равен тому,
     * что лежит в кошельке, минус уже учтённое, но ещё не списанное.
     *
     * <p>Раньше вместо этого проверяли {@code hasMana(...)} прямо в цикле
     * добычи: значение в кошельке до конца каста не меняется, поэтому такая
     * проверка либо проходила все итерации, либо падала на первой — добыча
     * жила в том или ином случае не по факту потраченной энергии бездны.
     */
    public int remainingManaBudget() {
        if (!(caster instanceof net.minecraft.world.entity.player.Player p)) {
            return 0;
        }
        if (com.github.fireprime5.spellcraft.energy.AbyssEnergy.isInfinite(p)) {
            return Integer.MAX_VALUE;
        }
        int extra = Math.max(0, manaSpent - prepaidMana);
        return Math.max(0,
                com.github.fireprime5.spellcraft.energy.AbyssPool.total(p) - extra);
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
     * Учитывает потраченную энергию бездны.
     *
     * <p>При бесконечной энергии бездны в счётчик идёт ноль, а не номинал: по счётчику
     * потом считается, сколько игрок реально потратил, и без этого панель
     * сообщала бы «каст за 40 энергии бездны», хотя ничего не списалось.
     */
    public void spendMana(int amount) {
        manaSpent += caster instanceof net.minecraft.world.entity.player.Player p
                ? com.github.fireprime5.spellcraft.energy.AbyssEnergy.spentAmount(p, amount)
                : amount;
    }

    /**
     * Хватает ли энергии бездны на каст.
     *
     * <p>Проверка идёт через {@code AbyssEnergy.has}, а не через сравнение
     * с текущим значением: бесконечная энергия бездны хранится отдельным флагом и не
     * влияет на само число в кошельке, поэтому сравнение «хватает ли» её бы
     * отклонило, и заклинание падало бы с «не хватает энергии бездны».
     */
    public boolean hasMana(int amount) {
        return caster instanceof net.minecraft.world.entity.player.Player p
                && com.github.fireprime5.spellcraft.energy.AbyssEnergy.has(p, amount);
    }

    public void spend(int amount) {
        if (caster instanceof net.minecraft.world.entity.player.Player p) {
            com.github.fireprime5.spellcraft.energy.AbyssEnergy.consume(p, amount);
        }
    }

    /** Множители брони (id модуля -> значение), которые модули пишут при исполнении. */
    private final java.util.Map<String, Double> armorModifiers = new java.util.HashMap<>();

    /**
     * Записать боевой множитель брони (используется модулями-модификаторами).
     *
     * <p>Броня не исполняется как заклинание: её схему читают каждый тик, и
     * модуль-модификатор кладёт сюда свой множитель, а {@code ArmorEvents} затем
     * применяет его к игроку. Значение 1.0 означает «без изменений».
     */
    public void putArmorModifier(String id, double multiplier) {
        armorModifiers.put(id, multiplier);
    }

    /** Значение боевого множителя брони или 1.0, если модуль его не выставил. */
    public double armorModifier(String id) {
        return armorModifiers.getOrDefault(id, 1.0);
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