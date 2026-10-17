package com.github.fireprime5.spellcraft.spell;

import com.github.fireprime5.spellcraft.data.PlayerKnowledge;
import com.github.fireprime5.spellcraft.data.SpellLibrary;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    void недостижимыйМодульПомеченЯвно() {
        // иначе палитра обещает «просканируй» тому, кто отсканировать
        // не в состоянии, и игрок бессмысленно ищет цель
        String unreachable = SpellPrerequisites.UNREACHABLE;
        assertNotNull(SpellModules.get(SpellModules.id(unreachable)),
                "помеченный недостижимым модуль должен существовать: " + unreachable);
        assertTrue(SpellPrerequisites.isUnreachable(unreachable));
        assertTrue(!ScanRewards.isReward(unreachable),
                "помеченный недостижимым модуль выдаётся за сканирование: " + unreachable);
        assertTrue(!reachableFromStart().contains(unreachable),
                "помеченный недостижимым модуль оказался достижим: " + unreachable);
    }

    @Test
    void каждыйМодульДостижимИгроком() {
        Set<String> reachable = reachableFromStart();
        List<String> lost = new ArrayList<>();
        for (SpellModuleType type : SpellModules.all()) {
            String path = type.id().getPath();
            if (SpellPrerequisites.isUnreachable(path)) {
                continue;
            }
            if (!reachable.contains(path)) {
                lost.add(path);
            }
        }
        assertTrue(lost.isEmpty(),
                "модули, которые нельзя получить ни сканированием, ни предпосылкой "
                        + "(каждый должен иметь награду за скан либо предпосылку, "
                        + "ведущую к награде): " + lost);
    }

    @Test
    void недостижимымОсталсяТолькоРасщепление() {
        Set<String> reachable = reachableFromStart();
        List<String> lost = new ArrayList<>();
        for (SpellModuleType type : SpellModules.all()) {
            String path = type.id().getPath();
            if (!reachable.contains(path) && !SpellPrerequisites.isUnreachable(path)) {
                lost.add(path);
            }
        }
        assertTrue(lost.isEmpty(),
                "кроме «Расщепления» не должно быть недостижимых модулей: " + lost);
    }

    @Test
    void предпосылкиСуществуютИНеЦиклятся() {
        for (Map.Entry<String, String> entry : SpellPrerequisites.all().entrySet()) {
            String module = entry.getKey();
            String required = entry.getValue();
            assertNotNull(SpellModules.get(SpellModules.id(module)),
                    "предпосылка объявлена для неизвестного модуля: " + module);
            assertNotNull(SpellModules.get(SpellModules.id(required)),
                    "предпосылка ссылается на неизвестный модуль: "
                            + module + " -> " + required);

            // идём по цепочке до старта; если дошли до себя — кольцо,
            // и разблокировка зависнет намертво
            Set<String> seen = new LinkedHashSet<>();
            String cursor = module;
            while (cursor != null && seen.add(cursor)) {
                cursor = SpellPrerequisites.of(cursor);
            }
            assertTrue(cursor == null,
                    "предпосылки образуют цикл вокруг " + module);
        }
    }

    @Test
    void уМодуляСНаградойНетПредпосылки() {
        // иначе награда за сканирование может оказаться бесполезной:
        // игрок её получит, а модуль останется заблокирован
        List<String> odd = new ArrayList<>();
        for (String reward : allScanRewards()) {
            if (SpellPrerequisites.hasPrerequisite(reward)) {
                odd.add(reward);
            }
        }
        assertTrue(odd.isEmpty(),
                "у этих модулей есть и награда, и предпосылка — выбери что-то одно: " + odd);
    }

    @Test
    void константыИСвояПозицияДоступныСразу() {
        // константы — подножие, а не умение: их не за что «изучать»
        for (String constant : List.of("const_zero", "const_one", "const_five",
                "const_ten", "const_neg_one")) {
            assertTrue(StartModules.all().contains(constant),
                    constant + " должен быть стартовым модулем");
        }
        assertTrue(StartModules.all().contains("self_position"),
                "«здесь» должно быть сразу, иначе первое заклинание не собрать");
    }

    /** Всё, что игрок может знать: стартовое плюс награды, развёрнутые по цепочкам. */
    private static Set<String> reachableFromStart() {
        Set<String> known = new HashSet<>(StartModules.all());
        known.addAll(allScanRewards());
        // Третий путь получения, добавленный вместе с личными модулями: «Анализ
        // себя» выдаёт игроку ровно один из набора. Набор целиком добавляем
        // сюда, потому что «достижим» значит «может достаться хоть кому-то
        // одному» — сам факт выдачи проверяется отдельно, в
        // UniqueModulesTest: один игрок получает один модуль, а не весь набор.
        known.addAll(List.of(UniqueModules.ALL));
        // разворачиваем предпосылки до упора, как это делает клиент
        List<SpellModuleType> all = SpellModules.all();
        for (int pass = 0; pass < all.size(); pass++) {
            boolean changed = false;
            for (SpellModuleType type : all) {
                String need = SpellPrerequisites.of(type);
                if (need != null && known.contains(need)) {
                    changed |= known.add(type.id().getPath());
                }
            }
            if (!changed) {
                break;
            }
        }
        return known;
    }

    private static Set<String> allScanRewards() {
        Set<String> rewards = new HashSet<>(ScanRewards.blocks().values());
        rewards.addAll(ScanRewards.mobs().values());
        return rewards;
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