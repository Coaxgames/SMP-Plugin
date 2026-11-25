package com.coaxgames.smp.ritual;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RitualManagerTest {
    @Test
    void formatsRitualCountdownAtMinuteBoundaries() {
        assertEquals("3:00", RitualManager.formatRemainingTime(180));
        assertEquals("1:01", RitualManager.formatRemainingTime(61));
        assertEquals("0:01", RitualManager.formatRemainingTime(1));
        assertEquals("0:00", RitualManager.formatRemainingTime(0));
        assertEquals("0:00", RitualManager.formatRemainingTime(-1));
    }
}
