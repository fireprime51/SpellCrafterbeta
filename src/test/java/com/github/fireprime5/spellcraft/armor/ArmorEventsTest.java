package com.github.fireprime5.spellcraft.armor;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-level regression checks, following InfiniteAbyssEnergyTest: the unit
 * test harness cannot create live Minecraft players. In-game checks are still
 * needed for ability packets, logout persistence, and integrated-server ticks.
 */
class ArmorEventsTest {
    private static String source() throws IOException {
        return Files.readString(Path.of(
                "src/main/java/com/github/fireprime5/spellcraft/armor/ArmorEvents.java"));
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "Missing method: " + signature);
        int opening = source.indexOf('{', start);
        int depth = 1;
        int end = opening + 1;
        while (depth > 0 && end < source.length()) {
            char c = source.charAt(end++);
            if (c == '{') depth++;
            if (c == '}') depth--;
        }
        return source.substring(opening + 1, end - 1);
    }

    @Test
    void clientTickReturnsBeforeReadingArmorOrMutatingSharedState() throws IOException {
        String tick = method(source(), "void onPlayerTick(");
        assertTrue(tick.matches("(?s)\\s*Player player = event.getEntity\\(\\);\\s*"
                + "if \\(player.level\\(\\).isClientSide\\) \\{\\s*return;\\s*\\}.*"),
                "The logical-client tick must return before the armor flow");
    }

    @Test
    void cloneAndLogoutDiscardEveryPerPlayerCache() throws IOException {
        String source = source();
        String clear = method(source, "void clearPlayerState(");
        for (String cache : new String[] {"CHARGED", "PROTECTION", "FLYING", "SAVED_MAYFLY",
                "LAST_CHARGE", "ARMOR_ON", "LAST_MODS"}) {
            assertTrue(clear.contains(cache + ".remove(id);"), "Uncleared cache: " + cache);
        }
        String clone = method(source, "void onRespawn(");
        assertTrue(clone.contains("clearPlayerState(event.getEntity().getUUID());"));
        String logout = method(source, "void onLogout(");
        assertTrue(source.contains("onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event)"));
        assertTrue(logout.contains("restoreFlight(player);"),
                "Remove armor-owned flight before the player is saved on logout");
        assertTrue(logout.indexOf("restoreFlight(player);") < logout.indexOf("clearPlayerState(player.getUUID());"),
                "Restore abilities before discarding their ownership state");
    }

    @Test
    void restoringArmorFlightPreservesCurrentVanillaFlight() throws IOException {
        String restore = method(source(), "void restoreFlight(");
        int modes = restore.indexOf("if (player.isCreative() || player.isSpectator())");
        int saved = restore.indexOf("SAVED_MAYFLY.remove(player.getUUID())");
        int mutation = restore.indexOf("player.getAbilities().mayfly =");
        assertTrue(saved >= 0 && modes > saved && mutation > modes,
                "Forget armor ownership, then preserve the current vanilla game mode before changing abilities");
        assertTrue(restore.substring(modes, mutation).contains("return;"),
                "Creative and spectator flight must not be revoked");
    }
}
