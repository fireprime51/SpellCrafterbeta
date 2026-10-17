package com.github.fireprime5.spellcraft.client;

import com.github.fireprime5.spellcraft.Spellcraft;
import com.github.fireprime5.spellcraft.network.NetworkBlockEntity;
import com.github.fireprime5.spellcraft.network.LineKind;
import com.github.fireprime5.spellcraft.network.NetworkFluids;
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
 * <p>Координаты модельные, 0..1, и берутся как есть: у блочного рендерера начало
 * координат — нижний угол блока, а не его центр. В этом можно убедиться по
 * ванильным блочным рендерерам: {@code BeaconRenderer} сдвигается на
 * {@code +0.5}, чтобы попасть в центр блока, а {@code ChestRenderer} не сдвигается
 * вовсе. Лишний сдвиг на полблока уводил половину линии внутрь машины, а
 * половину — в соседний блок.
 */
public class WireRenderer implements BlockEntityRenderer<NetworkBlockEntity> {

    /**
     * Меш по набору подключений: ключ — вид, цвет, маска, жидкость и заполнение.
     *
     * <p>У трубы в ключ входит ещё и ступень заливки: пустая, наполовину полная и
     * полная труба выглядят по-разному, а кэш отдал бы прежнюю картинку.
     */
    private final Map<List<Integer>, Меши> cache = new HashMap<>();

    /** Ошибки, о которых уже сказали в лог: иначе одно и то же каждый кадр. */
    private static final Set<String> warned = new HashSet<>();

    @Override
    public void render(NetworkBlockEntity сеть, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int light, int overlay) {
        if (сеть.isEmpty()) {
            return;
        }
        Меши меши = mesh(сеть);
        if (меши == null || (меши.оболочка.isEmpty() && меши.содержимое.isEmpty())) {
            // линии в блоке есть, а рисовать нечего: это поломка, и она молча
            // выглядит как «провод не рисуется»
            warn("в блоке с линиями собрался пустой меш");
            return;
        }
        pose.pushPose();
        try {
            // оболочка трубы прозрачная, поэтому рисуется второй: сначала то,
            // что внутри, потом стекло поверх
            if (!меши.оболочка.isEmpty()) {
                draw(меши.оболочка, buffer.getBuffer(меши.прозрачная
                        ? RenderType.translucent() : RenderType.cutout()),
                        pose, light, overlay);
            }
            if (!меши.содержимое.isEmpty()) {
                draw(меши.содержимое, buffer.getBuffer(RenderType.solid()),
                        pose, light, overlay);
            }
        } finally {
            pose.popPose();
        }
    }

    private static void draw(Mesh меш, VertexConsumer consumer, PoseStack pose,
                             int light, int overlay) {
        for (Квадро quad : меш.квадро) {
            quad.write(consumer, pose.last(), light, overlay);
        }
    }

    /**
     * Меш для текущего набора линий: из кэша или собранный заново.
     *
     * <p>Кэш ограничен: мир с тысячей разных развилок иначе съел бы память
     * клиента, а пересобирать двадцать кубиков там, где видно три блока, дешевле.
     */
    private Меши mesh(NetworkBlockEntity сеть) {
        List<Integer> ключ = new java.util.ArrayList<>();
        for (NetworkLine линия : сеть.lines()) {
            ключ.add(линия == null ? -1
                    : (линия.kind().ordinal() << 12) | (линия.color().ordinal() << 6)
                    | линия.mask());
            // труба: жидкость задаёт цвет, заполнение — видно ли содержимое
            ключ.add(линия == null || линия.kind() != LineKind.FLUID ? 0
                    : (линия.fluid() == null ? 0 : линия.fluid().hashCode())
                    ^ NetworkFluids.ступень(линия) * 0x5f3759df);
        }
        Меши меш = cache.get(ключ);
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
    private Меши build(NetworkBlockEntity сеть) {
        Mesh оболочка = new Mesh();
        Mesh содержимое = new Mesh();
        boolean прозрачная = false;
        List<NetworkLine> линии = сеть.lines();
        for (int slot = 0; slot < линии.size() && slot < NetworkGeometry.SLOTS; slot++) {
            NetworkLine линия = линии.get(slot);
            if (линия == null) {
                continue;
            }
            UV uv = uv(линия);
            boolean труба = линия.kind() == LineKind.FLUID;
            прозрачная |= труба;
            float[] краска = труба ? цветЖидкости(линия) : цвет(линия.color());
            for (NetworkGeometry.Segment отрезок
                    : NetworkGeometry.segments(slot, линия.sides())) {
                box(оболочка, отрезок, uv, труба ? СТЕКЛО : краска);
                if (труба && NetworkFluids.виднаЖидкость(линия)) {
                    // жидкость уже отступа: она должна быть видна сквозь стенку,
                    // иначе стекло закрывает её целиком
                    box(содержимое, NetworkGeometry.inset(отрезок, NetworkGeometry.FLUID_INSET), uv, краска);
                }
            }
        }
        return new Меши(оболочка, содержимое, прозрачная);
    }

    /**
     * Оболочка трубы: белая и полупрозрачная.
     *
     * <p>Цвет несут края спрайта, а середина почти прозрачна: сквозь неё и видно
     * жидкость внутри.
     */
    private static final float[] СТЕКЛО = {1f, 1f, 1f, 0.5f};

    /**
     * Цвет жидкости по её виду.
     *
     * <p>Ванильные жидкости известны наперёд, остальным достаётся нейтральный
     * цвет: точный оттенок модовской жидкости лежит в её клиентском расширении, а
     * дёргать то и другое из блока линии — лишняя связность ради оттенка.
     */
    private static float[] цветЖидкости(NetworkLine линия) {
        // источник и текучая жидкость — разные id, а выглядят одинаково: и то и
        // другое встречается в трубе, и без второй строки труба с водой из
        // текучей воды красилась бы нейтральным цветом
        int argb = switch (String.valueOf(линия.fluid())) {
            case "minecraft:water", "minecraft:flowing_water" -> 0x3F76E4;
            case "minecraft:lava", "minecraft:flowing_lava" -> 0xF76B0C;
            default -> 0xB4D8E8;
        };
        return new float[]{((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f, 1f};
    }

    /**
     * Углы спрайта линии из блочного атласа.
     *
     * <p>Отсутствующий спрайт атлас не отдаёт пустотой: на неизвестный id он
     * подставляет шаблон, и линия нарисовалась бы фиолетово-чёрной шашкой молча.
     * Поэтому проверяется имя спрайта, а не {@code null}: опечатка в пути
     * попадает в лог, а не на экран.
     */
    private UV uv(NetworkLine линия) {
        var спрайтId = NetworkItems.sprite(линия);
        TextureAtlasSprite спрайт = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(спрайтId);
        if (!спрайтId.equals(спрайт.contents().name())) {
            warn("в блочном атласе нет " + спрайтId + ", вместо него шаблон "
                    + спрайт.contents().name());
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
     * <p>Каждая грань пишется один раз, в порядке вершин, совпадающем с
     * нормалью: у {@code cutout} задние грани отсекаются, и вторая запись той же
     * грани была бы либо бесполезной, либо мерцанием в глубину.
     */
    private void box(Mesh меш, NetworkGeometry.Segment отрезок, UV uv, float[] tint) {
        for (NetworkGeometry.Face грань : NetworkGeometry.faces(отрезок)) {
            float[][] вершины = new float[4][3];
            for (int i = 0; i < 4; i++) {
                for (int ось = 0; ось < 3; ось++) {
                    вершины[i][ось] = (float) (грань.вершины()[i][ось] / 16f);
                }
            }
            меш.add(uv, tint, вершины, грань.nx(), грань.ny(), грань.nz());
        }
    }

    private static float[] цвет(DyeColor цвет) {
        int argb = цвет.getTextureDiffuseColor();
        return new float[]{((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f, 1f};
    }

    /**
     * Что рисовать: оболочка линий и то, что внутри неё.
     *
     * <p>У провода и трубы бездны содержимого нет; у трубы жидкости оболочка
     * прозрачная, а внутри неё видна сама жидкость.
     */
    private record Меши(Mesh оболочка, Mesh содержимое, boolean прозрачная) {
    }

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
