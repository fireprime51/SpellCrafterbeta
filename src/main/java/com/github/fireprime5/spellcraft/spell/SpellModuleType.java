package com.github.fireprime5.spellcraft.spell;

import net.minecraft.network.chat.Component;
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
         * Зарезервированный слот шкалы.
         *
         * <p>Модулей этой категории в моде нет: она осталась пятой ступенью
         * тёплой шкалы, чтобы палитра (assets/spellcraft/palette.json) и её
         * варианты оставались общей шкалой из пяти цветов. Добавит кто-то
         * категорию — это будет шестой цвет, и проверка палитры упадёт.
         */
        NETWORK("network");

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

    public int maxInstances() {
        return -1;
    }

    public int manaCost() {
        return 0;
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
     * Модуль, который нужно сначала изучить, чтобы этот стал доступен
     * (например, модификация «Удача» открывается после «Область»).
     */
    public ResourceLocation requiresModule() {
        return null;
    }

    public void execute(SpellContext ctx, List<SpellValue> inputs, List<SpellValue> outputs) {
    }
}