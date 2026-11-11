package com.coaxgames.smp.listener;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

final class RiftTraversal {
    enum Endpoint { ENTRANCE, EXIT }

    private final UUID creator;
    private final Set<UUID> inside = new HashSet<>();
    private boolean initialCreatorDeparture = true;

    RiftTraversal(UUID creator) {
        this.creator = creator;
        inside.add(creator);
    }

    Endpoint enter(UUID player, boolean inEntrance, boolean inExit) {
        if (!inEntrance && !inExit) {
            boolean wasInside = inside.remove(player);
            if (initialCreatorDeparture && player.equals(creator) && wasInside) {
                initialCreatorDeparture = false;
                inside.add(player);
                return Endpoint.ENTRANCE;
            }
            return null;
        }
        if (!inside.add(player)) {
            return null;
        }
        if (player.equals(creator)) {
            initialCreatorDeparture = false;
        }
        return inEntrance ? Endpoint.ENTRANCE : Endpoint.EXIT;
    }

    void leave(UUID player) {
        inside.remove(player);
    }
}
