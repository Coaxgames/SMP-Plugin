package com.coaxgames.smp.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

class MaceDamageTest {
    @Test
    void capsBaseDamageAtSevenHearts() {
        assertEquals(14.0, MaceDamage.capBaseDamage(30.0, 14.0));
        assertEquals(8.0, MaceDamage.capBaseDamage(8.0, 14.0));
    }

    @Test
    void starMaceMissesResetProgressionAndOnlyAMissAtFiveStartsCooldown() {
        assertEquals(2, MaceDamage.nextStarMaceWindBurst(1, true));
        assertEquals(5, MaceDamage.nextStarMaceWindBurst(4, true));
        assertEquals(5, MaceDamage.nextStarMaceWindBurst(5, true));
        assertEquals(1, MaceDamage.nextStarMaceWindBurst(4, false));
        assertEquals(1, MaceDamage.nextStarMaceWindBurst(5, false));
        assertFalse(MaceDamage.startsStarMaceCooldown(4, true));
        assertFalse(MaceDamage.startsStarMaceCooldown(5, true));
        assertTrue(MaceDamage.startsStarMaceCooldown(5, false));
        for (int level = 1; level < 5; level++) {
            assertEquals(1, MaceDamage.nextStarMaceWindBurst(level, false));
            assertFalse(MaceDamage.startsStarMaceCooldown(level, false));
        }
        assertThrows(IllegalArgumentException.class,
                () -> MaceDamage.nextStarMaceWindBurst(0, true));
    }

    @Test
    void onlyDamagingVanillaMaceSmashesAdvanceStarMace() {
        NamespacedKey smash = NamespacedKey.minecraft("mace_smash");
        assertFalse(MaceDamage.advancesStarMace(4.0, NamespacedKey.minecraft("player_attack")));
        assertFalse(MaceDamage.advancesStarMace(4.0, new NamespacedKey("other", "mace_smash")));
        assertFalse(MaceDamage.advancesStarMace(0.0, smash));
        assertTrue(MaceDamage.advancesStarMace(4.0, smash));
    }

    @Test
    void collapsingSparksScaleWithDamageAndConsecutiveHits() {
        assertEquals(1, MaceDamage.collapsingSparkCount(2.0, 1));
        assertEquals(5, MaceDamage.collapsingSparkCount(8.0, 2));
        assertEquals(8, MaceDamage.collapsingSparkCount(14.0, 2));
        assertEquals(0, MaceDamage.collapsingSparkCount(1.99, 1));
        assertThrows(IllegalArgumentException.class,
                () -> MaceDamage.collapsingSparkCount(Double.POSITIVE_INFINITY, 1));
        assertThrows(IllegalArgumentException.class,
                () -> MaceDamage.collapsingSparkCount(2.0, 0));
    }

    @Test
    void rejectsInvalidDamageValues() {
        assertThrows(IllegalArgumentException.class,
                () -> MaceDamage.capBaseDamage(Double.NaN, 14.0));
    }
}
