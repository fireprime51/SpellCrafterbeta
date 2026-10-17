package com.github.fireprime5.spellcraft.spell;

import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.UUID;

import static com.github.fireprime5.spellcraft.spell.PortType.NUMBER;

/**
 * Личные модули игрока.
 *
 * <p>Розыгрыш идёт один раз при первом входе в мир, но игрок узнаёт свой модуль
 * только когда проанализирует себя модулем «Анализ себя». До этого модуль
 * назначен, но скрыт: он не появляется в палитре и не известен игроку. В новом
 * мире розыгрыш проходит заново и выдаёт другой модуль.
 *
 * <p>Смысл в том, чтобы модуль был <b>частью игрока</b>, а не частью
 * прогрессии. Обычный модуль добывается сканированием мира, и через месяц игры
 * он у каждого один и тот же. Личный — нет: у одного игрока «Подпись» равна 73,
 * у другого 41, и одна и та же схема у них даёт разный результат.
 *
 * <p>Поэтому почти все они работают с данными самого игрока — его хешем,
 * числом изученных модулей, потраченной бездной — и превращают эти данные в
 * число на входе заклинания. Дальше это обычное число: с ним работают
 * арифметика, сравнения, условия.
 *
 * <p>Прячутся в палитре наравне с остальными неоткрытыми модулями: незакрытый
 * модуль не показывается никогда, независимо от того, добывается он сканированием
 * или выдаётся личным розыгрышем.
 *
 * @see PlayerKnowledge#assignUnique
 * @see PlayerKnowledge#discoverUnique
 */
public final class UniqueModules {

    private UniqueModules() {
    }

    /**
     * Раздел «Действия».
     *
     * <p>Раньше личные модули жили в «Числах», где они терялись среди
     * арифметики, а собственного подраздела в палитре не было — отдельной
     * вкладки «Личный» не существует вовсе, а заголовок подраздела рисуется
     * только когда в списке больше одного подраздела. Итог: открытый личный
     * модуль было почти невозможно найти.
     */
    private static final SpellModuleType.Category ACT = SpellModuleType.Category.ACTIONS;
    /** Раздел «эффекты»: категория, из которой броня берёт свои модули. */
    private static final SpellModuleType.Category ARMOR = SpellModuleType.Category.EFFECTS;
    /** Цвет личных модулей: холодный бирюзовый, не путается с константами. */
    private static final int C_UNIQUE = 0xFF7BE7DA;
    /** Цвет «Вечности»: блёклое золото, читается как «дорого и опасно». */
    private static final int C_ETERNITY = 0xFFE8C86A;
    /** Цвет «Глаза»: холодный белый — его не спутать с числовыми модулями. */
    private static final int C_EYE = 0xFFF0F4FF;

    /** Идентификаторы — они же порядок выдачи при совпадении хешей. */
    public static final String SIGNATURE = "unique_signature";
    public static final String RESONANCE = "unique_resonance";
    public static final String DEPTH = "unique_depth";
    public static final String OATH = "unique_oath";
    public static final String LEDGER = "unique_ledger";

    /**
     * «Вечность» — единственный личный модуль, который живёт в броне, а не в
     * заклинании. Остальные пять — числа, которые считаются от подписи игрока;
     * эта — постоянное состояние, пока модуль стоит на нагруднике.
     */
    public static final String ETERNITY = "unique_eternity";

    /**
     * «Глаз» — десятая часть от любого расхода бездны.
     *
     * <p>Единственный из личных, который ничего не умножает и ничего не стоит:
     * он не узел в схеме, а свойство игрока. Нашёл — и всё, что ты дальше
     * кастуешь, стоит в десять раз дешевле, включая налог брони.
     */
    public static final String EYE = "unique_eye";

    /** Набор личных модулей в порядке, заданном автором. */
    public static final String[] ALL = {
            SIGNATURE, RESONANCE, DEPTH, OATH, LEDGER, ETERNITY, EYE};

    /**
     * Выбор личного модуля — случайный.
     *
     * <p>Именно случайный, а не выведенный из UUID: личный модуль задуман как
     * то, чего нет у другого игрока, и детерминированный хеш давал бы «уникальность
     * на всю жизнь» — переносишь мир, и модуль тот же. Розыгрыш происходит один
     * раз при первом входе в мир и сразу записывается, поэтому дальше и перезаходы,
     * и смерть, и переустановка мода его не меняют. В следующем мире — другой
     * розыгрыш, потому что там другие данные игрока.
     *
     * @return путь модуля или {@code ""}, если набор пуст
     */
    public static String choose(net.minecraft.util.RandomSource random) {
        if (ALL.length == 0) {
            return "";
        }
        return ALL[random.nextInt(ALL.length)];
    }

    public static void registerAll() {
        // пять числовых — в раздел «личные», «Вечность» — в раздел «броня»,
        // но остаётся среди личных (см. SpellSubsections.registerUniqueArmor)
        SpellSubsections.registerUnique(SIGNATURE, RESONANCE, DEPTH, OATH, LEDGER,
                EYE);
        SpellSubsections.registerUniqueArmor(ETERNITY);
        registerSignature();
        registerResonance();
        registerDepth();
        registerOath();
        registerLedger();
        registerEternity();
        registerEye();
    }

    /**
     * «Вечность» — аннулирование всего входящего урона, пока модуль на нагруднике.
     *
     * <p>Сама по себе она ничего не делает: списание бездны, лимит в
     * {@link com.github.fireprime5.spellcraft.armor.Eternity.MAX_ACTIVE} игроков и
     * отмена события урона живут в {@code ArmorEvents} — как полёт у
     * {@code creative_flight}. Модулю достаточно присутствовать в схеме.
     */
    /**
     * «Глаз»: сам себе скидка.
     *
     * <p>Модуль ничего не делает при исполнении — работать он начинает в момент
     * открытия, а не в момент каста. На выходе текущий множитель, чтобы в схеме
     * было видно, сколько на самом деле стоит то, что ты считаешь.
     */
    private static void registerEye() {
        SpellModules.register(new SpellModuleType(SpellModules.id(EYE), ACT, C_EYE) {
            @Override public int inputs() { return 0; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 0; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                ctx.trace("eye x" + com.github.fireprime5.spellcraft.energy.AbyssEnergy.EYE_DISCOUNT);
                out.add(SpellValue.of(
                        com.github.fireprime5.spellcraft.energy.AbyssEnergy.EYE_DISCOUNT));
            }
        });
    }

    private static void registerEternity() {
        SpellModules.register(new SpellModuleType(SpellModules.id(ETERNITY), ARMOR, C_ETERNITY) {
            @Override public int inputs() { return 0; }
            @Override public int outputs() { return 0; }
            @Override public int manaCost() { return 0; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                ctx.trace("eternity");
            }
        });
    }

    /**
     * «Подпись» — личное число игрока.
     *
     * <p>Точка входа во все остальные: их множители считаются от него. Само по
     * себе число бесполезно, но оно делает видимым, что у игроков разные данные.
     */
    private static void registerSignature() {
        SpellModules.register(new SpellModuleType(SpellModules.id(SIGNATURE), ACT, C_UNIQUE) {
            @Override public int inputs() { return 0; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 0; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }

            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                out.add(SpellValue.of(signatureOf(ctx.caster())));
            }
        });
    }

    /**
     * «Резонанс» — умножает число на персональный коэффициент.
     *
     * <p>Коэффициент выводится из подписи и лежит в диапазоне 1.0–2.0: сильный
     * игрок получает вдвое больше, но никто не получает ровно вдвое от круглой
     * константы — поэтому его нельзя заменить обычным «Умножить».
     */
    private static void registerResonance() {
        SpellModules.register(new SpellModuleType(SpellModules.id(RESONANCE), ACT, C_UNIQUE) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 4; }
            @Override public PortType[] inputPorts() { return new PortType[]{NUMBER}; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }

            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                double base = in.get(0) == null ? 1.0 : in.get(0).asNumber();
                int signature = signatureOf(ctx.caster());
                double factor = 1.0 + (signature % 101) / 100.0;
                ctx.trace("resonance " + signature + " x" + factor);
                out.add(SpellValue.of(base * factor));
            }
        });
    }

    /**
     * «Глубина» — усиливает число тем, сколько игрок изучил.
     *
     * <p>Растёт с прогрессом, поэтому модуль, попавший в игру на первом минуте,
     * через час игры работает совсем иначе. Шкала нарочно пологая: каждый
     * десятый изученный модуль добавляет примерно треть к множителю, и без
     * предельного потолка связка «Глубина → Урон» к концу игры ломала бы баланс.
     */
    private static void registerDepth() {
        SpellModules.register(new SpellModuleType(SpellModules.id(DEPTH), ACT, C_UNIQUE) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 4; }
            @Override public PortType[] inputPorts() { return new PortType[]{NUMBER}; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }

            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                double base = in.get(0) == null ? 1.0 : in.get(0).asNumber();
                int learned = learnedCount(ctx.caster());
                double factor = 1.0 + Math.min(3.0, learned / 30.0);
                ctx.trace("depth learned=" + learned + " x" + factor);
                out.add(SpellValue.of(base * factor));
            }
        });
    }

    /**
     * «Обет» — удваивает число, но сжигает бездну, равную входу.
     *
     * <p>Единственный личный модуль, который тратит ресурс. Смысл в том, чтобы
     * игрок не мог набирать силу бесплатно: обет — это всегда размен «больше
     * энергии бездны сейчас». Проверка идёт до списания, иначе на пустом
     * кошельке модуль сначала списал бы в долг, а потом упал.
     */
    private static void registerOath() {
        SpellModules.register(new SpellModuleType(SpellModules.id(OATH), ACT, C_UNIQUE) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 0; }
            @Override public PortType[] inputPorts() { return new PortType[]{NUMBER}; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }

            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                double base = in.get(0) == null ? 0.0 : in.get(0).asNumber();
                int price = (int) Math.ceil(Math.abs(base));
                if (price > 0) {
                    if (ctx.remainingManaBudget() < price) {
                        throw new SpellContext.SpellException("abyss.insufficient", price);
                    }
                    ctx.spendMana(price);
                }
                ctx.trace("oath x2 for " + price);
                out.add(SpellValue.of(base * 2.0));
            }
        });
    }

    /**
     * «Счёт» — отдаёт то, сколько игрок уже потратил бездны за жизнь.
     *
     * <p>Единственный личный модуль, который читает не число, а накопленную
     * статистику. Полезен тем, что даёт доступ к прогрессу, которого больше нигде
     * нет: счёт растёт от первого каста, и его можно сравнить с чем-то — например,
     * построить порог и пропускать эффект через «Условие».
     */
    private static void registerLedger() {
        SpellModules.register(new SpellModuleType(SpellModules.id(LEDGER), ACT, C_UNIQUE) {
            @Override public int inputs() { return 0; }
            @Override public int outputs() { return 1; }
            @Override public int manaCost() { return 0; }
            @Override public PortType[] outputPorts() { return new PortType[]{NUMBER}; }

            @Override public void execute(SpellContext ctx, List<SpellValue> in,
                                          List<SpellValue> out) {
                int spent = spentAbyssOf(ctx.caster());
                ctx.trace("ledger " + spent);
                out.add(SpellValue.of(spent));
            }
        });
    }

    // ------------------------------------------------------------ данные игрока

    /**
     * Личное число игрока: хеш UUID в развёрнутом виде.
     *
     * <p>Хеш, а не номер счёта и не время регистрации: переустановка модов и
     * перенос мира сохраняют UUID, а счёт обнуляется. Для не-игрока (каст сущностью
     * в тестах) берётся фиксированное число, чтобы контекст не падал.
     */
    public static int signatureOf(net.minecraft.world.entity.LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return 0;
        }
        UUID uuid = player.getUUID();
        long mixed = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
        mixed ^= (mixed >>> 33);
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= (mixed >>> 33);
        // 0..9999: четыре значащих цифры, читается как «подпись», а не как мусор
        return (int) Math.floorMod(mixed, 10_000L);
    }

    private static int learnedCount(net.minecraft.world.entity.LivingEntity entity) {
        return entity instanceof Player player
                ? com.github.fireprime5.spellcraft.data.PlayerKnowledge.learnedCount(player)
                : 0;
    }

    /** Сколько бездны игрок потратил за жизнь. */
    private static int spentAbyssOf(net.minecraft.world.entity.LivingEntity entity) {
        return entity instanceof Player player
                ? com.github.fireprime5.spellcraft.data.PlayerKnowledge.totalAbyssSpent(player)
                : 0;
    }
}
