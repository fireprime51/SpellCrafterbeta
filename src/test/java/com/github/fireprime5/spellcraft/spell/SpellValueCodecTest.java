package com.github.fireprime5.spellcraft.spell;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Значения заклинания: сериализация для клиента и защита от мусора в пакетах.
 */
class SpellValueCodecTest {

    @Test
    void числоПереживаетСериализацию() {
        SpellValue value = SpellValue.of(12.5);
        SpellValue back = SpellValue.load(value.save());
        assertEquals(SpellValue.Kind.NUMBER, back.kind());
        assertEquals(12.5, back.asNumber(), 1e-9);
    }

    @Test
    void точкаИВекторПереживаютСериализацию() {
        SpellValue point = SpellValue.of(new Vec3(10.5, 64.0, -3.5), SpellValue.Kind.POSITION);
        SpellValue back = SpellValue.load(point.save());
        assertEquals(SpellValue.Kind.POSITION, back.kind());
        assertEquals(new Vec3(10.5, 64.0, -3.5), back.asVec(),
                "координаты должны доезжать до клиента, а не превращаться в заглушку");

        SpellValue vector = SpellValue.of(new Vec3(1.0, 2.0, 3.0), SpellValue.Kind.VECTOR);
        assertEquals(new Vec3(1.0, 2.0, 3.0), SpellValue.load(vector.save()).asVec());
    }

    @Test
    void предметИСущностьДоезжаютДоКлиента() {
        CompoundTag item = new CompoundTag();
        item.putString("kind", "ITEM");
        item.putString("label", "minecraft:stone");
        item.putInt("count", 3);
        SpellValue back = SpellValue.load(item);
        assertEquals(SpellValue.Kind.ITEM, back.kind());
        assertEquals("minecraft:stone x3", back.describe().getString(),
                "предмет должен доехать до клиента подписью, а не заглушкой");

        CompoundTag dead = new CompoundTag();
        dead.putString("kind", "ENTITY");
        assertDoesNotThrow(() -> SpellValue.load(dead).describe());
    }

    @Test
    void мусорныйТегНеРоняетЧтение() {
        CompoundTag broken = new CompoundTag();
        broken.putString("kind", "НЕТ_ТАКОГО");
        SpellValue unknownKind = SpellValue.load(broken);
        assertEquals(SpellValue.Kind.NUMBER, unknownKind.kind(),
                "неизвестный вид трактуется как число");

        CompoundTag nan = new CompoundTag();
        nan.putString("kind", "NUMBER");
        nan.putDouble("num", Double.NaN);
        // NaN не должен попадать в арифметику заклинания
        assertDoesNotThrow(() -> SpellValue.load(nan).asNumber());
        assertEquals(0.0, SpellValue.load(nan).asNumber(), 1e-9);
    }

    @Test
    void неЧисловоеЗначениеДаётПонятнуюОшибку() {
        assertThrows(SpellContext.SpellException.class,
                () -> SpellValue.of(new Vec3(0, 0, 0), SpellValue.Kind.VECTOR).asNumber());
        assertFalse(SpellValue.of(1).asNumber() == 0.0);
    }

    @Test
    void бесконечноеЧислоНеПроходитКакЧисло() {
        SpellValue inf = SpellValue.of(Double.POSITIVE_INFINITY);
        assertThrows(SpellContext.SpellException.class, inf::asNumber,
                "бесконечность должна отсекаться, иначе она ломает расчёты и урон");
    }
}