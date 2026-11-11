package com.coaxgames.smp.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NovaShieldRulesTest {
    @Test
    void onlyFullyChargedAxeCritsActuallyBlockedByAnEnabledShieldQualify() {
        assertTrue(NovaShieldRules.qualifies(true, true, 1.0F, true, false));
        assertFalse(NovaShieldRules.qualifies(false, true, 1.0F, true, false));
        assertFalse(NovaShieldRules.qualifies(true, false, 1.0F, true, false));
        assertFalse(NovaShieldRules.qualifies(true, true, 0.99F, true, false));
        assertFalse(NovaShieldRules.qualifies(true, true, 1.0F, false, false));
        assertFalse(NovaShieldRules.qualifies(true, true, 1.0F, true, true));
    }

    @Test
    void twoQualifyingHitsDisableForTenSeconds() {
        int hits = NovaShieldRules.nextHitCount(0, true);
        assertEquals(1, hits);
        assertEquals(1, NovaShieldRules.nextHitCount(hits, false));
        assertEquals(2, NovaShieldRules.nextHitCount(hits, true));
        assertEquals(200, NovaShieldRules.DISABLE_TICKS);
        assertThrows(IllegalArgumentException.class, () -> NovaShieldRules.nextHitCount(2, true));
    }
}
