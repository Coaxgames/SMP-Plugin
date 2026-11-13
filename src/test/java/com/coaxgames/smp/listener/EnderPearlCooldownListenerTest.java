package com.coaxgames.smp.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.util.ItemCooldownMarkers;
import io.papermc.paper.event.player.PlayerItemCooldownEvent;
import io.papermc.paper.event.player.PlayerItemGroupCooldownEvent;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class EnderPearlCooldownListenerTest {
    private final GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
    private final EnderPearlCooldownListener listener = new EnderPearlCooldownListener(plugin);

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

    @Test
    void bypassesPearlAndCustomItemCooldownsWithoutAffectingOtherGroups() {
        Player player = mock(Player.class);
        when(plugin.bypassesCooldowns(player)).thenReturn(true);
        var pearl = new PlayerItemCooldownEvent(player, Material.ENDER_PEARL,
                new NamespacedKey("minecraft", "ender_pearl"), 20);
        listener.onItemCooldown(pearl);
        assertEquals(0, pearl.getCooldown());
        var spear = new PlayerItemGroupCooldownEvent(player, ItemCooldownMarkers.group("nebula_spear"), 100);
        listener.onGroupCooldown(spear);
        assertEquals(0, spear.getCooldown());
        var unrelated = new PlayerItemGroupCooldownEvent(player, new NamespacedKey("other", "item"), 100);
        listener.onGroupCooldown(unrelated);
        assertEquals(100, unrelated.getCooldown());
    }

    private static PlayerItemCooldownEvent cooldown(Material material, int ticks) {
        return new PlayerItemCooldownEvent(mock(Player.class), material,
                new NamespacedKey("minecraft", "ender_pearl"), ticks);
    }
}
