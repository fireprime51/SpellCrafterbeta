package com.github.fireprime5.spellcraft.portal;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Когда именно портал переносит сущность.
 *
 * <p>Здесь воспроизводится тот сбой, из-за которого двусторонние врата
 * «телепортировали сами в себя»: сущность переносилась туда-обратно каждые 20
 * тиков, потому что единственной защитой был таймер, а «я уже стою внутри»
 * никто не запоминал.
 */
class PortalEntryTest {

    /** Оба конца врат — одна запись реестра и один id. */
    private static final UUID GATE = UUID.randomUUID();
    /** Второй, независимый портал в другом месте. */
    private static final UUID OTHER = UUID.randomUUID();

    private static final BlockPos END_A = new BlockPos(0, 64, 0);
    private static final BlockPos END_B = new BlockPos(500, 70, -500);

    private UUID player;

    @BeforeEach
    void setUp() {
        PortalRegistry.forgetAll();
        player = UUID.randomUUID();
    }

    /**
     * Основной случай: вошёл в A, оказался в B, стоит в B — и больше никуда не
     * дёргается.
     *
     * <p>До починки здесь был пинг-понг A→B→A каждые 20 тиков.
     */
    @Test
    void вошедшегоБольшеНеКрутитМеждуКонцами() {
        long t = 0;
        // шаг 1: вход в A — переносим
        assertTrue(PortalRegistry.shouldEnter(player, GATE, END_A, t),
                "вход в портал должен переносить");

        // шаг 2: оказались в B. Это тот же портал (одна запись), значит
        // прибытие не должно считаться новым входом
        assertFalse(PortalRegistry.shouldEnter(player, GATE, END_B, t + 1),
                "прибытие в противоположный конец не должно сразу переносить обратно");

        // шаг 3...: игрок СТОИТ в B. Каждый следующий тик — тоже нет
        for (long time = t + 2; time < t + 200; time++) {
            assertFalse(PortalRegistry.shouldEnter(player, GATE, END_B, time),
                    "стоя внутри портала, переноситься нельзя: тик " + time);
        }
    }

    /** Вышел и зашёл снова — перенос должен произойти. */
    @Test
    void повторныйВходПослеВыходаРаботает() {
        long t = 0;
        assertTrue(PortalRegistry.shouldEnter(player, GATE, END_A, t));

        // пока игрок не в портале, shouldEnter вообще не зовётся: событие
        // приходит только из блока. Значит достаточно просто не вызывать его,
        // пока «игрок» ходит по миру, а потом вернуться
        assertTrue(PortalRegistry.shouldEnter(player, GATE, END_A, t + 30),
                "вошёл заново после выхода — надо перенести снова");
    }

    /** Стоять в портале, потом уйти и вернуться заметно позже. */
    @Test
    void выходИВозвратЧерезДолгоеВремя() {
        long t = 100;
        assertTrue(PortalRegistry.shouldEnter(player, GATE, END_A, t));
        // стояли 40 тиков
        for (long time = t + 1; time < t + 40; time++) {
            assertFalse(PortalRegistry.shouldEnter(player, GATE, END_A, time));
        }
        // вышли и ходили по миру: shouldEnter не зовётся, потому что событие
        // приходит только из блока портала
        long back = t + 50;
        assertTrue(PortalRegistry.shouldEnter(player, GATE, END_A, back),
                "после настоящего выхода вернуться можно");
    }

    /**
     * Пауза между выходом и возвратом не может быть нулевой.
     *
     * <p>Отметка обновляется каждый тик, пока сущность в портале, и забывается
     * через {@code PRESENCE_GRACE} тиков без обновления. Вернуться быстрее
     * нельзя: чтобы выйти из блока и войти снова, надо пройти минимум два
     * блока, а это десятки тиков даже на бегу. Поэтому «мы видели её здесь в
     * прошлом тике» надёжно означает «она всё ещё стоит внутри».
     */
    @Test
    void вернутьсяМгновенноНевозможно() {
        long t = 200;
        assertTrue(PortalRegistry.shouldEnter(player, GATE, END_A, t));
        // стояли внутри, затем вышли и вернулись в следующем же тике
        assertFalse(PortalRegistry.shouldEnter(player, GATE, END_A, t + 1));
        // но спустя десяток тиков «наружи» перенос снова разрешён
        assertTrue(PortalRegistry.shouldEnter(player, GATE, END_A, t + 12));
    }

    /** Два портала рядом не должны путаться. */
    @Test
    void соседниеПорталыНеПутаются() {
        long t = 0;
        assertTrue(PortalRegistry.shouldEnter(player, GATE, END_A, t));
        // стоим в первом
        assertFalse(PortalRegistry.shouldEnter(player, GATE, END_A, t + 1));
        // шагнули во второй — это другой портал, значит перенос
        assertTrue(PortalRegistry.shouldEnter(player, OTHER, END_B, t + 2));
    }

    /** Мгновенный шаг назад и снова вперёд не должен переносить по кругу. */
    @Test
    void мгновенныйВозвратНеЗацикливает() {
        long t = 0;
        assertTrue(PortalRegistry.shouldEnter(player, GATE, END_A, t));
        // шагнули в другой конец и СРАЗУ назад, раньше истечения льготного окна
        assertFalse(PortalRegistry.shouldEnter(player, GATE, END_B, t + 1));
        assertFalse(PortalRegistry.shouldEnter(player, GATE, END_A, t + 2),
                "за пару тиков туда-обратно ходить нельзя");
    }

    /** Разные сущности не мешают друг другу. */
    @Test
    void сущностиНеМешаютДругДругу() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        long t = 0;
        assertTrue(PortalRegistry.shouldEnter(a, GATE, END_A, t));
        assertTrue(PortalRegistry.shouldEnter(b, GATE, END_A, t),
                "второй игрок должен уйти так же, как первый");
    }

    /** Льготное окно должно быть заметно меньше секунды, иначе выхода нет. */
    @Test
    void льготноеОкноКороткое() {
        assertTrue(PortalRegistry.REENTRY_GRACE_TICKS <= 10,
                "окно прибытия должно быть короче секунды, иначе игрок "
                        + "не успевает выйти: " + PortalRegistry.REENTRY_GRACE_TICKS);
    }
}