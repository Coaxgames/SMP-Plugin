package com.coaxgames.smp.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MassOrbDamageTest {
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
