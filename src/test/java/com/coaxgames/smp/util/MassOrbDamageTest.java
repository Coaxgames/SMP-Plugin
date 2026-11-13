package com.coaxgames.smp.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.bukkit.util.BoundingBox;

class MassOrbDamageTest {
    @Test
    void slamDamageMatchesSpecifiedHeightsAndCap() {
        assertEquals(5.0, MassOrbDamage.slamDamage(10.0), 1.0e-9);
        assertEquals(9.5, MassOrbDamage.slamDamage(19.0), 1.0e-9);
        assertEquals(10.0, MassOrbDamage.slamDamage(20.0), 1.0e-9);
        assertEquals(10.0, MassOrbDamage.slamDamage(21.0), 1.0e-9);
        assertEquals(10.0, MassOrbDamage.slamDamage(40.0), 1.0e-9);
        assertEquals(10.0, MassOrbDamage.slamDamage(200.0), 1.0e-9);
        assertThrows(IllegalArgumentException.class, () -> MassOrbDamage.slamDamage(-1));
        assertThrows(IllegalArgumentException.class, () -> MassOrbDamage.slamDamage(Double.NaN));
    }

    @Test
    void slamRequiresCrossingTheTargetsTopWithinItsFootprint() {
        BoundingBox target = new BoundingBox(-0.3, 0, -0.3, 0.3, 2, 0.3);
        assertEquals(0.5, MassOrbDamage.impactFraction(0, 3, 0, 0, 1, 0, 0.6, target), 1.0e-9);
        assertEquals(-1.0, MassOrbDamage.impactFraction(2, 3, 0, 2, 1, 0, 0.6, target));
        assertEquals(-1.0, MassOrbDamage.impactFraction(0, 1, 0, 0, 3, 0, 0.6, target));
        assertEquals(-1.0, MassOrbDamage.impactFraction(0, 1.5, 0, 0, 1, 0, 0.6, target));
        assertEquals(0.5, MassOrbDamage.impactFraction(-2, 3, 0, 2, 1, 0, 0.6, target), 1.0e-9);
        assertEquals(-1.0, MassOrbDamage.impactFraction(0, 3, 0, 2, 1, 0, 0.6, target));
    }

    @Test
    void scalesDamageByFallHeightThenAppliesFeatherFallingPlaceholder() {
        assertEquals(0.0, MassOrbDamage.calculate(3.0, 3.0, 1.0, 40.0, 15, 0.12, 0.8), 1.0e-9);
        assertEquals(2.0, MassOrbDamage.calculate(13.0, 3.0, 1.0, 40.0, 15, 0.08, 0.8), 1.0e-9);
    }

    @Test
    void enforcesDamageCapAndRejectsInvalidInputs() {
        assertEquals(8.0, MassOrbDamage.calculate(100.0, 3.0, 2.0, 40.0, 15, 0.12, 0.8), 1.0e-9);
        assertThrows(IllegalArgumentException.class,
                () -> MassOrbDamage.calculate(Double.NaN, 3.0, 1.0, 40.0, 15, 0.12, 0.8));
    }
}
