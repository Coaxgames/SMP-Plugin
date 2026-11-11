package com.coaxgames.smp.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LegendaryCarryLimitTest {
    @Test
    void legendaryItemsShareTwoSlotsIncludingReservations() {
        assertFalse(LegendaryCarryLimit.allows(false, true, 2, 0, 1));
        assertFalse(LegendaryCarryLimit.allows(false, true, 1, 1, 1));
        assertTrue(LegendaryCarryLimit.allows(false, true, 1, 0, 1));
    }

    @Test
    void operatorsAndUsablesBypassTheLimit() {
        assertTrue(LegendaryCarryLimit.allows(true, true, 5, 1, 1));
        assertTrue(LegendaryCarryLimit.allows(false, false, 2, 0, 64));
        assertThrows(IllegalArgumentException.class,
                () -> LegendaryCarryLimit.allows(false, true, -1, 0, 1));
    }
}
