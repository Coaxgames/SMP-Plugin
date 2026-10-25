package com.coaxgames.smp.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DamageCapTest {
    @Test
    void capsOnlyDamageAboveTheMaximum() {
        assertEquals(14.0, DamageCap.capFinalDamage(20.0, 14.0));
        assertEquals(5.0, DamageCap.capFinalDamage(5.0, 14.0));
        assertEquals(0.0, DamageCap.capFinalDamage(5.0, 0.0));
    }

    @Test
    void rejectsInvalidDamageValues() {
        assertThrows(IllegalArgumentException.class, () -> DamageCap.capFinalDamage(-1.0, 4.0));
        assertThrows(IllegalArgumentException.class, () -> DamageCap.capFinalDamage(4.0, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> DamageCap.capFinalDamage(Double.POSITIVE_INFINITY, 4.0));
    }
}
