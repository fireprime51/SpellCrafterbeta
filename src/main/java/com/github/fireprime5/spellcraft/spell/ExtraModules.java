package com.github.fireprime5.spellcraft.spell;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.github.fireprime5.spellcraft.spell.PortType.ANY;
import static com.github.fireprime5.spellcraft.spell.PortType.ENTITY;
import static com.github.fireprime5.spellcraft.spell.PortType.ITEM;
import static com.github.fireprime5.spellcraft.spell.PortType.NUMBER;
import static com.github.fireprime5.spellcraft.spell.PortType.POINT;
import static com.github.fireprime5.spellcraft.spell.PortType.VECTOR;

/**
 * Расширенный набор модулей: математика, сущности, мир, логика.
 *
 * <p>Здесь больше нет модулей предметов («Предмет в руке», «Предмет в ячейке
 * хотбара», «Блок на месте», «Выдать предмет» и прочие). Вместе они давали
 * бесконечные предметы: стопка копировалась из мира и инвентаря и тут же
 * попадала обратно, ничего не тратя. Постройка осталась только из блока, который
 * игрок и так держит в руке (см. {@code WorldModules#place_held_block}).
 */
public final class ExtraModules {

    private ExtraModules() {
    }

    private static final SpellModuleType.Category NUM = SpellModuleType.Category.NUMBERS;
    private static final SpellModuleType.Category ENT = SpellModuleType.Category.ENTITIES;
    private static final SpellModuleType.Category ACT = SpellModuleType.Category.ACTIONS;
    private static final SpellModuleType.Category LOG = SpellModuleType.Category.LOGIC;

    private static final int C_NUM = 0xFFE2A44E;
    private static final int C_ENT = 0xFF63C97C;
    private static final int C_ACT = 0xFFE86060;
    private static final int C_LOG = 0xFF9285F0;

    /** Дальность поиска цели по прицелу. */
    private static final double CROSSHAIR_RANGE = 32;

    /**
     * Сколько ячеек читает {@code held_slot}: 9 хотбара + 27 основного инвентаря.
     *
     * <p>Ровно длина {@code Inventory#items}, поэтому индекс «номер минус один»
     * попадает и в хотбар, и в основной инвентарь.
     */
    public static final int INVENTORY_SLOTS = 36;

    public static void registerAll() {
        numbers();
        entities();
        world();
        items();
        logic();
        blocks();
        actions();
    }

    private static void reg(SpellModuleType type) {
        SpellModules.register(type);
    }

    // ---------------------------------------------------------------- числа

    private static void numbers() {
        reg(new SpellModuleType(SpellModules.id("random"), NUM, C_NUM) {
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 4; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                out.add(SpellValue.of(ctx.level().random.nextInt(101)));
            }
        });

        reg(new SpellModuleType(SpellModules.id("random_range"), NUM, C_NUM) {
            @Override public int inputs() { return 2; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 5; }
            @Override public PortType[] inputPorts() { return new PortType[]{NUMBER, NUMBER}; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                double a = in.get(0).asNumber();
                double b = in.get(1).asNumber();
                double min = Math.min(a, b);
                double max = Math.max(a, b);
                out.add(SpellValue.of(min + ctx.level().random.nextDouble() * (max - min)));
            }
        });

        math1("abs", 2, Math::abs);
        math1("floor", 2, Math::floor);
        math1("ceil", 2, Math::ceil);
        math1("round", 2, Math::round);
        math1("sqrt", 3, Math::sqrt);
        math1("sign", 2, Math::signum);

        // Тригонометрия в градусах, а не в радианах: градусами же считает
        // «Поворот вектора», и радианы в заклинании пришлось бы переводить
        // вручную, чего без константы «пи/180» сделать нечем.
        math1("sin", 2, a -> Math.sin(Math.toRadians(a)));
        math1("cos", 2, a -> Math.cos(Math.toRadians(a)));

        math2("min", 2, Math::min);
        math2("max", 2, Math::max);
        math2("percent_of", 2, (a, b) -> a * b / 100.0);
        math2("modulo", 3, (a, b) -> {
            if (b == 0) {
                throw new SpellContext.SpellException("cast.division_by_zero");
            }
            return a % b;
        });

        reg(new SpellModuleType(SpellModules.id("clamp"), NUM, C_NUM) {
            @Override public int inputs() { return 3; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 3; }
            @Override public PortType[] inputPorts() {
                return new PortType[]{NUMBER, NUMBER, NUMBER};
            }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                double v = in.get(0).asNumber();
                double lo = in.get(1).asNumber();
                double hi = in.get(2).asNumber();
                out.add(SpellValue.of(Math.clamp(v, Math.min(lo, hi), Math.max(lo, hi))));
            }
        });

        // Плавный переход: 0 даёт первое число, 1 второе, 0,5 — середину.
        // Третий вход зажимается в 0..1, иначе «lerp на 5» уводил бы значение
        // далеко за второй конец и ломал бы любой плавный график.
        reg(new SpellModuleType(SpellModules.id("lerp"), NUM, C_NUM) {
            @Override public int inputs() { return 3; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 3; }
            @Override public PortType[] inputPorts() {
                return new PortType[]{NUMBER, NUMBER, NUMBER};
            }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                double from = in.get(0).asNumber();
                double to = in.get(1).asNumber();
                double t = Math.clamp(in.get(2).asNumber(), 0, 1);
                out.add(SpellValue.of(from + (to - from) * t));
            }
        });
    }

    private interface Op1 {
        double apply(double a);
    }

    private interface Op2 {
        double apply(double a, double b);
    }

    /** Модуль с одним числовым входом. */
    private static void math1(String name, int cost, Op1 op) {
        reg(new SpellModuleType(SpellModules.id(name), NUM, C_NUM) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return cost; }
            @Override public PortType[] inputPorts() { return new PortType[]{NUMBER}; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                out.add(SpellValue.of(op.apply(in.get(0).asNumber())));
            }
        });
    }

    /** Модуль с двумя числовыми входами. */
    private static void math2(String name, int cost, Op2 op) {
        reg(new SpellModuleType(SpellModules.id(name), NUM, C_NUM) {
            @Override public int inputs() { return 2; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return cost; }
            @Override public PortType[] inputPorts() { return new PortType[]{NUMBER, NUMBER}; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                out.add(SpellValue.of(op.apply(in.get(0).asNumber(), in.get(1).asNumber())));
            }
        });
    }

    // ------------------------------------------------------------- сущности

    private static void entities() {
        vectorGaps();
        registerFreeze();
        registerEntitiesInRadius();
        registerEntitiesCountInRadius();

        reg(new SpellModuleType(SpellModules.id("nearest_entity"), ENT, C_ENT) {
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 8; }
            @Override public PortType[] outputPorts() { return new PortType[]{ENTITY}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                out.add(SpellValue.of(nearestLiving(ctx, 16)));
            }
        });

        reg(new SpellModuleType(SpellModules.id("entity_under_crosshair"), ENT, C_ENT) {
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 8; }
            @Override public PortType[] outputPorts() { return new PortType[]{ENTITY}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                Vec3 from = ctx.caster().getEyePosition();
                Vec3 dir = ctx.caster().getLookAngle();
                Vec3 to = from.add(dir.scale(CROSSHAIR_RANGE));
                // область от глаза, а не от хитбокса ног (иначе цель под ногами
                // или над головой не находится)
                AABB box = new AABB(from, to).inflate(1);
                Entity hit = null;
                double best = Double.MAX_VALUE;
                for (Entity e : ctx.level().getEntities(ctx.caster(), box,
                        ExtraModules::living)) {
                    double d = e.position().distanceTo(from);
                    if (d < best && e.getBoundingBox().inflate(0.3).clip(from, to).isPresent()) {
                        best = d;
                        hit = e;
                    }
                }
                if (hit == null) {
                    throw new SpellContext.SpellException("cast.no_target");
                }
                out.add(SpellValue.of(hit));
            }
        });

        reg(new SpellModuleType(SpellModules.id("self_position"), ENT, C_ENT) {
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 1; }
            @Override public PortType[] outputPorts() { return new PortType[]{POINT}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                out.add(SpellValue.of(ctx.caster().position(), SpellValue.Kind.POSITION));
            }
        });

        reg(new SpellModuleType(SpellModules.id("entity_health"), ENT, C_ENT) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 3; }
            @Override public PortType[] inputPorts() { return new PortType[]{ENTITY}; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                out.add(SpellValue.of(health(in.get(0).asEntity())));
            }
        });

        reg(new SpellModuleType(SpellModules.id("entity_max_health"), ENT, C_ENT) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 3; }
            @Override public PortType[] inputPorts() { return new PortType[]{ENTITY}; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                out.add(SpellValue.of(in.get(0).asEntity() instanceof LivingEntity l
                        ? l.getMaxHealth() : 1));
            }
        });

        reg(new SpellModuleType(SpellModules.id("entity_distance"), ENT, C_ENT) {
            @Override public int inputs() { return 2; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 4; }
            @Override public PortType[] inputPorts() { return new PortType[]{ENTITY, ENTITY}; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                out.add(SpellValue.of(in.get(0).asEntity().distanceTo(in.get(1).asEntity())));
            }
        });

        reg(new SpellModuleType(SpellModules.id("entity_velocity"), ENT, C_ENT) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 3; }
            @Override public PortType[] inputPorts() { return new PortType[]{ENTITY}; }
            @Override public PortType[] outputPorts() { return new PortType[]{VECTOR}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                out.add(SpellValue.of(in.get(0).asEntity().getDeltaMovement(), SpellValue.Kind.VECTOR));
            }
        });

        reg(new SpellModuleType(SpellModules.id("is_player"), ENT, C_ENT) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 2; }
            @Override public PortType[] inputPorts() { return new PortType[]{ENTITY}; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                out.add(SpellValue.of(in.get(0).asEntity() instanceof Player ? 1 : 0));
            }
        });

        // Сорт сущности. Отдельными узлами, а не одним «тип сущности» строкой:
        // в моде нет строкового порта, а «бей только мобов» нужно собирать из
        // готовых условий, а не из хранения id в числе.
        entityKind("is_mob", e -> e instanceof net.minecraft.world.entity.Mob);
        entityKind("is_item", e -> e instanceof net.minecraft.world.entity.item.ItemEntity);
        entityKind("is_projectile",
                e -> e instanceof net.minecraft.world.entity.projectile.Projectile);
        entityKind("is_vehicle", e -> e instanceof net.minecraft.world.entity.vehicle
                .AbstractMinecart || e instanceof net.minecraft.world.entity.vehicle.Boat
                || e instanceof net.minecraft.world.entity.LivingEntity л && л.isPassenger());
        entityKind("is_alive", e -> e instanceof LivingEntity && e.isAlive());

        reg(new SpellModuleType(SpellModules.id("entities_nearby"), ENT, C_ENT) {
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 10; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                AABB box = AABB.ofSize(ctx.caster().position(), 16, 8, 16);
                int count = ctx.level().getEntities(ctx.caster(), box, ExtraModules::living)
                        .size();
                out.add(SpellValue.of(count));
            }
        });
    }

    // ------------------------------------------------------------------ мир

    private static void world() {
        reg(new SpellModuleType(SpellModules.id("block_under_crosshair"), ENT, C_ENT) {
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 6; }
            @Override public PortType[] outputPorts() { return new PortType[]{POINT}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                out.add(SpellValue.of(lookBlock(ctx), SpellValue.Kind.POSITION));
            }
        });

        reg(new SpellModuleType(SpellModules.id("is_air"), NUM, C_NUM) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 2; }
            @Override public PortType[] inputPorts() { return new PortType[]{POINT}; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                BlockPos pos = in.get(0).asBlockPos();
                ctx.requireInRange(pos);
                out.add(SpellValue.of(ctx.level().getBlockState(pos).isAir() ? 1 : 0));
            }
        });

        reg(new SpellModuleType(SpellModules.id("block_light"), NUM, C_NUM) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 3; }
            @Override public PortType[] inputPorts() { return new PortType[]{POINT}; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                BlockPos pos = in.get(0).asBlockPos();
                ctx.requireInRange(pos);
                out.add(SpellValue.of(ctx.level().getBrightness(LightLayer.BLOCK, pos)));
            }
        });

        reg(new SpellModuleType(SpellModules.id("time_of_day"), NUM, C_NUM) {
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 1; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                out.add(SpellValue.of(ctx.level().getDayTime() % 24000L));
            }
        });

        // «Точка возрождения» читает мир, а не ищет сущность: держать её в
        // «Сущностях» разносило подраздел «Чтение мира» по двум вкладкам, и
        // игрок искал спавн то в «Числах», то в «Сущностях»
        reg(new SpellModuleType(SpellModules.id("world_spawn"), NUM, C_NUM) {
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 5; }
            @Override public PortType[] outputPorts() { return new PortType[]{POINT}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                if (ctx.level() instanceof ServerLevel server) {
                    out.add(SpellValue.of(Vec3.atBottomCenterOf(server.getSharedSpawnPos()),
                            SpellValue.Kind.POSITION));
                } else {
                    throw new SpellContext.SpellException("cast.server_only");
                }
            }
        });
    }

    // -------------------------------------------------------------- предметы

    // ---------------------------------------------------------------- логика

    /**
     * Предмет как значение заклинания: ТОЛЬКО ЧТЕНИЕ.
     *
     * <p>Раньше здесь был целый конвейер, и он был дырой: «Блок на месте» и
     * «Жидкость на месте» копировали стопку из мира, «Предмет в руке» и «Предмет
     * в ячейке хотбара» — из инвентаря, ничего не убирая, а «Выдать предмет»
     * и «Выбросить предмет» клали копию обратно. Три модуля вместе давали
     * бесконечные блоки за 11 энергии бездны.
     *
     * <p>Вернулось только чтение. Единственный сток для ITEM теперь — постройка
     * ({@code place_block}, {@code fill_region}), а она берёт блоки из инвентаря
     * поштучно. Пока предмет читается, но никуда не кладётся, схема не может
     * ни создать предмет, ни размножить его.
     */
    private static void items() {
        reg(new SpellModuleType(SpellModules.id("held_item"), ENT, C_ENT) {
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 2; }
            @Override public PortType[] outputPorts() { return new PortType[]{ITEM}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                if (!(ctx.caster() instanceof Player player)) {
                    throw new SpellContext.SpellException("cast.inv_read");
                }
                ItemStack stack = player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND);
                if (stack.isEmpty()) {
                    throw new SpellContext.SpellException("cast.empty_hand_here");
                }
                out.add(SpellValue.of(stack.copy()));
            }
        });

        /**
         * Предмет из ячейки инвентаря: 1-9 — хотбар, 10-36 — основной инвентарь.
         *
         * <p>Раньше верхняя граница была 9, и постройка работала только из
         * хотбара: блок, лежащий в основном инвентаре, использовать было нечем.
         * Это была моя недоработка после удаления предметного конвейера — на
         * остатке осталось два источника ITEM, и я сузил один из них до
         * девяти ячеек.
         *
         * <p>Нумерация как у {@code Inventory}: индекс списка минус один,
         * поэтому брать можно и хотбар, и основной инвентарь одной нумерацией.
         * Броня и вторая рука сюда не входят намеренно — иначе «инвентарь»
         * стал бы читаться как «всё, что на игроке».
         */
        reg(new SpellModuleType(SpellModules.id("held_slot"), ENT, C_ENT) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 2; }
            @Override public PortType[] inputPorts() { return new PortType[]{NUMBER}; }
            @Override public PortType[] outputPorts() { return new PortType[]{ITEM}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                if (!(ctx.caster() instanceof Player player)) {
                    throw new SpellContext.SpellException("cast.inv_read");
                }
                // номер приходит из заклинания, поэтому приводится к целому и
                // зажимается: «0» и «999» не должны ни падать, ни читать чужое
                int slot = (int) Math.clamp(Math.round(in.get(0).asNumber()),
                        1, INVENTORY_SLOTS);
                ItemStack stack = player.getInventory().items.get(slot - 1);
                if (stack.isEmpty()) {
                    throw new SpellContext.SpellException("cast.slot_empty", slot);
                }
                out.add(SpellValue.of(stack.copy()));
            }
        });

        reg(new SpellModuleType(SpellModules.id("item_count"), NUM, C_NUM) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 3; }
            @Override public PortType[] inputPorts() { return new PortType[]{ITEM}; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                ItemStack wanted = in.get(0) == null ? ItemStack.EMPTY : in.get(0).asItem();
                int total = 0;
                if (ctx.caster() instanceof Player player) {
                    for (ItemStack stack : player.getInventory().items) {
                        if (ItemStack.isSameItem(stack, wanted)) {
                            total += stack.getCount();
                        }
                    }
                }
                out.add(SpellValue.of(total));
            }
        });
    }

    private static void logic() {
        reg(new SpellModuleType(SpellModules.id("compare"), LOG, C_LOG) {
            @Override public int inputs() { return 2; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 3; }
            @Override public PortType[] inputPorts() { return new PortType[]{NUMBER, NUMBER}; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                double a = in.get(0).asNumber();
                double b = in.get(1).asNumber();
                out.add(SpellValue.of(a > b ? 1 : (a < b ? -1 : 0)));
            }
        });

        reg(new SpellModuleType(SpellModules.id("select"), LOG, C_LOG) {
            @Override public int inputs() { return 4; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 6; }
            @Override public PortType[] inputPorts() {
                return new PortType[]{NUMBER, NUMBER, ANY, ANY};
            }
            @Override public PortType[] outputPorts() { return new PortType[]{ANY}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                double a = in.get(0).asNumber();
                double b = in.get(1).asNumber();
                out.add(a > b ? in.get(2) : in.get(3));
            }
        });

        reg(new SpellModuleType(SpellModules.id("duplicate"), LOG, C_LOG) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 2; }
            @Override public int manaCost() { return 4; }
            @Override public PortType[] inputPorts() { return new PortType[]{ANY}; }
            @Override public PortType[] outputPorts() { return new PortType[]{ANY, ANY}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                out.add(in.get(0));
                out.add(in.get(0));
            }
        });

        // «И» и «Или» живут в «Логике», а не в «Числах»: их регистрировал
        // math2, который жёстко берёт категорию NUMBERS, из-за чего половина
        // логических операций оказывалась в соседней вкладке палитры
        reg(new SpellModuleType(SpellModules.id("and"), LOG, C_LOG) {
            @Override public int inputs() { return 2; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 2; }
            @Override public PortType[] inputPorts() { return new PortType[]{NUMBER, NUMBER}; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                out.add(SpellValue.of(in.get(0).asNumber() != 0 && in.get(1).asNumber() != 0
                        ? 1 : 0));
            }
        });
        reg(new SpellModuleType(SpellModules.id("or"), LOG, C_LOG) {
            @Override public int inputs() { return 2; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 2; }
            @Override public PortType[] inputPorts() { return new PortType[]{NUMBER, NUMBER}; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                out.add(SpellValue.of(in.get(0).asNumber() != 0 || in.get(1).asNumber() != 0
                        ? 1 : 0));
            }
        });
        reg(new SpellModuleType(SpellModules.id("not"), LOG, C_LOG) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 2; }
            @Override public PortType[] inputPorts() { return new PortType[]{NUMBER}; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                out.add(SpellValue.of(in.get(0).asNumber() == 0 ? 1 : 0));
            }
        });

        reg(new SpellModuleType(SpellModules.id("select_random"), LOG, C_LOG) {
            @Override public int inputs() { return 2; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 5; }
            @Override public PortType[] inputPorts() { return new PortType[]{ANY, ANY}; }
            @Override public PortType[] outputPorts() { return new PortType[]{ANY}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                out.add(ctx.level().random.nextBoolean() ? in.get(0) : in.get(1));
            }
        });
    }

    // ---------------------------------------------------------------- блоки

    private static void blocks() {
        reg(new SpellModuleType(SpellModules.id("create_water"), ACT, C_ACT) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 0; }
            @Override public int manaCost() { return 6; }
            @Override public int maxInstances() { return 1; }
            @Override public PortType[] inputPorts() { return new PortType[]{POINT}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                setBlock(ctx, in.get(0).asBlockPos(),
                        net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
            }
        });

        reg(new SpellModuleType(SpellModules.id("create_lava"), ACT, C_ACT) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 0; }
            @Override public int manaCost() { return 6; }
            @Override public int maxInstances() { return 1; }
            @Override public PortType[] inputPorts() { return new PortType[]{POINT}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                setBlock(ctx, in.get(0).asBlockPos(),
                        net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState());
            }
        });

        reg(new SpellModuleType(SpellModules.id("create_fire"), ACT, C_ACT) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 0; }
            @Override public int manaCost() { return 5; }
            @Override public int maxInstances() { return 1; }
            @Override public PortType[] inputPorts() { return new PortType[]{POINT}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                setBlock(ctx, in.get(0).asBlockPos(),
                        net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState());
            }
        });

        
    }

    // -------------------------------------------------------------- действия

    private static void actions() {
        reg(new SpellModuleType(SpellModules.id("heal_percent"), ACT, C_ACT) {
            @Override public int inputs() { return 2; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 14; }
            @Override public PortType[] inputPorts() { return new PortType[]{ENTITY, NUMBER}; }
            @Override public PortType[] outputPorts() { return new PortType[]{ENTITY}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                Entity target = in.get(0).asEntity();
                if (!(target instanceof LivingEntity living)) {
                    throw new SpellContext.SpellException("cast.not_living");
                }
                ctx.requireCanHarm(target);
                living.heal((float) (living.getMaxHealth() * in.get(1).asNumber() / 100.0));
                out.add(in.get(0));
            }
        });

        reg(new SpellModuleType(SpellModules.id("ignite"), ACT, C_ACT) {
            @Override public int inputs() { return 2; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 12; }
            @Override public PortType[] inputPorts() { return new PortType[]{ENTITY, NUMBER}; }
            @Override public PortType[] outputPorts() { return new PortType[]{ENTITY}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                Entity target = in.get(0).asEntity();
                if (!(target instanceof LivingEntity living)) {
                    throw new SpellContext.SpellException("cast.not_living");
                }
                ctx.requireCanHarm(target);
                living.setRemainingFireTicks((int) Math.clamp(in.get(1).asNumber() * 20, 0, 12000));
                out.add(in.get(0));
            }
        });

        /**
         * Перенос сущности в точку.
         *
         * <p>Цена 150 — самая высокая среди модулей. Телепорт меняет позицию
         * мгновенно и без проверки препятствий, поэтому это единственный модуль,
         * который способен выкинуть цель сквозь стену или увести её с карты
         * одним кастом. Дёшево такое делать нельзя.
         *
         * <p>Точка назначения и цель оба обязаны быть в радиусе досягаемости:
         * иначе схема «Кастер → импульс 999999» уносила бы кого угодно за
         * пределы карты.
         */
        reg(new SpellModuleType(SpellModules.id("teleport"), ACT, C_ACT) {
            @Override public int inputs() { return 2; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 150; }
            @Override public PortType[] inputPorts() { return new PortType[]{ENTITY, POINT}; }
            @Override public PortType[] outputPorts() { return new PortType[]{ENTITY}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                Entity target = in.get(0).asEntity();
                Vec3 pos = in.get(1).asVec();
                ctx.requireInRange(pos);
                ctx.requireCanHarm(target);
                target.teleportTo(pos.x, pos.y, pos.z);
                ctx.trace("teleport to " + pos);
                out.add(in.get(0));
            }
        });

        reg(new SpellModuleType(SpellModules.id("lightning"), ACT, C_ACT) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 0; }
            @Override public int manaCost() { return 45; }
            @Override public int maxInstances() { return 1; }
            @Override public PortType[] inputPorts() { return new PortType[]{POINT}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                Vec3 pos = in.get(0).asVec();
                if (ctx.level() instanceof ServerLevel server) {
                    ctx.requireWritable(net.minecraft.core.BlockPos.containing(pos));
                    LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, server);
                    bolt.setPos(pos);
                    server.addFreshEntity(bolt);
                }
            }
        });

        reg(new SpellModuleType(SpellModules.id("arrow"), ACT, C_ACT) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 0; }
            @Override public int manaCost() { return 15; }
            @Override public int maxInstances() { return 1; }
            @Override public PortType[] inputPorts() { return new PortType[]{VECTOR}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                Vec3 dir = unit(in.get(0));
                if (ctx.level() instanceof ServerLevel server) {
                    Arrow arrow = new Arrow(server, ctx.caster(), ItemStack.EMPTY, null);
                    arrow.setPos(ctx.caster().getEyePosition());
                    arrow.setDeltaMovement(dir.scale(2.5));
                    arrow.setRemainingFireTicks(100);
                    server.addFreshEntity(arrow);
                }
            }
        });

        reg(new SpellModuleType(SpellModules.id("sound_at"), ACT, C_ACT) {
            @Override public int inputs() { return 2; }
            @Override public int outputs() { return 0; }
            @Override public int manaCost() { return 8; }
            @Override public PortType[] inputPorts() { return new PortType[]{POINT, NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                Vec3 pos = in.get(0).asVec();
                ctx.requireInRange(pos);
                double pitch = Math.clamp(in.get(1).asNumber(), 0.5, 2.0);
                ctx.level().playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_POWER_SELECT,
                        SoundSource.BLOCKS, 0.9f, (float) pitch);
            }
        });

    }

    // --------------------------------------------------------------- утилиты

    private static void setBlock(SpellContext ctx, BlockPos pos,
                                 BlockState state) {
        if (!(ctx.level() instanceof ServerLevel server)) {
            throw new SpellContext.SpellException("cast.server_only");
        }
        // дальность и загруженность чанка — обязательны для любой записи в мир:
        // точка приходит из схемы игрока и может указывать куда угодно
        ctx.requireWritable(pos);
        server.setBlockAndUpdate(pos, state);
    }

    /**
     * Единичный вектор направления.
     *
     * <p>Раньше тут стоял {@code Vec3.normalize()}, который для нулевого
     * вектора молча возвращает {@code Vec3.ZERO}: «Стрела» по вектору нулей
     * выпускала снаряд, который стоял на месте. Теперь это ошибка заклинания.
     */
    private static Vec3 unit(SpellValue value) {
        Vec3 dir = value.asVec().normalize();
        if (dir.lengthSqr() < 1e-9) {
            throw new SpellContext.SpellException("cast.zero_direction");
        }
        return dir;
    }

    private static final int MAX_RADIUS = 128;

    /** Радиус по умолчанию, если во входе пусто или мусор. */
    private static final double DEFAULT_RADIUS = 16;

    /**
     * «Существа в радиусе»: находит сразу несколько целей рядом с кастером.
     *
     * <p>Четыре выхода вместо одного — сознательно. У {@code nearest_entity}
     * одна цель, и схема «ударить двоих» не собиралась: ставить два узла поиска
     * рядом бессмысленно, оба вернут одного и того же. Здесь ближайшие занимают
     * выходы по очереди, остальные остаются пустыми, и на каждый выход вешается
     * свой урон.
     *
     * <p>Радиус настоящий круг, а не коробка: вход 10 — это ровно 10 блоков
     * в любую сторону. Коробка искала бы по углам дальше, чем показано на входе,
     * и игрок не мог бы предсказать, кого модуль выберет.
     *
     * <p>Радиус и количество ограничены сверху: поиск на сотни блоков — это
     * обход всей области каждый каст, а больше четырёх целей всё равно некуда
     * положить, портов у модуля ровно четыре.
     */
    private static void registerEntitiesInRadius() {
        reg(new SpellModuleType(SpellModules.id("entities_in_radius"), ENT, C_ENT) {
            @Override public int inputs() { return 2; }
            @Override public int outputs() { return TARGET_PORTS; }
            @Override public int manaCost() { return 14; }
            @Override public PortType[] inputPorts() { return new PortType[]{NUMBER, NUMBER}; }
            @Override public PortType[] outputPorts() {
                PortType[] ports = new PortType[TARGET_PORTS];
                java.util.Arrays.fill(ports, ENTITY);
                return ports;
            }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                double radius = clampRadius(in.get(0));
                int wanted = clampCount(in.get(1));
                for (LivingEntity target : livingWithin(ctx, radius, wanted)) {
                    out.add(SpellValue.of(target));
                }
            }
        });
    }

    /**
     * «Сколько существ в радиусе» — то же, что {@code entities_nearby}, но радиус
     * приходит числом, а не зашит в модуль.
     */
    private static void registerEntitiesCountInRadius() {
        reg(new SpellModuleType(SpellModules.id("entities_count_in_radius"), ENT, C_ENT) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 10; }
            @Override public PortType[] inputPorts() { return new PortType[]{NUMBER}; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                double radius = clampRadius(in.get(0));
                out.add(SpellValue.of(livingWithin(ctx, radius, Integer.MAX_VALUE).size()));
            }
        });
    }

    /** Сколько целей модуль может выдать: столько же у него выходов. */
    private static final int TARGET_PORTS = 4;

    /**
     * Радиус со входа: пусто, мусор или ноль — берётся значение по умолчанию.
     *
     * <p>Ноль отдельно ловить надо: в порт часто ведут «пустую» константу, и без
     * этой проверки такой модуль молча находил бы пустоту вместо целей.
     */
    private static double clampRadius(SpellValue value) {
        double radius = value == null ? Double.NaN : value.number();
        if (!(radius > 0)) {
            return DEFAULT_RADIUS;
        }
        return Math.min(radius, MAX_RADIUS);
    }

    /** Сколько целей просят: не меньше одной и не больше, чем есть выходов. */
    private static int clampCount(SpellValue value) {
        int wanted = (int) Math.round(value == null ? Double.NaN : value.number());
        if (wanted < 1) {
            return 1;
        }
        return Math.min(wanted, TARGET_PORTS);
    }

    /**
     * Живые существа в круге радиуса, ближайшие первыми.
     *
     * <p>Из мира берётся коробка 2r × 2r × 2r — это всё, где может лежать цель, —
     * а точность до круга добирается сравнением расстояния. Так коробка остаётся
     * выборкой движка (она индексирована по чанкам), а не перебором всего уровня.
     *
     * @param wanted сколько нужно; {@link Integer#MAX_VALUE} — все
     */
    private static java.util.List<LivingEntity> livingWithin(SpellContext ctx, double radius,
                                                            int wanted) {
        Vec3 center = ctx.caster().position();
        AABB box = AABB.ofSize(center, radius * 2, radius * 2, radius * 2);
        java.util.List<LivingEntity> найденные = new java.util.ArrayList<>();
        double radiusSq = radius * radius;
        for (Entity e : ctx.level().getEntities(ctx.caster(), box, ExtraModules::living)) {
            if (e instanceof LivingEntity living && living.distanceToSqr(ctx.caster()) <= radiusSq) {
                найденные.add(living);
            }
        }
        // по расстоянию: иначе порядок выходов зависел бы от того, в каком
        // порядке движок отдал сущности, и игрок не смог бы предсказать,
        // кто попадёт в первый выход
        найденные.sort(java.util.Comparator.comparingDouble(e -> e.distanceTo(ctx.caster())));
        if (найденные.size() > wanted) {
            return java.util.List.copyOf(найденные.subList(0, wanted));
        }
        return найденные;
    }

    /**
     * Модуль-сортировщик: «это оно?» — один вход, один выход, 1 или 0.
     *
     * <p>Условия собираются из существующих «если», поэтому на выходе должно быть
     * именно число: ноль — это «нет» без единого лишнего узла между собой.
     */
    private static void entityKind(String path, java.util.function.Predicate<Entity> условие) {
        reg(new SpellModuleType(SpellModules.id(path), ENT, C_ENT) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 2; }
            @Override public PortType[] inputPorts() { return new PortType[]{ENTITY}; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                Entity кто = in.get(0) == null ? null : in.get(0).asEntity();
                out.add(SpellValue.of(кто != null && условие.test(кто) ? 1 : 0));
            }
        });
    }

    /**
     * Расстояние между двумя векторами.
     *
     * <p>Было только «длина вектора», а это до неё один узел не доходит:
     * чтобы узнать, насколько далеко цель от смещённой точки, нужно было руками
     * собрать «вычесть» и «длина». Обе операции есть, но пара «минус вектор, потом
     * длина» на каждом шагу заклинания — ровно тот шум в графе, из-за которого
     * и появились готовые узлы сравнений.
     */
    private static void vectorGaps() {
        reg(new SpellModuleType(SpellModules.id("vector_distance"), NUM, C_NUM) {
            @Override public int inputs() { return 2; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 3; }
            @Override public PortType[] inputPorts() { return new PortType[]{VECTOR, VECTOR}; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                Vec3 a = in.get(0).asVec();
                Vec3 b = in.get(1).asVec();
                out.add(SpellValue.of(a.distanceTo(b)));
            }
        });

        // Точка между двумя: треть пути от a к b при t = 0.5.
        // Существует не «всегда», а ради «мигания» в схеме — но по дороге
        // перестаёшь строить «от a шаг на (b − a)», а это четыре узла.
        reg(new SpellModuleType(SpellModules.id("vector_lerp"), NUM, C_NUM) {
            @Override public int inputs() { return 3; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 5; }
            @Override public PortType[] inputPorts() {
                return new PortType[]{VECTOR, VECTOR, NUMBER};
            }
            @Override public PortType[] outputPorts() { return new PortType[]{VECTOR}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                Vec3 a = in.get(0).asVec();
                Vec3 b = in.get(1).asVec();
                double t = in.get(2).asNumber();
                out.add(SpellValue.of(a.lerp(b, t), SpellValue.Kind.VECTOR));
            }
        });
    }

    /**
     * Заморозка: сущность стоит на месте, пока не истекут тики.
     *
     * <p>Зельями это не сделать — слизость и прыгучесть меняют модификаторы, но
     * не делают существо неподвижным. Поэтому движение гасится напрямую, каждый
     * тик, через {@link Frozen}.
     */
    private static void registerFreeze() {
        reg(new SpellModuleType(SpellModules.id("freeze"), ACT, C_ENT) {
            @Override public int inputs() { return 2; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 18; }
            @Override public PortType[] inputPorts() { return new PortType[]{ENTITY, NUMBER}; }
            @Override public PortType[] outputPorts() { return new PortType[]{ENTITY}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                Entity цель = in.get(0).asEntity();
                int тики = in.get(1) == null ? 0 : (int) Math.round(in.get(1).number());
                // без срока заморозка была бы навсегда: снимать её придётся
                // через смерть, а это не «заморозка», а «удаление из мира»
                if (тики > 0) {
                    Frozen.freeze(цель.getUUID(), ctx.level().getGameTime() + тики);
                }
                ctx.trace("freeze " + тики + " ticks");
                out.add(in.get(0));
            }
        });

        reg(new SpellModuleType(SpellModules.id("unfreeze"), ACT, C_ENT) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 4; }
            @Override public PortType[] inputPorts() { return new PortType[]{ENTITY}; }
            @Override public PortType[] outputPorts() { return new PortType[]{ENTITY}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                Frozen.thaw(in.get(0).asEntity().getUUID());
                out.add(in.get(0));
            }
        });
    }

    private static boolean living(Entity e) {
        return e instanceof LivingEntity;
    }

    private static double health(Entity e) {
        return e instanceof LivingEntity l ? l.getHealth() : 0;
    }

    private static Entity nearestLiving(SpellContext ctx, double radius) {
        AABB box = AABB.ofSize(ctx.caster().position(), radius, radius, radius);
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : ctx.level().getEntities(ctx.caster(), box, ExtraModules::living)) {
            double d = e.distanceTo(ctx.caster());
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        if (best == null) {
            throw new SpellContext.SpellException("cast.no_target");
        }
        return best;
    }

    private static Vec3 lookBlock(SpellContext ctx) {
        var hit = ctx.level().clip(new ClipContext(
                ctx.caster().getEyePosition(),
                ctx.caster().getEyePosition().add(ctx.caster().getLookAngle().scale(CROSSHAIR_RANGE)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ctx.caster()));
        if (!(hit instanceof net.minecraft.world.phys.BlockHitResult blockHit)
                || hit.getType() == HitResult.Type.MISS) {
            throw new SpellContext.SpellException("cast.no_block");
        }
        // берём сам блок, а не точку на его грани
        return Vec3.atCenterOf(blockHit.getBlockPos());
    }
}