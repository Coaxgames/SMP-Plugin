package com.coaxgames.smp.ability;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class PhaseFourDailyEffectManagerTest {
    @Test
    void alreadyPresentOrStrongerEffectsAreNotRepeatedlyRefreshed() {
        assertFalse(PhaseFourDailyEffectManager.needsRefresh(2, 6000, false, 2, 6000));
        assertFalse(PhaseFourDailyEffectManager.needsRefresh(2, 5980, false, 2, 6000));
        assertFalse(PhaseFourDailyEffectManager.needsRefresh(3, 5, false, 2, 6000));
        assertFalse(PhaseFourDailyEffectManager.needsRefresh(2, -1, true, 2, 6000));
        assertTrue(PhaseFourDailyEffectManager.needsRefresh(2, 5979, false, 2, 6000));
        assertTrue(PhaseFourDailyEffectManager.needsRefresh(1, 6000, false, 2, 6000));
    }

    @Test
    void refusedEffectsAreRetriedAndWarningsAreRateLimited() {
        assertTrue(PhaseFourDailyEffectManager.retryDue(null, 1_000));
        assertTrue(PhaseFourDailyEffectManager.shouldWarn(null, 1_000));
        var failure = new PhaseFourDailyEffectManager.Failure(6_000, 1_000);
        assertFalse(PhaseFourDailyEffectManager.retryDue(failure, 5_999));
        assertTrue(PhaseFourDailyEffectManager.retryDue(failure, 6_000));
        assertFalse(PhaseFourDailyEffectManager.shouldWarn(failure, 60_999));
        assertTrue(PhaseFourDailyEffectManager.shouldWarn(failure, 61_000));
    }

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
