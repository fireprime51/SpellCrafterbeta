package com.github.fireprime5.spellcraft.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Узел графа: конкретный размещённый модуль с позицией в сетке.
 */
public class SpellNode {

    /**
     * Полный кодек узла: положение, связи и включённые модификаторы.
     * Используется и для сохранения в NBT, и для вложения игрока
     * ({@link com.github.fireprime5.spellcraft.data.PlayerSpellStore}), поэтому
     * обязан содержать все поля узла — иначе часть данных теряется при релоге.
     */
    public static final Codec<SpellNode> FULL_CODEC = RecordCodecBuilder.create(inst -> inst.group(
            ResourceLocation.CODEC.fieldOf("module").forGetter(n -> n.moduleId),
            Codec.INT.fieldOf("grid_x").orElse(0).forGetter(n -> n.gridX),
            Codec.INT.fieldOf("grid_y").orElse(0).forGetter(n -> n.gridY),
            Codec.INT.listOf().optionalFieldOf("inputs", List.of())
                    .forGetter(n -> n.inputConnections),
            Codec.STRING.listOf().optionalFieldOf("mods", List.of())
                    .forGetter(n -> n.modifiers),
            Codec.STRING.optionalFieldOf("param", "").forGetter(n -> n.param)
    ).apply(inst, (module, gx, gy, inputs, mods, param) -> {
        SpellNode node = new SpellNode(module, gx, gy);
        node.inputConnections = new ArrayList<>(inputs);
        node.modifiers = new ArrayList<>(mods);
        node.param = param;
        return node;
    }));

    /** Сколько связей и модификаторов разрешено на одном узле (защита от мусора в NBT). */
    public static final int MAX_CONNECTIONS = 64;
    public static final int MAX_MODIFIERS = 8;

    public ResourceLocation moduleId;
    public int gridX;
    public int gridY;
    /** id узлов, подключённых к каждому входному порту (индекс = номер порта). */
    public List<Integer> inputConnections = new ArrayList<>();
    /** Включённые модификаторы модуля (см. SpellModifiers). */
    public List<String> modifiers = new ArrayList<>();
    /**
     * Параметр модуля: у модулей сети здесь машина и предмет фильтра.
     * Пустая строка — параметр не задан.
     */
    public String param = "";

    public SpellNode(ResourceLocation moduleId, int gridX, int gridY) {
        this.moduleId = moduleId;
        this.gridX = gridX;
        this.gridY = gridY;
    }

    public CompoundTag save() {
        return (CompoundTag) FULL_CODEC.encodeStart(NbtOps.INSTANCE, this).getOrThrow();
    }

    /**
     * Чтение узла из NBT. Никогда не бросает исключений: битый тег превращается
     * в пустой узел, иначе один плохой пакет от клиента роняет сервер
     * (или клиент при вставке заклинания из буфера обмена).
     */
    public static SpellNode load(CompoundTag tag) {
        var parsed = FULL_CODEC.parse(NbtOps.INSTANCE, tag).result();
        if (parsed.isPresent()) {
            SpellNode node = parsed.get();
            node.sanitize();
            return node;
        }
        // старый формат: связи лежали в списке "conns", модификаторы — в "mods"
        return loadLegacy(tag);
    }

    private static SpellNode loadLegacy(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("module"));
        if (id == null) {
            return new SpellNode(ResourceLocation.fromNamespaceAndPath("spellcraft", "unknown"), 0, 0);
        }
        SpellNode node = new SpellNode(id, tag.getInt("grid_x"), tag.getInt("grid_y"));
        node.param = tag.getString("param");
        var conns = tag.getList("conns", Tag.TAG_INT);
        for (int i = 0; i < conns.size(); i++) {
            node.inputConnections.add(((net.minecraft.nbt.IntTag) conns.get(i)).getAsInt());
        }
        var mods = tag.getList("mods", Tag.TAG_STRING);
        for (int i = 0; i < mods.size(); i++) {
            node.modifiers.add(mods.getString(i));
        }
        node.sanitize();
        return node;
    }

    /** Обрезает списки до допустимого размера и убирает пустые модификаторы. */
    public void sanitize() {
        while (inputConnections.size() > MAX_CONNECTIONS) {
            inputConnections.remove(inputConnections.size() - 1);
        }
        if (modifiers.size() > MAX_MODIFIERS) {
            modifiers = new ArrayList<>(modifiers.subList(0, MAX_MODIFIERS));
        }
        modifiers.removeIf(m -> m == null || m.isBlank());
        if (param != null && param.length() > 256) {
            // параметр — это id предмета или координаты, длиннее быть не может
            param = param.substring(0, 256);
        }
    }
}