package com.github.fireprime5.spellcraft.spell;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Значение в стеке заклинания: число, сущность, позиция, вектор или предмет.
 */
public record SpellValue(Kind kind, double number, Object ref) {

    public enum Kind {
        NUMBER, ENTITY, POSITION, VECTOR, ITEM, LOCATION
    }


    /**
     * Адрес в мире: измерение вместе с точкой.
     *
     * <p>Отдельная запись, а не «точка плюс где-то сбоку измерение»: у точки
     * без измерения нет смысла — координаты (100, 64, 100) в обычном мире и в
     * Незере это разные адреса, и портал обязан знать, в какой из них вести.
     *
     * <p>Измерение хранится строкой ({@code minecraft:the_nether}), а не
     * {@code ResourceKey}: значение переживает пакет между сервером и клиентом,
     * где серверных ключей нет.
     */
    public record Place(String dimension, Vec3 pos) {

        public Place {
            if (dimension == null || dimension.isBlank()) {
                throw new SpellContext.SpellException("place.no_dimension");
            }
            if (pos == null) {
                throw new SpellContext.SpellException("place.bad_position");
            }
        }

        public BlockPos blockPos() {
            return BlockPos.containing(pos);
        }
    }

    public static SpellValue of(double d) {
        return new SpellValue(Kind.NUMBER, d, null);
    }

    public static SpellValue of(Entity e) {
        return new SpellValue(Kind.ENTITY, 0, e);
    }

    public static SpellValue of(Vec3 v, Kind k) {
        return new SpellValue(k, 0, v);
    }

    public static SpellValue of(ItemStack s) {
        return new SpellValue(Kind.ITEM, 0, s);
    }

    /** Адрес в мире: измерение и точка. */
    public static SpellValue ofPlace(Place place) {
        return new SpellValue(Kind.LOCATION, 0, place);
    }

    /** Место по ключу измерения и точке. */
    public static SpellValue ofPlace(String dimension, Vec3 pos) {
        return ofPlace(new Place(dimension, pos));
    }

    public Place asPlace() {
        if (kind != Kind.LOCATION || !(ref instanceof Place place)) {
            throw new SpellContext.SpellException("type.location");
        }
        return place;
    }

    public double asNumber() {
        if (kind != Kind.NUMBER || !Double.isFinite(number)) {
            throw new SpellContext.SpellException("type.number");
        }
        return number;
    }

    public Entity asEntity() {
        if (kind != Kind.ENTITY || !(ref instanceof Entity e) || !e.isAlive()) {
            throw new SpellContext.SpellException("type.entity");
        }
        return e;
    }

    public Vec3 asVec() {
        if (ref instanceof Vec3 v) {
            return v;
        }
        throw new SpellContext.SpellException("type.vector");
    }

    public BlockPos asBlockPos() {
        return BlockPos.containing(asVec());
    }

    public ItemStack asItem() {
        if (kind != Kind.ITEM || !(ref instanceof ItemStack s)) {
            throw new SpellContext.SpellException("type.item");
        }
        return s;
    }

    public Component describe() {
        return switch (kind) {
            case NUMBER -> Component.literal(trim(number));
            case ENTITY -> ref instanceof Entity e ? e.getName()
                    : ref instanceof String s ? Component.literal(s)
                    : Component.translatable("value.spellcraft.dead_entity");
            case POSITION -> ref instanceof Vec3 v ? Component.literal(blockish(v))
                    : Component.translatable("value.spellcraft.position");
            case VECTOR -> ref instanceof Vec3 v ? Component.literal(vec(v))
                    : Component.translatable("value.spellcraft.vector");
            case ITEM -> ref instanceof ItemStack s && !s.isEmpty()
                    ? s.getHoverName()
                    : ref instanceof String s ? Component.literal(s)
                    : Component.translatable("value.spellcraft.item");
            // место показываем вместе с измерением: без него координаты в панели
            // выглядят как «(100, 64, 100)», и непонятно, что это за адрес
            case LOCATION -> ref instanceof Place place
                    ? Component.translatable("value.spellcraft.place",
                    shortDimension(place.dimension()),
                    Component.literal(blockish(place.pos())))
                    : Component.translatable("value.spellcraft.location");
        };
    }

    /** Короткое имя измерения без пространства имён: Nether вместо minecraft:the_nether. */
    private static Component shortDimension(String dimension) {
        int colon = dimension.indexOf(':');
        return Component.translatable("dimension.spellcraft."
                + (colon < 0 ? dimension : dimension.substring(colon + 1)));
    }

    private static String trim(double d) {
        if (d == Math.floor(d) && Math.abs(d) < 1e9) {
            return String.valueOf((long) d);
        }
        return String.format("%.2f", d);
    }

    private static String vec(Vec3 v) {
        return String.format("%.1f/%.1f/%.1f", v.x, v.y, v.z);
    }

    private static String blockish(Vec3 v) {
        BlockPos p = BlockPos.containing(v);
        return p.getX() + ", " + p.getY() + ", " + p.getZ();
    }

    /**
     * Сериализация значения для пакета результата. Кроме вида и числа уходят
     * и сами данные (координаты, имя сущности, id предмета) — иначе панель
     * стека на клиенте показывала бы заглушки вместо значений.
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", kind.name());
        tag.putDouble("num", Double.isFinite(number) ? number : 0.0);
        switch (kind) {
            case POSITION, VECTOR -> {
                if (ref instanceof Vec3 v) {
                    tag.putDouble("x", v.x);
                    tag.putDouble("y", v.y);
                    tag.putDouble("z", v.z);
                }
            }
            case ENTITY -> {
                if (ref instanceof Entity e) {
                    tag.putString("label", e.getName().getString());
                }
            }
            case ITEM -> {
                if (ref instanceof ItemStack s && !s.isEmpty()) {
                    tag.putString("label", net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .getKey(s.getItem()).toString());
                    tag.putInt("count", s.getCount());
                }
            }
            case LOCATION -> {
                if (ref instanceof Place place) {
                    tag.putString("dimension", place.dimension());
                    tag.putDouble("x", place.pos().x);
                    tag.putDouble("y", place.pos().y);
                    tag.putDouble("z", place.pos().z);
                }
            }
            case NUMBER -> {
            }
        }
        return tag;
    }

    /**
     * Чтение значения из NBT (пакеты, файлы). Никогда не бросает исключений:
     * неизвестный вид трактуется как число, недостающие поля — как нули.
     * Сущности и предметы приходят уже как готовая подпись — на клиенте
     * разбирать их в объекты снова не нужно.
     */
    public static SpellValue load(CompoundTag tag) {
        Kind kind;
        try {
            kind = Kind.valueOf(tag.getString("kind"));
        } catch (IllegalArgumentException e) {
            kind = Kind.NUMBER;
        }
        double number = tag.getDouble("num");
        if (!Double.isFinite(number)) {
            number = 0.0;
        }
        return switch (kind) {
            case POSITION, VECTOR -> new SpellValue(kind, 0,
                    new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z")));
            // предмет приходит подписью (id + количество): разбирать его в
            // предмет на клиенте не нужно, а describe() покажет подпись
            case ITEM -> new SpellValue(kind, 0, label(tag));
            // сущность приходит подписью: искать её по uuid на клиенте смысла нет
            case ENTITY -> new SpellValue(kind, 0, label(tag));
            case LOCATION -> {
                // измерение обязано быть: без него адрес не значит ничего
                String dimension = tag.getString("dimension");
                if (dimension.isBlank()) {
                    yield new SpellValue(Kind.NUMBER, 0, null);
                }
                yield new SpellValue(kind, 0, new Place(dimension,
                        new Vec3(tag.getDouble("x"), tag.getDouble("y"),
                                tag.getDouble("z"))));
            }
            case NUMBER -> new SpellValue(kind, number, null);
        };
    }

    private static String label(CompoundTag tag) {
        String label = tag.getString("label");
        if (label.isEmpty()) {
            return null;
        }
        int count = tag.getInt("count");
        return count > 1 ? label + " x" + count : label;
    }
}
