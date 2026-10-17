package com.github.fireprime5.spellcraft.spell;

import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.github.fireprime5.spellcraft.spell.PortType.ANY;
import static com.github.fireprime5.spellcraft.spell.PortType.ENTITY;
import static com.github.fireprime5.spellcraft.spell.PortType.NUMBER;
import static com.github.fireprime5.spellcraft.spell.PortType.POINT;
import static com.github.fireprime5.spellcraft.spell.PortType.VECTOR;

/**
 * Встроенные модули заклинаний.
 * Конструктор: (id, категория, цвет).
 */
public final class BuiltinModules {

    private BuiltinModules() {
    }

    /** Отбрасывает сущности от эпицентра взрыва. */
    private static void shoveEntities(SpellContext ctx, Vec3 center, float radius) {
        var box = new net.minecraft.world.phys.AABB(center, center).inflate(radius + 1);
        for (var entity : ctx.level().getEntities(ctx.caster(), box,
                e -> e != ctx.caster() && e.isAlive())) {
            var dir = entity.position().subtract(center);
            if (dir.lengthSqr() < 0.01) {
                dir = new Vec3(0, 1, 0);
            }
            entity.push(dir.normalize().scale(1.4));
            entity.hurtMarked = true;
        }
    }

    private static SpellModuleType.Category NUM = SpellModuleType.Category.NUMBERS;
    private static SpellModuleType.Category ENT = SpellModuleType.Category.ENTITIES;
    private static SpellModuleType.Category ACT = SpellModuleType.Category.ACTIONS;
    private static SpellModuleType.Category LOG = SpellModuleType.Category.LOGIC;

    private static final int C_NUMBER = 0xFFE2A44E;
    private static final int C_ENTITY = 0xFF63C97C;
    private static final int C_ACTION = 0xFFE86060;
    private static final int C_LOGIC = 0xFF9285F0;

    /** Дальность лучей (поиск сущности и блока по взгляду). */
    private static final double RAYCAST_RANGE = 28;

    /** Потолок урона от одного каста заклинания. */
    public static final double MAX_SPELL_DAMAGE = 1000;

    public static void registerAll() {
        // --- Числа ---
        constant("const_zero", 0);
        constant("const_one", 1);
        constant("const_five", 5);
        constant("const_ten", 10);
        constant("const_neg_one", -1);

        comparisons();

        math("add", 2, (a, b) -> a + b);
        math("subtract", 2, (a, b) -> a - b);
        math("multiply", 2, (a, b) -> a * b);
        math("divide", 2, (a, b) -> {
            if (b == 0) {
                throw new SpellContext.SpellException("cast.division_by_zero");
            }
            return a / b;
        });
        math("power", 2, Math::pow);
        math("negate", 1, (a, b) -> -a);

        // --- Сущности ---
        register(new SpellModuleType(SpellModules.id("caster_entity"), ENT, C_ENTITY) {
            @Override public int outputs() { return 1; }
            @Override public PortType[] outputPorts() { return new PortType[]{ENTITY}; }
            // стартовая цепочка бесплатна: без неё нельзя добыть первую энергию бездны
            @Override public int manaCost() { return 0; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                out.add(SpellValue.of(ctx.caster()));
            }
        });

        register(new SpellModuleType(SpellModules.id("entity_look"), ENT, C_ENTITY) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public PortType[] inputPorts() { return new PortType[]{ENTITY}; }
            @Override public PortType[] outputPorts() { return new PortType[]{VECTOR}; }
            @Override public int manaCost() { return 0; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                out.add(SpellValue.of(in.get(0).asEntity().getLookAngle(), SpellValue.Kind.VECTOR));
            }
        });

        register(new SpellModuleType(SpellModules.id("entity_position"), ENT, C_ENTITY) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public PortType[] inputPorts() { return new PortType[]{ENTITY}; }
            @Override public PortType[] outputPorts() { return new PortType[]{POINT}; }
            @Override public int manaCost() { return 2; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                out.add(SpellValue.of(in.get(0).asEntity().position(), SpellValue.Kind.POSITION));
            }
        });

        register(new SpellModuleType(SpellModules.id("raycast_entity"), ENT, C_ENTITY) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 0; }
            @Override public PortType[] inputPorts() { return new PortType[]{VECTOR}; }
            @Override public PortType[] outputPorts() { return new PortType[]{ENTITY}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                Vec3 dir = unit(in.get(0));
                Vec3 from = ctx.caster().getEyePosition();
                Vec3 to = from.add(dir.scale(RAYCAST_RANGE));
                // область поиска строим от глаза, а не от хитбокса ног:
                // иначе взгляд вверх/вниз не находил бы сущности
                AABB box = new AABB(from, to).inflate(1);
                Entity hit = null;
                double best = Double.MAX_VALUE;
                for (Entity e : ctx.level().getEntities(ctx.caster(), box, BuiltinModules::living)) {
                    double dist = e.position().distanceTo(from);
                    if (dist < best && e.getBoundingBox().inflate(0.3).clip(from, to).isPresent()) {
                        best = dist;
                        hit = e;
                    }
                }
                if (hit == null) {
                    throw new SpellContext.SpellException("cast.no_target");
                }
                out.add(SpellValue.of(hit));
            }
        });

        register(new SpellModuleType(SpellModules.id("raycast_block"), ENT, C_ENTITY) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public PortType[] inputPorts() { return new PortType[]{VECTOR}; }
            @Override public PortType[] outputPorts() { return new PortType[]{POINT}; }
            @Override public int manaCost() { return 0; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                Vec3 dir = unit(in.get(0));
                var hit = ctx.level().clip(new ClipContext(
                        ctx.caster().getEyePosition(),
                        ctx.caster().getEyePosition().add(dir.scale(RAYCAST_RANGE)),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ctx.caster()));
                if (!(hit instanceof net.minecraft.world.phys.BlockHitResult blockHit)
                        || hit.getType() == HitResult.Type.MISS) {
                    throw new SpellContext.SpellException("cast.no_block");
                }
                // именно блок из хита: location лежит на грани и мог бы указать на воздух
                out.add(SpellValue.of(Vec3.atCenterOf(blockHit.getBlockPos()),
                        SpellValue.Kind.POSITION));
            }
        });

        // --- Логика ---
        register(new SpellModuleType(SpellModules.id("pick_first"), LOG, C_LOGIC) {
            @Override public int inputs() { return 2; }
            @Override public int outputs() { return 1; }
            @Override public PortType[] inputPorts() { return new PortType[]{ANY, ANY}; }
            @Override public PortType[] outputPorts() { return new PortType[]{ANY}; }
            @Override public int manaCost() { return 3; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                out.add(in.get(0));
            }
        });

        register(new SpellModuleType(SpellModules.id("pick_second"), LOG, C_LOGIC) {
            @Override public int inputs() { return 2; }
            @Override public int outputs() { return 1; }
            @Override public PortType[] inputPorts() { return new PortType[]{ANY, ANY}; }
            @Override public PortType[] outputPorts() { return new PortType[]{ANY}; }
            @Override public int manaCost() { return 3; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                out.add(in.get(1));
            }
        });

        register(new SpellModuleType(SpellModules.id("swap"), LOG, C_LOGIC) {
            @Override public int inputs() { return 2; }
            @Override public int outputs() { return 2; }
            @Override public PortType[] inputPorts() { return new PortType[]{ANY, ANY}; }
            @Override public PortType[] outputPorts() { return new PortType[]{ANY, ANY}; }
            @Override public int manaCost() { return 4; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                out.add(in.get(1));
                out.add(in.get(0));
            }
        });

        // --- Действия ---
        register(new SpellModuleType(SpellModules.id("damage"), ACT, C_ACTION) {
            @Override public int inputs() { return 2; }
            @Override public int outputs() { return 1; }
            @Override public PortType[] inputPorts() { return new PortType[]{ENTITY, NUMBER}; }
            @Override public PortType[] outputPorts() { return new PortType[]{ENTITY}; }
            @Override public int manaCost() { return 15; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                Entity target = in.get(0).asEntity();
                double amount = in.get(1).asNumber();
                if (!(target instanceof LivingEntity living)) {
                    throw new SpellContext.SpellException("cast.not_living");
                }
                // living.hurt() идёт мимо Player.attack(), а значит мимо
                // правил сложности, команд и гейм-правила PvP
                ctx.requireCanHarm(target);
                var source = ctx.caster() instanceof Player p
                        ? ctx.level().damageSources().playerAttack(p)
                        : ctx.level().damageSources().mobAttack((LivingEntity) ctx.caster());
                living.hurt(source, (float) Math.clamp(amount, 0, MAX_SPELL_DAMAGE));
                out.add(in.get(0));
            }
        });

        register(new SpellModuleType(SpellModules.id("heal"), ACT, C_ACTION) {
            @Override public int inputs() { return 2; }
            @Override public int outputs() { return 1; }
            @Override public PortType[] inputPorts() { return new PortType[]{ENTITY, NUMBER}; }
            @Override public PortType[] outputPorts() { return new PortType[]{ENTITY}; }
            @Override public int manaCost() { return 10; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                Entity target = in.get(0).asEntity();
                double amount = in.get(1).asNumber();
                if (!(target instanceof LivingEntity living)) {
                    throw new SpellContext.SpellException("cast.not_living");
                }
                ctx.requireCanHarm(target);
                living.heal((float) amount);
                out.add(in.get(0));
            }
        });

        register(new SpellModuleType(SpellModules.id("explode"), ACT, C_ACTION) {
            @Override public int inputs() { return 2; }
            @Override public int outputs() { return 0; }
            @Override public PortType[] inputPorts() { return new PortType[]{POINT, NUMBER}; }
            @Override public int manaCost() { return 60; }
            @Override public int maxInstances() { return 1; }
            @Override public java.util.List<SpellModifier> modifiers() {
                return SpellModifiers.explosion();
            }
            @Override public String inputHint(int port, java.util.Set<String> enabled) {
                if (port != 1) {
                    return "";
                }
                if (enabled.contains(SpellModifiers.POWERFUL.id())) {
                    return Component.translatable("hint.spellcraft.explode_powerful").getString();
                }
                return Component.translatable("hint.spellcraft.explode").getString();
            }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                Vec3 pos = in.get(0).asVec();
                ctx.requireInRange(pos);
                double power = Math.clamp(in.get(1).asNumber(), 0, 8);

                boolean noBlocks = ctx.hasModifier(SpellModifiers.NO_BLOCKS);
                boolean wide = ctx.hasModifier(SpellModifiers.WIDE);
                boolean fire = ctx.hasModifier(SpellModifiers.FIRE);
                boolean shove = ctx.hasModifier(SpellModifiers.SHOVE);
                boolean powerful = ctx.hasModifier(SpellModifiers.POWERFUL);

                // «Усиленный» множил и power, и радиус, но в трассу уходил
                // только power — лог врал, а взрыв брал исходный радиус
                float radius = (float) (wide ? power * 1.5 : power);
                if (powerful) {
                    radius *= 1.5f;
                    power = radius;
                }

                var interaction = noBlocks
                        ? Level.ExplosionInteraction.NONE
                        : Level.ExplosionInteraction.MOB;

                if (fire) {
                    ctx.level().explode(ctx.caster(), pos.x, pos.y, pos.z, radius, true,
                            interaction);
                } else {
                    ctx.level().explode(ctx.caster(), pos.x, pos.y, pos.z, radius, interaction);
                }

                if (shove) {
                    shoveEntities(ctx, pos, radius);
                }

                ctx.trace("explosion at " + pos + " power " + power
                        + (noBlocks ? " (no blocks)" : "")
                        + (wide ? " (wide)" : "")
                        + (fire ? " (fire)" : "")
                        + (shove ? " (shove)" : "")
                        + (powerful ? " (powerful)" : ""));
            }
        });

        register(new SpellModuleType(SpellModules.id("fireball"), ACT, C_ACTION) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 0; }
            @Override public PortType[] inputPorts() { return new PortType[]{VECTOR}; }
            @Override public int manaCost() { return 25; }
            @Override public int maxInstances() { return 1; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                Vec3 dir = unit(in.get(0));
                if (ctx.level() instanceof ServerLevel server) {
                    LargeFireball ball = new LargeFireball(server, ctx.caster(), dir.scale(1.5), 2);
                    ball.setPos(ctx.caster().getEyePosition());
                    ball.setDeltaMovement(dir.scale(1.5));
                    server.addFreshEntity(ball);
                }
            }
        });

        register(new SpellModuleType(SpellModules.id("impulse"), ACT, C_ACTION) {
            @Override public int inputs() { return 2; }
            @Override public int outputs() { return 1; }
            @Override public PortType[] inputPorts() { return new PortType[]{ENTITY, VECTOR}; }
            @Override public PortType[] outputPorts() { return new PortType[]{ENTITY}; }
            @Override public int manaCost() { return 8; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                Entity target = in.get(0).asEntity();
                Vec3 dir = unit(in.get(1));
                ctx.requireCanHarm(target);
                target.push(dir.x, dir.y, dir.z);
                target.hurtMarked = true;
                out.add(in.get(0));
            }
        });

        register(new SpellModuleType(SpellModules.id("sparkle"), ACT, C_ACTION) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 0; }
            @Override public PortType[] inputPorts() { return new PortType[]{POINT}; }
            @Override public int manaCost() { return 4; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                Vec3 pos = in.get(0).asVec();
                if (ctx.level() instanceof ServerLevel server) {
                    ctx.requireInRange(pos);
                    server.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z,
                            24, 0.3, 0.3, 0.3, 0.05);
                    server.playSound(null, ctx.caster().getX(), ctx.caster().getY(),
                            ctx.caster().getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                            net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1.4f);
                }
            }
        });

        register(new SpellModuleType(SpellModules.id("sound"), ACT, C_ACTION) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 0; }
            @Override public PortType[] inputPorts() { return new PortType[]{NUMBER}; }
            @Override public int manaCost() { return 5; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                double freq = in.get(0).asNumber();
                ctx.level().playSound(null, ctx.caster().getX(), ctx.caster().getY(),
                        ctx.caster().getZ(), SoundEvents.BEACON_AMBIENT,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.8f,
                        (float) Math.clamp(0.5 + freq / 40.0, 0.5, 2.0));
            }
        });
    }

    private static boolean living(Entity e) {
        return e instanceof LivingEntity;
    }

    /**
     * Единичный вектор направления; нулевой — ошибка заклинания.
     *
     * <p>{@code Vec3.normalize()} для нулевого вектора возвращает
     * {@code Vec3.ZERO}, и «Огненный шар» по вектору нулей выпускал снаряд,
     * который сразу же падал. Тихая поломка хуже явной ошибки.
     */
    private static Vec3 unit(SpellValue value) {
        Vec3 dir = value.asVec().normalize();
        if (dir.lengthSqr() < 1e-9) {
            throw new SpellContext.SpellException("cast.zero_direction");
        }
        return dir;
    }

    private static void register(SpellModuleType type) {
        SpellModules.register(type);
    }

    private static void constant(String name, double value) {
        register(new SpellModuleType(SpellModules.id(name), NUM, C_NUMBER) {
            @Override public int outputs() { return 1; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public int manaCost() { return 1; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                out.add(SpellValue.of(value));
            }
        });
    }

    private interface DoubleOp {
        double apply(double a, double b);
    }

    private static void math(String name, int inputs, DoubleOp op) {
        register(new SpellModuleType(SpellModules.id(name), NUM, C_NUMBER) {
            @Override public int inputs() { return inputs; }
            @Override public int outputs() { return 1; }
            @Override public PortType[] inputPorts() {
                return new PortType[]{NUMBER, NUMBER};
            }

            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public int manaCost() { return 2; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                double a = in.get(0).asNumber();
                double b = in.size() > 1 ? in.get(1).asNumber() : 0;
                out.add(SpellValue.of(op.apply(a, b)));
            }
        });
    }

    /**
     * Сравнения отдельными узлами.
     *
     * <p>Раньше был только {@code compare}, и любое неравенство в схеме
     * собиралось из четырёх узлов: «вычесть», «знак», «модуль», «сравнить». Это
     * не изящество, а мусор в графе — на каждое условие уходило четыре блока, и
     * в схеме среднего заклинания их становилось видно глазом.
     *
     * <p>Порядок аргументов такой же, как у «сравнить»: слева то, что проверяем.
     * На выходе 1 или 0, а не «истина» и «ложь»: значение идёт в «если», где
     * ноль и неноль уже есть готовым условием.
     */
    private static void comparisons() {
        comparison("equals", 2, Comparisons.EQUALS);
        comparison("not_equals", 2, Comparisons.NOT_EQUALS);
        comparison("greater", 2, Comparisons.GREATER);
        comparison("less", 2, Comparisons.LESS);
        comparison("greater_or_equal", 2, Comparisons.GREATER_OR_EQUAL);
        comparison("less_or_equal", 2, Comparisons.LESS_OR_EQUAL);
    }

    private static void comparison(String path, int стоимость,
                                   Comparisons.Cmp условие) {
        register(new SpellModuleType(SpellModules.id(path), LOG, C_LOGIC) {
            @Override public int inputs() { return 2; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return стоимость; }
            @Override public PortType[] inputPorts() {
                return new PortType[]{NUMBER, NUMBER};
            }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                out.add(SpellValue.of(условие.test(in.get(0).asNumber(),
                        in.get(1).asNumber()) ? 1 : 0));
            }
        });
    }
}
