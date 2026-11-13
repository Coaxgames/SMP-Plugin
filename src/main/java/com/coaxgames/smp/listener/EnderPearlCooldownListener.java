package com.coaxgames.smp.listener;

import io.papermc.paper.event.player.PlayerItemCooldownEvent;
import io.papermc.paper.event.player.PlayerItemGroupCooldownEvent;
import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.util.ItemCooldownMarkers;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class EnderPearlCooldownListener implements Listener {
    private final GalaxySmpPlugin plugin;

    public EnderPearlCooldownListener(GalaxySmpPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemCooldown(PlayerItemCooldownEvent event) {
        if (event.getType() == Material.ENDER_PEARL && event.getCooldown() > 0) {
            event.setCooldown(plugin.bypassesCooldowns(event.getPlayer()) ? 0 : 30);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGroupCooldown(PlayerItemGroupCooldownEvent event) {
        if (event.getCooldown() > 0 && ItemCooldownMarkers.isCustomGroup(event.getCooldownGroup())
                && plugin.bypassesCooldowns(event.getPlayer())) {
            event.setCooldown(0);
        }
    }
}
