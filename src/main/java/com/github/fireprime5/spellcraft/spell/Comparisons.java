package com.github.fireprime5.spellcraft.spell;

/**
 * Сами сравнения, отдельно от их регистрации.
 *
 * <p>Раньше условие было лямбдой прямо в теле модуля, и проверять его было
 * нечем: чтобы вызвать {@code execute}, нужен живой {@code ServerLevel}. А
 * перевёрнутый оператор — самая тихая из поломок: модуль зарегистрирован,
 * порты верные, тесты зелёные, а «больше» считает «меньше», и заметить это
 * можно только в игре на конкретном заклинании.
 *
 * <p>Модули ссылаются на эти же самые объекты, поэтому проверка идёт по коду,
 * который реально работает в игре.
 */
public final class Comparisons {

    private Comparisons() {
    }

    /**
     * Сравнение двух чисел.
     *
     * <p>Примитивы, а не {@code BiPredicate<Double, Double>} — и это не вкусовое
     * предпочтение. В обёртках {@code a == b} сравнивает <b>ссылки</b>: два
     * равных значения «равны» только если указывают на один объект. С
     * обёртками «Равно» почти никогда не было бы истиной, и модуль молча
     * отвечал бы нулём на всё подряд.
     */
    @FunctionalInterface
    public interface Cmp {
        boolean test(double a, double b);
    }

    public static final Cmp EQUALS = (a, b) -> a == b;
    public static final Cmp NOT_EQUALS = (a, b) -> a != b;
    public static final Cmp GREATER = (a, b) -> a > b;
    public static final Cmp LESS = (a, b) -> a < b;
    public static final Cmp GREATER_OR_EQUAL = (a, b) -> a >= b;
    public static final Cmp LESS_OR_EQUAL = (a, b) -> a <= b;

    /** Проверка по имени узла — так удобно прогонять все шесть разом. */
    public static boolean test(String path, double a, double b) {
        return switch (path) {
            case "equals" -> EQUALS.test(a, b);
            case "not_equals" -> NOT_EQUALS.test(a, b);
            case "greater" -> GREATER.test(a, b);
            case "less" -> LESS.test(a, b);
            case "greater_or_equal" -> GREATER_OR_EQUAL.test(a, b);
            case "less_or_equal" -> LESS_OR_EQUAL.test(a, b);
            default -> throw new IllegalArgumentException("нет такого сравнения: " + path);
        };
    }
}