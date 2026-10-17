package com.github.fireprime5.spellcraft.network;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Предметы линий: по линии находится предмет, которым её кладут и снимают.
 *
 * <p>Идентификаторы не зашиты здесь таблицей, а собираются из вида и цвета:
 * {@code wire_<цвет>} для провода и {@code abyss_pipe} для трубы. Плюс один
 * список на оба места — положить линию и уронить её с блока, — иначе рано или
 * поздно названия разъедутся, и игрок будет получать трубу вместо провода.
 */
public final class NetworkItems {

    /** Пространство имён мода. */
    private static final String MODID = "spellcraft";

    private NetworkItems() {
    }

    /** Идентификатор предмета линии. */
    public static ResourceLocation id(NetworkLine линия) {
        return ResourceLocation.fromNamespaceAndPath(MODID, имя(линия));
    }

    /**
     * Идентификатор текстуры линии в блочном атласе.
     *
     * <p>Отдельно от id предмета: в атласе блочных текстур спрайт лежит под
     * путём {@code block/...}, и рендерер, искавший {@code spellcraft:wire_red},
     * не находил ничего — блок стоял, а рисовался пустым.
     */
    public static ResourceLocation sprite(NetworkLine линия) {
        return ResourceLocation.fromNamespaceAndPath(MODID,
                "block/" + имя(линия));
    }

    private static String имя(NetworkLine линия) {
        if (линия.kind() == LineKind.ABYSS) {
            return "abyss_pipe";
        }
        // труба одна на все жидкости, поэтому и предмета на каждую не заводим
        return линия.kind() == LineKind.FLUID ? "fluid_pipe"
                : "wire_" + линия.color().getSerializedName();
    }

    /** Предмет линии. */
    public static Item of(NetworkLine линия) {
        return BuiltInRegistries.ITEM.get(id(линия));
    }

    /** Ст��к предмета линии. */
    public static ItemStack stack(NetworkLine линия) {
        return new ItemStack(of(линия));
    }

    /** Ст��к предмета линии такого вида и цвета: для падения с блока. */
    public static ItemStack stack(LineKind kind, DyeColor color) {
        return stack(NetworkLine.empty(kind, color));
    }
}
