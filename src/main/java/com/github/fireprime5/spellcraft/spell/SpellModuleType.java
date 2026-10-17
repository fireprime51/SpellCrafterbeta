package com.github.fireprime5.spellcraft.spell;

import net.minecraft.network.chat.Component;
import com.github.fireprime5.spellcraft.Spellcraft;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Тип модуля заклинания — узел блок-графа (в стиле Psi / Upload Labs).
 */
public abstract class SpellModuleType {

    /** Категория модуля — отображается вкладкой в палитре. */
    public enum Category {
        NUMBERS("numbers"),
        ENTITIES("entities"),
        ACTIONS("actions"),
        LOGIC("logic"),
        /**
         * Эффекты — отдельным разделом.
         *
         * <p>Их тридцать семь, и среди них есть смертельные. Если держать их в
         * «Действиях», вкладка превращается в простыню, где полезное «Лечение»
         * стоит рядом с «Иссушение». Отдельный раздел даёт им место и позволяет
         * положить в палитру цвет, который не путается с боевыми модулями.
         */
        EFFECTS("effects");

        private final String key;

        Category(String key) {
            this.key = key;
        }

        public String translationKey() {
            return "category.spellcraft." + key;
        }
    }

    private final ResourceLocation id;
    private final int color;
    private final Category category;

    protected SpellModuleType(ResourceLocation id, Category category, int color) {
        this.id = id;
        this.category = category;
        this.color = color;
    }

    public ResourceLocation id() {
        return id;
    }

    public int color() {
        return color;
    }

    public Category category() {
        return category;
    }

    /** Короткое имя для блока: module.spellcraft.add */
    public String nameKey() {
        return "module." + id.getNamespace() + "." + id.getPath();
    }

    /** Описание для панели/подсказки: module.spellcraft.add.desc */
    public String descriptionKey() {
        return nameKey() + ".desc";
    }

    public Component label() {
        return Component.translatable(nameKey());
    }

    public Component description() {
        return Component.translatable(descriptionKey());
    }

    public int inputs() {
        return 0;
    }

    public int outputs() {
        return 0;
    }

    /**
     * Сколько последних входов можно НЕ подключать (необязательные).
     * Пример: у модулей добычи необязательный вход «область» — без него
     * добывается один блок, с ним — целая область.
     */
    public int optionalInputs() {
        return 0;
    }

    public boolean isOptional(int port) {
        return port >= inputs() - optionalInputs();
    }

    /**
     * Типы значений на входах (по порядку портов). null = все ANY.
     */
    public PortType[] inputPorts() {
        return null;
    }

    /**
     * Типы значений на выходах (по порядку портов). null = все ANY.
     */
    public PortType[] outputPorts() {
        return null;
    }

    public PortType inputType(int port) {
        return PortType.fill(inputPorts(), inputs())[port];
    }

    public PortType outputType(int port) {
        return PortType.fill(outputPorts(), outputs())[port];
    }

    /** Подписи входов для GUI: "Сущность, Число". */
    public String inputList() {
        if (inputs() == 0) {
            return Component.translatable("port.spellcraft.none").getString();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < inputs(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(inputType(i).label().getString());
        }
        return sb.toString();
    }

    /** Подписи выходов для GUI. */
    public String outputList() {
        if (outputs() == 0) {
            return Component.translatable("port.spellcraft.nothing").getString();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < outputs(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(outputType(i).label().getString());
        }
        return sb.toString();
    }

    /**
 * Подраздел модуля внутри категории — см. {@link SpellSubsections}.
 *
 * <p>Поиск в палитре учитывает название подраздела, поэтому «стек» находит и
 * «Взять из стека», и сам подраздел «Стек».
 */
    public String subCategory() {
        return SpellSubsections.of(this);
    }

    public Component subName() {
        return Component.translatable(SpellSubsections.nameKey(subCategory()));
    }

public int maxInstances() {
        return -1;
    }

    public int manaCost() {
        return 0;
    }

    /**
     * Стоимость каста с учётом состояния игрока.
     *
     * <p>Нужна модулям, цена которых не выводится из одного числа: «Расщепление»
     * сжигает весь кошелёк, поэтому стоит ровно столько, сколько энергии бездны у игрока
     * сейчас есть. Обычным модулям хватает {@link #manaCost()}.
     *
     * @param manaCapacity потолок энергии бездны из конфига; 0 — неизвестно (палитра,
     *                    предпросмотр), тогда берётся обычная цена
     */
    public int castCost(int manaCapacity) {
        return manaCost();
    }

    /**
     * Сколько энергии бездны модуль ест в нагруднике, в секунду.
     *
     * <p>Раньше налог считался «по одной единице за узел за 40 тиков», и этого
     * хватало только на дешёвые модули. Барьер держит урон отовсюду непрерывно
     * и стоит в шестьдесят раз дороже полёта, поэтому расход пришлось назвать
     * явно и в нормальных единицах — в секунду.
     *
     * <p>Значение умножается на длительность налогового периода в тиках, делённую
     * на 20, то есть на число секунд в периоде.
     */
    public double armorUpkeepPerSecond() {
        return 0.5;
    }

    /** Модификаторы, доступные этому модулю (галочки в меню по ПКМ). */
    public java.util.List<SpellModifier> modifiers() {
        return java.util.List.of();
    }

    public boolean hasModifiers() {
        return !modifiers().isEmpty();
    }

    /**
     * Подсказка про необязательный вход: что означает число, когда галочки
     * выключены (пустая строка — подсказки нет).
     *
     * @param enabled включённые на модуле модификаторы
     */
    public String inputHint(int port, java.util.Set<String> enabled) {
        return "";
    }

    /**
     * Стартовый ли модуль (доступен без изучения) — см. StartModules.
     */
    public boolean isStartModule() {
        return StartModules.isStartModule(id());
    }

    /**
     * Модуль, который нужно сначала изучить, чтобы этот стал доступен.
     *
     * <p>Само правило живёт в {@link SpellPrerequisites}: поле было написано,
     * но не переопределялось ни одним модулем, из-за чего 51 модуль был
     * недостижим вообще — вместе с базовой арифметикой.
     */
    public ResourceLocation requiresModule() {
        String required = SpellPrerequisites.of(this);
        return required == null ? null : Spellcraft.id(required);
    }

    /**
     * Личный модуль игрока: выдаётся «Анализом себя» и не добывается сканированием.
     *
     * <p>У игрока такой модуль ровно один, и он не в общей прогрессии — его
     * нельзя открыть цепочкой предпосылок или наградой за сканирование. Палитра
     * такие модули прячет полностью: серым показывать нечего, за ними нет
     * награды, а обещание «просканируй» было бы враньём.
     *
     * @see com.github.fireprime5.spellcraft.spell.UniqueModules
     */
    public boolean isUnique() {
        return SpellSubsections.isUnique(this);
    }

    public void execute(SpellContext ctx, List<SpellValue> inputs, List<SpellValue> outputs) {
    }
}