package com.github.fireprime5.spellcraft.data;

import com.github.fireprime5.spellcraft.Spellcraft;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Знания игрока: какие модули изучены и что уже просканировано.
 *
 * Очков прогресса больше нет: сканирование сразу даёт конкретный модуль,
 * а усиленные версии добываются модулями-модификациями за ману.
 */
public final class PlayerKnowledge {

    /** Процент маны от базовой цены при повторной добыче с одной цели: 100, 50, 33, ... */
    public static final int[] DRAIN_FALLOFF = {100, 50, 33, 25, 20, 16, 14, 12, 11, 10};

    /** Сколько последних открытий хранится в журнале. */
    public static final int LOG_LIMIT = 12;

    /** Сколько целей помним для падающей добычи маны. */
    private static final int MAX_DRAIN_TARGETS = 512;

    /**
     * Записи игрока: изученные модули, изученные цели, журнал открытий,
     * изученные модификаторы и счётчики добычи маны (антифарм).
     */
    public record Knowledge(Set<String> modules, Set<String> scanned, List<String> log,
                            Set<String> modifiers, Map<String, Integer> drained) {

        public static final Codec<Knowledge> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.listOf().fieldOf("modules").orElse(List.of())
                        .forGetter(k -> List.copyOf(k.modules())),
                Codec.STRING.listOf().fieldOf("scanned").orElse(List.of())
                        .forGetter(k -> List.copyOf(k.scanned())),
                Codec.STRING.listOf().fieldOf("log").orElse(List.of())
                        .forGetter(k -> List.copyOf(k.log())),
                Codec.STRING.listOf().fieldOf("modifiers").orElse(List.of())
                        .forGetter(k -> List.copyOf(k.modifiers())),
                Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("drained").orElse(Map.of())
                        .forGetter(Knowledge::drained)
        ).apply(inst, (modules, scanned, log, modifiers, drained) ->
                new Knowledge(new LinkedHashSet<>(modules),
                        new LinkedHashSet<>(scanned), List.copyOf(log),
                        new LinkedHashSet<>(modifiers), new LinkedHashMap<>(drained))));

        public Knowledge {
            modules = new LinkedHashSet<>(modules);
            scanned = new LinkedHashSet<>(scanned);
            log = new ArrayList<>(log);
            modifiers = new LinkedHashSet<>(modifiers);
            drained = new LinkedHashMap<>(drained);
        }

        /** Старый конструктор — без счётчиков добычи. */
        public Knowledge(Set<String> modules, Set<String> scanned, List<String> log,
                         Set<String> modifiers) {
            this(modules, scanned, log, modifiers, Map.of());
        }
    }

    /** Регистрация вложения: только в игре, тесты её не трогают. */
    public static final class Attachments {
        public static final DeferredRegister<AttachmentType<?>> REGISTRY =
                DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries
                        .ATTACHMENT_TYPES, Spellcraft.MODID);

        public static final DeferredHolder<AttachmentType<?>, AttachmentType<Knowledge>> TYPES =
                REGISTRY.register("spell_knowledge", () -> AttachmentType
                        .<Knowledge>builder((java.util.function.Supplier<Knowledge>)
                                () -> new Knowledge(Set.of(), Set.of(), List.of(), Set.of()))
                        .serialize(Knowledge.CODEC)
                        .copyOnDeath()
                        .build());

        private Attachments() {
        }
    }

    private PlayerKnowledge() {
    }

    public static Knowledge get(Player player) {
        return player.getData(Attachments.TYPES.get());
    }

    public static void set(Player player, Knowledge knowledge) {
        player.setData(Attachments.TYPES.get(), knowledge);
    }

    /** Изучен ли модуль. Стартовые модули доступны всегда. */
    public static boolean knows(Player player, net.minecraft.resources.ResourceLocation id) {
        if (com.github.fireprime5.spellcraft.spell.StartModules.isStartModule(id)) {
            return true;
        }
        return get(player).modules().contains(id.toString());
    }

    /** id всех зарегистрированных модулей — то, что выдаёт команда модулей. */
    public static List<String> allModuleIds() {
        List<String> ids = new java.util.ArrayList<>();
        for (var type : com.github.fireprime5.spellcraft.spell.SpellModules.all()) {
            ids.add(type.id().toString());
        }
        return List.copyOf(ids);
    }

    /**
     * Открывает игроку все модули (отладочная команда {@code /spellcraft modules}).
     *
     * @return сколько модулей реально открыто (уже изученные не считаются)
     */
    public static int learnAll(Player player) {
        int granted = 0;
        for (String id : allModuleIds()) {
            var resource = net.minecraft.resources.ResourceLocation.tryParse(id);
            if (resource != null && learn(player, resource)) {
                granted++;
            }
        }
        return granted;
    }

    public static boolean knows(Player player, com.github.fireprime5.spellcraft.spell.SpellModuleType type) {
        return knows(player, type.id());
    }

    /** Изучает модуль. true — если это действительно новое открытие. */
    public static boolean learn(Player player, net.minecraft.resources.ResourceLocation id) {
        Knowledge knowledge = get(player);
        if (knowledge.modules().contains(id.toString())) {
            return false;
        }
        Set<String> modules = new LinkedHashSet<>(knowledge.modules());
        modules.add(id.toString());
        List<String> log = trimLog(knowledge.log(), id.toString());
        set(player, new Knowledge(modules, knowledge.scanned(), log, knowledge.modifiers(),
                knowledge.drained()));
        return true;
    }

    /** Изучен ли модификатор. */
    public static boolean knowsModifier(Player player, String modifierId) {
        return get(player).modifiers().contains(modifierId);
    }

    /** Изучает модификатор (после успешной модификации за ману). */
    public static boolean learnModifier(Player player, String modifierId) {
        Knowledge knowledge = get(player);
        if (knowledge.modifiers().contains(modifierId)) {
            return false;
        }
        Set<String> modifiers = new LinkedHashSet<>(knowledge.modifiers());
        modifiers.add(modifierId);
        List<String> log = trimLog(knowledge.log(), "modifier:" + modifierId);
        set(player, new Knowledge(knowledge.modules(), knowledge.scanned(), log, modifiers,
                knowledge.drained()));
        return true;
    }

    /** Журнал открытий ограничен по размеру (как и в learn()). */
    private static List<String> trimLog(List<String> log, String entry) {
        List<String> copy = new ArrayList<>(log);
        copy.add(entry);
        while (copy.size() > LOG_LIMIT) {
            copy.remove(0);
        }
        return List.copyOf(copy);
    }

    /** Все изученные модификаторы. */
    public static java.util.Set<String> knownModifiers(Player player) {
        return get(player).modifiers();
    }

    /**
     * Отмечает цель как изученную.
     *
     * @return true, если цель сканируют впервые
     */
    public static boolean markScanned(Player player, String targetId) {
        Knowledge knowledge = get(player);
        if (knowledge.scanned().contains(targetId)) {
            return false;
        }
        Set<String> scanned = new LinkedHashSet<>(knowledge.scanned());
        scanned.add(targetId);
        set(player, new Knowledge(knowledge.modules(), scanned, knowledge.log(),
                knowledge.modifiers(), knowledge.drained()));
        return true;
    }

    public static boolean wasScanned(Player player, String targetId) {
        return get(player).scanned().contains(targetId);
    }

    /**
     * Сколько раз с цели уже брали ману. Каждая следующая добыча с той же цели
     * даёт меньше: 100%, 50%, 33% ... и так до 10% от базовой цены.
     */
    public static int drainCount(Player player, String targetKey) {
        return get(player).drained().getOrDefault(targetKey, 0);
    }

    /** Учитывает добычу маны с цели, чтобы её нельзя было фармить бесконечно. */
    public static void recordDrain(Player player, String targetKey) {
        Knowledge knowledge = get(player);
        Map<String, Integer> drained = new LinkedHashMap<>(knowledge.drained());
        drained.merge(targetKey, 1, Integer::sum);
        // не храним бесконечный список целей
        while (drained.size() > MAX_DRAIN_TARGETS) {
            String firstKey = drained.keySet().iterator().next();
            drained.remove(firstKey);
        }
        set(player, new Knowledge(knowledge.modules(), knowledge.scanned(), knowledge.log(),
                knowledge.modifiers(), drained));
    }

    /** Сколько всего модулей изучено. */
    public static int learnedCount(Player player) {
        int count = 0;
        for (var type : com.github.fireprime5.spellcraft.spell.SpellModules.all()) {
            if (knows(player, type)) {
                count++;
            }
        }
        return count;
    }

    /** Изученные модули (без стартовых). */
    public static java.util.List<String> knownModules(Player player) {
        return java.util.List.copyOf(get(player).modules());
    }

    /** Журнал последних открытий (для панели). */
    public static List<String> recentLog(Player player) {
        return get(player).log();
    }
}