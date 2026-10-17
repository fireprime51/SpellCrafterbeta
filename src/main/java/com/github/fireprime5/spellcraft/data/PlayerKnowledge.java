package com.github.fireprime5.spellcraft.data;

import com.github.fireprime5.spellcraft.Spellcraft;
import com.github.fireprime5.spellcraft.spell.UniqueModules;
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
import java.util.function.Supplier;

/**
 * Знания игрока: какие модули изучены и что уже просканировано.
 *
 * Очков прогресса больше нет: сканирование сразу даёт конкретный модуль,
 * а усиленные версии добываются модулями-модификациями за энергию бездны.
 */
public final class PlayerKnowledge {

    /** Процент энергии бездны от базовой цены при повторной добыче с одной цели: 100, 50, 33, ... */
    public static final int[] DRAIN_FALLOFF = {100, 50, 33, 25, 20, 16, 14, 12, 11, 10};

    /** Сколько последних открытий хранится в журнале. */
    public static final int LOG_LIMIT = 12;

    /** Сколько целей помним для падающей добычи энергии бездны. */
    private static final int MAX_DRAIN_TARGETS = 512;

    /**
     * Записи игрока: изученные модули, изученные цели, журнал открытий,
     * изученные модификаторы и счётчики добычи энергии бездны (антифарм).
     */
    public record Knowledge(Set<String> modules, Set<String> scanned, List<String> log,
                            Set<String> modifiers, Map<String, Integer> drained,
                            Set<String> dimensions, String unique, int abyssSpent) {

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
                        .forGetter(Knowledge::drained),
                // посещённые измерения открывают модули «Точка: Незер/Энд»:
                // портал в измерение, где игрок не был, иначе был бы бесплатным
                // экспортом координат со всей карты
                Codec.STRING.listOf().fieldOf("dimensions").orElse(List.of())
                        .forGetter(k -> List.copyOf(k.dimensions())),
                // Личный модуль. Пустая строка — «ещё не выдан». orElse нужен
                // для миров, где игрок играл до появления механики: без него
                // чтение старой записи падало бы и ломало загрузку игрока.
                Codec.STRING.optionalFieldOf("unique", "").forGetter(Knowledge::unique),
                // Сколько бездны потрачено за жизнь. Считает личный модуль
                // «Счёт»; orElse — то же, для старых записей.
                Codec.INT.optionalFieldOf("abyssSpent", 0).forGetter(Knowledge::abyssSpent)
        ).apply(inst, (modules, scanned, log, modifiers, drained, dimensions, unique, abyssSpent) ->
                new Knowledge(new LinkedHashSet<>(modules),
                        new LinkedHashSet<>(scanned), List.copyOf(log),
                        new LinkedHashSet<>(modifiers), new LinkedHashMap<>(drained),
                        new LinkedHashSet<>(dimensions), unique, abyssSpent)));

        public Knowledge {
            modules = normalizeModuleIds(modules);
            scanned = new LinkedHashSet<>(scanned);
            log = new ArrayList<>(log);
            modifiers = new LinkedHashSet<>(modifiers);
            drained = new LinkedHashMap<>(drained);
            dimensions = new LinkedHashSet<>(dimensions);
            unique = unique == null ? "" : unique;
        }

        /** Старый конструктор — без счётчиков добычи, измерений и личного модуля. */
        public Knowledge(Set<String> modules, Set<String> scanned, List<String> log,
                         Set<String> modifiers) {
            this(modules, scanned, log, modifiers, Map.of(), Set.of(), "", 0);
        }

        /** Запись без личного модуля, но с посещёнными измерениями. */
        public Knowledge(Set<String> modules, Set<String> scanned, List<String> log,
                         Set<String> modifiers, Map<String, Integer> drained,
                         Set<String> dimensions) {
            this(modules, scanned, log, modifiers, drained, dimensions, "", 0);
        }

        /** Копия с заменой одного поля — иначе каждое изменение переписывает всё. */
        public Knowledge withUnique(String value) {
            return new Knowledge(modules, scanned, log, modifiers, drained, dimensions, value,
                    abyssSpent);
        }

        /**
         * Идентификаторы модулей хранятся с пространством имён.
         *
         * <p>«Анализ себя» когда-то записывал личный модуль голым путём —
         * {@code "unique_resonance"} вместо {@code "spellcraft:unique_resonance"}.
         * На клиенте {@code ResourceLocation.tryParse} разбирал такое как
         * {@code minecraft:unique_resonance}, а палитра искала
         * {@code spellcraft:unique_resonance} — и открытый модуль не появлялся
         * нигде. Нормализация чинит и уже сохранённые миры, а не только новые.
         *
         * <p>Нормализуются только модули: в {@code scanned} лежат id сущностей
         * вида {@code minecraft:zombie}, и дописывать им наше пространство имён
         * нельзя.
         */
        private static java.util.Set<String> normalizeModuleIds(
                java.util.Set<String> raw) {
            java.util.Set<String> out = new LinkedHashSet<>(raw.size());
            for (String id : raw) {
                if (id == null || id.isBlank()) {
                    continue;
                }
                out.add(id.indexOf(':') < 0 ? Spellcraft.MODID + ":" + id : id);
            }
            return out;
        }

        /** Копия с добавленным модулем: используется и журналом, и счётчиком. */
        public Knowledge withModule(String moduleId) {
            Set<String> next = new LinkedHashSet<>(modules);
            next.add(moduleId);
            return new Knowledge(next, scanned, trimLog(log, moduleId), modifiers, drained,
                    dimensions, unique, abyssSpent);
        }

        public Knowledge withAbyssSpent(int value) {
            return new Knowledge(modules, scanned, log, modifiers, drained, dimensions, unique,
                    Math.max(0, value));
        }
    }

    /** Регистрация вложения: только в игре, тесты её не трогают. */
    public static final class Attachments {
        public static final DeferredRegister<AttachmentType<?>> REGISTRY =
                DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries
                        .ATTACHMENT_TYPES, Spellcraft.MODID);

        /** Пустая запись для нового игрока. */
        private static final Supplier<Knowledge> EMPTY = () -> new Knowledge(
                Set.of(), Set.of(), List.of(), Set.of(), Map.of(), Set.of(), "", 0);

        public static final DeferredHolder<AttachmentType<?>, AttachmentType<Knowledge>> TYPES =
                REGISTRY.register("spell_knowledge", () -> AttachmentType
                        .builder(EMPTY)
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
     * <p>Личные модули команда не выдаёт: они открываются только «Анализом себя»,
     * иначе отладочная команда обходила бы саму механику открытия.
     *
     * @return сколько модулей реально открыто (уже изученные не считаются)
     */
    public static int learnAll(Player player) {
        int granted = 0;
        for (String id : allModuleIds()) {
            var resource = net.minecraft.resources.ResourceLocation.tryParse(id);
            var type = resource == null ? null
                    : com.github.fireprime5.spellcraft.spell.SpellModules.get(resource);
            if (type != null && type.isUnique()) {
                continue;
            }
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
        set(player, knowledge.withModule(id.toString()));
        return true;
    }

    /**
     * Розыгрывает личный модуль — один раз на игрока в этом мире.
     *
     * <p>Модуль назначается, но не открывается: игрок узнает его только через
     * «Анализ себя». Поэтому он не попадает в {@code modules} и не виден в
     * палитре, пока {@link #discoverUnique} его не туда не перенесёт.
     *
     * <p>Запись хранится в данных игрока, поэтому переживает перезаход, смерть и
     * переустановку мода, а в новом мире проходит новый розыгрыш.
     *
     * @return назначенный модуль, либо {@code ""}, если он уже был назначен
     */
    public static String assignUnique(Player player, net.minecraft.util.RandomSource random) {
        Knowledge knowledge = get(player);
        if (!knowledge.unique().isEmpty()) {
            return knowledge.unique();
        }
        String chosen = UniqueModules.choose(random);
        if (chosen.isEmpty()) {
            return "";
        }
        set(player, knowledge.withUnique(chosen));
        return chosen;
    }

    /**
     * Открывает назначенный личный модуль — «Анализ себя».
     *
     * @return путь открытого модуля, либо {@code ""}, если его уже открывали
     */
    public static String discoverUnique(Player player) {
        Knowledge knowledge = get(player);
        String assigned = knowledge.unique();
        if (assigned.isEmpty()) {
            // Мир, в котором игрок зашёл до появления розыгрыша при входе: у него
            // просто не записано, что ему досталось. Розыгрываем здесь же и сразу
            // пишем — иначе «Анализ себя» отвечал бы «ты уже нашёл» игроку, который
            // ничего не находил. Повторно это не лотерея: результат сохраняется.
            assigned = assignUnique(player, player.getRandom());
        }
        var id = Spellcraft.id(assigned);
        if (knowledge.modules().contains(id.toString())) {
            return "";
        }
        // именно id.toString(), а не assigned: в modules лежат полные
        // идентификаторы «spellcraft:unique_resonance», и голый путь оттуда
        // превращался на клиенте в minecraft:unique_resonance — открытый модуль
        // не появлялся в палитре вообще
        set(player, get(player).withModule(id.toString()));
        return assigned;
    }

    /** Сколько бездны игрок потратил за жизнь. */
    public static int totalAbyssSpent(Player player) {
        return get(player).abyssSpent();
    }

    /** Начисляет потраченную бездну к счётчику личного модуля «Счёт». */
    public static void addAbyssSpent(Player player, int amount) {
        if (amount <= 0) {
            return;
        }
        Knowledge knowledge = get(player);
        set(player, knowledge.withAbyssSpent(knowledge.abyssSpent() + amount));
    }

    /** Изучен ли модификатор. */
    public static boolean knowsModifier(Player player, String modifierId) {
        return get(player).modifiers().contains(modifierId);
    }

    /** Изучает модификатор (после успешной модификации за энергию бездны). */
    public static boolean learnModifier(Player player, String modifierId) {
        Knowledge knowledge = get(player);
        if (knowledge.modifiers().contains(modifierId)) {
            return false;
        }
        Set<String> modifiers = new LinkedHashSet<>(knowledge.modifiers());
        modifiers.add(modifierId);
        List<String> log = trimLog(knowledge.log(), "modifier:" + modifierId);
        set(player, new Knowledge(knowledge.modules(), knowledge.scanned(), log, modifiers,
                knowledge.drained(), knowledge.dimensions(),
                knowledge.unique(), knowledge.abyssSpent()));
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
                knowledge.modifiers(), knowledge.drained(), knowledge.dimensions(),
                knowledge.unique(), knowledge.abyssSpent()));
        return true;
    }

    public static boolean wasScanned(Player player, String targetId) {
        return get(player).scanned().contains(targetId);
    }

    /**
     * Сколько раз с цели уже брали энергию бездны. Каждая следующая добыча с той же цели
     * даёт меньше: 100%, 50%, 33% ... и так до 10% от базовой цены.
     */
    public static int drainCount(Player player, String targetKey) {
        return get(player).drained().getOrDefault(targetKey, 0);
    }

    /** Учитывает добычу энергии бездны с цели, чтобы её нельзя было фармить бесконечно. */
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
                knowledge.modifiers(), drained, knowledge.dimensions(),
                knowledge.unique(), knowledge.abyssSpent()));
    }

    /** Помечает измерение посещённым. Вызывается при смене измерения. */
    public static boolean markDimension(Player player, String dimension) {
        Knowledge knowledge = get(player);
        if (knowledge.dimensions().contains(dimension)) {
            return false;
        }
        Set<String> dimensions = new LinkedHashSet<>(knowledge.dimensions());
        dimensions.add(dimension);
        set(player, new Knowledge(knowledge.modules(), knowledge.scanned(), knowledge.log(),
                knowledge.modifiers(), knowledge.drained(), dimensions,
                knowledge.unique(), knowledge.abyssSpent()));
        return true;
    }

    /** Был ли игрок в этом измерении. */
    public static boolean hasVisited(Player player, String dimension) {
        return get(player).dimensions().contains(dimension);
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