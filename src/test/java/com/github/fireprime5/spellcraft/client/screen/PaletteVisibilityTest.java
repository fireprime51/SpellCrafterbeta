package com.github.fireprime5.spellcraft.client.screen;

import com.github.fireprime5.spellcraft.Spellcraft;
import com.github.fireprime5.spellcraft.spell.SpellModuleType;
import com.github.fireprime5.spellcraft.spell.SpellModules;
import com.github.fireprime5.spellcraft.spell.SpellSubsections;
import com.github.fireprime5.spellcraft.spell.StartModules;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Что палитра показывает, а что прячет.
 *
 * <p>Правило «неоткрытые модули скрыты» держится на одном методе без графики,
 * поэтому его и проверяем здесь. Раньше закрытые модули показывались серыми, и
 * палитра по сути была списком всего, что есть в игре.
 */
class PaletteVisibilityTest {

    private static List<SpellModuleType> all;

    @BeforeAll
    static void setUp() {
        SpellModules.init();
        all = SpellModules.all();
    }

    /** Базовый набор знаний игрока: стартовые модули. */
    private static Set<net.minecraft.resources.ResourceLocation> starter() {
        Set<net.minecraft.resources.ResourceLocation> set = new LinkedHashSet<>();
        for (String id : StartModules.all()) {
            set.add(Spellcraft.id(id));
        }
        return set;
    }

    /** Без знаний не должен быть виден ни один модуль. */
    @Test
    void безЗнанийПалитраПуста() {
        List<SpellModuleType> shown = ModulePalette.selectVisible(all, Set.of(), true, null,
                ModulePalette.Mode.SPELL);
        assertTrue(shown.isEmpty(),
                "на пустых знаниях видно " + shown.size() + " модулей");
    }

    /** Каждый видимый модуль обязан быть в знаниях игрока. */
    @Test
    void видимыеМодулиВсегдаОткрыты() {
        Set<net.minecraft.resources.ResourceLocation> known = starter();
        List<SpellModuleType> shown = ModulePalette.selectVisible(all, known, true, null,
                ModulePalette.Mode.SPELL);
        for (SpellModuleType type : shown) {
            assertTrue(known.contains(type.id()),
                    "в палитре закрытый модуль: " + type.id());
        }
    }

    /** Обратное тоже: открытый модуль обязан быть виден, иначе знание бесполезно. */
    @Test
    void открытыеМодулиВидны() {
        Set<net.minecraft.resources.ResourceLocation> known = starter();
        List<SpellModuleType> shown = ModulePalette.selectVisible(all, known, true, null,
                ModulePalette.Mode.SPELL);
        List<String> paths = shown.stream().map(t -> t.id().getPath()).toList();
        for (var id : known) {
            SpellModuleType type = SpellModules.get(id);
            if (type != null && SpellSubsections.isArmor(type)) {
                continue;
            }
            assertTrue(paths.contains(id.getPath()),
                    "открытый модуль не показан: " + id);
        }
    }

    /** Поиск тоже не должен вытаскивать закрытые модули. */
    @Test
    void поискНеПоказываетЗакрытое() {
        String[] searches = {"урон", "взрыв", "зелье", "коричневый", "speed"};
        for (String query : searches) {
            for (SpellModuleType type : SpellModules.search(query, null)) {
                List<SpellModuleType> shown = ModulePalette.selectVisible(List.of(type), starter(),
                        true, null, ModulePalette.Mode.SPELL);
                assertTrue(shown.isEmpty(),
                        "поиск «" + query + "» выдал закрытый модуль " + type.id());
            }
        }
    }

    /** В режиме брони показываются только разрешённые модули, а не весь набор. */
    @Test
    void вРежимеБрониРаботаетБелыйСписок() {
        Set<String> allowed = com.github.fireprime5.spellcraft.armor.ModularSchema.allowed();
        Predicate<SpellModuleType> filter = type -> allowed.contains(type.id().getPath());
        List<SpellModuleType> shown = ModulePalette.selectVisible(all, starter(), false, filter,
                ModulePalette.Mode.ARMOR);
        assertTrue(!shown.isEmpty(), "в режиме брони не показано ни одного модуля");
        for (SpellModuleType type : shown) {
            assertTrue(allowed.contains(type.id().getPath()),
                    "в режиме брони показан посторонний модуль " + type.id());
        }
    }

    /** В обычном режиме модулей брони быть не должно: они не кастуются. */
    @Test
    void вОбычномРежимеНетМодулейБрони() {
        List<SpellModuleType> shown = ModulePalette.selectVisible(all, starter(), false, null,
                ModulePalette.Mode.SPELL);
        for (SpellModuleType type : shown) {
            assertFalse(SpellSubsections.isArmor(type),
                    "модуль брони попал в обычную палитру: " + type.id());
        }
    }

    /** Личные модули не должны быть видны, пока их не открыли. */
    @Test
    void личныеМодулиСкрытыДоАнализаСебя() {
        Set<net.minecraft.resources.ResourceLocation> known = starter();
        // ровно тот случай: игрок знает только стартовые модули
        List<SpellModuleType> shown = ModulePalette.selectVisible(all, known, true, null,
                ModulePalette.Mode.SPELL);
        for (SpellModuleType type : shown) {
            assertFalse(type.isUnique(), "личный модуль виден до анализа: " + type.id());
        }
    }

    /** После «Анализа себя» открытый личный модуль обязан появиться. */
    @Test
    void открытыйЛичныйМодульВиден() {
        Set<net.minecraft.resources.ResourceLocation> known = starter();
        String opened = com.github.fireprime5.spellcraft.spell.UniqueModules.SIGNATURE;
        known.add(Spellcraft.id(opened));
        List<SpellModuleType> shown = ModulePalette.selectVisible(all, known, true, null,
                ModulePalette.Mode.SPELL);
        List<String> paths = shown.stream().map(t -> t.id().getPath()).toList();
        assertTrue(paths.contains(opened), "открытый личный модуль не показан");
    }

    /** Ни один чужой личный модуль показываться не должен. */
    @Test
    void чужиеЛичныеМодулиОстаютсяСкрытыми() {
        Set<net.minecraft.resources.ResourceLocation> known = starter();
        List<SpellModuleType> shown = ModulePalette.selectVisible(all, known, true, null,
                ModulePalette.Mode.SPELL);
        Set<String> visibleUnique = new HashSet<>();
        for (SpellModuleType type : shown) {
            if (type.isUnique()) {
                visibleUnique.add(type.id().getPath());
            }
        }
        assertTrue(visibleUnique.isEmpty(),
                "видны чужие личные модули: " + visibleUnique);
    }

    /** Четыре модуля из пяти скрыты, пока открыт один — это и есть «по одному». */
    @Test
    void открытРовноОдинЛичныйМодуль() {
        Set<net.minecraft.resources.ResourceLocation> known = starter();
        known.add(Spellcraft.id(com.github.fireprime5.spellcraft.spell.UniqueModules.RESONANCE));
        List<String> visibleUnique = new ArrayList<>();
        for (SpellModuleType type : ModulePalette.selectVisible(all, known, true, null,
                ModulePalette.Mode.SPELL)) {
            if (type.isUnique()) {
                visibleUnique.add(type.id().getPath());
            }
        }
        assertTrue(visibleUnique.size() == 1,
                "после анализа видно личных модулей: " + visibleUnique);
    }
}
