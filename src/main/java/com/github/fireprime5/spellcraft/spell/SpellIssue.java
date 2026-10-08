package com.github.fireprime5.spellcraft.spell;

import net.minecraft.network.chat.Component;

/**
 * Проблема в заклинании: ключ перевода + аргумент.
 * Хранится и передаётся по сети как строка "key\0arg".
 */
public record SpellIssue(String key, String arg) {

    private static final char SEP = '\0';

    public static SpellIssue of(String key) {
        return new SpellIssue(key, "");
    }

    public static SpellIssue of(String key, Object arg) {
        return new SpellIssue(key, String.valueOf(arg));
    }

    public Component text() {
        return arg.isEmpty()
                ? Component.translatable("error.spellcraft." + key)
                : Component.translatable("error.spellcraft." + key, arg);
    }

    public String encode() {
        return key + SEP + arg;
    }

    public static SpellIssue decode(String encoded) {
        int i = encoded.indexOf(SEP);
        return i < 0 ? new SpellIssue(encoded, "")
                : new SpellIssue(encoded.substring(0, i), encoded.substring(i + 1));
    }
}