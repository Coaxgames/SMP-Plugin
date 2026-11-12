package com.coaxgames.smp.listener;

import io.papermc.paper.event.player.PlayerItemCooldownEvent;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class EnderPearlCooldownListener implements Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemCooldown(PlayerItemCooldownEvent event) {
        if (event.getType() == Material.ENDER_PEARL && event.getCooldown() > 0) {
            event.setCooldown(30);
        }
    }
}
