package com.coaxgames.smp.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class EnderPearlStackListenerTest {
    @Test
    void splitsPearlsWithoutChangingTheirTotal() {
        assertEquals(List.of(), EnderPearlStackListener.splitAmounts(0));
        assertEquals(List.of(8), EnderPearlStackListener.splitAmounts(8));
        assertEquals(List.of(8, 8), EnderPearlStackListener.splitAmounts(16));
        assertEquals(List.of(8, 8, 8, 1), EnderPearlStackListener.splitAmounts(25));
        assertThrows(IllegalArgumentException.class, () -> EnderPearlStackListener.splitAmounts(-1));
    }
}
