package com.coaxgames.smp.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HitCounterTest {
    private final UUID attacker = UUID.randomUUID();

    @Test
    void countsHitsWithinTheWindowAndResetsAfterItExpires() {
        HitCounter counter = new HitCounter(Duration.ofSeconds(5));

        assertEquals(1, counter.record(attacker, 1_000));
        assertEquals(2, counter.record(attacker, 6_000));
        assertEquals(1, counter.record(attacker, 11_001));
    }

    @Test
    void countsAreIndependentAndCanBeClearedOrExpired() {
        HitCounter counter = new HitCounter(Duration.ofSeconds(5));
        UUID otherAttacker = UUID.randomUUID();

        counter.record(attacker, 1_000);
        counter.record(otherAttacker, 1_000);
        counter.clear(attacker);
        assertEquals(0, counter.getCount(attacker, 1_001));
        assertEquals(1, counter.getCount(otherAttacker, 1_001));

        counter.clearExpired(6_001);
        assertEquals(0, counter.getCount(otherAttacker, 6_001));
    }

    @Test
    void rejectsNonPositiveWindows() {
        assertThrows(IllegalArgumentException.class, () -> new HitCounter(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new HitCounter(Duration.ofNanos(1)));
    }
}
