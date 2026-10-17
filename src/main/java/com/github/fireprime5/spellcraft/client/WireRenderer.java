package com.github.fireprime5.spellcraft.client;

import com.github.fireprime5.spellcraft.network.NetworkBlockEntity;
import com.github.fireprime5.spellcraft.network.NetworkGeometry;
import com.github.fireprime5.spellcraft.network.NetworkItems;
import com.github.fireprime5.spellcraft.network.NetworkLine;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import com.mojang.math.Transformation;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Рисует линии блока сети.
 *
 * <p>Обычная blockstate-модель тут не годится: в блоке до трёх линий, у каждой
 * шесть подключений и свой цвет, и такой набор вариантов не помещается ни в
 * какой JSON. Поэтому меш собирается здесь, из коробок {@link NetworkGeometry},
 * по тем же координатам, что и форма блока: что видно, то и выделяется.
 *
 * <p>Смычка как в у Modern Industrialization: линия идёт от центра своего слота
 * прямо к точке входа в грань. Три слота смещены по диагонали блока (5/16,
 * 8/16, 11/16), поэтому в одну грань машины входят три линии разных цветов в
 * трёх разных точках и не пересекаются дальше.
 *
 * <p>Меш кэшируется по набору подключений: бейк квадро на каждую грань каждого
 * отрезка на каждом кадре — это работа, которой не должно быть видно глазом.
 */
public class WireRenderer implements net.minecraft.client.renderer.blockentity.BlockEntityRenderer<NetworkBlockEntity> {

    /** Печёт квадро с правильным обходом вершин и нормалью. */
    private final FaceBakery bakery = new FaceBakery();

    /** Кэш мешей: ключ — набор линий, значение — готовые квадро. */
    private final Map<List<Integer>, List<BakedQuad>> cache = new HashMap<>();

    /** Модель без поворота и без блокировки UV: нам всё это не надо. */
    private static final ModelState МОДЕЛЬ = new ModelState() {
        @Override
        public Transformation getRotation() {
            return Transformation.identity();
        }

        @Override
        public boolean isUvLocked() {
            return false;
        }
    };

    @Override
    public void render(NetworkBlockEntity сеть, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int light, int overlay) {
        if (сеть.isEmpty()) {
            return;
        }
        List<BakedQuad> меш = меш(сеть);
        if (меш.isEmpty()) {
            return;
        }
        var vertices = buffer.getBuffer(Sheets.solidBlockSheet());
        pose.pushPose();
        try {
            // квадро печётся в координатах блока (0..1), а блок в мире рисуется
            // от -0.5 до 0.5: сдвиг делаем позой, иначе FaceBakery получает
            // отрицательные координаты и молча не отдаёт грань
            pose.translate(-0.5, -0.5, -0.5);
            for (BakedQuad quad : меш) {
                vertices.putBulkData(pose.last(), quad, 1f, 1f, 1f, 1f, light, overlay);
            }
        } finally {
            pose.popPose();
        }
    }

    /**
     * Меш для текущего набора линий, из кэша или собранный заново.
     *
     * <p>Кэш ограничен: иначе мир с тысячей разных развилок съел бы память
     * клиента, а пересобирать меш там, где видно два-три блока, дешевле.
     */
    private List<BakedQuad> меш(NetworkBlockEntity сеть) {
        List<Integer> ключ = new ArrayList<>();
        for (NetworkLine линия : сеть.lines()) {
            ключ.add(линия == null ? -1
                    : (линия.kind().ordinal() << 12) | (линия.color().ordinal() << 6)
                    | линия.mask());
        }
        List<BakedQuad> меш = cache.get(ключ);
        if (меш != null) {
            return меш;
        }
        меш = build(сеть);
        if (cache.size() > 512) {
            cache.clear();
        }
        cache.put(ключ, меш);
        return меш;
    }

    /** Сборка меша: у каждой линии своя текстура и свои отрезки. */
    private List<BakedQuad> build(NetworkBlockEntity сеть) {
        List<BakedQuad> меш = new ArrayList<>();
        List<NetworkLine> линии = сеть.lines();
        for (int slot = 0; slot < линии.size() && slot < NetworkGeometry.SLOTS; slot++) {
            NetworkLine линия = линии.get(slot);
            if (линия == null) {
                continue;
            }
            TextureAtlasSprite спрайт = sprite(линия);
            for (NetworkGeometry.Segment отрезок : NetworkGeometry.segments(slot, линия.sides())) {
                for (Direction грань : Direction.values()) {
                    BakedQuad quad = bake(отрезок, грань, спрайт);
                    if (quad != null) {
                        меш.add(quad);
                    }
                }
            }
        }
        return меш;
    }

    /** Квадро одной грани отрезка. */
    private BakedQuad bake(NetworkGeometry.Segment отрезок, Direction грань,
                           TextureAtlasSprite спрайт) {
        Vector3f from = new Vector3f((float) отрезок.x1() / 16,
                (float) отрезок.y1() / 16, (float) отрезок.z1() / 16);
        Vector3f to = new Vector3f((float) отрезок.x2() / 16,
                (float) отрезок.y2() / 16, (float) отрезок.z2() / 16);
        BlockElementFace face = new BlockElementFace(null, 0, "line",
                new net.minecraft.client.renderer.block.model.BlockFaceUV(
                        new float[]{0f, 16f, 16f, 0f}, 0));
        try {
            BakedQuad quad = bakery.bakeQuad(from, to, face, спрайт, грань,
                    МОДЕЛЬ, null, true);
            if (quad == null) {
                warn("грань " + грань + " не испечена: координаты " + from + ".." + to);
            }
            return quad;
        } catch (RuntimeException битый) {
            warn("грань " + грань + " не испечена: " + битый);
            return null;
        }
    }

    /** Предупреждение о том, что блок не нарисуется, — один раз на причину. */
    private static void warn(String причина) {
        if (warned.add(причина)) {
            com.github.fireprime5.spellcraft.Spellcraft.LOGGER.warn(
                    "Сеть: {}", причина);
        }
    }

    private static final Set<String> warned = new java.util.HashSet<>();

    /**
     * Спрайт линии из атласа блочных текстур.
     *
     * <p>Текстура берётся по спрайту блочного атласа: у провода это свой цвет, у
     * трубы бездны — своя. Так линия выглядит тем же, чем её предмет в
     * инвентаре.
     */
    private TextureAtlasSprite sprite(NetworkLine линия) {
        ResourceLocation спрайтId = NetworkItems.sprite(линия);
        TextureAtlasSprite спрайт = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(спрайтId);
        if (спрайт == null) {
            warn("в блочном атласе нет " + спрайтId);
        }
        return спрайт;
    }
}
