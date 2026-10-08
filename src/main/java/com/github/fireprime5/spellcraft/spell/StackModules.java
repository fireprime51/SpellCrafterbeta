package com.github.fireprime5.spellcraft.spell;

import java.util.List;

import static com.github.fireprime5.spellcraft.spell.PortType.ANY;
import static com.github.fireprime5.spellcraft.spell.PortType.NUMBER;

/**
 * Модули работы со стеком заклинания (как в Hex Casting).
 *
 * Стек — это значения, которые копятся во время каста. Обычные модули
 * передают значения по соединениям, а эти кладут и берут их из стека.
 */
public final class StackModules {

    private StackModules() {
    }

    private static final SpellModuleType.Category LOG = SpellModuleType.Category.LOGIC;
    private static final int C_STACK = 0xFF4FB0C8;

    public static void registerAll() {
        // положить значение в стек
        SpellModules.register(new SpellModuleType(SpellModules.id("stack_push"), LOG, C_STACK) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 0; }
            @Override public int manaCost() { return 3; }
            @Override public PortType[] inputPorts() { return new PortType[]{ANY}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                SpellValue value = in.get(0);
                if (value == null) {
                    throw new SpellContext.SpellException("graph.missing_input");
                }
                ctx.push(value);
                ctx.trace("push " + value.describe().getString());
            }
        });

        // взять верхнее значение из стека
        SpellModules.register(new SpellModuleType(SpellModules.id("stack_pop"), LOG, C_STACK) {
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 3; }
            @Override public PortType[] outputPorts() { return new PortType[]{ANY}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                SpellValue value = ctx.pop();
                ctx.trace("pop " + value.describe().getString());
                out.add(value);
            }
        });

        // дублировать верхнее значение
        SpellModules.register(new SpellModuleType(SpellModules.id("stack_dup"), LOG, C_STACK) {
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 4; }
            @Override public PortType[] outputPorts() { return new PortType[]{ANY}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                SpellValue value = ctx.peek();
                ctx.push(value);
                ctx.trace("dup " + value.describe().getString());
                out.add(value);
            }
        });

        // выбросить верхнее значение
        SpellModules.register(new SpellModuleType(SpellModules.id("stack_drop"), LOG, C_STACK) {
            @Override public int outputs() { return 0; }
            @Override public int manaCost() { return 1; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                SpellValue value = ctx.pop();
                ctx.trace("drop " + value.describe().getString());
            }
        });

        // очистить стек
        SpellModules.register(new SpellModuleType(SpellModules.id("stack_clear"), LOG, C_STACK) {
            @Override public int outputs() { return 0; }
            @Override public int manaCost() { return 4; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                int size = ctx.stackSize();
                for (int i = 0; i < size; i++) {
                    ctx.pop();
                }
                ctx.trace("clear stack (" + size + ")");
            }
        });

        // размер стека
        SpellModules.register(new SpellModuleType(SpellModules.id("stack_size"), LOG, C_STACK) {
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 1; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                out.add(SpellValue.of(ctx.stackSize()));
                ctx.trace("stack size = " + ctx.stackSize());
            }
        });

        loops();
        cells();
    }

    // ------------------------------------------------------------- циклы

    /**
     * Циклы по стеку.
     *
     * <p>Схема заклинания — ациклический граф, повторить узел в нём нельзя.
     * Цикл собирается иначе: «Повтор» раскладывает значение на стеке N копий,
     * а «Сдвинуть» и «Отбросить» убирают по одной. Получается перебор
     * элементов без единого повторяющегося блока в схеме.
     *
     * <p>Цикл всегда конечен: количество повторов задаётся числом и
     * ограничено {@link SpellContext#MAX_STACK}, поэтому заклинание не может
     * зависнуть.
     */
    private static void loops() {
        // Повтор: положить на стек N копий верхнего значения.
        // Вход — сколько раз; само значение берётся со стека.
        SpellModules.register(new SpellModuleType(SpellModules.id("stack_repeat"),
                LOG, C_STACK) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 6; }
            @Override public PortType[] inputPorts() { return new PortType[]{NUMBER}; }
            @Override public PortType[] outputPorts() { return new PortType[]{ANY}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                int times = (int) Math.clamp(Math.round(in.get(0).asNumber()), 0,
                        SpellContext.MAX_STACK);
                if (times <= 0) {
                    // ноль повторов — не ошибка: просто ничего не разложить.
                    // Значение при этом остаётся на стеке, как его и оставил
                    // тот модуль, что его положил.
                    ctx.trace("repeat 0");
                    return;
                }
                SpellValue value = ctx.pop();
                int placed = 0;
                for (int i = 0; i < times; i++) {
                    if (!ctx.tryPush(value)) {
                        break;
                    }
                    placed++;
                }
                ctx.trace("repeat x" + placed);
                if (placed > 0) {
                    out.add(value);
                }
            }
        });

        // Сдвинуть: поднять N-е значение сверху наверх.
        // Нумерация как в «Взять»: 1 — самое верхнее.
        SpellModules.register(new SpellModuleType(SpellModules.id("stack_swap"),
                LOG, C_STACK) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 4; }
            @Override public PortType[] inputPorts() { return new PortType[]{NUMBER}; }
            @Override public PortType[] outputPorts() { return new PortType[]{ANY}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                int depth = depth(in.get(0), ctx);
                if (depth == 1) {
                    // взять верхнее и положить обратно — ничего не меняется,
                    // но ошибкой это не считаем: так ведёт себя любой стек
                    ctx.trace("swap 1 (no-op)");
                    out.add(ctx.peek());
                    return;
                }
                // снимок идёт сверху вниз: peek(0) — верхнее значение
                List<SpellValue> items = ctx.stackSnapshot();
                SpellValue top = items.get(0);
                SpellValue deep = items.get(depth - 1);
                List<SpellValue> swapped = new java.util.ArrayList<>(items);
                swapped.set(0, deep);
                swapped.set(depth - 1, top);
                rebuild(ctx, swapped);
                ctx.trace("swap " + depth + ": " + deep.describe().getString()
                        + " <-> " + top.describe().getString());
                out.add(deep);
            }
        });

        // Выбор: взять N-е значение сверху, не убирая его (копия).
        SpellModules.register(new SpellModuleType(SpellModules.id("stack_pick"),
                LOG, C_STACK) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 4; }
            @Override public PortType[] inputPorts() { return new PortType[]{NUMBER}; }
            @Override public PortType[] outputPorts() { return new PortType[]{ANY}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                int depth = depth(in.get(0), ctx);
                SpellValue value = atDepth(ctx, depth);
                ctx.trace("pick " + depth + ": " + value.describe().getString());
                out.add(value);
            }
        });

        // Отбросить N значений сверху (по умолчанию одно).
        SpellModules.register(new SpellModuleType(SpellModules.id("stack_drop_n"),
                LOG, C_STACK) {
            @Override public int inputs() { return 1; }
            @Override public int optionalInputs() { return 1; }
            @Override public int outputs() { return 0; }
            @Override public int manaCost() { return 1; }
            @Override public PortType[] inputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                int count = in.get(0) == null ? 1
                        : Math.clamp((int) Math.round(in.get(0).asNumber()), 0,
                        SpellContext.MAX_STACK);
                int dropped = 0;
                for (int i = 0; i < count && !ctx.stackIsEmpty(); i++) {
                    ctx.pop();
                    dropped++;
                }
                ctx.trace("drop n=" + dropped);
            }
        });

        // Выход: прекратить каст. Не ошибка — так заклинание заканчивается
        // раньше остальных узлов, например внутри цикла.
        SpellModules.register(new SpellModuleType(SpellModules.id("stack_exit"),
                LOG, C_STACK) {
            @Override public int inputs() { return 1; }
            @Override public int optionalInputs() { return 1; }
            @Override public int outputs() { return 0; }
            @Override public int manaCost() { return 2; }
            @Override public PortType[] inputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                SpellValue condition = in.get(0);
                // без условия выход всегда срабатывает; с условием — когда оно
                // истинно. Ноль считается ложью, всё остальное истиной.
                boolean stop = condition == null || condition.asNumber() != 0;
                if (stop) {
                    ctx.halt();
                }
                ctx.trace("exit " + (stop ? "stop" : "pass"));
            }
        });

        // Пропустить: выйти, если условие ложно. Обратная сторона «Выхода»:
        // удобно ставить в начале цикла, чтобы выкинуть лишний элемент.
        SpellModules.register(new SpellModuleType(SpellModules.id("stack_skip"),
                LOG, C_STACK) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 0; }
            @Override public int manaCost() { return 3; }
            @Override public PortType[] inputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                if (in.get(0).asNumber() == 0) {
                    ctx.halt();
                    ctx.trace("skip");
                }
            }
        });
    }

    // -------------------------------------------------------------- ячейки

    /**
     * Ячейки хранения: значение кладётся в ячейку и достаётся позже.
     *
     * <p>Отличие от стека — в живучести. В цикле стек каждый шаг расходуется,
     * а ячейка переживает его целиком: положил до цикла, взял после.
     */
    private static void cells() {
        SpellModules.register(new SpellModuleType(SpellModules.id("stack_store"),
                LOG, C_STACK) {
            @Override public int inputs() { return 2; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 4; }
            @Override public PortType[] inputPorts() { return new PortType[]{ANY, NUMBER}; }
            @Override public PortType[] outputPorts() { return new PortType[]{ANY}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                int slot = ctx.slotIndex(in.get(1));
                SpellValue value = in.get(0);
                if (!ctx.store(slot, value)) {
                    // занятую ячейку не перезаписываем: иначе в цикле можно было бы
                    // незаметно испортить уже сохранённое значение
                    throw new SpellContext.SpellException("slot.busy", slot);
                }
                ctx.trace("store " + slot + ": " + value.describe().getString());
                out.add(value);
            }
        });

        SpellModules.register(new SpellModuleType(SpellModules.id("stack_load"),
                LOG, C_STACK) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 3; }
            @Override public PortType[] inputPorts() { return new PortType[]{NUMBER}; }
            @Override public PortType[] outputPorts() { return new PortType[]{ANY}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                int slot = ctx.slotIndex(in.get(0));
                SpellValue value = ctx.take(slot);
                if (value == null) {
                    throw new SpellContext.SpellException("slot.empty", slot);
                }
                ctx.trace("load " + slot + ": " + value.describe().getString());
                out.add(value);
            }
        });

        SpellModules.register(new SpellModuleType(SpellModules.id("stack_slots_used"),
                LOG, C_STACK) {
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 1; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                out.add(SpellValue.of(ctx.slotsUsed()));
            }
        });

        SpellModules.register(new SpellModuleType(SpellModules.id("stack_clear_slots"),
                LOG, C_STACK) {
            @Override public int outputs() { return 0; }
            @Override public int manaCost() { return 4; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                int freed = 0;
                for (int i = 0; i < SpellContext.SLOTS; i++) {
                    if (ctx.take(i) != null) {
                        freed++;
                    }
                }
                ctx.trace("freed " + freed + " slots");
            }
        });
    }

    // -------------------------------------------------------------- хелперы

    /**
     * Глубина из числа: округляется и не даёт уйти ниже верха.
     *
     * <p>Ноль и отрицательные числа значат «самое верхнее», а не ошибку: иначе
     * заклинание, где глубина считается вычитанием, падало бы на первом шаге.
     *
     * <p>Слишком глубокое значение, наоборот, честно ругается: просить 10-е
     * значение при трёх на стеке — ошибка самого заклинания, и молча
     * подставить «последнее» значило бы скрыть её от игрока.
     */
    private static int depth(SpellValue value, SpellContext ctx) {
        if (value == null) {
            return Math.max(1, ctx.stackSize());
        }
        int depth = (int) Math.round(value.asNumber());
        if (depth < 1) {
            return 1;
        }
        if (depth > ctx.stackSize()) {
            throw new SpellContext.SpellException("stack.depth", depth);
        }
        return depth;
    }

    /**
     * Пересобирает стек из списка, заданного сверху вниз.
     *
     * <p>Значения между переставляемыми нельзя терять, а «взять и положить
     * обратно» по одной требует знать промежуточные. Поэтому проще пересобрать
     * стек целиком из снимка — так пропустить значение невозможно в принципе.
     */
    private static void rebuild(SpellContext ctx, List<SpellValue> topToBottom) {
        while (!ctx.stackIsEmpty()) {
            ctx.pop();
        }
        for (int i = topToBottom.size() - 1; i >= 0; i--) {
            ctx.push(topToBottom.get(i));
        }
    }

    /** Значение на глубине 1 — самое верхнее. Глубину проверяет {@link #depth}. */
    private static SpellValue atDepth(SpellContext ctx, int depth) {
        List<SpellValue> items = ctx.stackSnapshot();
        if (depth > items.size()) {
            throw new SpellContext.SpellException("stack.depth", depth);
        }
        return items.get(depth - 1);
    }
}