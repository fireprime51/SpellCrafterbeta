package com.github.fireprime5.spellcraft.energy;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Две ошибки, найденные при первом же запуске с сосудами.
 *
 * <p>Обе приводят к падению игры, а не к неправильному числу, поэтому
 * поймать их обычными тестами нельзя: нужен игрок с сосудом и загруженные
 * ресурсы. Здесь фиксируется форма, в которой они повториться не смогут.
 */
class AbyssPoolRecursionTest {

    /**
     * Пул не зовёт публичное добавление энергии бездны.
     *
     * <p>Так был вылет: {@code AbyssPool.add} звал {@code AbyssEnergy.add},
     * а тот — снова {@code AbyssPool.add}. Получалась бесконечная рекурсия, и
     * первое же добавление энергии бездны при установке сосуда роняло сервер
     * переполнением стека. Путь должен вести в {@code addToWallet}, который
     * сосудов не видит и потому петлю не замыкает.
     */
    @Test
    void пулНеЗовётДобавлениеМаныЧерезПул() {
        String source = read("energy/AbyssPool.java");
        assertFalse(source.contains("AbyssEnergy.add("),
                "AbyssPool не должен звать AbyssEnergy.add: тот ходит в "
                        + "пул, и вызов замыкает бесконечную рекурсию");
        assertTrue(source.contains("AbyssEnergy.addToWallet("),
                "пул должен звать addToWallet: это единственный путь в кошелёк "
                        + "без обратной связи");
    }

    /**
     * Ключи вариантов blockstate перечисляют свойства.
     *
     * <p>У провода варианты были записаны как {@code "swd"}. Minecraft такого
     * формата не знает: ключ должен выглядеть как
     * {@code north=true,east=false,...}. Из-за этого все 64 варианта не
     * загружались, в логе сыпались исключения загрузчика, а провод выглядел
     * ненарисованным при вполне рабочей линии.
     */
    @Test
    void уБлокаСетиНеОсталосьВариантовСостояния() throws IOException {
        // Подключения линий уехали в NBT блок-сущности: в блоке до трёх линий, и
        // в свойствах блока им места нет (шесть бит на линию плюс цвета в
        // blockstate не помещаются). Возврат вариантов означал бы снова тысячу
        // моделей — и снова блок, умещающий одну линию.
        com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(
                Files.readString(Path.of(
                        "src/main/resources/assets/spellcraft/blockstates/wire.json"),
                        StandardCharsets.UTF_8)).getAsJsonObject();
        com.google.gson.JsonObject variants = root.getAsJsonObject("variants");
        assertTrue(variants != null, "у блока сети должна быть таблица состояний");
        assertEquals(1, variants.size(),
                "у блока без свойств должен быть ровно один вариант с пустым "
                        + "ключом, а их " + variants.size());
        assertTrue(variants.has(""),
                "у блока без свойств ключ варианта пустой: так его понимает "
                        + "Minecraft");

        String блок = Files.readString(Path.of(
                "src/main/java/com/github/fireprime5/spellcraft/network/WireBlock.java"),
                StandardCharsets.UTF_8);
        assertTrue(!блок.contains("BlockStateProperties.NORTH")
                        && !блок.contains("createBlockStateDefinition"),
                "у блока сети не должно быть свойств подключения");
    }

    @Test
    void кошелёкМеряетсяПоСвоейЁмкости() {
        String pool = read("energy/AbyssPool.java");
        assertFalse(pool.contains("roomInWallet = Math.max(0, capacity(player)"),
                "свободное место в кошельке нельзя мерить по ёмкости пула: "
                        + "кошелёк вмещает меньше, и весь излишек до сосуда "
                        + "не дойдёт");
        assertTrue(pool.contains("walletCapacity = AbyssEnergy.capacity()"),
                "кошелёк должен мериться по собственной ёмкости");
        assertFalse(pool.contains("get(player) < capacity(player)"),
                "проверка «полон» тоже должна смотреть на ёмкость кошелька, а "
                        + "не пула, иначе с сосудом полно не наступает никогда");
    }

    /** Читает исходник класса: сам класс в тестах не поднимается. */
    private static String read(String relative) {
        // путь ищем вверх от рабочего каталога: Gradle запускает тесты не из
        // корня проекта, и жёсткий относительный путь ловил бы опечатки
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 5 && dir != null; i++) {
            Path candidate = dir.resolve("src/main/java/com/github/fireprime5/spellcraft/")
                    .resolve(relative);
            if (Files.exists(candidate)) {
                try {
                    return Files.readString(candidate, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new AssertionError("не прочитать " + relative, e);
                }
            }
            dir = dir.getParent();
        }
        throw new AssertionError("не найден исходник " + relative
                + " выше рабочего каталога");
    }
}