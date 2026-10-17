package com.github.fireprime5.spellcraft.client;

import com.github.fireprime5.spellcraft.network.AbyssEnergySyncPayload;
import com.github.fireprime5.spellcraft.network.OpenBuilderPayload;
import com.github.fireprime5.spellcraft.network.OpenMachineGridPayload;
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

        void onMana(AbyssEnergySyncPayload payload);

        void onOpenBuilder(OpenBuilderPayload payload);

        void onOpenMachineGrid(OpenMachineGridPayload payload);

        /** Схема модулей машины пришла с сервера. */
        void onSyncMachineGrid(
                com.github.fireprime5.spellcraft.network.SyncMachineGridPayload payload);

        /** Схема модулей брони пришла с сервера. */
        void onSyncArmorGraph(
                com.github.fireprime5.spellcraft.network.SyncArmorGraphPayload payload);

        /** Открыть конструктор модулей брони. */
        void onOpenArmorBuilder(
                com.github.fireprime5.spellcraft.network.OpenArmorBuilderPayload payload);

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
        public void onMana(AbyssEnergySyncPayload payload) {
        }

        @Override
        public void onOpenBuilder(OpenBuilderPayload payload) {
        }

        @Override
        public void onOpenMachineGrid(OpenMachineGridPayload payload) {
        }

        @Override
        public void onSyncMachineGrid(
                com.github.fireprime5.spellcraft.network.SyncMachineGridPayload payload) {
        }

        @Override
        public void onSyncArmorGraph(
                com.github.fireprime5.spellcraft.network.SyncArmorGraphPayload payload) {
        }

        @Override
        public void onOpenArmorBuilder(
                com.github.fireprime5.spellcraft.network.OpenArmorBuilderPayload payload) {
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