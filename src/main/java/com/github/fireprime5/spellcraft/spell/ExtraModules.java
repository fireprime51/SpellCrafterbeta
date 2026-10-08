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
import net.minecraft.world.level.Level;
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
 * Расширенный набор модулей: математика, сущности, мир, предметы, логика.
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
                out.add(SpellValue.of(ctx.level().getBlockState(in.get(0).asBlockPos()).isAir()
                        ? 1 : 0));
            }
        });

        reg(new SpellModuleType(SpellModules.id("block_light"), NUM, C_NUM) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 3; }
            @Override public PortType[] inputPorts() { return new PortType[]{POINT}; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                out.add(SpellValue.of(ctx.level()
                        .getBrightness(LightLayer.BLOCK, in.get(0).asBlockPos())));
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

        reg(new SpellModuleType(SpellModules.id("world_spawn"), ENT, C_ENT) {
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

    private static void items() {
        reg(new SpellModuleType(SpellModules.id("held_item"), ENT, C_ENT) {
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 2; }
            @Override public PortType[] outputPorts() { return new PortType[]{ITEM}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                out.add(SpellValue.of(ctx.caster()
                        .getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND).copy()));
            }
        });

        reg(new SpellModuleType(SpellModules.id("item_count"), NUM, C_NUM) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 3; }
            @Override public PortType[] inputPorts() { return new PortType[]{ITEM}; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                ItemStack wanted = in.get(0).asItem();
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

    // ---------------------------------------------------------------- логика

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

        math2("and", 2, (a, b) -> (a != 0 && b != 0) ? 1 : 0);
        math2("or", 2, (a, b) -> (a != 0 || b != 0) ? 1 : 0);
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
        reg(new SpellModuleType(SpellModules.id("set_health"), ACT, C_ACT) {
            @Override public int inputs() { return 2; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 20; }
            @Override public PortType[] inputPorts() { return new PortType[]{ENTITY, NUMBER}; }
            @Override public PortType[] outputPorts() { return new PortType[]{ENTITY}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                Entity target = in.get(0).asEntity();
                double value = in.get(1).asNumber();
                if (target instanceof LivingEntity living) {
                    living.setHealth((float) Math.clamp(value, 0, living.getMaxHealth()));
                } else {
                    throw new SpellContext.SpellException("cast.not_living");
                }
                out.add(in.get(0));
            }
        });

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
                living.setRemainingFireTicks((int) Math.clamp(in.get(1).asNumber() * 20, 0, 12000));
                out.add(in.get(0));
            }
        });

        
        reg(new SpellModuleType(SpellModules.id("teleport"), ACT, C_ACT) {
            @Override public int inputs() { return 2; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 40; }
            @Override public PortType[] inputPorts() { return new PortType[]{ENTITY, POINT}; }
            @Override public PortType[] outputPorts() { return new PortType[]{ENTITY}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                Entity target = in.get(0).asEntity();
                Vec3 pos = in.get(1).asVec();
                target.teleportTo(pos.x, pos.y, pos.z);
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
                Vec3 dir = in.get(0).asVec().normalize();
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
        server.setBlockAndUpdate(pos, state);
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