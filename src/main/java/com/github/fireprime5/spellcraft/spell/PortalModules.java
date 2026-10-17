package com.github.fireprime5.spellcraft.spell;

import com.github.fireprime5.spellcraft.data.PlayerKnowledge;
import com.github.fireprime5.spellcraft.portal.PortalBlock;
import com.github.fireprime5.spellcraft.portal.PortalRegistry;
import com.github.fireprime5.spellcraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

import static com.github.fireprime5.spellcraft.spell.PortType.LOCATION;
import static com.github.fireprime5.spellcraft.spell.PortType.NUMBER;

/**
 * Модули порталов и адресов.
 *
 * <p>Портал — это блок в мире плюс запись в {@link PortalRegistry}, общем для
 * всех измерений. Модуль лишь создаёт эту пару; всё дальнейшее (переход,
 * истечение срока, снос) делает сам блок.
 *
 * <p>Два модуля различаются только числом концов:
 *
 * <ul>
 *   <li><b>«Проход»</b> — односторонний: блок ставится один, вернуться нельзя.
 *       Для схемы «свалить отряд в точку на другом конце карты».</li>
 *   <li><b>«Врата»</b> — двусторонний: блоков два, и войти можно с любого.
 *       По сути постоянный «Телепорт», только без повторной оплаты.</li>
 * </ul>
 */
public final class PortalModules {

    private PortalModules() {
    }

    private static final SpellModuleType.Category ACT = SpellModuleType.Category.ACTIONS;

    private static final int C_PORTAL = 0xFF3FD6A8;

    /** Модуль «Врата» (двусторонний), когда портал живёт недолго. */
    public static final int GATE_TIMED_COST = 200;
    /** Модуль «Проход» (односторонний), когда портал живёт недолго. */
    public static final int PASSAGE_TIMED_COST = 120;
    /** Во сколько раз дороже вечный портал. */
    public static final int PERMANENT_MULTIPLIER = 3;
    /** Максимальный срок жизни портала в секундах: неделя игрового времени. */
    public static final int MAX_PORTAL_SECONDS = 604800;

    public static void registerAll() {
        locations();
        portals();
    }

    // ---------------------------------------------------------- адреса

    private static void locations() {
        /**
         * Здесь: точка кастера вместе с его измерением.
         *
         * <p>Стартовый модуль. Нужен, чтобы поставить портал «под собой», не
         * выставляя измерение вручную.
         */
        SpellModules.register(new SpellModuleType(SpellModules.id("location_here"),
                ACT, C_PORTAL) {
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 1; }
            @Override public PortType[] outputPorts() { return new PortType[]{LOCATION}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                out.add(SpellValue.ofPlace(PortalModules.dimensionOf(ctx),
                        ctx.caster().position()));
            }
        });

        /**
         * Адрес в конкретном измерении.
         *
         * <p>Модулей три — по одному на измерение, и открываются они
         * посещением, а не сканированием. Это и есть гейт прогрессии: портал
         * ведёт туда, куда игрок уже доходил сам, поэтому моду не превращается
         * в бесплатный экспорт координат незнакомой части карты.
         *
         * <p>Координаты берутся как есть, без пересчёта 8:1 Незера: портал
         * ставится игроком по его собственным задумкам, и молчаливое умножение
         * сделало бы схему непредсказуемой.
         */
        for (Dimension dim : Dimension.values()) {
            SpellModules.register(new SpellModuleType(SpellModules.id("location_" + dim.key),
                    ACT, C_PORTAL) {
                @Override public int inputs() { return 1; }
                @Override public int outputs() { return 1; }
                @Override public int manaCost() { return 2; }
                @Override public PortType[] inputPorts() { return new PortType[]{PortType.POINT}; }
                @Override public PortType[] outputPorts() { return new PortType[]{LOCATION}; }
                @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                              List<SpellValue> out) {
                    if (!(ctx.caster() instanceof Player player)) {
                        throw new SpellContext.SpellException("cast.not_player");
                    }
                    if (!PlayerKnowledge.hasVisited(player, dim.id())) {
                        throw new SpellContext.SpellException("portal.dimension_unvisited",
                                Component.translatable("dimension.spellcraft." + dim.key)
                                        .getString());
                    }
                    out.add(SpellValue.ofPlace(dim.id(), in.get(0).asVec()));
                }
            });
        }
    }

    /** Измерения, для которых есть отдельный модуль адреса. */
    public enum Dimension {
        OVERWORLD("overworld", "minecraft:overworld"),
        NETHER("nether", "minecraft:the_nether"),
        END("end", "minecraft:the_end");

        private final String key;
        private final String id;

        Dimension(String key, String id) {
            this.key = key;
            this.id = id;
        }

        public String key() {
            return key;
        }

        /** Ключ измерения строкой — так адреса лежат в реестре порталов. */
        public String id() {
            return id;
        }
    }

    // --------------------------------------------------------- порталы

    private static void portals() {
        /**
         * Врата: двусторонний портал.
         *
         * <p>Ставятся два блока, и войти можно с любого конца — это постоянный
         * телепорт, за который платят один раз. Дороже одностороннего: значит
         * ставят два блока, и оба надо потом искать, если захочется снести.
         */
        SpellModules.register(new SpellModuleType(SpellModules.id("portal_gate"),
                ACT, C_PORTAL) {
            @Override public int inputs() { return 3; }
            @Override public int outputs() { return 1; }
            @Override public int optionalInputs() { return 1; }
            @Override public int manaCost() { return GATE_TIMED_COST; }
            @Override public PortType[] inputPorts() {
                return new PortType[]{LOCATION, LOCATION, NUMBER};
            }
            @Override public PortType[] outputPorts() { return new PortType[]{LOCATION}; }
            @Override public String inputHint(int port, java.util.Set<String> enabled) {
                return port == 2
                        ? Component.translatable("hint.spellcraft.portal_time").getString()
                        : "";
            }
            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                out.add(create(ctx, in, true, GATE_TIMED_COST));
            }
        });

        /**
         * Проход: односторонний портал.
         *
         * <p>Один блок, выход — в другое место, обратно не вернуться. Дешевле
         * врат, потому что нечего ломать пополам и не нужно искать второй конец.
         */
        SpellModules.register(new SpellModuleType(SpellModules.id("portal_passage"),
                ACT, C_PORTAL) {
            @Override public int inputs() { return 3; }
            @Override public int outputs() { return 1; }
            @Override public int optionalInputs() { return 1; }
            @Override public int manaCost() { return PASSAGE_TIMED_COST; }
            @Override public PortType[] inputPorts() {
                return new PortType[]{LOCATION, LOCATION, NUMBER};
            }
            @Override public PortType[] outputPorts() { return new PortType[]{LOCATION}; }
            @Override public String inputHint(int port, java.util.Set<String> enabled) {
                return port == 2
                        ? Component.translatable("hint.spellcraft.portal_time").getString()
                        : "";
            }
            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                out.add(create(ctx, in, false, PASSAGE_TIMED_COST));
            }
        });
    }

    /** Ключ измерения, в котором кастуют. */
    public static String dimensionOf(SpellContext ctx) {
        return ctx.level().dimension().location().toString();
    }

    /**
     * Общая часть обоих модулей: проверить, поставить блоки, записать в реестр.
     *
     * @param twoWay  ставить ли оба конца
     * @param baseCost цена временного варианта
     * @return адрес входа — его можно пустить дальше по схеме
     */
    private static SpellValue create(SpellContext ctx, List<SpellValue> in, boolean twoWay,
                                     int baseCost) {
        if (!(ctx.caster() instanceof ServerPlayer player)) {
            throw new SpellContext.SpellException("cast.not_player");
        }
        if (!(ctx.level() instanceof ServerLevel level)) {
            throw new SpellContext.SpellException("cast.server_only");
        }
        SpellValue.Place from = in.get(0).asPlace();
        SpellValue.Place to = in.get(1).asPlace();
        if (from.dimension().equals(to.dimension()) && from.blockPos().equals(to.blockPos())) {
            throw new SpellContext.SpellException("portal.same_place");
        }

        // срок жизни: 0 или без входа = навсегда
        double rawTime = in.get(2) == null ? 0 : in.get(2).asNumber();
        boolean permanent = rawTime <= 0;
        int seconds = permanent ? 0 : (int) Math.clamp(Math.round(rawTime),
                1, MAX_PORTAL_SECONDS);

        PortalRegistry registry = PortalRegistry.get(level.getServer());
        if (registry == null) {
            throw new SpellContext.SpellException("portal.unavailable");
        }
        if (registry.size() >= PortalRegistry.MAX_TOTAL) {
            throw new SpellContext.SpellException("portal.too_many_total",
                    PortalRegistry.MAX_TOTAL);
        }
        if (registry.countOwnedBy(player.getUUID()) >= PortalRegistry.MAX_PER_PLAYER) {
            throw new SpellContext.SpellException("portal.too_many_mine",
                    PortalRegistry.MAX_PER_PLAYER);
        }

        // вечный портал дороже: он остаётся в мире навсегда, и «подпорки»
        // из одноразовых дешёвых порталов в чужом регионе не должно быть
        if (permanent) {
            int extra = baseCost * (PERMANENT_MULTIPLIER - 1);
            if (extra > ctx.remainingManaBudget()) {
                throw new SpellContext.SpellException("abyss.insufficient_portal",
                        baseCost * PERMANENT_MULTIPLIER);
            }
            ctx.spendMana(extra);
        }

        BlockPos fromPos = from.blockPos();
        placePortal(level, fromPos);
        if (twoWay) {
            ServerLevel other = level.getServer().getLevel(
                    net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.DIMENSION,
                            ResourceLocation.tryParse(to.dimension())));
            if (other == null) {
                // откатываем: один конец без другого — это просто дорогой блок
                level.destroyBlock(fromPos, false);
                throw new SpellContext.SpellException("place.no_dimension");
            }
            placePortal(other, to.blockPos());
        }

        UUID id = UUID.randomUUID();
        long expiresAt = permanent ? 0 : level.getGameTime() + seconds * 20L;
        registry.add(new PortalRegistry.Entry(id, from.dimension(), fromPos,
                to.dimension(), to.blockPos(), twoWay, expiresAt, player.getUUID()));
        attach(level, fromPos, id);
        if (twoWay) {
            ServerLevel other = level.getServer().getLevel(
                    net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.DIMENSION,
                            ResourceLocation.tryParse(to.dimension())));
            if (other != null) {
                attach(other, to.blockPos(), id);
            }
        }

        ctx.trace("portal " + (twoWay ? "gate" : "passage")
                + (permanent ? " (permanent)" : " (" + seconds + "s)"));
        return in.get(0);
    }

    /** Ставит блок портала и возвращает его, либо null если место занято. */
    private static PortalBlock.PortalBlockEntity placePortal(ServerLevel level, BlockPos pos) {
        var existing = level.getBlockState(pos);
        if (!existing.isAir() && !existing.canBeReplaced()) {
            throw new SpellContext.SpellException("portal.place_blocked", pos.toShortString());
        }
        var state = ModBlocks.PORTAL.get().defaultBlockState();
        level.setBlock(pos, state, 3);
        if (level.getBlockEntity(pos) instanceof PortalBlock.PortalBlockEntity portal) {
            return portal;
        }
        return null;
    }

    private static void attach(ServerLevel level, BlockPos pos, UUID id) {
        if (level.getBlockEntity(pos) instanceof PortalBlock.PortalBlockEntity portal) {
            portal.setPortalId(id);
        }
    }
}