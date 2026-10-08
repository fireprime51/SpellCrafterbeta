package com.github.fireprime5.spellcraft.network;

import com.github.fireprime5.spellcraft.Spellcraft;
import com.github.fireprime5.spellcraft.client.ClientBridge;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Регистрация всех payload'ов. Регистрация нужна на обеих сторонах:
 * отправитель кодирует пакет по зарегистрированному кодеку.
 * S2C-обработчики просто прокидывают пакеты в ClientBridge — клиентский
 * код в этом классе не упоминается, поэтому сервер не ломается.
 */
public final class ModNetwork {

    private ModNetwork() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ModNetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Spellcraft.MODID);

        // --- клиент -> сервер ---
        registrar.playToServer(
                RequestSpellGraphPayload.TYPE,
                RequestSpellGraphPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        RequestSpellGraphPayload.handle(payload, sp);
                    }
                });

        registrar.playToServer(
                RequestManaPayload.TYPE,
                RequestManaPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        RequestManaPayload.handle(payload, sp);
                    }
                });

        registrar.playToServer(
                ModifierLearnPayload.TYPE,
                ModifierLearnPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        ModifierLearnPayload.handle(payload, sp);
                    }
                });

        registrar.playToServer(
                LibrarySyncRequest.TYPE,
                LibrarySyncRequest.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        LibrarySyncRequest.handle(payload, sp);
                    }
                });

        registrar.playToServer(
                SaveSpellPayload.TYPE,
                SaveSpellPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        SaveSpellPayload.handle(payload, sp);
                    }
                });

        registrar.playToServer(
                LoadSpellPayload.TYPE,
                LoadSpellPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        LoadSpellPayload.handle(payload, sp);
                    }
                });

        registrar.playToServer(
                DeleteSpellPayload.TYPE,
                DeleteSpellPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        DeleteSpellPayload.handle(payload, sp);
                    }
                });

        registrar.playToServer(
                UpdateSpellGraphPayload.TYPE,
                UpdateSpellGraphPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        UpdateSpellGraphPayload.handle(payload, sp);
                    }
                });

        // --- сервер -> клиент ---
        registrar.playToClient(
                SyncSpellGraphPayload.TYPE,
                SyncSpellGraphPayload.STREAM_CODEC,
                (payload, context) -> ClientBridge.handler().onSyncGraph(payload));

        registrar.playToClient(
                SpellResultPayload.TYPE,
                SpellResultPayload.STREAM_CODEC,
                (payload, context) -> ClientBridge.handler().onSpellResult(payload));

        registrar.playToClient(
                LibrarySyncPayload.TYPE,
                LibrarySyncPayload.STREAM_CODEC,
                (payload, context) -> ClientBridge.handler().onLibrary(payload));

        registrar.playToClient(
                ManaSyncPayload.TYPE,
                ManaSyncPayload.STREAM_CODEC,
                (payload, context) -> ClientBridge.handler().onMana(payload));

        registrar.playToClient(
                OpenBuilderPayload.TYPE,
                OpenBuilderPayload.STREAM_CODEC,
                (payload, context) -> ClientBridge.handler().onOpenBuilder(payload));
    }
}