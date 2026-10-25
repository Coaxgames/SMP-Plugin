package com.coaxgames.smp.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MaceDamageTest {
    @Test
    void capsBaseDamageAtSevenHeartsAndDetectsHitsOverFiveHearts() {
        assertEquals(14.0, MaceDamage.capBaseDamage(30.0, 14.0));
        assertEquals(8.0, MaceDamage.capBaseDamage(8.0, 14.0));
        assertFalse(MaceDamage.exceedsSparkThreshold(10.0));
        assertTrue(MaceDamage.exceedsSparkThreshold(10.01));
    }

    @Test
    void rejectsInvalidDamageValues() {
        assertThrows(IllegalArgumentException.class,
                () -> MaceDamage.capBaseDamage(Double.NaN, 14.0));
        assertThrows(IllegalArgumentException.class,
                () -> MaceDamage.exceedsSparkThreshold(Double.POSITIVE_INFINITY));
    }
}
