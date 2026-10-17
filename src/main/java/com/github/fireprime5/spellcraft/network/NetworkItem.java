package com.github.fireprime5.spellcraft.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.DyeColor;
import com.github.fireprime5.spellcraft.Spellcraft;
import com.github.fireprime5.spellcraft.registry.ModBlocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Предмет-линия: кладёт в блок сети провод своего цвета или трубу бездны.
 *
 * <p>Главное отличие от обычного {@link BlockItem}: если под курсором уже стоит
 * блок сети, предмет не ставит второй блок, а <b>добавляет линию</b> в
 * свободный слот. Блок сети вмещает три линии, и в этом весь смысл: игрок
 * подходит к тому же блоку и кладёт туда второй цвет или трубу, а не ищет, куда
 * поставить соседний блок.
 *
 * <p>Пока линия такого вида и цвета уже есть, предмет ничего не делает: молча
 * проглоченный предмет выглядит как поломка, и игрок посчитает, что слот сломан.
 */
public abstract class NetworkItem extends BlockItem {

    private final LineKind kind;
    private final DyeColor color;

    /**
     * @param kind вид линии
     * @param color цвет провода; для трубы бездны игнорируется
     */
    protected NetworkItem(Block block, LineKind kind, DyeColor color, Properties свойства) {
        super(block, свойства);
        this.kind = kind;
        this.color = color;
    }

    public LineKind kind() {
        return kind;
    }

    public DyeColor color() {
        return color;
    }

    @Override
    public InteractionResult useOn(UseOnContext контекст) {
        Level уровень = контекст.getLevel();
        if (уровень.isClientSide) {
            // клиент только спрашивает: класть линию будет сервер, а клиентская
            // правка блок-сущности всё равно затёрлась бы его ответом
            return addable(уровень, контекст) || new BlockPlaceContext(контекст).canPlace()
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        traceClick(контекст);
        if (addInto(уровень, контекст)) {
            return InteractionResult.CONSUME;
        }
        return placeWithLine(контекст);
    }

    /** След клика: раз в секунду, чтобы по логу было видно, доходит ли он. */
    private void traceClick(UseOnContext контекст) {
        long сейчас = System.currentTimeMillis();
        if (сейчас - последнийКлик < 1000) {
            return;
        }
        последнийКлик = сейчас;
        var под = контекст.getLevel().getBlockState(контекст.getClickedPos()).getBlock();
        Spellcraft.LOGGER.info("Сеть: клик {} по {} в {}",
                NetworkItems.id(new NetworkLine(kind, color, 0, 0)), под,
                контекст.getClickedPos());
    }

    private static long последнийКлик;

    /**
     * Установка нового блока сети сразу с линией.
     *
     * <p>Блок ставится вручную, а не через {@code BlockItem.place}: линия должна
     * появиться в блоке в тот же тик. Через {@code place} она не появлялась —
     * блок вставал пустым, форма у него оказывалась пустой, и по нему нельзя
     * было ни кликнуть, ни поставить рядом другой блок: место выглядело занятым,
     * но пустым, и игрок решал, что провод не ставится вовсе.
     *
     * <p>Позиция берётся из {@link BlockPlaceContext}, а не из клика напрямую.
     * Клик по траве — это клик по блоку, который не заменяется, и ванилль ставит
     * блок <b>над</b> ним. Собственная проверка «а можно ли сюда» смотрела на
     * саму траву, всегда получала «нельзя» и отдавала установку ванилльному
     * пути: блок вставал обычным BlockItem, без линии, пустой и навсегда.
     *
     * @return CONSUME, если блок поставлен, иначе — обычная установка
     */
    private InteractionResult placeWithLine(UseOnContext контекст) {
        Level уровень = контекст.getLevel();
        BlockPlaceContext место = new BlockPlaceContext(контекст);
        if (!место.canPlace()) {
            return super.useOn(контекст);
        }
        BlockPos позиция = место.getClickedPos();
        уровень.setBlock(позиция, ModBlocks.WIRE.get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        if (!(уровень.getBlockEntity(позиция) instanceof NetworkBlockEntity сеть)) {
            // блок-сущности нет — значит, линии некуда класть, и блок остался бы
            // пустым призраком, который держит место и ничего не рисует
            уровень.removeBlock(позиция, false);
            Spellcraft.LOGGER.warn(
                    "Сеть: блок в {} встал без блок-сущности, линия не положена",
                    позиция);
            return super.useOn(контекст);
        }
        if (!сеть.add(kind, color)) {
            // блок без линии — пустой призрак: он держит место и ничего не
            // рисует, и игрок ищет, где ошибка
            уровень.removeBlock(позиция, false);
            Spellcraft.LOGGER.warn("Сеть: в {} некуда класть линию {}",
                    позиция, NetworkItems.id(new NetworkLine(kind, color, 0, 0)));
            return InteractionResult.PASS;
        }
        // синхронизация нужна и без единого подключения: клиент по блоку сможет
        // нарисовать линию и кликнуть по ней
        сеть.sync(уровень, позиция);
        NetworkBlockEntity.refresh(уровень, позиция);
        сеть.sync(уровень, позиция);
        уровень.playSound(null, позиция,
                net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.4f, 1.4f);
        Spellcraft.LOGGER.info("Сеть: {} в {} — линий в блоке: {}",
                NetworkItems.id(new NetworkLine(kind, color, 0, 0)), позиция, сеть.lines().size());
        var игрок = контекст.getPlayer();
        if (игрок == null || !игрок.getAbilities().instabuild) {
            контекст.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    /** Можно ли добавить линию: блок сети под курсором и в нём есть место. */
    private boolean addable(Level уровень, UseOnContext контекст) {
        NetworkBlockEntity сеть = сетьПод(уровень, контекст.getClickedPos(), контекст);
        return сеть != null && сеть.hasFreeSlot() && сеть.line(kind, color) == null;
    }

    /**
     * Блок сети под курсором: сам блок или тот, что стоит перед кликнутой гранью.
     *
     * <p>Именно перед гранью, а не за ней: за кликнутой гранью лежит сам кликнутый
     * блок, и поиск там всегда упирался бы в него самого.
     */
    private NetworkBlockEntity сетьПод(Level уровень, net.minecraft.core.BlockPos позиция,
                                     UseOnContext контекст) {
        if (уровень.getBlockEntity(позиция) instanceof NetworkBlockEntity be) {
            return be;
        }
        var перед = позиция.relative(контекст.getClickedFace());
        return уровень.getBlockEntity(перед) instanceof NetworkBlockEntity be ? be : null;
    }

    /**
     * Добавляет линию в блок сети под курсором.
     *
     * @return true — линия добавлена и предмет утёк
     */
    private boolean addInto(Level уровень, UseOnContext контекст) {
        BlockPos позиция = контекст.getClickedPos();
        NetworkBlockEntity сеть = сетьПод(уровень, позиция, контекст);
        if (сеть == null) {
            return false;
        }
        if (позиция != сеть.getBlockPos()) {
            позиция = сеть.getBlockPos();
        }
        if (!сеть.hasFreeSlot() || сеть.line(kind, color) != null) {
            return false;
        }
        if (!сеть.add(kind, color)) {
            return false;
        }
        if (!уровень.isClientSide) {
            var игрок = контекст.getPlayer();
            if (игрок == null || !игрок.getAbilities().instabuild) {
                контекст.getItemInHand().shrink(1);
            }
            NetworkBlockEntity.refresh(уровень, позиция);
            сеть.sync(уровень, позиция);
            Spellcraft.LOGGER.info("Сеть: доклал {} в {} — линий в блоке: {}",
                    NetworkItems.id(new NetworkLine(kind, color, 0, 0)), позиция,
                    сеть.lines().size());
        }
        return true;
    }
}
