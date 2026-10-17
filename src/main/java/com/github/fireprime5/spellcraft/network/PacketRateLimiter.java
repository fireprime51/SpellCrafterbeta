package com.github.fireprime5.spellcraft.network;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Окно частоты пакетов на игрока: сколько раз в секунду игрок может прислать
 * пакет, который иначе пришлось бы обрабатывать без ограничений.
 *
 * <p>Обработчики пакетов в NeoForge выполняются на главном потоке сервера,
 * поэтому гонок за счётчики окна нет. Карта — на случай, если пакет решат
 * разбирать вне основного потока.
 *
 * <p>Окна чистятся в {@link #cleanup(UUID)} при выходе игрока, иначе UUID
 * игроков накапливались бы в памяти до перезапуска.
 */
final class PacketRateLimiter {

    private final int perSecond;
    private final Map<UUID, Window> windows = new ConcurrentHashMap<>();

    PacketRateLimiter(int perSecond) {
        this.perSecond = perSecond;
    }

    boolean allow(UUID playerId) {
        long now = System.currentTimeMillis();
        Window window = windows.computeIfAbsent(playerId, key -> new Window(now, perSecond));
        if (now - window.since >= 1000) {
            window.since = now;
            window.left = perSecond;
        }
        if (window.left <= 0) {
            return false;
        }
        window.left--;
        return true;
    }

    void cleanup(UUID playerId) {
        windows.remove(playerId);
    }

    private static final class Window {
        private long since;
        private int left;

        Window(long since, int left) {
            this.since = since;
            this.left = left;
        }
    }
}
