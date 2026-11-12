package com.coaxgames.smp.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import io.papermc.paper.event.player.PlayerItemCooldownEvent;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class EnderPearlCooldownListenerTest {
    private final EnderPearlCooldownListener listener = new EnderPearlCooldownListener();

    @Test
    void pearlUseReceivesThirtyTickCooldown() {
        var event = cooldown(Material.ENDER_PEARL, 20);
        listener.onItemCooldown(event);
        assertEquals(30, event.getCooldown());
    }

    @Test
    void otherItemsAndCooldownRemovalAreUnchanged() {
        var snowball = cooldown(Material.SNOWBALL, 20);
        listener.onItemCooldown(snowball);
        assertEquals(20, snowball.getCooldown());

        var removal = cooldown(Material.ENDER_PEARL, 0);
        listener.onItemCooldown(removal);
        assertEquals(0, removal.getCooldown());
    }

    private static PlayerItemCooldownEvent cooldown(Material material, int ticks) {
        return new PlayerItemCooldownEvent(mock(Player.class), material,
                new NamespacedKey("minecraft", "ender_pearl"), ticks);
    }
}
