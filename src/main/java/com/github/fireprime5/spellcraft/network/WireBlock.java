package com.github.fireprime5.spellcraft.network;

import com.github.fireprime5.spellcraft.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Блок сети: до трёх линий в одном блоке.
 *
 * <p>Раньше это был просто провод, у которого цвет жил в свойстве блока, а
 * подключения — ещё в шести булевых свойствах. С тремя линиями в блоке так не
 * выйдет: свойств потребовалось бы восемнадцать плюс цвета, и blockstate такой
 * набор не переживает. Поэтому подключения и цвета уехали в блок-сущность, а
 * рисование — в {@link NetworkGeometry} вместе с блок-сущностью: три линии стоят
 * в трёх слотах и входят в одну грань машины в трёх разных точках.
 *
 * <p>Соединение считается по шести соседям: линия соединяется с такой же линией
 * в соседнем блоке и с машиной, которая этот вид ресурса принимает. Диагонали не
 * соединяются намеренно — иначе две пересекающиеся под землёй линии стали бы
 * одной сетью. Разрыв в один блок линию разрывает.
 *
 * <p>Столкновений нет: блок — кабель, сквозь который проходят.
 */
public class WireBlock extends BaseEntityBlock implements NetworkNode {

    public static final MapCodec<WireBlock> CODEC = simpleCodec(WireBlock::new);

    public WireBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /** BaseEntityBlock требует кодек блока: без него блок не ставится в мир. */
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // линии рисует рендерер блок-сущности: в свойствах блока их набор не
        // помещается, а значит, не помещается и в JSON-модель
        return RenderShape.INVISIBLE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NetworkBlockEntity(pos, state);
    }

    /**
     * Перенос по всем линиям блока.
     *
     * <p>Тикает только свой тип: чужая под-сущность на этом месте получила бы
     * тик и упала бы при попытке перенести энергию.
     */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlocks.WIRE_ENTITY.get(),
                NetworkBlockEntity.ticker());
    }

    /**
     * Пересчёт подключений при постановке и снятии.
     *
     * <p>Сосед-узел входит в сеть, поэтому линия к нему дотягивается; сам себя
     * сосед при этом не обновляет — он не блок сети.
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState,
                           boolean movedByPiston) {
        // пересчёт подключений читает соседей, а на клиенте их блок-сущности в
        // этот момент ещё пусты: маски обнулились бы и поехали в пакет
        if (!level.isClientSide && !state.is(oldState.getBlock())) {
            NetworkBlockEntity.refresh(level, pos);
        }
    }

    /**
     * Разбор блока роняет все линии, которые в нём жили.
     *
     * <p>Раньше дроп задавала лут-таблица по одному предмету, и цвет в ней
     * приходилось хранить в NBT. Теперь в блоке до трёх линий разных цветов, и
     * таблица о них не знает: предметы роняет сам блок, по одной штуке на линию.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean movedByPiston) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            NetworkBlockEntity.refresh(level, pos);
            if (!movedByPiston && level.getBlockEntity(pos) instanceof NetworkBlockEntity сеть) {
                dropLines(level, pos, сеть);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private void dropLines(Level level, BlockPos pos, NetworkBlockEntity сеть) {
        for (NetworkLine линия : сеть.lines()) {
            Block.popResource(level, pos, NetworkItems.stack(линия));
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                   Block block, BlockPos fromPos, boolean movedByPiston) {
        if (!level.isClientSide) {
            NetworkBlockEntity.refresh(level, pos);
        }
        super.neighborChanged(state, level, pos, block, fromPos, movedByPiston);
    }

    /** Провод объявляет себя узлом — так узлом объявляет себя и машина. */
    @Override
    public Block asBlock() {
        return this;
    }

    /**
     * Форма блока: объединение форм всех линий.
     *
     * <p>Пока блок-сущности нет (мир грузится), формы нет и выделять нечего.
     */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return level != null && level.getBlockEntity(pos) instanceof NetworkBlockEntity сеть
                ? сеть.shape() : Shapes.empty();
    }

    /**
     * Столкновений у блока сети нет.
     *
     * <p>Сквозь кабель проходят: со столкновением игрок запирался бы в сетке, а
     * мобы не прошли бы между линиями — сетка в полстены разбирает комнату.
     */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return level != null && level.getBlockEntity(pos) instanceof NetworkBlockEntity сеть
                ? сеть.shape() : Shapes.empty();
    }

    /**
     * Снятие линии гаечным жестом: Shift и правый клик по самой линии.
     *
     * <p>Снимается та линия, под которую попал курсор, а не «какая-нибудь»: в блоке
     * их до трёх, и снять чужую сеть вместо своей — обидная ошибка. Остаётся пустым
     * блок — его разбирают обычным ломанием, и снятые линии падают предметами.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               net.minecraft.world.entity.player.Player player,
                                               BlockHitResult hit) {
        if (!player.isShiftKeyDown() || level.isClientSide
                || !(level.getBlockEntity(pos) instanceof NetworkBlockEntity сеть)) {
            return InteractionResult.PASS;
        }
        net.minecraft.world.phys.Vec3 точка = точкаВБлоке(hit, pos);
        NetworkLine линия = сеть.hit(точка);
        if (линия == null) {
            return InteractionResult.PASS;
        }
        int slot = сеть.hitSlot(точка);
        сеть.remove(slot);
        if (сеть.isEmpty()) {
            level.destroyBlock(pos, false);
        } else {
            NetworkBlockEntity.refresh(level, pos);
            сеть.sync(level, pos);
        }
        if (!player.getAbilities().instabuild) {
            player.getInventory().add(dropped(линия));
        }
        return InteractionResult.CONSUME;
    }

    /** Предмет снятой линии: провод своего цвета или трубу бездны. */
    private static ItemStack dropped(NetworkLine линия) {
        return NetworkItems.stack(линия);
    }

    /** Точка клика в координатах блока, а не мира. */
    private static net.minecraft.world.phys.Vec3 точкаВБлоке(BlockHitResult hit, BlockPos pos) {
        var точка = hit.getLocation();
        return new net.minecraft.world.phys.Vec3(точка.x() - pos.getX(),
                точка.y() - pos.getY(), точка.z() - pos.getZ());
    }
}
