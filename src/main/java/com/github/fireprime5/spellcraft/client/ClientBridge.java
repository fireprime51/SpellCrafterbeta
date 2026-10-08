package com.github.fireprime5.spellcraft.client;

import com.github.fireprime5.spellcraft.network.ManaSyncPayload;
import com.github.fireprime5.spellcraft.network.OpenBuilderPayload;
import com.github.fireprime5.spellcraft.network.SpellResultPayload;
import com.github.fireprime5.spellcraft.network.SyncSpellGraphPayload;

/**
 * Мост между общими сетевыми обработчиками и клиентским кодом.
 *
 * <p>Общий класс не содержит ссылок на классы клиента, поэтому безопасен
 * для выделенного сервера. Реальная реализация ставится только на клиенте
 * (см. ClientBridgeInstaller).
 */
public final class ClientBridge {

    public interface Handler {
        void onSyncGraph(SyncSpellGraphPayload payload);

        void onSpellResult(SpellResultPayload payload);

        void onMana(ManaSyncPayload payload);

        void onOpenBuilder(OpenBuilderPayload payload);

        void onLibrary(com.github.fireprime5.spellcraft.network.LibrarySyncPayload payload);
    }

    private static final Handler NOOP = new Handler() {
        @Override
        public void onSyncGraph(SyncSpellGraphPayload payload) {
        }

        @Override
        public void onSpellResult(SpellResultPayload payload) {
        }

        @Override
        public void onMana(ManaSyncPayload payload) {
        }

        @Override
        public void onOpenBuilder(OpenBuilderPayload payload) {
        }

        @Override
        public void onLibrary(com.github.fireprime5.spellcraft.network.LibrarySyncPayload payload) {
        }
    };

    private static volatile Handler handler = NOOP;

    private ClientBridge() {
    }

    public static void setHandler(Handler newHandler) {
        handler = newHandler == null ? NOOP : newHandler;
    }

    public static Handler handler() {
        return handler;
    }
}