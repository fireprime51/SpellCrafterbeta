package com.github.fireprime5.spellcraft.spell;

import com.github.fireprime5.spellcraft.palette.Palette;
import net.minecraft.network.chat.Component;

/**
 * Тип значения, которое течёт по порту модуля.
 * Показывается в GUI (цвет порта, подсказка, правая панель) и проверяется
 * при соединении модулей.
 */
public enum PortType {

    NUMBER("number", Palette.PORT_NUMBER, Shape.TRIANGLE),
    ENTITY("entity", Palette.PORT_ENTITY, Shape.CIRCLE),
    POINT("point", Palette.PORT_POINT, Shape.DIAMOND),
    VECTOR("vector", Palette.PORT_VECTOR, Shape.SQUARE),
    ITEM("item", Palette.PORT_ITEM, Shape.CROSS),
    /**
     * Место: точка вместе с измерением.
     *
     * <p>Отдельный тип от {@link #POINT} не для красоты: портал в Незер и портал
     * в той же точке обычного мира — это разные адреса, а порт хранит только
     * число. Пока оба типа были бы одним, схема «точка → портал» даже не знала
     * бы, в какое измерение вести.
     */
    LOCATION("location", Palette.PORT_LOCATION, Shape.HEXAGON),
    /** A wildcard port uses an outline so it is distinguishable from VECTOR without color. */
    ANY("any", Palette.PORT_ANY, Shape.OUTLINE);

    private static final PortType[] FILL = {ANY, ANY, ANY, ANY};

    /** Форма маркера порта — помогает различать типы значений. */
    public enum Shape {
        CIRCLE, SQUARE, DIAMOND, TRIANGLE, CROSS, HEXAGON, OUTLINE
    }

    private final String key;
    private final int color;
    private final Shape shape;

    PortType(String key, int color, Shape shape) {
        this.key = key;
        this.color = color;
        this.shape = shape;
    }

    public Shape shape() {
        return shape;
    }

    public int color() {
        return color;
    }

    public Component label() {
        return Component.translatable("port.spellcraft." + key);
    }

    public String translationKey() {
        return "port.spellcraft." + key;
    }

    /**
     * Совместимы ли типы источника и приёмника.
     * ANY совместим со всем, одинаковые — всегда.
     */
    public boolean accepts(PortType other) {
        return this == ANY || other == ANY || this == other;
    }

    /** Разворачивает декларацию портов модуля в массив нужной длины. */
    public static PortType[] fill(PortType[] declared, int count) {
        if (declared != null && declared.length >= count) {
            PortType[] copy = new PortType[count];
            System.arraycopy(declared, 0, copy, 0, count);
            return copy;
        }
        PortType[] copy = new PortType[count];
        for (int i = 0; i < count; i++) {
            copy[i] = i < FILL.length ? FILL[i] : ANY;
        }
        return copy;
    }
}
