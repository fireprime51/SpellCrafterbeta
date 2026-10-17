package com.github.fireprime5.spellcraft.network;

import net.minecraft.world.item.DyeColor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Линии блока сети: до трёх, по одной на слот.
 *
 * <p>Отдельный класс, а не поле в блок-сущности, по двум причинам. Первая:
 * правило «сколько линий может жить в блоке» — это и есть смысл блока, и оно
 * должно проверяться без мира. Вторая: блок-сущность создаётся только миром, а
 * список линий создаётся чем угодно, и его можно крутить в тестах как обычный
 * объект.
 */
public final class NetworkLines {

    private final List<NetworkLine> lines = new ArrayList<>(NetworkGeometry.SLOTS);

    /** Сколько линий в блоке. */
    public int size() {
        return lines.size();
    }

    /** Пуст ли блок: пустой блок сети снимается целиком. */
    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /** Есть ли свободный слот. */
    public boolean hasFreeSlot() {
        return lines.size() < NetworkGeometry.SLOTS;
    }

    /** Линия в слоте или {@code null}, если слот пуст или его нет. */
    public NetworkLine line(int slot) {
        return slot >= 0 && slot < lines.size() ? lines.get(slot) : null;
    }

    /**
     * Линия такого вида и цвета или {@code null}.
     *
     * <p>У бездны цвет не различает: труба в трубу — одна и та же линия, и второй
     * слот под неё занимать незачем.
     */
    public NetworkLine line(LineKind kind, DyeColor color) {
        for (NetworkLine линия : lines) {
            if (линия.kind() == kind
                    && (kind == LineKind.ABYSS || линия.color() == color)) {
                return линия;
            }
        }
        return null;
    }

    /**
     * Кладёт линию в свободный слот.
     *
     * <p>Возвращает {@code false}, если такой линии уже есть или слотов не
     * осталось: молча проглоченный предмет выглядит как поломка, и игрок
     * посчитает, что слот сломан.
     */
    public boolean add(LineKind kind, DyeColor color) {
        if (!hasFreeSlot() || line(kind, color) != null) {
            return false;
        }
        lines.add(NetworkLine.empty(kind, color));
        return true;
    }

    /** Заменяет содержимое: так читается NBT, где список приходит целиком. */
    public void setAll(List<NetworkLine> новые) {
        lines.clear();
        for (NetworkLine линия : новые) {
            if (lines.size() < NetworkGeometry.SLOTS && линия != null) {
                lines.add(линия);
            }
        }
    }

    /** Убирает линию из слота. */
    public NetworkLine remove(int slot) {
        return slot >= 0 && slot < lines.size() ? lines.remove(slot) : null;
    }

    /** Все линии: индекс списка и есть номер слота. */
    public List<NetworkLine> all() {
        return Collections.unmodifiableList(lines);
    }

    /** Подписи линий для NBT и отладки: индекс, вид, цвет, маска, запас. */
    @Override
    public String toString() {
        return lines.toString();
    }
}
