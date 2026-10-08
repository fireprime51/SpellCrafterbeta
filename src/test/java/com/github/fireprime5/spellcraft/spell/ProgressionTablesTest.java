package com.github.fireprime5.spellcraft.spell;

import com.github.fireprime5.spellcraft.data.PlayerKnowledge;
import com.github.fireprime5.spellcraft.data.SpellLibrary;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Согласованность игровых таблиц: награды за сканирование и модификаторы
 * должны указывать на реально зарегистрированные модули, иначе игрок
 * сканирует цель и ничего не получает.
 */
class ProgressionTablesTest {

    @BeforeAll
    static void setUp() {
        SpellModules.init();
    }

    @Test
    void всеНаградыЗаСканированиеСуществуют() {
        List<String> broken = new ArrayList<>();
        ScanRewards.blocks().forEach((block, reward) ->
                check(block, reward, broken));
        ScanRewards.mobs().forEach((mob, reward) -> check(mob, reward, broken));
        assertTrue(broken.isEmpty(), "награды ссылаются на неизвестные модули: " + broken);
        assertTrue(ScanRewards.size() > 50, "таблица наград не должна быть пустой");
    }

    @Test
    void всеНаградыМодификаторовСуществуют() {
        List<String> broken = new ArrayList<>();
        for (String target : List.of("minecraft:ancient_debris")) {
            String modifier = ScanRewards.modifierFor(target);
            assertNotNull(modifier, target + " должен учить модификатор");
            if (SpellModifiers.byId(modifier) == null) {
                broken.add(target + " -> " + modifier);
            }
        }
        assertTrue(broken.isEmpty(), "неизвестные модификаторы в таблице: " + broken);
    }

    @Test
    void стартовыеМодулиСуществуют() {
        for (String path : StartModules.all()) {
            assertNotNull(SpellModules.get(SpellModules.id(path)),
                    "стартовый модуль не зарегистрирован: " + path);
        }
    }

    @Test
    void каждыйМодульНаходитсяПоСвоемуId() {
        List<String> lost = new ArrayList<>();
        for (SpellModuleType type : SpellModules.all()) {
            boolean found = SpellModules.search(type.id().getPath(), null).stream()
                    .anyMatch(t -> t.id().equals(type.id()));
            if (!found) {
                lost.add(type.id().toString());
            }
        }
        assertTrue(lost.isEmpty(), "модули не находятся поиском по id: " + lost);
    }

    @Test
    void библиотекаИЗнанияНеПустые() {
        assertTrue(SpellLibrary.MAX_SPELLS > 0);
        assertTrue(PlayerKnowledge.DRAIN_FALLOFF.length > 0);
        assertTrue(PlayerKnowledge.DRAIN_FALLOFF[0] == 100, "первая добыча — полная цена");
    }

    private static void check(String target, String reward, List<String> broken) {
        if (SpellModules.get(SpellModules.id(reward)) == null) {
            broken.add(target + " -> " + reward);
        }
    }
}