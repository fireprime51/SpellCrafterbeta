package com.github.fireprime5.spellcraft.armor;

import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Щит и барьер: геометрия и состояние.
 *
 * <p>Проверяется всё, что не требует {@code ServerLevel}: держит ли защита удар
 * спереди, сзади и сбоку, сколько живёт и кто ею пользуется. Сам перехват урона
 * в тестах не поднять — там нужен живой игрок.
 */
class ShieldTest {

    /** Игрок смотрит в положительном X. */
    private static final Vec3 ГЛАЗА = new Vec3(0, 64, 0);
    private static final Vec3 ВЗГЛЯД = new Vec3(1, 0, 0);

    private static Shield.PlayerView вид() {
        return new Shield.PlayerView(UUID.randomUUID(), ГЛАЗА, ВЗГЛЯД);
    }

    @BeforeEach
    void чисто() {
        Shield.reset();
    }

    // ------------------------------------------------------ дуга спереди

    /** Удар прямо перед игроком — держится. */
    @Test
    void держитУдарПередСобой() {
        assertTrue(Shield.blocks(Shield.Shape.ARC, ГЛАЗА, ВЗГЛЯД, new Vec3(2, 64, 0)));
    }

    /**
     * Удар со спины проходит.
     *
     * <p>Это не баг, а смысл щита: так же ведёт себя настоящий. Если бы дуга
     * держала кругом, «щит спереди» и «барьер» стали бы одним модулем.
     */
    @Test
    void неДержитУдарСоСпины() {
        assertFalse(Shield.blocks(Shield.Shape.ARC, ГЛАЗА, ВЗГЛЯД, new Vec3(-2, 64, 0)));
    }

    @Test
    void неДержитУдарСбоку() {
        assertFalse(Shield.blocks(Shield.Shape.ARC, ГЛАЗА, ВЗГЛЯД, new Vec3(0, 64, 3)));
    }

    /** Граница сектора: сбоку под 90° уже не держит. */
    @Test
    void секторОграниченПолуУглом() {
        точкаПодУглом(50);
        assertTrue(Shield.blocks(Shield.Shape.ARC, ГЛАЗА, ВЗГЛЯД, точкаПодУглом(50)),
                "в пределах сектора щит обязан держать");
        assertFalse(Shield.blocks(Shield.Shape.ARC, ГЛАЗА, ВЗГЛЯД, точкаПодУглом(100)),
                "за сектором щит не держит");
    }

    /** Подойти вплотную значит выбить щит — значит он обязан держать удар в упор. */
    @Test
    void держитУдарВУпор() {
        assertTrue(Shield.blocks(Shield.Shape.ARC, ГЛАЗА, ВЗГЛЯД, ГЛАЗА));
    }

    /** Дальше радиуса щит не достаёт даже прямо спереди. */
    @Test
    void неДержитЗаРадиусом() {
        double далеко = Shield.SHIELD_RADIUS + 1;
        assertFalse(Shield.blocks(Shield.Shape.ARC, ГЛАЗА, ВЗГЛЯД,
                new Vec3(далеко, 64, 0)));
    }

    // ------------------------------------------------------ сфера

    /** Барьер держит со всех сторон — в этом его смысл. */
    @Test
    void барьерДержитСоВсехСторон() {
        assertTrue(Shield.blocks(Shield.Shape.SPHERE, ГЛАЗА, ВЗГЛЯД, new Vec3(-2, 64, 0)));
        assertTrue(Shield.blocks(Shield.Shape.SPHERE, ГЛАЗА, ВЗГЛЯД, new Vec3(0, 64, 3)));
        assertTrue(Shield.blocks(Shield.Shape.SPHERE, ГЛАЗА, ВЗГЛЯД, new Vec3(0, 64, -3)));
    }

    /** Но и барьер не бесконечный. */
    @Test
    void барьерНеДержитЗаРадиусом() {
        assertFalse(Shield.blocks(Shield.Shape.SPHERE, ГЛАЗА, ВЗГЛЯД,
                new Vec3(0, 64, Shield.BARRIER_RADIUS + 1)));
    }

    // ------------------------------------------------------ неизвестно

    /**
     * Нет источника — нет решения.
     *
     * <p>Гадать хуже, чем пропустить: молча закрытый урон выглядит как поломка.
     */
    @Test
    void неизвестныйИсточникНеДержит() {
        assertFalse(Shield.blocks(Shield.Shape.ARC, ГЛАЗА, ВЗГЛЯД, null));
        assertFalse(Shield.blocks(Shield.Shape.SPHERE, ГЛАЗА, ВЗГЛЯД, null));
    }

    // ------------------------------------------------------ состояние

    @Test
    void поднятыйЩитЖивДоСрока() {
        var игрок = вид();
        Shield.raise(игрок, Shield.Shape.ARC, 40, false, 1000);
        assertTrue(Shield.active(игрок.id(), 1000).isPresent());
    }

    @Test
    void щитУмираетПоСроку() {
        var игрок = вид();
        Shield.raise(игрок, Shield.Shape.ARC, 40, false, 1000);
        assertTrue(Shield.active(игрок.id(), 1040).isPresent(), "на последнем тике ещё жив");
        assertFalse(Shield.active(игрок.id(), 1041).isPresent(), "после срока уже нет");
    }

    /**
     * Повторный подъём продлевает, а не заканчивает.
     *
     * <p>Барьер в броне поднимается каждый тик. Если бы подъём обрывал прошлый
     * срок, щит в броне умирал бы через 15 тиков после надевания.
     */
    @Test
    void повторныйПодъёмПродлевает() {
        var игрок = вид();
        Shield.raise(игрок, Shield.Shape.SPHERE, 20, false, 1000);
        Shield.raise(игрок, Shield.Shape.SPHERE, 20, false, 1010);
        Shield.raise(игрок, Shield.Shape.SPHERE, 20, false, 1020);
        assertTrue(Shield.active(игрок.id(), 1030).isPresent(),
                "броня поднимает барьер каждый тик и должна держать его живым");
    }

    /** Режим брони: без срока, пока не снимут. */
    @Test
    void режимБрониДержитБезСрока() {
        var игрок = вид();
        Shield.raiseWhileEquipped(игрок, Shield.Shape.SPHERE, false, 1000);
        assertTrue(Shield.active(игрок.id(), 1_000_000).isPresent());
    }

    @Test
    void выходОчищаетЩит() {
        var игрок = вид();
        Shield.raise(игрок, Shield.Shape.ARC, 200, false, 1000);
        Shield.clear(игрок.id());
        assertFalse(Shield.active(игрок.id(), 1000).isPresent(),
                "защита не должна пережить выход с сервера");
    }

    /** Режим «только снаряды» должен доезжать до состояния, а не теряться по дороге. */
    @Test
    void режимТолькоСнарядыСохраняется() {
        var игрок = вид();
        Shield.raise(игрок, Shield.Shape.ARC, 40, true, 1000);
        assertTrue(Shield.active(игрок.id(), 1000).orElseThrow().projectilesOnly());
    }

    /**
     * Тик защиты не должен ломать игру на клиенте.
     *
     * <p>Событие тика приходит с обеих сторон, и на клиенте игрок — LocalPlayer.
     * Слепой каст в ServerPlayer ронял игру на первом же кадре: не было видно
     * экрана, не было мира. Обычный тест это не поймает — LocalPlayer в тестах
     * не создаётся, — поэтому проверка по исходнику, как в ArmorEventsTest.
     */
    @Test
    void тикЩитаНеЛомаетКлиент() throws IOException {
        String исходник = Files.readString(Path.of(
                "src/main/java/com/github/fireprime5/spellcraft/armor/ShieldEvents.java"),
                StandardCharsets.UTF_8);
        int начало = исходник.indexOf("public static void onPlayerTick(");
        assertTrue(начало >= 0, "не найден обработчик тика щита");
        int конец = исходник.indexOf("\n    }", начало);
        String метод = исходник.substring(начало, конец);
        assertFalse(метод.contains("(ServerPlayer) event.getEntity()"),
                "слепой каст к ServerPlayer: на клиенте игрок LocalPlayer, и игра падает");
        assertTrue(метод.contains("instanceof ServerPlayer"),
                "нужна проверка instanceof до любой работы с игроком");
    }

    private static Vec3 точкаПодУглом(double градусов) {
        double rad = Math.toRadians(градусов);
        return ГЛАЗА.add(new Vec3(Math.cos(rad), 0, Math.sin(rad)).scale(2));
    }
}