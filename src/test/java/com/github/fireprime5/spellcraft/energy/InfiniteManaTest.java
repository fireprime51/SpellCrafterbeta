package com.github.fireprime5.spellcraft.energy;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Бесконечная мана: точки, где режим обязан учитываться.
 *
 * <p>Сам {@code Player} в тестах не создаётся (реестр Minecraft не поднят), а
 * проверять тут нужно именно то, что режим нельзя обойти стороной. Для этого
 * берутся потребители маны в исходниках: если в одном из них появится
 * сравнение с текущим счётом в обход {@link ManaCapability#has}, режим
 * перестанет действовать там молча, и это не поймает ни один обычный тест.
 *
 * <p>По той же причине это проверки по тексту исходника — хрупкие, но
 * единственно доступные здесь. Фактическое поведение проверяется в игре.
 */
class InfiniteManaTest {

    private static final String SRC = "src/main/java/com/github/fireprime5/spellcraft/";

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    /**
     * Проверка маны идёт только через {@code ManaCapability.has}.
     *
     * <p>{@code SpellContext} сравнивал счёт сам. Из-за этого режим работал бы
     * в одном месте и не работал в другом: заклинание падало бы с «не хватает
     * маны», хотя режим включён.
     */
    @Test
    void контекстПроверяетЧерезCapability() throws IOException {
        String context = read(SRC + "spell/SpellContext.java");
        assertTrue(context.contains("ManaCapability.has("),
                "SpellContext должен проверять ману через ManaCapability.has");
        assertFalse(context.contains("ManaCapability.getMana(p) >= amount"),
                "в SpellContext не должно остаться прямого сравнения со счётом");
    }

    /** Каст тоже идёт через контекст, а не через прямой вызов capability. */
    @Test
    void исполнительНеСписываетНапрямую() throws IOException {
        String executor = read(SRC + "spell/SpellExecutor.java");
        assertFalse(executor.contains("ManaCapability.consumeMana"),
                "исполнитель должен списывать через контекст");
        assertTrue(executor.contains("ctx.consumeMana("),
                "списание должно идти через SpellContext");
    }

    /**
     * Изучение модификатора тоже не должно обходить режим.
     *
     * <p>Оно берёт ману напрямую: если бы режим действовал только на каст,
     * отладка модификаторов стоила бы настоящей маны, и режим вводился бы
     * вручную перед каждой покупкой.
     */
    @Test
    void изучениеМодификаторовУчитываетРежим() throws IOException {
        String hooks = read(SRC + "spell/KnowledgeHooks.java");
        assertTrue(hooks.contains("ManaCapability.has("),
                "проверка цены должна идти через ManaCapability.has");
        assertTrue(hooks.contains("ManaCapability.consumeMana("),
                "списание цены должно идти через consumeMana");
    }

    /**
     * Списание считается в одном месте, а не вручную в каждом.
     *
     * <p>Три места считают потраченное по-разному, и любое новое легко забыть
     * обновить. {@code spentAmount} — общая точка.
     */
    @Test
    void списаниеСчитаетсяЕдинообразно() throws IOException {
        String capability = read(SRC + "energy/ManaCapability.java");
        assertTrue(capability.contains("public static int spentAmount("),
                "нужен общий подсчёт списанного: spentAmount");
        String context = read(SRC + "spell/SpellContext.java");
        assertTrue(context.contains("ManaCapability.spentAmount("),
                "SpellContext должен считать списание через spentAmount");
    }

    /** Флаг выключается удалением ключа, а не записью false. */
    @Test
    void выключениеУдаляетКлюч() throws IOException {
        String capability = read(SRC + "energy/ManaCapability.java");
        assertTrue(capability.contains("remove(INFINITE_KEY)"),
                "выключение должно удалять ключ, а не писать false");
    }

    /**
     * Режим переживает смерть.
     *
     * <p>Флаг лежит в persistentData сущности, который при респауне не
     * переносится. Без явного копирования смерть молча отключала бы режим,
     * включённый ради отладки схемы.
     */
    @Test
    void режимПереживаетСмерть() throws IOException {
        String mod = read(SRC + "SpellcraftMod.java");
        assertTrue(mod.contains("ManaCapability.copyInfinite("),
                "обработчик клона должен переносить режим");
        String capability = read(SRC + "energy/ManaCapability.java");
        assertTrue(capability.contains("public static void copyInfinite("),
                "нужен copyInfinite рядом с copyMana");
    }

    /** Команда переключает, а не задаёт: повторный вызов выключает обратно. */
    @Test
    void командаПереключаетРежим() throws IOException {
        String commands = read(SRC + "command/ModCommands.java");
        assertTrue(commands.contains("Commands.literal(\"infmana\")"),
                "нужна команда /spellcraft infmana");
        assertTrue(commands.contains("ManaCapability.toggleInfinite("),
                "команда должна переключать режим");
        assertTrue(commands.contains("hasPermission(2)"),
                "команда меняет экономику и должна быть только операторской");
    }

    /**
     * Пакет синхронизации везёт признак режима.
     *
     * <p>Без него панель продолжала бы светиться красным «не хватает маны»
     * при бесплатном касте, и игрок считал бы это поломкой.
     */
    @Test
    void клиентУзнаётОРежиме() throws IOException {
        String payload = read(SRC + "network/ManaSyncPayload.java");
        assertTrue(payload.contains("boolean infinite"),
                "в пакете маны должен быть признак режима");
        assertTrue(payload.contains("ManaCapability.isInfinite(player)"),
                "сервер должен отправлять реальное состояние флага");
        assertTrue(payload.contains("ByteBufCodecs.BOOL"),
                "признак должен попадать в кодек пакета");
    }
}
