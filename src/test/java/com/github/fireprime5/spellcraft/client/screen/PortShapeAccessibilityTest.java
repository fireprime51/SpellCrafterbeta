package com.github.fireprime5.spellcraft.client.screen;

import com.github.fireprime5.spellcraft.spell.PortType;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Guards the non-color cues used by the port legend and canvas markers. */
class PortShapeAccessibilityTest {
    @Test
    void everyPortTypeHasItsOwnShapeCue() {
        EnumSet<PortType.Shape> shapes = EnumSet.noneOf(PortType.Shape.class);
        for (PortType type : PortType.values()) {
            shapes.add(type.shape());
        }
        assertEquals(PortType.values().length, shapes.size(),
                "port types must remain distinguishable without relying on color");
    }

    @Test
    void wildcardUsesOutlineInsteadOfVectorSquare() {
        assertNotEquals(PortType.VECTOR.shape(), PortType.ANY.shape());
        assertEquals(PortType.Shape.OUTLINE, PortType.ANY.shape());
    }
}
