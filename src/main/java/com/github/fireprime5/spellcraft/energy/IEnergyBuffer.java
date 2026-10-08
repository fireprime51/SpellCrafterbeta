package com.github.fireprime5.spellcraft.energy;

/**
 * Контейнер энергии мода.
 *
 * <p>Энергия одна на весь мод, но реализация сделана через интерфейс, чтобы позже
 * можно было добавить другие виды энергии без правок всех потребителей.
 *
 * <p>Единственная реализация здесь — {@link PlayerManaBuffer}: кошелёк игрока,
 * он же мана заклинаний.
 */
public interface IEnergyBuffer {

    /** Вместимость контейнера. */
    int capacity();

    /** Сколько энергии сейчас лежит. */
    int stored();

    /** Принимает энергию, возвращает сколько реально принято. */
    int receive(int amount);

    /** Отдаёт энергию, возвращает сколько реально отдано. */
    int extract(int amount);

    /** Прямая установка значения (сохранение/загрузка, админские команды). */
    int setStored(int amount);

    default int freeSpace() {
        return Math.max(0, capacity() - stored());
    }

    default boolean isEmpty() {
        return stored() <= 0;
    }

    default boolean isFull() {
        return stored() >= capacity();
    }

    /** Хватает ли энергии на указанную сумму. */
    default boolean canAfford(int amount) {
        return amount <= 0 || stored() >= amount;
    }

    /** Доля заполнения 0..1 — для полосок прогресса в GUI. */
    default float fillFraction() {
        int cap = capacity();
        return cap <= 0 ? 0f : Math.clamp((float) stored() / cap, 0f, 1f);
    }
}
