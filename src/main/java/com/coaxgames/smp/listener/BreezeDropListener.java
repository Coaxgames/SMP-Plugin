package com.coaxgames.smp.listener;

import org.bukkit.Material;
import org.bukkit.entity.Breeze;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

public final class BreezeDropListener implements Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Breeze)) {
            return;
        }
        for (ItemStack drop : event.getDrops()) {
            if (drop != null && drop.getType() == Material.BREEZE_ROD) {
                drop.setAmount(Math.multiplyExact(drop.getAmount(), 2));
            }
        }
    }
}
