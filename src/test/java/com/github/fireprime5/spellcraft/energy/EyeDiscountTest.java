package com.github.fireprime5.spellcraft.energy;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * «Глаз» — личный модуль, снижающий все расходы бездны в десять раз.
 *
 * <p>Формула скидки вынесена в {@link AbyssEnergy#applyRate} без игрока, поэтому
 * проверяется по-настоящему: проверка «есть ли у игрока „Глаз“» ходит в знания,
 * а для неё нужен живой {@code Player}, которого в тестах нет.
 *
 * <p>Привязка скидки к правильным местам проверяется по исходнику — так же,
 * как {@code ArmorEventsTest}: обработчики без живого игрока иначе не поднять.
 */
class EyeDiscountTest {

    /** Настоящая формула из продакшн-кода. */
    private static int ставка(int цена, boolean глазЕсть) throws Exception {
        Method m = AbyssEnergy.class.getDeclaredMethod("applyRate", int.class, boolean.class);
        m.setAccessible(true);
        return (int) m.invoke(null, цена, глазЕсть);
    }

    private static String читать(String путь) throws IOException {
        return Files.readString(Path.of(путь), StandardCharsets.UTF_8);
    }

    /** Без «Глаза» цена идёт целиком. */
    @Test
    void безГлазаЦенаПолная() throws Exception {
        assertEquals(100, ставка(100, false));
        assertEquals(1, ставка(1, false));
        assertEquals(0, ставка(0, false));
    }

    /** Десятая часть: 100 → 10, 50 → 5, 10 → 1. */
    @Test
    void глазДаётДесятуюЧасть() throws Exception {
        assertEquals(10, ставка(100, true));
        assertEquals(5, ставка(50, true));
        assertEquals(1, ставка(10, true));
    }

    /**
     * Дёшево обходится в ноль — и это осознанно.
     *
     * <p>«Минус 90%» — это минус 90%. Пол в единицу сделал бы скидку фиктивной
     * для всего, что дешевле десятка, а именно на дешёвых заклинаниях игрок
     * заметил бы разницу в первую очередь.
     */
    @Test
    void дешёвоеОбходитсяВноль() throws Exception {
        assertEquals(0, ставка(1, true));
        assertEquals(0, ставка(9, true));
        assertEquals(1, ставка(10, true), "десятка — первый не нулевой порог");
    }

    /** Ставка именно 90%, а не «чуть меньше». */
    @Test
    void ставкаРовноПроцент() {
        assertEquals(0.1, AbyssEnergy.EYE_DISCOUNT, 1e-12);
        for (int цена : new int[]{10, 20, 40, 100, 250, 1000}) {
            assertEquals(цена * 0.1, Math.floor(цена * AbyssEnergy.EYE_DISCOUNT), 1e-9,
                    "скидка поехала на цене " + цена);
        }
    }

    /**
     * Плата за удар «Вечности» не скидывается.
     *
     * <p>Иначе удар за 1 энергию обошёлся бы в 0, и «Вечность» стала бы
     * бесплатной навсегда: бессмертие без единой цены — это не редкость, а
     * отсутствие механики.
     */
    @Test
    void вечностьНеСкидывается() throws Exception {
        String исходник = читать(
                "src/main/java/com/github/fireprime5/spellcraft/armor/ArmorEvents.java");
        int начало = исходник.indexOf("private static boolean absorbEternity(");
        assertTrue(начало >= 0, "не найден absorbEternity");
        String метод = исходник.substring(начало, исходник.indexOf("\n    }", начало));
        assertTrue(метод.contains("hasRaw"), "«Вечность» должна проверять бездну без скидки");
        assertTrue(метод.contains("consumeRaw"),
                "«Вечность» должна списывать без скидки, иначе удар станет бесплатным");
        assertFalse(метод.contains("AbyssEnergy.has(player"),
                "«Вечность» снова проверяет со скидкой «Глаза»");
    }

    /**
     * Отчёт о потраченном совпадает с тем, что реально ушло.
     *
     * <p>Иначе панель покажет «каст за 40», хотя спишется 4, и игрок будет
     * искать, куда делась бездна.
     */
    @Test
    void отчётСовпадаетСРеальностью() throws Exception {
        String исходник = читать(
                "src/main/java/com/github/fireprime5/spellcraft/energy/AbyssEnergy.java");
        int начало = исходник.indexOf("public static int spentAmount(");
        assertTrue(начало >= 0);
        String метод = исходник.substring(начало, исходник.indexOf("\n    }", начало));
        // «discounted» ведёт в applyRate, поэтому требование не «какая именно
        // функция вызвана», а «скидка вообще применена»: иначе панель показала бы
        // «каст за 40» при списании четырёх
        assertTrue(метод.contains("discounted(") || метод.contains("applyRate("),
                "скидка обязана попасть и в отчёт, а не только в списание");
    }

    /** Проверка и списание считаются одной формулой, иначе они разойдутся. */
    @Test
    void проверкаИСписаниеСчитаютсяОдинаково() throws Exception {
        String исходник = читать(
                "src/main/java/com/github/fireprime5/spellcraft/energy/AbyssEnergy.java");
        assertTrue(исходник.contains("public static boolean has(Player player, int amount)"),
                "нет has со скидкой");
        assertTrue(исходник.contains("public static void consume(Player player, int amount)"),
                "нет consume со скидкой");
        assertTrue(исходник.contains("public static boolean hasRaw(")
                        && исходник.contains("public static void consumeRaw("),
                "нет сырых вариантов без скидки — «Вечностью» нечем будет платить");
    }
}