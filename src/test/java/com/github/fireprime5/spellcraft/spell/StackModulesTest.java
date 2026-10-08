package com.github.fireprime5.spellcraft.spell;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Модули стека: регистрация и семантика.
 */
class StackModulesTest {

    @BeforeAll
    static void setUp() {
        SpellModules.init();
    }

    private static SpellContext ctx() {
        return new SpellContext(null, null, net.minecraft.world.phys.Vec3.ZERO);
    }

    private static SpellModuleType module(String id) {
        SpellModuleType type = SpellModules.get(SpellModules.id(id));
        assertNotNull(type, "нет модуля " + id);
        return type;
    }

    /**
     * Выполнить модуль и вернуть его выходы.
     *
     * <p>Список собирается через ArrayList, а не List.of: null в массиве —
     * это неподключённый необязательный вход, а List.of такие элементы не
     * принимает вовсе.
     */
    private static List<SpellValue> run(SpellContext ctx, String id,
                                        SpellValue... inputs) {
        List<SpellValue> in = new java.util.ArrayList<>();
        Collections.addAll(in, inputs);
        List<SpellValue> out = new java.util.ArrayList<>();
        module(id).execute(ctx, in, out);
        return out;
    }

    /** Выполнить модуль, ожидая ошибку, и вернуть её ключ. */
    private static String failure(SpellContext ctx, String id, SpellValue... inputs) {
        try {
            run(ctx, id, inputs);
            org.junit.jupiter.api.Assertions.fail("ожидалась ошибка от " + id);
            return "";
        } catch (SpellContext.SpellException e) {
            return e.getKey();
        }
    }

    @Test
    void модулиСтекаЗарегистрированы() {
        for (String id : new String[]{"stack_push", "stack_pop", "stack_dup", "stack_drop",
                "stack_clear", "stack_size"}) {
            assertNotNull(SpellModules.get(SpellModules.id(id)), "нет модуля " + id);
        }
    }

    @Test
    void цикловыеМодулиЗарегистрированы() {
        for (String id : new String[]{"stack_repeat", "stack_swap", "stack_pick",
                "stack_drop_n", "stack_exit", "stack_skip",
                "stack_store", "stack_load", "stack_slots_used",
                "stack_clear_slots"}) {
            assertNotNull(SpellModules.get(SpellModules.id(id)), "нет модуля " + id);
        }
    }

    @Test
    void pushКладётВСтек() {
        SpellContext ctx = new SpellContext(null, null, net.minecraft.world.phys.Vec3.ZERO);
        var type = SpellModules.get(SpellModules.id("stack_push"));
        List<SpellValue> out = new java.util.ArrayList<>();
        type.execute(ctx, List.of(SpellValue.of(5)), out);
        assertEquals(1, ctx.stackSize(), "значение должно лежать в стеке");
    }

    @Test
    void popЗабираетВерхнее() {
        SpellContext ctx = new SpellContext(null, null, net.minecraft.world.phys.Vec3.ZERO);
        ctx.push(SpellValue.of(1));
        ctx.push(SpellValue.of(7));
        var pop = SpellModules.get(SpellModules.id("stack_pop"));
        List<SpellValue> out = new java.util.ArrayList<>();
        pop.execute(ctx, List.of(), out);
        assertEquals(1, ctx.stackSize());
        assertEquals(7, out.get(0).asNumber(), "берётся верхнее значение");
    }

    @Test
    void popПустогоСтекаДаётОшибку() {
        SpellContext ctx = new SpellContext(null, null, net.minecraft.world.phys.Vec3.ZERO);
        var pop = SpellModules.get(SpellModules.id("stack_pop"));
        List<SpellValue> out = new java.util.ArrayList<>();
        try {
            pop.execute(ctx, List.of(), out);
            org.junit.jupiter.api.Assertions.fail("ожидалась ошибка пустого стека");
        } catch (SpellContext.SpellException e) {
            assertEquals("stack.underflow", e.getKey());
        }
    }

    @Test
    void dupИsizeРаботают() {
        SpellContext ctx = new SpellContext(null, null, net.minecraft.world.phys.Vec3.ZERO);
        ctx.push(SpellValue.of(3));
        var dup = SpellModules.get(SpellModules.id("stack_dup"));
        List<SpellValue> out = new java.util.ArrayList<>();
        dup.execute(ctx, List.of(), out);
        assertEquals(2, ctx.stackSize());

        var size = SpellModules.get(SpellModules.id("stack_size"));
        List<SpellValue> out2 = new java.util.ArrayList<>();
        size.execute(ctx, List.of(), out2);
        assertEquals(2, out2.get(0).asNumber());

        SpellModules.get(SpellModules.id("stack_clear")).execute(ctx, List.of(), new java.util.ArrayList<>());
        assertEquals(0, ctx.stackSize());
    }

    @Test
    void контекстУмеетОтвечать() {
        SpellContext ctx = new SpellContext(null, null, net.minecraft.world.phys.Vec3.ZERO);
        assertTrue(ctx.stackIsEmpty());
        ctx.push(SpellValue.of(1));
        assertTrue(!ctx.stackIsEmpty());
        assertEquals("[1]", ctx.stackString());
    }

    // ------------------------------------------------------------- циклы

    /**
     * Повтор раскладывает верхнее значение на стеке.
     *
     * <p>Так выглядит цикл без повторяющихся узлов в схеме: значение
     * размножается, а «Сдвинуть» и «Отбросить» разбирают копии по одной.
     */
    @Test
    void повторКладётНесколькоКопий() {
        SpellContext ctx = ctx();
        ctx.push(SpellValue.of(9));
        run(ctx, "stack_repeat", SpellValue.of(4));
        assertEquals(4, ctx.stackSize(), "значение должно лежать в стеке 4 раза");
    }

    /** Повтор не может переполнить стек: иначе цикл съел бы память сервера. */
    @Test
    void повторОграниченРазмеромСтека() {
        SpellContext ctx = ctx();
        ctx.push(SpellValue.of(1));
        run(ctx, "stack_repeat", SpellValue.of(100_000));
        assertTrue(ctx.stackSize() <= SpellContext.MAX_STACK,
                "стек разросся: " + ctx.stackSize());
    }

    /** Ноль повторов — не ошибка: просто ничего не раскладываем. */
    @Test
    void нольПовторовНеОшибка() {
        SpellContext ctx = ctx();
        ctx.push(SpellValue.of(5));
        run(ctx, "stack_repeat", SpellValue.of(0));
        assertEquals(1, ctx.stackSize(), "значение осталось на месте");
    }

    @Test
    void повторПустогоСтекаДаётОшибку() {
        assertEquals("stack.underflow", failure(ctx(), "stack_repeat", SpellValue.of(3)));
    }

    /** Сдвиг поднимает глубинное значение наверх, не теряя остальные. */
    @Test
    void сдвигМеняетМестамиВерхнееИГлубинное() {
        SpellContext ctx = ctx();
        ctx.push(SpellValue.of(1));
        ctx.push(SpellValue.of(2));
        ctx.push(SpellValue.of(3));
        run(ctx, "stack_swap", SpellValue.of(3));
        assertEquals(1, run(ctx, "stack_pop").get(0).asNumber(), "глубинное наверху");
        assertEquals(2, run(ctx, "stack_pop").get(0).asNumber());
        assertEquals(3, run(ctx, "stack_pop").get(0).asNumber());
    }

    /** Сдвиг на глубину больше стека — ошибка, а не тихая порча стека. */
    @Test
    void сдвигЗаПределыСтекаДаётОшибку() {
        SpellContext ctx = ctx();
        ctx.push(SpellValue.of(1));
        assertEquals("stack.depth", failure(ctx, "stack_swap", SpellValue.of(9)));
    }

    /** Взятие копирует значение, не убирая его из стека. */
    @Test
    void выборКопируетГлубинное() {
        SpellContext ctx = ctx();
        ctx.push(SpellValue.of(4));
        ctx.push(SpellValue.of(5));
        List<SpellValue> out = run(ctx, "stack_pick", SpellValue.of(2));
        assertEquals(4, out.get(0).asNumber(), "берётся второе сверху");
        assertEquals(2, ctx.stackSize(), "значение осталось на стеке");
    }

    @Test
    void выборЗаПределыСтекаДаётОшибку() {
        SpellContext ctx = ctx();
        ctx.push(SpellValue.of(1));
        assertEquals("stack.depth", failure(ctx, "stack_pick", SpellValue.of(5)));
    }

    /** Отбросить N снимает ровно N верхних значений. */
    @Test
    void отброситьНесколько() {
        SpellContext ctx = ctx();
        ctx.push(SpellValue.of(1));
        ctx.push(SpellValue.of(2));
        ctx.push(SpellValue.of(3));
        run(ctx, "stack_drop_n", SpellValue.of(2));
        assertEquals(1, ctx.stackSize());
        assertEquals(1, ctx.pop().asNumber());
    }

    /** Больше, чем есть на стеке, снимает всё и не падает. */
    @Test
    void отброситьБольшеЧемЕстьНеОшибка() {
        SpellContext ctx = ctx();
        ctx.push(SpellValue.of(1));
        run(ctx, "stack_drop_n", SpellValue.of(50));
        assertTrue(ctx.stackIsEmpty());
    }

    /** Без числа «Отбросить N» снимает одно значение. */
    @Test
    void отброситьБезЧислаСнимаетОдно() {
        SpellContext ctx = ctx();
        ctx.push(SpellValue.of(1));
        ctx.push(SpellValue.of(2));
        run(ctx, "stack_drop_n", new SpellValue[]{null});
        assertEquals(1, ctx.stackSize());
    }

    /** Условный выход: ноль — продолжаем, ненулевое — останавливаем каст. */
    @Test
    void выходОстанавливаетКастПоУсловию() {
        SpellContext ctx = ctx();
        run(ctx, "stack_exit", SpellValue.of(1));
        assertTrue(ctx.halted());

        SpellContext other = ctx();
        run(other, "stack_exit", SpellValue.of(0));
        assertTrue(!other.halted(), "при ложном условии каст идёт дальше");
    }

    /** Без условия «Выход» срабатывает всегда — это обычное завершение. */
    @Test
    void выходБезУсловияВсегдаОстанавливает() {
        SpellContext ctx = ctx();
        run(ctx, "stack_exit", new SpellValue[]{null});
        assertTrue(ctx.halted());
    }

    @Test
    void пропуститьОстанавливаетТолькоПриЛожномУсловии() {
        SpellContext ctx = ctx();
        run(ctx, "stack_skip", SpellValue.of(0));
        assertTrue(ctx.halted());

        SpellContext other = ctx();
        run(other, "stack_skip", SpellValue.of(1));
        assertTrue(!other.halted());
    }

    /** Досрочный выход не должен выглядеть как ошибка каста. */
    @Test
    void досрочныйВыходНеОшибка() {
        SpellContext ctx = ctx();
        run(ctx, "stack_exit", SpellValue.of(1));
        assertTrue(!ctx.failed(), "выход не должен попадать в ошибки");
    }

    // -------------------------------------------------------------- ячейки

    /** Ячейка переживает цикл: положили, прогнали стек, забрали. */
    @Test
    void ячейкаПереживаетРасходСтека() {
        SpellContext ctx = ctx();
        run(ctx, "stack_store", SpellValue.of(42), SpellValue.of(0));
        for (int i = 0; i < 5; i++) {
            ctx.push(SpellValue.of(i));
            ctx.pop();
        }
        assertEquals(42, run(ctx, "stack_load", SpellValue.of(0)).get(0).asNumber());
    }

    /**
     * Занятую ячейку не перезаписываем.
     *
     * <p>Иначе цикл, кладущий значение каждый шаг, молча испортил бы то,
     * что было сохранено до него.
     */
    @Test
    void занятуюЯчейкуНеПерезаписать() {
        SpellContext ctx = ctx();
        run(ctx, "stack_store", SpellValue.of(1), SpellValue.of(2));
        assertEquals("slot.busy", failure(ctx, "stack_store",
                SpellValue.of(9), SpellValue.of(2)));
        assertEquals(1, ctx.load(2).asNumber(), "старое значение на месте");
    }

    @Test
    void пустуюЯчейкуПрочитатьНельзя() {
        assertEquals("slot.empty", failure(ctx(), "stack_load", SpellValue.of(3)));
    }

    /** Взятие освобождает ячейку: её можно использовать снова. */
    @Test
    void взятиеОсвобождаетЯчейку() {
        SpellContext ctx = ctx();
        run(ctx, "stack_store", SpellValue.of(7), SpellValue.of(1));
        run(ctx, "stack_load", SpellValue.of(1));
        assertTrue(ctx.slotFree(1));
        run(ctx, "stack_store", SpellValue.of(8), SpellValue.of(1));
        assertEquals(8, ctx.load(1).asNumber());
    }

    /**
     * Номер ячейки приводится к целому и зажимается.
     *
     * <p>Из заклинания приходит любое число, и «-1» или «999» не должны ни
     * падать, ни писать мимо массива.
     */
    @Test
    void номерЯчейкиЗажимается() {
        SpellContext ctx = ctx();
        run(ctx, "stack_store", SpellValue.of(1), SpellValue.of(-5));
        assertEquals(1, ctx.load(0).asNumber(), "отрицательное число -> первая ячейка");

        SpellContext high = ctx();
        run(high, "stack_store", SpellValue.of(1), SpellValue.of(9999));
        assertEquals(1, high.load(SpellContext.SLOTS - 1).asNumber(),
                "большое число -> последняя ячейка");
    }

    @Test
    void счётчикЯчеекСчитает() {
        SpellContext ctx = ctx();
        assertEquals(0, run(ctx, "stack_slots_used").get(0).asNumber());
        run(ctx, "stack_store", SpellValue.of(1), SpellValue.of(0));
        run(ctx, "stack_store", SpellValue.of(2), SpellValue.of(1));
        assertEquals(2, run(ctx, "stack_slots_used").get(0).asNumber());
    }

    @Test
    void очисткаЯчеекОсвобождаетВсе() {
        SpellContext ctx = ctx();
        run(ctx, "stack_store", SpellValue.of(1), SpellValue.of(0));
        run(ctx, "stack_store", SpellValue.of(2), SpellValue.of(1));
        run(ctx, "stack_clear_slots");
        assertEquals(0, ctx.slotsUsed());
        assertEquals("slot.empty", failure(ctx, "stack_load", SpellValue.of(0)));
    }
}