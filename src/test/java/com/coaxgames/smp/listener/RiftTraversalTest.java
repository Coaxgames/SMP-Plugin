package com.coaxgames.smp.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RiftTraversalTest {
    @Test
    void riftWitherUsesLevelOne() {
        assertEquals(0, PhaseFiveAbilityListener.RIFT_WITHER_AMPLIFIER);
    }

    @Test
    void creatorTravelsOnInitialDepartureAndCanReturnWithoutBouncing() {
        UUID creator = UUID.randomUUID();
        RiftTraversal traversal = new RiftTraversal(creator);
        assertNull(traversal.enter(creator, true, false));
        assertEquals(RiftTraversal.Endpoint.ENTRANCE, traversal.enter(creator, false, false));
        assertNull(traversal.enter(creator, false, true));
        assertNull(traversal.enter(creator, false, false));
        assertEquals(RiftTraversal.Endpoint.EXIT, traversal.enter(creator, false, true));
        assertNull(traversal.enter(creator, true, false));
        assertNull(traversal.enter(creator, false, false));
        assertEquals(RiftTraversal.Endpoint.ENTRANCE, traversal.enter(creator, true, false));
    }

    @Test
    void otherPlayersCanTraverseEitherEndRepeatedlyWithoutBouncing() {
        RiftTraversal traversal = new RiftTraversal(UUID.randomUUID());
        UUID player = UUID.randomUUID();
        assertEquals(RiftTraversal.Endpoint.EXIT, traversal.enter(player, false, true));
        assertNull(traversal.enter(player, true, false));
        assertNull(traversal.enter(player, false, false));
        assertEquals(RiftTraversal.Endpoint.ENTRANCE, traversal.enter(player, true, false));
        assertNull(traversal.enter(player, false, true));
    }
}
