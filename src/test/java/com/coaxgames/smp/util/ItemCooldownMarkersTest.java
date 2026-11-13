package com.coaxgames.smp.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ItemCooldownMarkersTest {
    @Test
    void convertsMillisecondsToTicksRoundingUp() {
        assertEquals(0, ItemCooldownMarkers.ticks(Duration.ZERO));
        assertEquals(1, ItemCooldownMarkers.ticks(Duration.ofMillis(1)));
        assertEquals(30, ItemCooldownMarkers.ticks(Duration.ofMillis(1500)));
        assertEquals(31, ItemCooldownMarkers.ticks(Duration.ofMillis(1501)));
        assertEquals(400, ItemCooldownMarkers.ticks(Duration.ofSeconds(20)));
        assertEquals(Integer.MAX_VALUE, ItemCooldownMarkers.ticks(Duration.ofMillis(Long.MAX_VALUE)));
    }

    @Test
    void eachCustomItemHasAnIndependentGroup() {
        Set<String> ids = Set.of("kuiper_ball", "mass_orb", "star_mace", "collapsing_star_mace", "nebula_spear", "nova_shield");
        assertTrue(ids.stream().allMatch(ItemCooldownMarkers::supported));
        assertEquals(ids.size(), ids.stream().map(ItemCooldownMarkers::group).collect(Collectors.toSet()).size());
        assertEquals(Set.of("kuiper_ball", "mass_orb", "star_mace", "collapsing_star_mace", "nebula_spear"),
                Set.copyOf(ItemCooldownMarkers.PERSISTED_ITEMS));
        assertFalse(ItemCooldownMarkers.supported("ordinary_shield"));
        assertThrows(IllegalArgumentException.class, () -> ItemCooldownMarkers.group("ordinary_shield"));
    }
}
