package com.github.fireprime5.spellcraft.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Провод присоединяется к машинам.
 *
 * <p>Здесь нет ни каналов, ни значений в сети: провод остался просто проводом.
 * Проверяется ровно то, что оказалось сломано при первом появлении блока:
 * провод соединялся только с проводами, а машина не считалась соседом, и
 * линия висела на ней оборванным концом.
 *
 * <p>Сам обход мира и пересчёт лап здесь не проверяются — для них нужен
 * сервер, а реестр Minecraft в тестах не поднимается.
 */
class WireBlockTest {

    /**
     * Машина обязана быть узлом сети.
     *
     * <p>Без этого провод вплотную к машине выглядел бы висящим обрывом, и
     * игрок не мог бы понять, что линия не работает.
     */
    @Test
    void машинаВходитВСеть() {
        assertTrue(NetworkNode.class.isAssignableFrom(
                        com.github.fireprime5.spellcraft.energy.AbyssExtractorBlock.class),
                "извлекатель должен быть узлом сети, иначе провод к нему "
                        + "не присоединится");
    }

    @Test
    void проводТожеУзел() {
        // одно понятие: и провод, и машина помечаются одним интерфейсом.
        // Иначе провод пришлось бы вписывать в проверку дважды, и одна из
        // проверок рано или поздно разошлась бы с другой
        assertTrue(NetworkNode.class.isAssignableFrom(WireBlock.class),
                "провод обязан быть узлом сети");
    }

    @Test
    void отладочныйИсточникНеУзел() {
        // он раздаёт энергию всему вокруг и ничего не потребляет, поэтому
        // подключать его к сети незачем: провод к нему тянуться не должен
        assertTrue(!NetworkNode.class.isAssignableFrom(
                        com.github.fireprime5.spellcraft.energy.DebugEnergySourceBlock.class),
                "источник энергии не должен входить в сеть");
    }
}
