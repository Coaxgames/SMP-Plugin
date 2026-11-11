package com.coaxgames.smp.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class CorruptEnderPearlTest {
    @Test
    void exactlyOneOfTwentyRollsDropsAPearl() {
        assertEquals(1, IntStream.range(0, 20).filter(PhaseFiveItems::isCorruptPearlDropRoll).count());
        assertThrows(IllegalArgumentException.class, () -> PhaseFiveItems.isCorruptPearlDropRoll(-1));
        assertThrows(IllegalArgumentException.class, () -> PhaseFiveItems.isCorruptPearlDropRoll(20));
    }
}
