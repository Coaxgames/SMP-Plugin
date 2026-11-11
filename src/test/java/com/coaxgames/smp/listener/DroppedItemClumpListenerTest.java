package com.coaxgames.smp.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class DroppedItemClumpListenerTest {
    @Test
    void transferRespectsThe128ItemLimit() {
        assertEquals(120, DroppedItemClumpListener.transferable(120, 8));
        assertEquals(1, DroppedItemClumpListener.transferable(64, 127));
        assertEquals(0, DroppedItemClumpListener.transferable(1, 128));
        assertThrows(IllegalArgumentException.class,
                () -> DroppedItemClumpListener.transferable(129, 1));
    }
}
