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
 * <p>Координаты модельные, 0..1, и сдвигаются на полблока в мировые: блок в мире
 * занимает -0.5..0.5, а модельная система координат — 0..1. Тот же сдвиг делает
 * ваниль, когда рисует обычные блоки.
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
            return;
        }
        Mesh меш = mesh(сеть);
        if (меш == null || меш.isEmpty()) {
            return;
        }
        VertexConsumer consumer = buffer.getBuffer(RenderType.cutout());
        pose.pushPose();
        try {
            // модельные 0..1 -> мировые -0.5..0.5
            pose.translate(-0.5, -0.5, -0.5);
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
     * Коробка отрезка: шесть граней, у каждой четыре вершины.
     *
     * <p>Грань кладётся в обе стороны обхода. Minecraft отсекает задние грани, а
     * порядок обхода у программного рендера ничем не задан и легко перепутать с
     * ванильным: линия в мире просто не видна, и нигде не сказано почему.
     * Лишняя грань отсечётся сама, а вот правильная нарисуется при любом из двух
     * порядков.
     */
    private void box(Mesh меш, NetworkGeometry.Segment отрезок, UV uv, DyeColor цвет) {
        float x1 = (float) отрезок.x1() / 16f;
        float y1 = (float) отрезок.y1() / 16f;
        float z1 = (float) отрезок.z1() / 16f;
        float x2 = (float) отрезок.x2() / 16f;
        float y2 = (float) отрезок.y2() / 16f;
        float z2 = (float) отрезок.z2() / 16f;
        if (x1 >= x2 || y1 >= y2 || z1 >= z2) {
            return;
        }
        float[] tint = цвет(цвет);
        // нижняя и верхняя
        face(меш, uv, tint, v(x1, y1, z1), v(x2, y1, z2), v(x2, y2, z2), v(x1, y2, z1), 0, -1, 0);
        face(меш, uv, tint, v(x1, y2, z2), v(x2, y2, z2), v(x2, y2, z1), v(x1, y2, z1), 0, 1, 0);
        // север и юг
        face(меш, uv, tint, v(x1, y2, z1), v(x2, y2, z1), v(x2, y1, z1), v(x1, y1, z1), 0, 0, -1);
        face(меш, uv, tint, v(x1, y1, z2), v(x2, y1, z2), v(x2, y2, z2), v(x1, y2, z2), 0, 0, 1);
        // запад и восток
        face(меш, uv, tint, v(x1, y1, z1), v(x1, y1, z2), v(x1, y2, z2), v(x1, y2, z1), -1, 0, 0);
        face(меш, uv, tint, v(x2, y1, z2), v(x2, y1, z1), v(x2, y2, z1), v(x2, y2, z2), 1, 0, 0);
    }

    /** Вершина квадро. */
    private static float[] v(float x, float y, float z) {
        return new float[]{x, y, z};
    }

    private static float[] цвет(DyeColor цвет) {
        int argb = цвет.getTextureDiffuseColor();
        return new float[]{((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f, 1f};
    }

    /** Одна грань: четыре вершины по порядку обхода и её обратный порядок. */
    private void face(Mesh меш, UV uv, float[] цвет, float[] a, float[] b, float[] c,
                     float[] d, float nx, float ny, float nz) {
        меш.add(uv, цвет, a, b, c, d, nx, ny, nz);
        меш.add(uv, цвет, a, d, c, b, nx, ny, nz);
    }

    /** Готовый меш: список квадро, которые осталось вывести. */
    private static final class Mesh {
        private final List<Квадро> квадро = new java.util.ArrayList<>();
        private boolean warned;

        void add(UV uv, float[] цвет, float[] a, float[] b, float[] c, float[] d,
                 float nx, float ny, float nz) {
            if (!warned && !Float.isFinite(a[0] + b[1] + c[2])) {
                warned = true;
                WireRenderer.warn("в линию попала нечисловая вершина");
                return;
            }
            квадро.add(new Квадро(uv, цвет, a, b, c, d, nx, ny, nz));
        }

        boolean isEmpty() {
            return квадро.isEmpty();
        }
    }

    /** Один квадро: четыре вершины, нормаль, цвет и UV. */
    private record Квадро(UV uv, float[] цвет, float[] a, float[] b, float[] c, float[] d,
                          float nx, float ny, float nz) {

        void write(VertexConsumer consumer, PoseStack.Pose pose, int light, int overlay) {
            вершина(consumer, pose, a, nx, ny, nz, light, overlay);
            вершина(consumer, pose, b, nx, ny, nz, light, overlay);
            вершина(consumer, pose, c, nx, ny, nz, light, overlay);
            вершина(consumer, pose, d, nx, ny, nz, light, overlay);
        }

        private void вершина(VertexConsumer consumer, PoseStack.Pose pose, float[] p,
                             float nx, float ny, float nz, int light, int overlay) {
            consumer.addVertex(pose.pose(), p[0], p[1], p[2])
                    .setColor(цвет[0], цвет[1], цвет[2], цвет[3])
                    .setUv(uv.u0(), uv.v0())
                    .setOverlay(overlay)
                    .setLight(light)
                    .setNormal(pose, nx, ny, nz);
        }
    }

    /** Предупреждение о том, что блок не нарисуется, — один раз на причину. */
    private static void warn(String причина) {
        if (warned.add(причина)) {
            Spellcraft.LOGGER.warn("Сеть: {}", причина);
        }
    }
}
