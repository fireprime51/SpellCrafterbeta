package com.github.fireprime5.spellcraft.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
 *
 * <p>Слот — это <b>позиция в списке</b>, и она же номер геометрии: 5/16, 8/16 и
 * 11/16 по {@link NetworkGeometry#OFFSET}. Поэтому список разрежённый: снятая
 * линия оставляет на своём месте пустоту, а не сдвигает хвост. Прежний
 * {@code List.remove(int)} сдвигал остальных влево, и после снятия первой линии
 * вторая и третья въезжали на чужие точки входа в грань: у соседнего блока эта
 * линия оставалась в прежнем слоте, и на стыке разъезжалось по три пикселя.
 * Вдобавок {@code hitSlot} начал бы отвечать не за ту линию, которую игрок видит.
 *
 * <p>NBT хранит номер слота в каждой записи. Без него чтение файла, сохранённого
 * плотным списком, снова сдвинул бы линии по слотам.
 */
public final class NetworkLines {

    /** Ключ списка линий в NBT. */
    private static final String LINES = "lines";

    /** Ключ номера слота в записи линии. */
    private static final String SLOT = "slot";

    /** Слоты по порядку; пустые слоты — {@code null}. */
    private final List<NetworkLine> lines = new ArrayList<>(NetworkGeometry.SLOTS);

    /** Длина списка: последний занятый слот плюс один. */
    public int size() {
        return lines.size();
    }

    /** Сколько линий в блоке на самом деле, без пустых слотов. */
    public int count() {
        int n = 0;
        for (NetworkLine линия : lines) {
            if (линия != null) {
                n++;
            }
        }
        return n;
    }

    /** Пуст ли блок: пустой блок сети снимается целиком. */
    public boolean isEmpty() {
        return count() == 0;
    }

    /**
     * Есть ли свободный слот.
     *
     * <p>По занятым слотам, а не по длине списка: длина списка — это номер
     * последнего занятого слота плюс один, и после снятия первой из трёх линий
     * она равна трём, хотя занято два слота.
     */
    public boolean hasFreeSlot() {
        return count() < NetworkGeometry.SLOTS;
    }

    /**
     * Дорастить список так, чтобы слот стал последним элементом.
     *
     * <p>Строго до {@code slot + 1}: вариант «пока размер меньше слота» оставлял
     * список на элемент короче, и запись в последний слот падала с выходом за
     * границу. Ровно этот случай и вылетел в игре — перенос линии в третий слот.
     */
    private void grow(int slot) {
        while (lines.size() <= slot) {
            lines.add(null);
        }
    }

    /** Номер слота линии такого вида и цвета или -1. */
    public int slot(LineKind kind, DyeColor color) {
        for (int slot = 0; slot < lines.size(); slot++) {
            NetworkLine линия = lines.get(slot);
            if (линия != null && линия.kind() == kind
                    && (kind == LineKind.ABYSS || линия.color() == color)) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Перенести линию в свободный слот.
     *
     * <p>Слот — номер геометрии, и место входа в грань блока. Переносят линию к
     * соседу, у которого такая же линия стоит в другом слоте: иначе оба блока
     * тянутся навстречу друг другу из разных точек, и на стыке видна ступенька.
     */
    public boolean move(int from, int to) {
        if (from < 0 || from >= lines.size() || to < 0 || to >= NetworkGeometry.SLOTS
                || from == to || lines.get(from) == null) {
            return false;
        }
        if (to < lines.size() && lines.get(to) != null) {
            return false;
        }
        NetworkLine линия = lines.get(from);
        grow(to);
        lines.set(to, линия);
        lines.set(from, null);
        trim();
        return true;
    }

    /**
     * Поменять две линии слотами местами.
     *
     * <p>Нужно, когда сосед заполнил слоты в обратном порядке: красная у него во
     * втором слоте, синяя — в первом. Ни одну из них нельзя сдвинуть в одиночку,
     * обе встанут на место, если поменяться слотами.
     */
    public boolean swap(int a, int b) {
        if (a < 0 || b < 0 || a == b || a >= lines.size() || b >= lines.size()
                || lines.get(a) == null || lines.get(b) == null) {
            return false;
        }
        NetworkLine линия = lines.get(a);
        lines.set(a, lines.get(b));
        lines.set(b, линия);
        return true;
    }

    /** Номер первого свободного слота или -1. */
    public int freeSlot() {
        for (int slot = 0; slot < NetworkGeometry.SLOTS; slot++) {
            if (slot >= lines.size() || lines.get(slot) == null) {
                return slot;
            }
        }
        return -1;
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
            if (линия != null && линия.kind() == kind
                    && (kind == LineKind.ABYSS || линия.color() == color)) {
                return линия;
            }
        }
        return null;
    }

    /**
     * Кладёт линию в первый свободный слот.
     *
     * <p>Возвращает {@code false}, если такой линии уже есть или слотов не
     * осталось: молча проглоченный предмет выглядит как поломка, и игрок
     * посчитает, что слот сломан.
     */
    public boolean add(LineKind kind, DyeColor color) {
        if (!hasFreeSlot() || line(kind, color) != null) {
            return false;
        }
        int slot = freeSlot();
        grow(slot);
        lines.set(slot, NetworkLine.empty(kind, color));
        return true;
    }

    /**
     * Заменяет содержимое: так читается NBT, где список приходит целиком.
     *
     * <p>Пустые слоты сохраняются на своих местах, хвост из пустот обрезается.
     */
    public void setAll(List<NetworkLine> новые) {
        lines.clear();
        for (int slot = 0; slot < новые.size() && slot < NetworkGeometry.SLOTS; slot++) {
            lines.add(новые.get(slot));
        }
        trim();
    }

    /**
     * Убирает линию из слота, оставляя слот на месте.
     *
     * <p>Хвостовые пустоты обрезаются, чтобы {@code size()} остался номером
     * последнего занятого слота плюс один.
     */
    public NetworkLine remove(int slot) {
        if (slot < 0 || slot >= lines.size()) {
            return null;
        }
        NetworkLine снятая = lines.set(slot, null);
        trim();
        return снятая;
    }

    /** Все линии по слотам, с пустотами вместо снятых. */
    public List<NetworkLine> all() {
        return Collections.unmodifiableList(lines);
    }

    /** Записать линии в NBT: у каждой записи есть её номер слота. */
    public void save(CompoundTag tag) {
        ListTag список = new ListTag();
        for (int slot = 0; slot < lines.size(); slot++) {
            NetworkLine линия = lines.get(slot);
            if (линия == null) {
                continue;
            }
            CompoundTag запись = линия.save(new CompoundTag());
            запись.putInt(SLOT, slot);
            список.add(запись);
        }
        tag.put(LINES, список);
    }

    /**
     * Прочитать линии из NBT.
     *
     * <p>Запись без номера слота — это старый плотный список: её номер считается
     * по порядку, иначе старые миры поедут по слотам после первого же чтения.
     */
    public void load(CompoundTag tag) {
        ListTag список = tag.getList(LINES, Tag.TAG_COMPOUND);
        List<NetworkLine> прочитанные = new ArrayList<>(NetworkGeometry.SLOTS);
        for (int i = 0; i < список.size() && i < NetworkGeometry.SLOTS; i++) {
            CompoundTag запись = список.getCompound(i);
            int slot = Math.clamp(запись.contains(SLOT) ? запись.getInt(SLOT) : i,
                    0, NetworkGeometry.SLOTS - 1);
            while (прочитанные.size() < slot) {
                прочитанные.add(null);
            }
            if (прочитанные.size() == slot) {
                прочитанные.add(NetworkLine.load(запись));
            } else {
                прочитанные.set(slot, NetworkLine.load(запись));
            }
        }
        setAll(прочитанные);
    }

    /** Обрезать хвост из пустых слотов. */
    private void trim() {
        while (!lines.isEmpty() && lines.get(lines.size() - 1) == null) {
            lines.remove(lines.size() - 1);
        }
    }

    /** Подписи линий для NBT и отладки: индекс, вид, цвет, маска, запас. */
    @Override
    public String toString() {
        return lines.toString();
    }
}
