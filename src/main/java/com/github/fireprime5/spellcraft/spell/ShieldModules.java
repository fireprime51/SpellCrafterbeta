package com.github.fireprime5.spellcraft.spell;

import com.github.fireprime5.spellcraft.armor.Shield;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.util.List;

import static com.github.fireprime5.spellcraft.spell.PortType.NUMBER;

/**
 * «Щит» и «Барьер» — защита, которую игрок поднимает ненадолго.
 *
 * <p>Разница только в форме: щит вырастает дугой там, куда игрок смотрит и держит
 * удар <b>спереди</b>, барьер — сферой вокруг игрока и держит отовсюду. Правило
 * одно и то же, поэтому и модулей два, а не четыре: «щит со всех сторон» и
 * «дуга на 360°» — это ровно то же самое, просто визуально иное.
 *
 * <p>Отдельно отмечено то, из-за чего щит спереди — не круговая оборона: у урона
 * от заклинаний этого мода в источнике нет позиции, направление определяется по
 * атакующей сущности. Удар от того, кто стоит за спиной, щит не держит, и это
 * осознанно: так же ведёт себя настоящий щит.
 *
 * <p>«Только снаряды» — модификатор, а не второй модуль: это переключатель
 * поведения без числа, и в моде для таких вещей уже есть механизм с группами
 * конфликта.
 */
public final class ShieldModules {

    private ShieldModules() {
    }

    /** Идентификатор модификатора «только снаряды». */
    static final String PROJECTILES_ONLY = "projectiles_only";

    /** Барьер: защита со всех сторон. Единственный из двух, кто идёт в броню. */
    public static final String BARRIER = "barrier";

    private static final String SHIELD = "shield";

    /** Расход барьера в броне, энергий бездны в секунду. */
    static final double BARRIER_UPKEEP = 30.0;

    static void registerAll() {
        register("shield", Shield.Shape.ARC);
        register("barrier", Shield.Shape.SPHERE);
    }

    private static void register(String path, Shield.Shape shape) {
        SpellModules.register(new SpellModuleType(SpellModules.id(path),
                SpellModuleType.Category.ACTIONS, 0xFF8FC7FF) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() {
                // барьер держит удар отовсюду и стоит вдвое: это не «щит покрепче»,
                // это другая по охвату гарантия
                return shape == Shield.Shape.SPHERE ? 30 : 16;
            }
            @Override public PortType[] inputPorts() { return new PortType[]{NUMBER}; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public java.util.List<SpellModifier> modifiers() {
                return SpellModifiers.SHIELD;
            }

            @Override
            public double armorUpkeepPerSecond() {
                // Барьер держит урон отовсюду и непрерывно, в отличие от полёта
                // и модификаторов, которые просто висят. Поэтому он дороже на
                // два порядка: при обычной ёмкости кошелька это треть минуты
                // настоящей круговой обороны, а не вечное преимущество.
                return shape == Shield.Shape.SPHERE ? BARRIER_UPKEEP : 0.5;
            }

            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                if (!(ctx.caster() instanceof Player player)
                        || !(ctx.caster().level() instanceof ServerLevel)) {
                    // щит держится на сервере: клиентский предпросмотр не должен
                    // оставлять в своём состоянии запись, которой нет на сервере
                    throw new SpellContext.SpellException("cast.not_player");
                }
                // без входа — берём то, что задумано по умолчанию: «щит» без
                // числа не должен молча превращаться в щит на один тик
                int ticks = durationTicks(in.get(0), shape);
                boolean толькоСнаряды = ctx.modifiers().contains(PROJECTILES_ONLY);
                Shield.raise(view(player), shape, ticks, толькоСнаряды,
                        ctx.level().getGameTime());
                ctx.trace(path + " " + ticks + " ticks"
                        + (толькоСнаряды ? " projectiles-only" : ""));
                // на выходе — сколько тиков провисит: это видно в предпросмотре,
                // и видно, что модуль сработал, а не молча вышел из строя
                out.add(SpellValue.of(ticks));
            }
        });
    }

    /** Тик защиты со входа, с потолком, чтобы «на миллион тиков» не было вечным щитом. */
    static int durationTicks(SpellValue value, Shield.Shape shape) {
        double вход = value == null ? Double.NaN : value.number();
        if (!(вход > 0)) {
            return shape.defaultTicks();
        }
        return (int) Math.clamp(Math.round(вход), 1, 20 * 60 * 10);
    }

    static Shield.PlayerView view(Player player) {
        return new Shield.PlayerView(player.getUUID(),
                player.getEyePosition(), player.getLookAngle());
    }
}