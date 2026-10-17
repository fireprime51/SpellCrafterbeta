package com.github.fireprime5.spellcraft.spell;

import java.util.List;

import static com.github.fireprime5.spellcraft.spell.PortType.ANY;
import static com.github.fireprime5.spellcraft.spell.PortType.NUMBER;

/**
 * Модули модульной брони.
 *
 * <p>Это не заклинания: у них нет «запуска», они висят на груди и срабатывают
 * от событий. Поэтому портов у них нет вовсе, а цена — постоянный налог на
 * бездну, а не списание за каст (см. {@code ArmorEvents}).
 *
 * <p>Модули без входов и выходов нельзя соединить, поэтому в схеме брони они
 * стоят сами по себе: игрок выбирает, какие триггеры и эффекты ему нужны, и
 * видит список модулей подсказкой на нагруднике.
 */
public final class ArmorModules {

    private ArmorModules() {
    }

    private static final SpellModuleType.Category ARMOR = SpellModuleType.Category.EFFECTS;
    private static final int C_TRIGGER = 0xFFD166;
    private static final int C_FLIGHT = 0x8ECAE6;

    public static void registerAll() {
        // триггер: игрок ударил кого-то
        SpellModules.register(new SpellModuleType(SpellModules.id(com.github.fireprime5.spellcraft.armor.ArmorModuleIds.TRIGGER_ON_HIT), ARMOR, C_TRIGGER) {
            @Override public int inputs() { return 0; }
            @Override public int outputs() { return 0; }
            @Override public int manaCost() { return 0; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                // триггер сам по себе ничего не делает: он разрешает остальным
                // модулям схемы сработать в момент удара
                ctx.trace("hit");
            }
        });

        // триггер: игрок получил урон
        SpellModules.register(new SpellModuleType(SpellModules.id("trigger_on_hurt"), ARMOR, C_TRIGGER) {
            @Override public int inputs() { return 0; }
            @Override public int outputs() { return 0; }
            @Override public int manaCost() { return 0; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                ctx.trace("hurt");
            }
        });

        // креативный полёт: пока модуль стоит и есть бездна, игрок может летать
        SpellModules.register(new SpellModuleType(SpellModules.id(com.github.fireprime5.spellcraft.armor.ArmorModuleIds.CREATIVE_FLIGHT), ARMOR, C_FLIGHT) {
            @Override public int inputs() { return 0; }
            @Override public int outputs() { return 0; }
            @Override public int manaCost() { return 0; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                ctx.trace("flight");
            }
        });

        // модификаторы брони: множители к скорости, урону и защите
        registerModifier(com.github.fireprime5.spellcraft.armor.ArmorModuleIds.MODIFIER_SPEED, 0x06D6A0);
        registerModifier(com.github.fireprime5.spellcraft.armor.ArmorModuleIds.MODIFIER_DAMAGE, 0xEF476F);
        registerModifier(com.github.fireprime5.spellcraft.armor.ArmorModuleIds.MODIFIER_PROTECTION, 0x118AB2);
    }

    /**
     * Модуль-модификатор.
     *
     * <p>Множитель приходит константой: так игрок управляет силой модуля,
     * не трогая ни код, ни баланс. Пустой вход означает «единица» — модуль,
     * в который ничего не воткнули, не должен ломать схему.
     */
    private static void registerModifier(String path, int color) {
        SpellModules.register(new SpellModuleType(SpellModules.id(path), ARMOR, color) {
            @Override public int inputs() { return 1; }
            @Override public int outputs() { return 0; }
            @Override public int manaCost() { return 0; }
            @Override public PortType[] inputPorts() { return new PortType[]{NUMBER}; }
            @Override public void execute(SpellContext ctx, List<SpellValue> in, List<SpellValue> out) {
                SpellValue value = in.get(0);
                double amount = value == null ? 1.0 : value.number();
                ctx.trace(id().getPath() + " " + amount);
                // результат уходит не в стек, а в накопитель брони: броню читают
                // каждый тик, и ArmorEvents применяет этот множитель к игроку
                // (скорость/урон — атрибутами, защита — уменьшением входящего урона).
                ctx.putArmorModifier(id().getPath(), amount);
            }
        });
    }
}
