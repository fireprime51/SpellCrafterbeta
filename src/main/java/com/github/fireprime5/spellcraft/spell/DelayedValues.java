package com.github.fireprime5.spellcraft.spell;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Отложенные значения: очередь, из которой что-то становится доступным не сразу.
 *
 * <p>Задержка внутри одного каста невозможна: исполнитель считает граф сверху
 * вниз за один проход и не умеет ждать. Ждать он и не должен — тогда заклинание
 * подвисало бы на кадры и мир стоял бы ради игрока.
 *
 * <p>Поэтому задержка сделана очередью, а не паузой. {@code delay_push} кладёт
 * значение «на потом», {@code delay_pop} забирает самое старое из тех, что уже
 * дозрело. Схема «бросить и забыть» и «забрать попозже» получается из двух
 * кастов: первый кладёт, второй — хоть через секунду, хоть через двадцать.
 *
 * <p>Очередь живёт между кастами намеренно: ради этого она и нужна. Иначе
 * «через 40 тиков» означало бы «до конца этого же каста», то есть ничего.
 */
public final class DelayedValues {

    /** Сколько значений может ждать одного игрока. */
    public static final int MAX_PENDING = 256;

    /** Значение и тик, с которого оно доступно. */
    private record Pending(SpellValue value, long due) {
    }

    /** Очереди по игрокам. */
    private static final Map<UUID, Deque<Pending>> QUEUES = new ConcurrentHashMap<>();

    private DelayedValues() {
    }

    /**
     * Отложить значение до указанного тика.
     *
     * @return {@code false}, если очередь переполнена или значение пустое
     */
    public static boolean push(UUID id, SpellValue value, long due) {
        if (value == null) {
            return false;
        }
        Deque<Pending> очередь = QUEUES.computeIfAbsent(id, k -> new ArrayDeque<>());
        synchronized (очередь) {
            if (очередь.size() >= MAX_PENDING) {
                // молча выкидывать нельзя: значение потерялось бы, и заклинание,
                // ждавшее его, тихо сошло бы на нет
                return false;
            }
            очередь.addLast(new Pending(value, due));
            return true;
        }
    }

    /**
     * Забрать самое старое значение, дозревшее не позже {@code неПозже}.
     *
     * @return значение или {@code null}, если ещё не дозрело ни одно
     */
    public static SpellValue pop(UUID id, long неПозже) {
        Deque<Pending> очередь = QUEUES.get(id);
        if (очередь == null) {
            return null;
        }
        synchronized (очередь) {
            // Перебор, а не «взять голову»: очередь упорядочена по порядку
            // добавления, и значение с дальним сроком, положенное первым, иначе
            // затыкало бы всё, что за ним уже дозрело. Например «через 500,
            // потом через 100» — на сотом тике второй уже готов, а первый нет.
            for (java.util.Iterator<Pending> it = очередь.iterator(); it.hasNext(); ) {
                Pending п = it.next();
                if (п.due() <= неПозже) {
                    it.remove();
                    return п.value();
                }
            }
            return null;
        }
    }

    /** Сколько значений ждёт. */
    public static int pending(UUID id) {
        Deque<Pending> очередь = QUEUES.get(id);
        if (очередь == null) {
            return 0;
        }
        synchronized (очередь) {
            return очередь.size();
        }
    }

    public static void clear(UUID id) {
        QUEUES.remove(id);
    }

    /** Только для тестов. */
    public static void reset() {
        QUEUES.clear();
    }
}