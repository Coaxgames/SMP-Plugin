package com.coaxgames.smp.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HeadDropMarkerTest {
    @Test
    void acceptsOnlyByteValueOneFromTheHeadDropMarker() {
        assertTrue(HeadDropHead.isDroppedMarkerValue((byte) 1));
        assertFalse(HeadDropHead.isDroppedMarkerValue(null));
        assertFalse(HeadDropHead.isDroppedMarkerValue((byte) 0));
        assertFalse(HeadDropHead.isDroppedMarkerValue((byte) 2));
    }
}
