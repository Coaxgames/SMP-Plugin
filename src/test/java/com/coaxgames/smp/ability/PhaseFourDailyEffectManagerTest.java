package com.coaxgames.smp.ability;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PhaseFourDailyEffectManagerTest {
    @Test
    void distinctDailyWindowsTriggerAtTheirConfiguredTimes() {
        assertFalse(PhaseFourDailyEffectManager.inTriggerWindow(5_999, 6_000, false));
        assertTrue(PhaseFourDailyEffectManager.inTriggerWindow(6_000, 6_000, false));
        assertTrue(PhaseFourDailyEffectManager.inTriggerWindow(6_019, 6_000, false));
        assertFalse(PhaseFourDailyEffectManager.inTriggerWindow(6_020, 6_000, false));
        assertFalse(PhaseFourDailyEffectManager.inTriggerWindow(6_000, 13_500, false));
        assertTrue(PhaseFourDailyEffectManager.inTriggerWindow(13_500, 13_500, false));
        assertFalse(PhaseFourDailyEffectManager.inTriggerWindow(13_500, 6_000, false));
    }

    @Test
    void lunarCatchUpStartsAtItsTriggerAndRemainsOptional() {
        assertFalse(PhaseFourDailyEffectManager.inTriggerWindow(13_499, 13_500, true));
        assertTrue(PhaseFourDailyEffectManager.inTriggerWindow(14_000, 13_500, true));
        assertFalse(PhaseFourDailyEffectManager.inTriggerWindow(14_000, 13_500, false));
    }
}
