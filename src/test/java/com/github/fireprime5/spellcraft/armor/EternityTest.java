package com.github.fireprime5.spellcraft.armor;

import com.github.fireprime5.spellcraft.spell.SpellModules;
import com.github.fireprime5.spellcraft.spell.SpellSubsections;
import com.github.fireprime5.spellcraft.spell.UniqueModules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * «Вечность»: лимит в три игрока и правила, из-за которых она вообще конечна.
 *
 * <p>Само состояние проверяется по-настоящему: {@link Eternity} решает всё по
 * UUID, поэтому лимит считается без сущности игрока. А вот привязку к энергии
 * бездны и к событию урона приходится смотреть в исходниках — {@code Player} в
 * тестах не создаётся (реестр Minecraft не поднят). Так же поступает
 * {@code ArmorEventsTest} и {@code InfiniteAbyssEnergyTest}.
 */
class EternityTest {

    /**
     * Реестр модулей наполняется только здесь.
     *
     * <p>Без этого проверки модуля зависели бы от того, какой класс тестов
     * выполнился раньше: по одиночке они падали бы с «не зарегистрирована».
     */
    @org.junit.jupiter.api.BeforeAll
    static void наполнитьРеестр() {
        SpellModules.init();
    }

    @BeforeEach
    void чистоеСостояние() {
        Eternity.reset();
    }

    private static UUID игрок(int номер) {
        return new UUID(0x5E7E0000L, номер);
    }

    // ------------------------------------------------------------ лимит

    /** Четвёртый, кому «Вечность» не досталась, защиты не получает. */
    @Test
    void четвёртыйИгрокОстаётсяБезЗащиты() {
        for (int i = 1; i <= Eternity.MAX_ACTIVE; i++) {
            assertEquals(Eternity.State.ACTIVE, Eternity.refresh(игрок(i), true),
                    "игрок " + i + " должен был получить «Вечность»");
        }
        assertEquals(Eternity.State.DENIED, Eternity.refresh(игрок(4), true),
                "«Вечность» выдалась четвёртому: лимит не работает");
        assertFalse(Eternity.isActive(игрок(4)),
                "отказник всё равно оказался под защитой");
        assertEquals(Eternity.MAX_ACTIVE, Eternity.activeCount(),
                "активных больше лимита");
    }

    /**
     * Место освобождается сразу, а не «когда-нибудь потом».
     *
     * <p>Иначе после смерти владельца сервер навсегда остался бы с двумя
     * «Вечностями» вместо трёх, и каждый следующий погибший ширел бы лимит.
     */
    @Test
    void освободившеесяМестоДостаётсяСледующему() {
        for (int i = 1; i <= Eternity.MAX_ACTIVE; i++) {
            Eternity.refresh(игрок(i), true);
        }
        Eternity.deactivate(игрок(1));
        assertEquals(Eternity.State.ACTIVE, Eternity.refresh(игрок(4), true),
                "освободившееся место не досталось ожидающему");
        assertEquals(Eternity.MAX_ACTIVE, Eternity.activeCount());
    }

    /** Снятие модуля с брони возвращает место в том же тике. */
    @Test
    void снятиеМодуляВозвращаетМесто() {
        Eternity.refresh(игрок(1), true);
        assertEquals(Eternity.State.OFF, Eternity.refresh(игрок(1), false));
        assertFalse(Eternity.isActive(игрок(1)));
        assertEquals(0, Eternity.activeCount());
    }

    /**
     * Повторный тик не выбивает уже защищённого.
     *
     * <p>Проверка каждый тик — не «раз в интервал», как у налога брони. Если бы
     * тик считали заново, игроки состязались бы за место каждый кадр и получали
     * бы защиту рывками.
     */
    @Test
    void повторныйТикНеОтнимаетЗащиту() {
        assertEquals(Eternity.State.ACTIVE, Eternity.refresh(игрок(1), true));
        for (int i = 0; i < 50; i++) {
            assertEquals(Eternity.State.ACTIVE, Eternity.refresh(игрок(1), true));
        }
        assertEquals(1, Eternity.activeCount());
    }

    /** Смерть и выход не должны оставлять занятое место навсегда. */
    @Test
    void очисткаОсвобождаетМесто() {
        for (int i = 1; i <= Eternity.MAX_ACTIVE; i++) {
            Eternity.refresh(игрок(i), true);
        }
        Eternity.clear(игрок(1));
        Eternity.clear(игрок(2));
        assertEquals(Eternity.MAX_ACTIVE - 2, Eternity.activeCount());
        assertEquals(Eternity.State.ACTIVE, Eternity.refresh(игрок(4), true));
    }

    // ------------------------------------------------------------ модуль

    /** «Вечность» обязана быть в наборе личных — иначе её не выдаст «Анализ себя». */
    @Test
    void вечностьВходитВНаборЛичных() {
        assertTrue(List.of(UniqueModules.ALL).contains(UniqueModules.ETERNITY),
                "«Вечность» не в наборе личных модулей: её не розыгрывает «Анализ себя»");
        var type = SpellModules.get(SpellModules.id(UniqueModules.ETERNITY));
        assertNotNull(type, "«Вечность» не зарегистрирована");
        assertTrue(type.isUnique(), "«Вечность» потеряла личность");
        // остальные пять остались числами в разделе «Личный»
        assertEquals(SpellSubsections.ARMOR, SpellSubsections.of(type),
                "«Вечность» обязана лежать в разделе «Броня», иначе её положат в заклинание");
    }

    /** Модуль без входов и выходов: в схеме брони ему нечего считать. */
    @Test
    void вечностьНеЧисловойМодуль() {
        var type = SpellModules.get(SpellModules.id(UniqueModules.ETERNITY));
        assertEquals(0, type.inputs());
        assertEquals(0, type.outputs());
        assertEquals(0, type.manaCost());
    }

    /** Нагрудник обязан её принимать, иначе она не попадёт в палитру брони. */
    @Test
    void вечностьРазрешенаВНагруднике() {
        assertTrue(ModularSchema.allows(UniqueModules.ETERNITY),
                "«Вечность» не разрешена в нагруднике");
    }

    // ------------------------------------------------- привязка к бездне

    private static String читать(String путь) throws IOException {
        return Files.readString(Path.of(путь), StandardCharsets.UTF_8);
    }

    private static String блок(String исходник, String сигнатура) {
        int начало = исходник.indexOf(сигнатура);
        assertTrue(начало >= 0, "Не найден метод: " + сигнатура);
        int открытие = исходник.indexOf('{', начало);
        int глубина = 1;
        int конец = открытие + 1;
        while (глубина > 0 && конец < исходник.length()) {
            char c = исходник.charAt(конец++);
            if (c == '{') глубина++;
            if (c == '}') глубина--;
        }
        return исходник.substring(открытие + 1, конец - 1);
    }

    /**
     * Энергия не восстанавливается, пока «Вечность» активна.
     *
     * <p>Проверяется в {@code AbyssEnergy.add}, а не в каждом источнике: извлекатель,
     * сосуды и модули-заклинания дают бездну только через этот метод. Новое место,
     * забытое про правило, не появится само.
     */
    @Test
    void безднаНеВосстанавлиПокаАктивна() throws IOException {
        String add = блок(читать(
                "src/main/java/com/github/fireprime5/spellcraft/energy/AbyssEnergy.java"),
                "public static int add(Player player, int amount)");
        assertTrue(add.contains("Eternity.isActive(player)"),
                "add() не спрашивает про «Вечность»: бездна будет восстанавливаться");
        assertTrue(add.indexOf("Eternity.isActive") < add.indexOf("return AbyssPool.add"),
                "проверка стоит после начисления: часть порции уже успела бы дойти");
    }

    /** Каждый перехваченный удар стоит ровно одну энергию бездны. */
    @Test
    void ударСтоитОднуЭнергиюБездны() throws IOException {
        String armor = читать(
                "src/main/java/com/github/fireprime5/spellcraft/armor/ArmorEvents.java");
        String absorb = блок(armor, "private static boolean absorbEternity(");
        assertTrue(absorb.contains("event.setCanceled(true)"),
                "«Вечность» не отменяет урон");
        assertTrue(absorb.contains("AbyssEnergy.hasRaw(player, ETERNITY_COST)"),
                "нет проверки бездны перед отменой урона");
        assertTrue(absorb.contains("AbyssEnergy.consumeRaw(player, ETERNITY_COST)"),
                "перехваченный удар не оплачивается");
        assertTrue(armor.contains("private static final int ETERNITY_COST = 1;"),
                "удор должен стоить одну энергию бездны, а не сколько-то другое");
    }

    /**
     * «Вечность» отменяет урон, но не отменяет смерть.
     *
     * <p>Порядок важен: без проверки бездны защита была бы вечной — игрок, у
     * которого бездна кончилась, всё равно гасил бы удары и никогда не умер.
     */
    @Test
    void безБездныУронПроходит() throws IOException {
        String absorb = блок(читать(
                "src/main/java/com/github/fireprime5/spellcraft/armor/ArmorEvents.java"),
                "private static boolean absorbEternity(");
        int проверка = absorb.indexOf("if (!AbyssEnergy.hasRaw(player, ETERNITY_COST))");
        int отмена = absorb.indexOf("event.setCanceled(true)");
        assertTrue(проверка >= 0, "нет ветки нехватки бездны");
        assertTrue(проверка < отмена,
                "урон отменяется раньше, чем проверена бездна: смерть невозможна");
        assertTrue(absorb.contains("return false;"),
                "при нехватке бездны метод должен пропустить удар, а не отменить его");
        assertTrue(absorb.contains("Eternity.deactivate(player)"),
                "при нехватке бездны защита должна сняться, а не висеть дальше");
    }

    /** Событие урона гасится до брони и триггеров, которые после отмены бесполезны. */
    @Test
    void вечностьПроверяетсяРаньшеОстальнойБрони() throws IOException {
        String incoming = блок(читать(
                "src/main/java/com/github/fireprime5/spellcraft/armor/ArmorEvents.java"),
                "public static void onIncomingDamage(");
        assertTrue(incoming.indexOf("Eternity.isActive(player)")
                        < incoming.indexOf("double protection = PROTECTION.getOrDefault"),
                "«Вечность» должна проверяться до защиты брони");
    }

    /**
     * Налог берётся по расходу в секунду, а не по числу узлов.
     *
     * <p>Проверка по исходнику, а не поведением: {@code chargeEnergy} живёт в
     * обработчике тика, и без живого игрока он не вызывается. Раньше здесь стояло
     * «по одной единице за узел», и с появлением барьера, который дороже полёта
     * в шестьдесят раз, такая арифметика тихо разъехалась бы с настоящей ценой.
     */
    @Test
    void налогСчитаетсяПоРасходуВСекунду() throws IOException {
        String armor = читать(
                "src/main/java/com/github/fireprime5/spellcraft/armor/ArmorEvents.java");
        String charge = блок(armor, "private static void chargeEnergy(");
        assertTrue(charge.contains("schema.armorUpkeepPerSecond()"),
                "chargeEnergy должен брать расход в секунду");
        assertTrue(charge.contains("CHARGE_INTERVAL / 20.0"),
                "расход в секунду надо переводить в длину налогового периода");
        assertFalse(charge.contains("taxableNodes()"),
                "поштучный подсчёт узлов больше не используется");
        assertFalse(charge.contains("schema.size()"),
                "chargeEnergy снова считает налог по размеру схемы, а не по расходу");
    }

    /** Тик брони включает «Вечность» только при живом владельце. */
    @Test
    void мёртвыйВладелецНеДержитМесто() throws IOException {
        String tick = блок(читать(
                "src/main/java/com/github/fireprime5/spellcraft/armor/ArmorEvents.java"),
                "public static void onPlayerTick(");
        assertTrue(tick.contains("player.isAlive() && schema.has(UniqueModules.ETERNITY)"),
                "«Вечность» выдаётся и трупу: погибший держал бы лимит до респауна");
    }

    /**
     * Слизень бьёт каждый тик, и платить двадцать раз за одно касание нельзя.
     *
     * <p>Это ровно то, что было видно в логе: тики 34490, 34491, 34492… подряд,
     * все события разные. Ударов там не двадцать — слизень наносит урон
     * непрерывно, и за секунду касания это один удар, а не двадцать.
     */
    @Test
    void непрерывныйУронСтоитРазВСекунду() {
        var id = игрок(1);
        int списаний = 0;
        // секунда тика по 20 раз — как контактный урон слини
        for (int tick = 0; tick < 20; tick++) {
            if (Eternity.chargeDue(id, tick)) {
                списаний++;
            }
        }
        assertEquals(1, списаний,
                "секунда непрерывного урона должна стоить одну единицу, а не " + списаний);
    }

    /** Две секунды непрерывного урона — две единицы, а не сорок. */
    @Test
    void двеСекундыНепрерывногоУронаСтоятДве() {
        var id = игрок(2);
        int списаний = 0;
        for (int tick = 0; tick < 40; tick++) {
            if (Eternity.chargeDue(id, tick)) {
                списаний++;
            }
        }
        assertEquals(2, списаний, "непрерывный урон должен стоить раз в секунду");
    }

    /**
     * Два настоящих удара зомби в 9 тиков разницы — это два разных удара.
     *
     * <p>Ровно как в твоём логе: тики 34728 и 34737. Платить за оба.
     */
    @Test
    void дваНастоящихУдараОплачиваютсяОба() {
        var id = игрок(3);
        assertTrue(Eternity.chargeDue(id, 34728), "первый удар обязан быть оплачен");
        assertTrue(Eternity.chargeDue(id, 34737),
                "второй настоящий удар через 9 тиков обязан быть оплачен отдельно");
    }

    /** Первая попытка всегда оплачивается, иначе модуль был бы бесплатным. */
    @Test
    void первыйУдарОплачиваетсяВсегда() {
        assertTrue(Eternity.chargeDue(игрок(4), 999));
    }

    /**
     * Пауза короче окна не превращает очередь слини в двадцать отдельных ударов.
     *
     * <p>Урон идёт не каждый тик, а через три. Это всё ещё один непрерывный урон
     * с перебоями: восемь тиков молчания не делают урон «новым».
     */
    @Test
    void короткаяПаузаНеПревращаетОчередьВОтдельныеУдары() {
        var id = игрок(5);
        int списаний = 0;
        for (int tick = 0; tick < 60; tick += 3) {
            if (Eternity.chargeDue(id, tick)) {
                списаний++;
            }
        }
        // 60 тиков — три секунды, по одной единице за секунду. Если бы паузы в три
        // тика считались отдельными ударами, списаний было бы двадцать
        assertEquals(3, списаний,
                "урон с паузами короче окна — это тот же непрерывный урон, списаний "
                        + списаний);
    }

    /** Слизень отпустил, кто-то ударил позже — это уже отдельный удар. */
    @Test
    void послеДолгойПаузыУдарСноваОтдельный() {
        var id = игрок(6);
        Eternity.chargeDue(id, 0);
        assertTrue(Eternity.chargeDue(id, 40),
                "после настоящей паузы урон снова считается отдельным ударом");
    }

    /** Выход и смерть не должны оставлять состояние оплаты. */
    @Test
    void состояниеОплатыОчищается() {
        var id = игрок(7);
        Eternity.chargeDue(id, 5000);
        Eternity.clear(id);
        assertTrue(Eternity.chargeDue(id, 5000),
                "после очистки игрок не должен ждать конца окна");
    }


    /** Живой игрок без модуля место не занимает. */
    @Test
    void безМодуляМестоНеЗанимается() throws IOException {
        String tick = блок(читать(
                "src/main/java/com/github/fireprime5/spellcraft/armor/ArmorEvents.java"),
                "public static void onPlayerTick(");
        assertTrue(tick.contains("updateEternity(player, false);"),
                "снятие брони не снимает «Вечность»: место утекает");
    }
}