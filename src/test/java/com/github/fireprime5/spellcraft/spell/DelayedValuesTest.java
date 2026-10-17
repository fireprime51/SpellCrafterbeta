package com.github.fireprime5.spellcraft.spell;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Отложенные значения — очередь задержки между кастами.
 *
 * <p>Графа, который умеет ждать, в моде нет и быть не должно: пауза посреди
 * каста подвесила бы кадр у всех, кто рядом. Задержка сделана очередью, и вся
 * её логика проверяется здесь обычным тестом — {@code DelayedValues} не
 * касается мира.
 */
class DelayedValuesTest {

    private UUID игрок;

    @BeforeEach
    void чисто() {
        DelayedValues.reset();
        игрок = UUID.randomUUID();
    }

    /** Значение дозревает только к своему тику, не раньше. */
    @Test
    void значениеДозреваетНеРаньшеСрока() {
        DelayedValues.push(игрок, SpellValue.of(5), 1000);
        assertNull(DelayedValues.pop(игрок, 999), "раньше срока брать нечего");
        assertNotNull(DelayedValues.pop(игрок, 1000), "в срок значение уже доступно");
    }

    /** Забирается самое старое — иначе порядок отложенного зависел бы от того,
     *  как именно его клали. */
    @Test
    void забираетсяСамоеСтарое() {
        DelayedValues.push(игрок, SpellValue.of(1), 100);
        DelayedValues.push(игрок, SpellValue.of(2), 100);
        assertEquals(1.0, DelayedValues.pop(игрок, 100).asNumber(), 1e-9);
        assertEquals(2.0, DelayedValues.pop(игрок, 100).asNumber(), 1e-9);
    }

    /** Разные сроки: взять позднее нельзя, не задев раннее. */
    @Test
    void раннееНеМешаетЖдатьПозднее() {
        DelayedValues.push(игрок, SpellValue.of(2), 500);
        DelayedValues.push(игрок, SpellValue.of(1), 100);
        assertEquals(1.0, DelayedValues.pop(игрок, 100).asNumber(), 1e-9);
        assertNull(DelayedValues.pop(игрок, 400), "позднее ещё не дозрело");
        assertNotNull(DelayedValues.pop(игрок, 500));
    }

    /**
     * Переполненная очередь обязана отказывать, а не выкидывать молча.
     *
     * <p>Тихое отбрасывание — это значение потерялось, и заклинание, ждавшее
     * его, сошло бы на нет без единого слова.
     */
    @Test
    void переполненнаяОчередьОтказывает() {
        for (int i = 0; i < DelayedValues.MAX_PENDING; i++) {
            assertTrue(DelayedValues.push(игрок, SpellValue.of(i), 1000), "место должно быть");
        }
        assertEquals(DelayedValues.MAX_PENDING, DelayedValues.pending(игрок));
        assertFalse(DelayedValues.push(игрок, SpellValue.of(999), 1000),
                "в переполненную очередь принимать нельзя");
    }

    /** Пустое значение откладывать нечего — иначе в очереди будет дыра. */
    @Test
    void пустоеЗначениеНеОткладывается() {
        assertFalse(DelayedValues.push(игрок, null, 1000));
        assertEquals(0, DelayedValues.pending(игрок));
    }

    /** Чужие игроки не видят чужой очереди. */
    @Test
    void очередьУКаждогоСвоя() {
        DelayedValues.push(игрок, SpellValue.of(7), 1000);
        assertNull(DelayedValues.pop(UUID.randomUUID(), 5000),
                "чужая очередь не должна отдавать значение");
    }

    @Test
    void выходОчищаетОчередь() {
        DelayedValues.push(игрок, SpellValue.of(7), 1000);
        DelayedValues.clear(игрок);
        assertEquals(0, DelayedValues.pending(игрок));
    }

    /** Значение возвращается тем же объектом, а не копией. */
    @Test
    void возвращаетсяТоЖеЗначение() {
        var значение = SpellValue.of(42);
        DelayedValues.push(игрок, значение, 1000);
        assertSame(значение, DelayedValues.pop(игрок, 1000),
                "копия значения разорвала бы ссылку на сущность или точку");
    }
}