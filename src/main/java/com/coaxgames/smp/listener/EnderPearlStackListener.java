package com.coaxgames.smp.listener;

import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class EnderPearlStackListener implements Listener {
    private final JavaPlugin plugin;
    private final Set<UUID> pending = new HashSet<>();

    public EnderPearlStackListener(JavaPlugin plugin) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        ItemStack stack = event.getEntity().getItemStack();
        if (clearLegacyLimit(stack)) {
            event.getEntity().setItemStack(stack);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInventoryChange(PlayerInventorySlotChangeEvent event) {
        if (hasLegacyLimit(event.getNewItemStack())) {
            scheduleRefresh(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        scheduleRefresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreativeItem(InventoryCreativeEvent event) {
        ItemStack stack = event.getCursor();
        if (clearLegacyLimit(stack)) {
            event.setCursor(stack);
        }
    }

    private void scheduleRefresh(Player player) {
        if (!pending.add(player.getUniqueId())) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            pending.remove(player.getUniqueId());
            if (!player.isOnline()) {
                return;
            }
            var inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (clearLegacyLimit(stack)) {
                    inventory.setItem(slot, stack);
                }
            }
            ItemStack cursor = player.getItemOnCursor();
            if (clearLegacyLimit(cursor)) {
                player.setItemOnCursor(cursor);
            }
        });
    }

    private static boolean hasLegacyLimit(ItemStack stack) {
        if (stack == null || stack.getType() != Material.ENDER_PEARL) {
            return false;
        }
        var meta = stack.getItemMeta();
        return meta.hasMaxStackSize() && meta.getMaxStackSize() == 8;
    }

    static boolean clearLegacyLimit(ItemStack stack) {
        if (!hasLegacyLimit(stack)) {
            return false;
        }
        var meta = stack.getItemMeta();
        meta.setMaxStackSize(null);
        if (!stack.setItemMeta(meta)) {
            throw new IllegalStateException("Could not remove the legacy Ender Pearl stack limit.");
        }
        return true;
    }
}
