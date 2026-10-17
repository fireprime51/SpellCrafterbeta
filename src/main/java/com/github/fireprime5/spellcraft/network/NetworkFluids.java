package com.github.fireprime5.spellcraft.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/**
 * Перенос жидкости по трубам блока сети.
 *
 * <p>Труба одна на все жидкости, и какой она — узнаёт от соседа: предмет
 * {@code fluid_pipe} ничего не говорит о содержимом. Поэтому линия помнит
 * жидкость, а не цвет, и трубы разной жидкости не смешиваются: смена жидкости на
 * непустой трубе запрещена.
 *
 * <p>Перенос симметричный и по объёму, как перенос энергии: сначала набор из
 * источников, потом раздача в хранилища. Источники двух сортов — соседние линии
 * того же блока и любой {@link IFluidHandler} за стеной. Источники воды и лавы в
 * мире не хранилища, а блоки, поэтому берутся отдельно: из блока жидкости можно
 * набрать столько же, сколько из бака.
 */
public final class NetworkFluids {

    /** Сколько жидкости помещается в трубе: восемь ведер. */
    public static final int CAPACITY = 8000;

    /** Сколько жидкости переносится за тик с одного соседа. */
    public static final int TRANSFER = 40;

    private NetworkFluids() {
    }

    /**
     * Залить в трубу.
     *
     * <p>Вынесено отдельно от переноса ради проверки: правила «не больше
     * свободного места» и «не смешивать жидкости» проверяются без мира, а по ним
     * ходят и перенос, и capability, и вёдра игрока.
     *
     * @return сколько миллибакет реально принято
     */
    public static int fill(NetworkLine линия, FluidStack ресурс, boolean симуляция) {
        return fill(линия, BuiltInRegistries.FLUID.getKey(ресурс.getFluid()),
                ресурс.getAmount(), симуляция);
    }

    /**
     * Залить в трубу по id жидкости и числу миллибакет.
     *
     * <p>Ядро переноса — здесь, а не в обёртке над {@link FluidStack}: стек жидкости
     * без мира не собирается, а проверять «не переполняется» и «не смешивает
     * жидкости» надо именно без него.
     *
     * @return сколько миллибакет реально принято
     */
    public static int fill(NetworkLine линия, ResourceLocation жидкость, int amount,
                           boolean симуляция) {
        if (!принимает(линия, жидкость) || amount <= 0) {
            return 0;
        }
        int свободно = CAPACITY - линия.stored();
        if (свободно <= 0) {
            return 0;
        }
        int принято = Math.min(свободно, amount);
        if (!симуляция) {
            линия.fluid(жидкость);
            линия.stored(линия.stored() + принято);
        }
        return принято;
    }

    /**
     * Годится ли эта жидкость в эту трубу.
     *
     * <p>Труба берёт только то, с чем уже связана, а пока не связана — что угодно:
     * узнать жидкость она может только от соседа.
     */
    public static boolean принимает(NetworkLine линия, ResourceLocation жидкость) {
        return линия.kind() == LineKind.FLUID && жидкость != null
                && (линия.fluid() == null || линия.fluid().equals(жидкость));
    }

    /**
     * Слить из трубы.
     *
     * @return что и сколько слито; пустой стек, если сливать нечего
     */
    public static FluidStack drain(NetworkLine линия, int amount, boolean симуляция) {
        FluidStack в_трубе = линия.getFluidInTank(0);
        if (в_трубе.isEmpty()) {
            return FluidStack.EMPTY;
        }
        if (в_трубе.isEmpty() || amount <= 0) {
            return FluidStack.EMPTY;
        }
        int слито = чтоМожноСлить(линия, amount, симуляция);
        return слито <= 0 ? FluidStack.EMPTY : new FluidStack(в_трубе.getFluid(), слито);
    }

    /**
     * Сколько и чего слилось бы из трубы.
     *
     * <p>Ядро слива — здесь, а не в обёртке над {@link FluidStack}: стек жидкости
     * без мира не собирается, а проверять «симуляция ничего не меняет» надо без
     * него. Ноль означает «сливать нечего».
     */
    public static int чтоМожноСлить(NetworkLine линия, int amount, boolean симуляция) {
        if (линия.kind() != LineKind.FLUID || линия.fluid() == null || amount <= 0) {
            return 0;
        }
        int слито = Math.min(amount, линия.stored());
        if (!симуляция) {
            линия.stored(линия.stored() - слито);
        }
        return слито;
    }

    /** Заполнена ли труба: по этому рисуется видимая жидкость. */
    public static boolean виднаЖидкость(NetworkLine линия) {
        return линия.stored() > 0;
    }

    /**
     * Итоговая ступень заливки: 0..4.
     *
     * <p>Рендерер кэширует меш, и ключ обязан меняться вместе с содержимым: пустая,
     * наполовину наполненная и полная труба выглядят по-разному, а кэш отдал бы
     * прежнюю картинку.
     */
    public static int ступень(NetworkLine линия) {
        return Math.clamp(линия.stored() * 4 / CAPACITY, 0, 4);
    }

    /**
     * Тик жидкостной линии: собрать у соседей, раздать соседям.
     *
     * @param уровень мир
     * @param позиция где стоит блок
     * @param линия сама линия: её запас пополняется и тратится
     */
    public static void transfer(Level уровень, BlockPos позиция, NetworkLine линия) {
        if (линия.kind() != LineKind.FLUID) {
            return;
        }
        узнатьЖидкость(уровень, позиция, линия);

        for (Direction d : линия.sides()) {
            if (CAPACITY - линия.stored() <= 0) {
                break;
            }
            NetworkLine рядом = соседняяЛиния(уровень, позиция, линия, d);
            if (рядом != null) {
                FluidStack есть = рядом.getFluidInTank(0);
                if (!есть.isEmpty()) {
                    fill(линия, BuiltInRegistries.FLUID.getKey(есть.getFluid()),
                            Math.min(TRANSFER, есть.getAmount()), false);
                }
                continue;
            }
            FluidStack взято = взятьУСоседа(уровень, позиция.relative(d), d, линия);
            if (!взято.isEmpty()) {
                fill(линия, взято, false);
            }
        }

        for (Direction d : линия.sides()) {
            if (линия.stored() <= 0) {
                break;
            }
            NetworkLine рядом = соседняяЛиния(уровень, позиция, линия, d);
            if (рядом != null) {
                FluidStack что = линия.getFluidInTank(0);
                if (!что.isEmpty()) {
                    int room = CAPACITY - рядом.stored();
                    if (room > 0) {
                        int принято = fill(рядом,
                                BuiltInRegistries.FLUID.getKey(что.getFluid()),
                                Math.min(TRANSFER, Math.min(room, линия.stored())), false);
                        линия.stored(линия.stored() - принято);
                    }
                }
                continue;
            }
            FluidStack что = линия.getFluidInTank(0);
            if (что.isEmpty()) {
                continue;
            }
            IFluidHandler чужой = capability(уровень, позиция.relative(d), d);
            if (чужой == null) {
                continue;
            }
            int принято = чужой.fill(new FluidStack(что.getFluid(),
                    Math.min(TRANSFER, линия.stored())), FluidAction.EXECUTE);
            линия.stored(линия.stored() - Math.max(0, принято));
        }
    }

    /**
     * Взять у соседа порцию нашей жидкости: из хранилища или из блока жидкости.
     *
     * @return что взяли; пустой стек, если брать нечего или не ту жидкость
     */
    public static FluidStack взятьУСоседа(Level уровень, BlockPos сосед, Direction d,
                                          NetworkLine линия) {
        // источник в мире проверяется первым и в обход capability: у блока воды или
        // лавы capability есть, но отдать он ничего не может — он не хранилище.
        // Сначала спрашивать хранилище значило всегда получать пустоту: труба
        // присоединялась к воде и оставалась пустой
        var состояние = уровень.getFluidState(сосед);
        if (!состояние.isEmpty()) {
            var жидкость = состояние.getType();
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(жидкость);
            if (линия.fluid() != null && !линия.fluid().equals(id)) {
                return FluidStack.EMPTY;
            }
            линия.fluid(id);
            return new FluidStack(жидкость, TRANSFER);
        }
        IFluidHandler чужой = capability(уровень, сосед, d);
        if (чужой == null) {
            return FluidStack.EMPTY;
        }
        FluidStack есть = чужой.getFluidInTank(0);
        if (есть.isEmpty() || !NetworkFluids.принимает(линия,
                BuiltInRegistries.FLUID.getKey(есть.getFluid()))) {
            return FluidStack.EMPTY;
        }
        return чужой.drain(new FluidStack(есть.getFluid(),
                Math.min(TRANSFER, есть.getAmount())), FluidAction.EXECUTE);
    }


    /**
     * Узнать, что за жидкость в линии: у первого соседа, у которого она есть.
     *
     * <p>Труба сама ничего не знает: предмет один на все жидкости. Пока она не
     * подключилась ни к чему с жидкостью, жидкость неизвестна, и труба стыкуется с
     * кем угодно — иначе в блоке встала бы вода, а рядом лава протолкнулась бы в
     * неё же.
     */
    public static ResourceLocation узнатьЖидкость(Level уровень, BlockPos позиция,
                                                 NetworkLine линия) {
        if (линия.kind() != LineKind.FLUID || линия.fluid() != null) {
            return линия.fluid();
        }
        for (Direction d : Direction.values()) {
            BlockPos сосед = позиция.relative(d);
            if (уровень.getBlockEntity(сосед) instanceof NetworkBlockEntity be) {
                NetworkLine рядом = be.line(LineKind.FLUID, линия.color());
                if (рядом != null && рядом != линия && рядом.fluid() != null) {
                    линия.fluid(рядом.fluid());
                    return линия.fluid();
                }
            }
            IFluidHandler чужой = capability(уровень, сосед, d);
            if (чужой != null) {
                FluidStack есть = чужой.getFluidInTank(0);
                if (!есть.isEmpty()) {
                    линия.fluid(BuiltInRegistries.FLUID.getKey(есть.getFluid()));
                    return линия.fluid();
                }
            }
            var состояние = уровень.getFluidState(сосед);
            if (!состояние.isEmpty()) {
                линия.fluid(BuiltInRegistries.FLUID.getKey(состояние.getType()));
                return линия.fluid();
            }
        }
        return null;
    }

    /** Есть ли за стеной хоть какая-то жидкость: трубе без неё не с чем соединяться. */
    public static boolean естьЧемНаполнить(Level уровень, BlockPos позиция, Direction d) {
        if (!уровень.getFluidState(позиция.relative(d)).isEmpty()) {
            return true;
        }
        IFluidHandler чужой = capability(уровень, позиция.relative(d), d);
        return чужой != null && !чужой.getFluidInTank(0).isEmpty();
    }

    private static NetworkLine соседняяЛиния(Level уровень, BlockPos позиция,
                                             NetworkLine линия, Direction d) {
        if (!(уровень.getBlockEntity(позиция.relative(d)) instanceof NetworkBlockEntity be)) {
            return null;
        }
        NetworkLine рядом = be.line(линия.kind(), линия.color());
        return рядом != null && рядом != линия && рядом.sameNetwork(линия) ? рядом : null;
    }

    private static IFluidHandler capability(Level уровень, BlockPos позиция,
                                            Direction d) {
        if (уровень == null) {
            return null;
        }
        return уровень.getCapability(Capabilities.FluidHandler.BLOCK, позиция, d);
    }
}
