package com.github.fireprime5.spellcraft.network;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.DyeColor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Блок сети: три линии, подключения, снятие линии.
 *
 * <p>Мир в тестах не поднимается, поэтому сам ход пересчёта подключений проверить
 * нечем: он ходит в соседей. Проверяется всё, что можно проверить без мира, —
 * правила на уровне исходника и чистых функций.
 *
 * <p>Главное, что тут ловится, — возврат к одному проводу на блок. Пока в блоке
 * лежит одна линия, в одну грань машины входит один кабель, и «три цвета с
 * одной стороны» сделать невозможно в принципе: соседняя грань занята другим
 * блоком.
 */
class NetworkBlockTest {

    private static final Path JAVA =
            Path.of("src/main/java/com/github/fireprime5/spellcraft");

    private static String исходник(String файл) throws IOException {
        return Files.readString(JAVA.resolve(файл), StandardCharsets.UTF_8);
    }

    /**
     * Блок сети вмещает три линии — по числу слотов.
     *
     * <p>Три — это число Modern Industrialization: больше слотов в блоке не
     * помещается, три линии входят в грань машины в трёх точках и не
     * пересекаются, а четвёртая уже не поместится в оставшийся зазор.
     */
    @Test
    void вБлокеТриСлота() {
        assertEquals(3, NetworkGeometry.SLOTS,
                "слотов должно быть три: столько, сколько влезает в грань");
        assertEquals(3, NetworkGeometry.OFFSET.length,
                "у каждого слота должна быть своя позиция");
        // слоты не наезжают друг на друга: иначе линии слились бы в одну
        for (int slot = 1; slot < NetworkGeometry.SLOTS; slot++) {
            assertTrue(NetworkGeometry.OFFSET[slot] - NetworkGeometry.SIDE
                            >= NetworkGeometry.OFFSET[slot - 1],
                    "слот " + slot + " наезжает на слот " + (slot - 1));
        }
    }

    /**
     * Блок умеет держать три линии разных видов и цветов.
     *
     * <p>Проверяется на настоящей блок-сущности: она создаётся без мира и без
     * реестра, потому что хранит только список линий.
     */
    @Test
    void блокДержитТриРазныеЛинии() {
        NetworkLines сеть = new NetworkLines();
        assertTrue(сеть.isEmpty(), "новый блок должен быть пуст");

        assertTrue(сеть.add(LineKind.ENERGY, DyeColor.RED));
        assertTrue(сеть.add(LineKind.ENERGY, DyeColor.BLUE));
        assertTrue(сеть.add(LineKind.ABYSS, DyeColor.WHITE));
        assertFalse(сеть.hasFreeSlot(), "после трёх линий слотов быть не должно");
        assertFalse(сеть.add(LineKind.ENERGY, DyeColor.GREEN),
                "четвёртая линия не должна помещаться");

        assertEquals(3, сеть.size());
        assertEquals(DyeColor.RED, сеть.line(0).color());
        assertEquals(DyeColor.BLUE, сеть.line(1).color());
        assertEquals(LineKind.ABYSS, сеть.line(2).kind());
        assertEquals(DyeColor.RED, сеть.line(LineKind.ENERGY, DyeColor.RED).color());
        assertEquals(LineKind.ABYSS, сеть.line(LineKind.ABYSS, DyeColor.WHITE).kind());
    }

    /** Повторная такая же линия не занимает второй слот. */
    @Test
    void повторнаяЛинияНеЗанимаетСлот() {
        NetworkLines сеть = new NetworkLines();
        assertTrue(сеть.add(LineKind.ENERGY, DyeColor.RED));
        assertFalse(сеть.add(LineKind.ENERGY, DyeColor.RED),
                "второй красный провод в том же блоке не нужен");
        assertTrue(сеть.add(LineKind.ENERGY, DyeColor.GREEN),
                "провод другого цвета — это другая линия");
        assertEquals(2, сеть.size());
    }

    /**
     * Снятие линии возвращает блок в состояние без неё.
     *
     * <p>Снятие по слоту, а не «первую»: игрок снимает ту линию, под которую
     * попал курсор, и порядок слотов для него ничего не значит.
     */
    @Test
    void снятиеЛинииОсвобождаетСлот() {
        NetworkLines сеть = new NetworkLines();
        сеть.add(LineKind.ENERGY, DyeColor.RED);
        сеть.add(LineKind.ABYSS, DyeColor.WHITE);

        NetworkLine снятая = сеть.remove(1);
        assertEquals(LineKind.ABYSS, снятая.kind());
        assertEquals(1, сеть.size());
        assertTrue(сеть.hasFreeSlot());
        assertTrue(сеть.add(LineKind.ENERGY, DyeColor.BLUE),
                "после снятия линии слот должен освободиться");
        assertTrue(сеть.remove(9) == null,
                "снятие несуществующего слота не должно ничего ломать");
    }

    /**
     * Блок-сущность переживает NBT: иначе после перезагрузки мира сеть распалась
     * бы на отдельные линии, и машины остались бы без питания.
     */
    @Test
    void линииПереживаютNbt() {
        NetworkLines сеть = new NetworkLines();
        сеть.add(LineKind.ENERGY, DyeColor.RED);
        сеть.add(LineKind.ABYSS, DyeColor.WHITE);
        NetworkLine красная = сеть.line(0);
        красная.connect(Direction.NORTH, true);
        красная.stored(500);
        NetworkLine бездна = сеть.line(1);
        бездна.stored(250);

        CompoundTag tag = new CompoundTag();
        List<Integer> маски = new ArrayList<>();
        for (NetworkLine линия : сеть.all()) {
            маски.add(линия.mask());
        }
        tag.putInt("energy", 500);
        tag.putInt("abyss", 250);

        // тот же путь, что в игре: запись и чтение идут через save/load самих линий
        ListTag список = new ListTag();
        for (NetworkLine линия : сеть.all()) {
            список.add(линия.save(new CompoundTag()));
        }
        tag.put("lines", список);

        // читаем так же, как load() блока: список линий из NBT
        List<NetworkLine> линии = new ArrayList<>();
        for (int i = 0; i < список.size(); i++) {
            линии.add(NetworkLine.load(список.getCompound(i)));
        }
        assertEquals(2, линии.size());
        assertEquals(DyeColor.RED, линии.get(0).color());
        assertTrue(линии.get(0).connected(Direction.NORTH),
                "подключение не пережило запись");
        assertEquals(500, линии.get(0).stored());
        assertEquals(250, линии.get(1).stored());
    }

    /**
     * Провод соединяется и с проводом, и с машиной, а труба бездны — с портом.
     *
     * <p>Проверяется по исходнику: правило читает соседей через {@code Level},
     * которого в тестах нет. Смысл проверки — в том, что правило одно и у
     * энергии, и у бездны, и что машина принимает провод любого цвета.
     */
    @Test
    void правилоСоединенияОдноДляВидаИЦвета() throws IOException {
        String be = исходник("network/NetworkBlockEntity.java");
        assertTrue(be.contains("public static boolean connects("),
                "правило соединения должно быть одним методом, а не размазано по блокам");
        int начало = be.indexOf("public static boolean connects(");
        String метод = be.substring(начало, be.indexOf("\n    }", начало));
        assertTrue(метод.contains("NetworkBlockEntity"),
                "линия должна соединяться с такой же линией в соседнем блоке");
        assertTrue(метод.contains("NetworkNode"),
                "энергетическая линия должна доходить до машины");
        assertTrue(метод.contains("AbyssPort"),
                "труба бездны должна доходить до машины с портом");
        // порядок важен: блок сети проверяется раньше узла, иначе линия стыкуется
        // с пустым блоком сети — он тоже помечен как узел, и сеть течёт сквозь
        // блоки, в которых её нет
        int сеть = метод.indexOf("WireBlock");
        int узел = метод.indexOf("NetworkNode");
        assertTrue(сеть >= 0 && узел >= 0 && сеть < узел,
                "блок сети должен проверяться раньше узла, иначе линия "
                        + "подключится к пустому блоку сети");
    }

    /**
     * Линии рисует рендерер, а не blockstate-модель.
     *
     * <p>Возврат моделей означал бы возврат к одному проводу на блок: столько
     * комбинаций подключений в JSON не помещается, и часть состояний просто не
     * нашлась бы, а линия выглядела бы ненарисованной при вполне рабочей сети.
     */
    @Test
    void линииРисуетРендерер() throws IOException {
        String блок = исходник("network/WireBlock.java");
        assertTrue(блок.contains("RenderShape.INVISIBLE"),
                "блок сети не должен рисоваться своей моделью");
        assertTrue(блок.contains("new NetworkBlockEntity("),
                "блок сети должен создавать свою блок-сущность с линиями");

        String клиент = исходник("client/ClientBridgeInstaller.java");
        assertTrue(клиент.contains("WireRenderer"),
                "рендерер линий должен быть зарегистрирован");

        String рендерер = исходник("client/WireRenderer.java");
        assertTrue(рендерер.contains("NetworkGeometry.segments("),
                "рендерер должен рисовать ровно те же отрезки, что и форма блока");
        assertTrue(рендерер.contains("NetworkItems.sprite("),
                "рендерер должен брать текстуру линии из блочного атласа");
        // FaceBakery заточена под модели и на динамической геометрии возвращает
        // null: грань пропадала, линия не рисовалась, и никакого следа в логе
        // проверяется вызов, а не слово: FaceBakery упоминается в описании
        // класса, и поиск по тексту поймал бы комментарий вместо кода
        assertTrue(!рендерер.contains("bakeQuad(") && !рендерер.contains("new FaceBakery("),
                "рендерер не должен печь квадро через FaceBakery: на динамике "
                        + "она отдаёт null и линии исчезают");
        assertTrue(рендерер.contains("addVertex("),
                "вершины должны писаться прямо в VertexConsumer");
        // без текстуры линия обязана остаться видна, просто цветом
        assertTrue(рендерер.contains("warn(\"в блочном атласе нет \"")
                        && рендерер.contains("return new UV(0, 0, 1, 1);")
                        && рендерер.contains("getTextureDiffuseColor"),
                "если спрайта нет, линия должна рисоваться цветом красителя и "
                        + "об этом должен быть отзыв в логе");
        assertTrue(рендерер.contains("RenderType.cutout()"),
                "тонкие линии рисуются cutout — так же, как трубы в MI");
        // порядок обхода у программного рендера ничем не задан, а задние грани
        // отсекаются: грань кладётся в обе стороны, иначе половина блока не видна
        assertTrue(рендерер.contains("NetworkGeometry.faces(отрезок)"),
                "рендерер должен брать грани из правила, а не собирать вершины "
                        + "руками: перекошенная грань не рисуется и выглядит как "
                        + "невидимый провод");
        // порядок вершин задаёт грань правило, а рендерер пишет каждую грань
        // один раз: вторая запись той же грани при отсечении задних граней
        // бесполезна, а без отсечения мерцает в глубину
        String box = тело(рендерер, "private void box(");
        assertEquals(1, box.split("меш\\.add\\(", -1).length - 1,
                "грань пишется один раз: повторная запись обратным обходом при "
                        + "отсечении задних граней бесполезна, а без отсечения "
                        + "мерцает в глубину");
    }

    /**
     * Текстура линии ищется в блочном атласе по пути {@code block/...}.
     *
     * <p>Так было: рендерер искал {@code spellcraft:wire_red}, в атласе лежит
     * {@code spellcraft:block/wire_red}. Спрайт не находился, блок стоял и не
     * рисовался вовсе — выглядело как «провод не ставится».
     */
    @Test
    void текстураЛинииБерётсяИзБлочногоАтласа() {
        NetworkLine провод = NetworkLine.empty(LineKind.ENERGY, DyeColor.RED);
        NetworkLine бездна = NetworkLine.empty(LineKind.ABYSS, DyeColor.WHITE);
        assertEquals("spellcraft:block/wire_red",
                NetworkItems.sprite(провод).toString());
        assertEquals("spellcraft:block/abyss_pipe",
                NetworkItems.sprite(бездна).toString());
        for (NetworkLine линия : List.of(провод, бездна)) {
            Path texture = Path.of("src/main/resources/assets/spellcraft",
                    "textures", NetworkItems.sprite(линия).getPath() + ".png");
            assertTrue(Files.isRegularFile(texture),
                    "текстуры линии нет на диске: " + texture);
        }
    }

    /** Форма блока — это объединение форм линий, а не пустота. */
    @Test
    void формаБлокаСобираетсяИзЛиний() throws IOException {
        String блок = исходник("network/WireBlock.java");
        assertTrue(блок.contains("сеть.shape()"),
                "форма блока должна браться из блок-сущности, иначе выделяется "
                        + "пустота");
        String форма = блок.substring(блок.indexOf("public VoxelShape getShape"));
        assertTrue(форма.contains("Shapes.empty()"),
                "без блок-сущности формы быть не может, и блок должен выделяться "
                        + "пустым, а не падать");
    }

    /** Тело метода по имени сигнатуры: проверяется то, что внутри, а не рядом. */
    private static String тело(String исходник, String сигнатура) {
        int начало = исходник.indexOf(сигнатура);
        assertTrue(начало >= 0, "в исходнике нет метода " + сигнатура);
        int конец = исходник.indexOf("\n    }", начало);
        assertTrue(конец > начало, "тело метода " + сигнатура + " не нашлось");
        return исходник.substring(начало, конец);
    }

    /**
     * Клиент должен получать состав линий, а не пустой блок.
     *
     * <p>Проверяется по исходнику, а не запуском мира: без мира этот путь не
     * нащупать. Но важность проверки — как раз отсюда. Умолчание ванилльного
     * {@code BlockEntity#getUpdatePacket} возвращает {@code null}, и
     * {@code ChunkHolder} не отправляет пакет вовсе; умолчание
     * {@code getUpdateTag} возвращает пустой тег. Обе границы молчат: сервер
     * считает, что линии есть, клиент рисует пустоту, а в логе нет ни одного
     * слова об ошибке.
     */
    @Test
    void линииДоезжаютДоКлиента() throws IOException {
        String блок = исходник("network/NetworkBlockEntity.java");
        assertTrue(блок.contains("public Packet<ClientGamePacketListener> getUpdatePacket()")
                        && блок.contains("ClientboundBlockEntityDataPacket.create(this)"),
                "без пакета с блочными данными клиент не получит линии вовсе: "
                        + "нужно переопределить getUpdatePacket, а не полагаться "
                        + "на умолчание ванилльности, которое возвращает null");
        assertTrue(тело(блок, "public CompoundTag getUpdateTag(").contains("saveAdditional(tag, registries)"),
                "при загрузке чанка клиент получает умолчание getUpdateTag — "
                        + "пустой тег; линии должны попадать в него явно");
        // данные изменения блока должны уходить пакетом состояния, а иначе
        // позиция не попадёт в очередь на рассылку и пакет с линиями не уйдёт
        String sync = тело(блок, "public void sync(");
        assertTrue(sync.contains("setChanged()") && sync.contains("sendBlockUpdated(")
                        && !sync.contains("false"),
                "sync должен и сохранять блок, и звать sendBlockUpdated: "
                        + "иначе позиция не попадёт в очередь на рассылку, "
                        + "и пакет с линиями клиенту не уйдёт никогда");
        assertTrue(тело(блок, "void reconnect(").contains("sync(level, pos)"),
                "пересчёт подключений обязан дёргать ту же sync: иначе появление "
                        + "нового соседа до клиента не дойдёт");
    }

    /**
     * Линии не должны быть уехавшими на полблока.
     *
     * <p>У блочного рендерера начало координат — нижний угол блока: так
     * {@code BeaconRenderer} сдвигается на +0.5, чтобы попасть в центр, а
     * {@code ChestRenderer} не сдвигается вовсе. Сдвиг на -0.5 уводил половину
     * линии внутрь машины, а половину — в соседний блок.
     */
    @Test
    void рендерерНеСдвигаетЛинииНаПолблока() throws IOException {
        String рендерер = исходник("client/WireRenderer.java");
        assertFalse(рендерер.contains("translate(-0.5"),
                "у блочного рендерера начало координат — угол блока, а не центр: "
                        + "сдвиг на полблока уводит линию из блока");
    }

    /**
     * Ни клик, ни каждый блок в лог не пишутся.
     *
     * <p>Сеть видна и соединяется, а лог всё равно засорялся строками на каждый
     * клик и на каждый пересобранный меш: их сотни за минуту игры, и в них
     * утонуло всё, что действительно важно. Остаются только предупреждения — они
     * молчат, пока всё в порядке, и срабатывают на поломке.
     */
    @Test
    void сетьНеПишетВЛогНаКаждыйБлок() throws IOException {
        for (String файл : new String[]{"network/NetworkItem.java", "client/WireRenderer.java"}) {
            String исходник = исходник(файл);
            assertFalse(исходник.contains("LOGGER.info"),
                    файл + ": построчный лог засоряет вывод и ничего не сообщает");
            assertFalse(исходник.contains("LOGGER.debug"),
                    файл + ": и отладочный вывод здесь лишний");
        }
        assertTrue(исходник("network/NetworkBlockEntity.java").contains("LOGGER"),
                "у блок-сущности должен остаться хоть какой-то вывод на поломку");
    }

    /**
     * Труба стыкуется только с тем, что налито.
     *
     * <p>Проверяется по исходнику: пересчёт ходит в соседи, а мир в тестах не
     * поднимается. Без этой проверки труба соединялась бы с пустым баком и с
     * воздухом, тянула бы в себя что-то и выглядела бы сетью без содержимого.
     */
    @Test
    void трубаСтыкуетсяТолькоСНалитым() throws IOException {
        String блок = исходник("network/NetworkBlockEntity.java");
        assertTrue(тело(блок, "public static boolean connects(")
                        .contains("NetworkFluids.естьЧемНаполнить(level, pos, d)"),
                "труба должна стыковаться с хранилищем или источником, где что-то "
                        + "есть, а не с любым соседом");
        assertTrue(тело(блок, "public IFluidHandler fluidStorage()")
                        .contains("LineKind.FLUID"),
                "наружу отдаётся именно жидкостная линия, а не первая попавшаяся");
    }

    /**
     * Жидкость рисуется уже оболочки и прозрачной оболочкой.
     *
     * <p>Прозрачная стенка без содержимого выглядит как стеклянная палочка, а
     * содержимое вровень со стенкой — как мутная колба: жидкость должна быть
     * видна сквозь стекло.
     */
    @Test
    void жидкостьВиднаСквозьСтенку() throws IOException {
        String рендерер = исходник("client/WireRenderer.java");
        assertTrue(рендерер.contains("NetworkGeometry.inset(отрезок, 0.5)"),
                "жидкость рисуется вписанной в отрезок: вровень со стенкой её "
                        + "не видно");
        assertTrue(рендерер.contains("RenderType.translucent()"),
                "оболочка трубы прозрачная — иначе жидкость внутри не покажут");
        assertTrue(рендерер.contains("NetworkFluids.виднаЖидкость(линия)"),
                "пустая труба не рисует содержимого: стеклянная палочка вместо "
                        + "трубы с водой");
        assertTrue(рендерер.contains("NetworkFluids.ступень(линия)"),
                "ступень заливки обязана быть в ключе кэша: полная и пустая трубы "
                        + "выглядят по-разному, а кэш отдал бы прежнюю картинку");
    }
}
