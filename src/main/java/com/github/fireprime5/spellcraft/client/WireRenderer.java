package com.github.fireprime5.spellcraft.client;

import com.github.fireprime5.spellcraft.Spellcraft;
import com.github.fireprime5.spellcraft.network.NetworkBlockEntity;
import com.github.fireprime5.spellcraft.network.NetworkGeometry;
import com.github.fireprime5.spellcraft.network.NetworkItems;
import com.github.fireprime5.spellcraft.network.NetworkLine;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;

import java.util.HashMap;
import java.util.HashSet;
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
 * <p>Вершины пишутся прямо в {@link VertexConsumer}, без {@code FaceBakery} и
 * {@code BakedQuad}: пекарня заточена под модели, читает их состояния, UV-повороты
 * и возвращает {@code null} на геометрии, которую не ожидала. Молчаливый
 * {@code null} здесь стоил бы невидимых линий — именно так они и пропадали.
 *
 * <p>Сетка как у Modern Industrialization: линия идёт от центра своего слота
 * прямо к точке входа в грань. Три слота смещены по диагонали блока (5/16,
 * 8/16, 11/16), поэтому в одну грань машины входят три линии разных цветов в трёх
 * разных точках и не пересекаются.
 *
 * <p>Координаты модельные, 0..1, и берутся как есть: у блочного рендерера начало
 * координат — нижний угол блока, а не его центр. В этом можно убедиться по
 * ванильным блочным рендерерам: {@code BeaconRenderer} сдвигается на
 * {@code +0.5}, чтобы попасть в центр блока, а {@code ChestRenderer} не сдвигается
 * вовсе. Лишний сдвиг на полблока уводил половину линии внутрь машины, а
 * половину — в соседний блок.
 */
public class WireRenderer implements BlockEntityRenderer<NetworkBlockEntity> {

    /** Меш по набору подключений: ключ — вид, цвет и маска каждой линии. */
    private final Map<List<Integer>, Mesh> cache = new HashMap<>();

    /** Ошибки, о которых уже сказали в лог: иначе одно и то же каждый кадр. */
    private static final Set<String> warned = new HashSet<>();

    @Override
    public void render(NetworkBlockEntity сеть, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int light, int overlay) {
        if (сеть.isEmpty()) {
            trace("пустой блок", сеть, 0);
            return;
        }
        Mesh меш = mesh(сеть);
        if (меш == null || меш.isEmpty()) {
            trace("меш пуст", сеть, 0);
            return;
        }
        trace("линии на месте", сеть, меш.квадро.size());
        VertexConsumer consumer = buffer.getBuffer(RenderType.cutout());
        pose.pushPose();
        try {
            for (Квадро quad : меш.квадро) {
                quad.write(consumer, pose.last(), light, overlay);
            }
        } finally {
            pose.popPose();
        }
    }

    /**
     * Меш для текущего набора линий: из кэша или собранный заново.
     *
     * <p>Кэш ограничен: мир с тысячей разных развилок иначе съел бы память
     * клиента, а пересобирать двадцать кубиков там, где видно три блока, дешевле.
     */
    private Mesh mesh(NetworkBlockEntity сеть) {
        List<Integer> ключ = new java.util.ArrayList<>();
        for (NetworkLine линия : сеть.lines()) {
            ключ.add(линия == null ? -1
                    : (линия.kind().ordinal() << 12) | (линия.color().ordinal() << 6)
                    | линия.mask());
        }
        Mesh меш = cache.get(ключ);
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
    private Mesh build(NetworkBlockEntity сеть) {
        Mesh меш = new Mesh();
        List<NetworkLine> линии = сеть.lines();
        for (int slot = 0; slot < линии.size() && slot < NetworkGeometry.SLOTS; slot++) {
            NetworkLine линия = линии.get(slot);
            if (линия == null) {
                continue;
            }
            UV uv = uv(линия);
            for (NetworkGeometry.Segment отрезок
                    : NetworkGeometry.segments(slot, линия.sides())) {
                box(меш, отрезок, uv, линия.color());
            }
        }
        // границы ловят и масштаб, и сдвиг: по логу видно, где на самом деле
        // лежит меш, не дожидаясь картинки на экране
        Spellcraft.LOGGER.info("Сеть: собрал меш на {} линий — {}",
                линии.size(), меш.bounds());
        return меш;
    }

    /**
     * Углы спрайта линии из блочного атласа.
     *
     * <p>Если спрайта нет (рес-пак без текстуры, опечатка в id), берётся весь
     * квадрат 0..1 и линия рисуется сплошным цветом своего красителя. Раньше
     * вместо этого спрайт был {@code null}, и линия просто не рисовалась.
     */
    private UV uv(NetworkLine линия) {
        var спрайтId = NetworkItems.sprite(линия);
        TextureAtlasSprite спрайт = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(спрайтId);
        if (спрайт == null) {
            warn("в блочном атласе нет " + спрайтId + ", линия рисуется цветом");
            return new UV(0, 0, 1, 1);
        }
        return new UV(спрайт.getU0(), спрайт.getV0(),
                спрайт.getU1(), спрайт.getV1());
    }

    /** Углы спрайта: текстура натягивается на грань целиком. */
    private record UV(float u0, float v0, float u1, float v1) {
    }

    /**
     * Коробка отрезка: шесть граней из {@link NetworkGeometry#faces}.
     *
     * <p>Грани строит правило, а не рендерер: перекошенная грань с двумя разными
     * значениями одной оси не рисуется вообще и выглядит как «провод невидим».
     * Такую ошибку можно проверить тестом, а код рендерера — только в игре.
     *
     * <p>У каждой грани оба обхода вершин: задние грани отсекаются, а порядок
     * обхода у программного рендера ничем не задан. Лишний отсечётся сам.
     */
    private void box(Mesh меш, NetworkGeometry.Segment отрезок, UV uv, DyeColor цвет) {
        float[] tint = цвет(цвет);
        for (NetworkGeometry.Face грань : NetworkGeometry.faces(отрезок)) {
            float[][] вершины = new float[4][3];
            for (int i = 0; i < 4; i++) {
                for (int ось = 0; ось < 3; ось++) {
                    вершины[i][ось] = (float) (грань.вершины()[i][ось] / 16f);
                }
            }
            меш.add(uv, tint, вершины, грань.nx(), грань.ny(), грань.nz());
            float[][] обратный = {вершины[0], вершины[3], вершины[2], вершины[1]};
            меш.add(uv, tint, обратный, грань.nx(), грань.ny(), грань.nz());
        }
    }

    private static float[] цвет(DyeColor цвет) {
        int argb = цвет.getTextureDiffuseColor();
        return new float[]{((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f, 1f};
    }

    /**
     * След рендера: раз в пять секунд.
     *
     * <p>Пока линии не видны, по логу надо отличать «рендерер не рисует» от
     * «на клиенте в блоке нет линий»: снаружи это одно и то же пустое место.
     */
    private static void trace(String что, NetworkBlockEntity сеть, int квадро) {
        long сейчас = System.currentTimeMillis();
        // троттлинг по позиции, а не на весь клиент: иначе из трёх сетей рядом
        // видно одну, и «пустой блок» заглушает «линии на месте»
        if (следы.size() > 256) {
            следы.clear();
        }
        Long прошлый = следы.get(сеть.getBlockPos());
        if (прошлый != null && сейчас - прошлый < 5000) {
            return;
        }
        следы.put(сеть.getBlockPos(), сейчас);
        Spellcraft.LOGGER.info("Сеть: {} в {} — линий {}, квадро {}",
                что, сеть.getBlockPos(), сеть.lines().size(), квадро);
    }

    private static final Map<BlockPos, Long> следы = new HashMap<>();

    /** Готовый меш: список квадро, которые осталось вывести. */
    private static final class Mesh {
        private final List<Квадро> квадро = new java.util.ArrayList<>();

        void add(UV uv, float[] цвет, float[][] вершины, float nx, float ny, float nz) {
            for (float[] вершина : вершины) {
                for (float координата : вершина) {
                    if (!Float.isFinite(координата)) {
                        WireRenderer.warn("в линию попала нечисловая вершина");
                        return;
                    }
                }
            }
            // UV раскладывается по четырём углам грани: одинаковая UV на всех
            // вершинах схлопывает текстуру в один пиксель
            float[][] uvКоординаты = {{uv.u0(), uv.v0()}, {uv.u1(), uv.v0()},
                    {uv.u1(), uv.v1()}, {uv.u0(), uv.v1()}};
            квадро.add(new Квадро(uvКоординаты, цвет, вершины, nx, ny, nz));
        }

        boolean isEmpty() {
            return квадро.isEmpty();
        }

        /** Границы меша в модельных координатах. */
        String bounds() {
            if (квадро.isEmpty()) {
                return "пусто";
            }
            float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
            float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            for (Квадро q : квадро) {
                for (float[] вершина : q.вершины()) {
                    for (int ось = 0; ось < 3; ось++) {
                        min[ось] = Math.min(min[ось], вершина[ось]);
                        max[ось] = Math.max(max[ось], вершина[ось]);
                    }
                }
            }
            return String.format("x %.3f..%.3f  y %.3f..%.3f  z %.3f..%.3f",
                    min[0], max[0], min[1], max[1], min[2], max[2]);
        }
    }

    /** Один квадро: четыре вершины с UV, нормаль и цвет. */
    private record Квадро(float[][] uv, float[] цвет, float[][] вершины,
                          float nx, float ny, float nz) {

        void write(VertexConsumer consumer, PoseStack.Pose pose, int light, int overlay) {
            for (int i = 0; i < 4; i++) {
                consumer.addVertex(pose.pose(), вершины[i][0], вершины[i][1], вершины[i][2])
                        .setColor(цвет[0], цвет[1], цвет[2], цвет[3])
                        .setUv(uv[i][0], uv[i][1])
                        .setOverlay(overlay)
                        .setLight(light)
                        .setNormal(pose, nx, ny, nz);
            }
        }
    }

    /** Предупреждение о том, что блок не нарисуется, — один раз на причину. */
    private static void warn(String причина) {
        if (warned.add(причина)) {
            Spellcraft.LOGGER.warn("Сеть: {}", причина);
        }
    }
}
