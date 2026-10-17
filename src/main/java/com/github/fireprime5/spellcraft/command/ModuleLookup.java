package com.github.fireprime5.spellcraft.command;

import com.github.fireprime5.spellcraft.spell.SpellModuleType;
import com.github.fireprime5.spellcraft.spell.SpellModules;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;

/**
 * Поиск модуля по тому, что игрок вводит в команду.
 *
 * <p>Порядок от точного к размытому: сначала полный идентификатор, потом путь без
 * пространства имён, потом точное название, и только потом неоднозначный поиск по
 * началу строки. Так «{@code spellcraft:unique_resonance}» и «Резонанс» находят
 * одно и то же, а опечатка не выдаёт молча другой модуль.
 *
 * <p>Отдельный класс без команд и Brigadier — иначе поиск нечем было бы проверить:
 * {@code CommandSourceStack} в тестах не создаётся, а здесь нужен только реестр
 * модулей, который поднимается обычным {@code SpellModules.init()}.
 */
public final class ModuleLookup {

    /** Найденный модуль либо список кандидатов, когда запрос неоднозначен. */
    public record Result(SpellModuleType exact, List<SpellModuleType> candidates) {

        /** Найден ровно один модуль — его можно выдавать. */
        public boolean found() {
            return exact != null;
        }

        /** Под запрос подошло несколько модулей: выбирать наугад нельзя. */
        public boolean ambiguous() {
            return exact == null && candidates.size() > 1;
        }

        public static Result miss() {
            return new Result(null, List.of());
        }

        public static Result several(List<SpellModuleType> candidates) {
            return new Result(null, List.copyOf(candidates));
        }

        public static Result one(SpellModuleType type) {
            return new Result(type, List.of(type));
        }
    }

    private ModuleLookup() {
    }

    /**
     * Найти модуль по строке команды.
     *
     * @param query полный id, путь, название или его начало
     */
    public static Result resolve(String query) {
        if (query == null || query.isBlank()) {
            return Result.miss();
        }
        String trimmed = query.trim();
        int colon = trimmed.indexOf(':');

        // 1. Явное пространство имён — уважаем его. Молча подставить spellcraft
        //    значило бы спрятать опечатку оператора: он просил minecraft:damage,
        //    а получил бы spellcraft:damage и не понял бы почему.
        if (colon >= 0) {
            String namespace = trimmed.substring(0, colon);
            String path = trimmed.substring(colon + 1);
            if (namespace.isEmpty() || !validNamespace(namespace) || !validPath(path)) {
                return Result.miss();
            }
            SpellModuleType exact = byId(namespace, path);
            return exact == null ? Result.miss() : Result.one(exact);
        }

        // 2. Путь без пространства имён. Все модули живут в spellcraft, поэтому
        //    ищем только там: искать ещё и в minecraft незачем и опасно.
        if (validPath(trimmed)) {
            SpellModuleType byPath = byId("spellcraft", trimmed);
            if (byPath != null) {
                return Result.one(byPath);
            }
        }

        // 3. Точное название: сначала локализованное (доступно в игре), затем
        //    синонимы из кода. Синонимы сверяются по словам: «молния» обязана
        //    найти «молния разряд», а не строку целиком.
        //
        //    Совпадения собираются ВСЕ, а не берётся первое: слово «личный» есть
        //    у всех шести личных модулей, и поиск по первому выдал бы оператору
        //    не тот модуль молча.
        String wanted = normalize(trimmed);
        List<SpellModuleType> exact = new java.util.ArrayList<>();
        for (SpellModuleType type : SpellModules.all()) {
            if (matchesExactly(type, wanted)) {
                exact.add(type);
            }
        }
        if (exact.size() == 1) {
            return Result.one(exact.getFirst());
        }
        if (!exact.isEmpty()) {
            return Result.several(exact);
        }

        // 4. Начало строки. Один кандидат — берём, несколько — просим уточнить,
        //    молча выбрать один из них нельзя: выдача не того модуля хуже отказа
        List<SpellModuleType> found = SpellModules.search(trimmed, null);
        if (found.size() == 1) {
            return Result.one(found.getFirst());
        }
        if (found.isEmpty()) {
            return Result.miss();
        }
        return Result.several(found);
    }

    /** Модуль по готовым namespace и path, либо {@code null}. */
    private static SpellModuleType byId(String namespace, String path) {
        if (path.isEmpty()) {
            return null;
        }
        return SpellModules.get(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    /**
     * Проверка допустимых символов вместо {@code ResourceLocation.tryParse}.
     *
     * <p>{@code tryParse} на недопустимом пути не возвращает {@code null}, а
     * бросает {@code ResourceLocationException} — команда падала бы на любом
     * запросе с кириллицей, то есть ровно на том, ради чего она и нужна.
     */
    private static boolean validPath(String s) {
        return validChars(s, "abcdefghijklmnopqrstuvwxyz0123456789_-./");
    }

    private static boolean validNamespace(String s) {
        return validChars(s, "abcdefghijklmnopqrstuvwxyz0123456789_-.");
    }

    private static boolean validChars(String s, String allowed) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (allowed.indexOf(s.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    /** Название модуля так, как его видит игрок. */
    private static String name(SpellModuleType type) {
        return Component.translatable(type.nameKey()).getString();
    }

    /** Совпадает ли с запросом название модуля или одно из его слов-синонимов. */
    private static boolean matchesExactly(SpellModuleType type, String wanted) {
        if (normalize(name(type)).equals(wanted)) {
            return true;
        }
        for (String word : SpellModules.aliasesOf(type.id().getPath()).split("\\s+")) {
            if (!word.isEmpty() && normalize(word).equals(wanted)) {
                return true;
            }
        }
        return false;
    }

    /** То же приведение, что и в поиске палитры: регистр, пробелы и подчёркивания. */
    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        String lower = s.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c != ' ' && c != '_') {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}