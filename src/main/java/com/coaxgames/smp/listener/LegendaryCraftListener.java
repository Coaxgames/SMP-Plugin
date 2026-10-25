package com.coaxgames.smp.listener;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.item.HeadDropHead;
import com.coaxgames.smp.item.LegendaryItem;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.block.Crafter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

public final class LegendaryCraftListener implements Listener {
    private final GalaxySmpPlugin plugin;

    public LegendaryCraftListener(GalaxySmpPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        LegendaryItem item = plugin.getLegendaryItems().getForRecipe(event.getRecipe()).orElse(null);
        if (item == null) {
            return;
        }
        CraftingInventory inventory = event.getInventory();
        if (craftBlocked(item, inventory.getMatrix())
                || event.getView().getPlayer() instanceof Player player
                && !plugin.getLegendaryItems().hasCapacity(player, item.id(), 1)) {
            inventory.setResult(null);
            return;
        }
        inventory.setResult(plugin.getLegendaryItems().createItem(item.id()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraft(CraftItemEvent event) {
        LegendaryItem item = plugin.getLegendaryItems().getForRecipe(event.getRecipe()).orElse(null);
        if (item == null) {
            if (event.getWhoClicked() instanceof Player player) {
                player.sendMessage(plugin.getConfig().getString("example-message", "Hello, world!"));
            }
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)
                || craftBlocked(item, event.getInventory().getMatrix())
                || !plugin.getLegendaryItems().hasCapacity(player, item.id(), 1)) {
            event.setCancelled(true);
            event.getInventory().setResult(null);
            return;
        }

        ItemStack result = event.getCurrentItem();
        if (result == null || plugin.getLegendaryItems().identify(result)
                .filter(registered -> registered.id().equals(item.id())).isEmpty()) {
            result = plugin.getLegendaryItems().createItem(item.id());
            event.setCurrentItem(result);
        }
        var itemUuid = plugin.getLegendaryItems().getUniqueId(result).orElseThrow(
                () -> new IllegalStateException("Prepared legendary recipe result has no UUID: " + item.id()));
        try {
            if (!plugin.getDataStore().recordCraftedItem(
                    item.id(),
                    item.maxCrafts(),
                    itemUuid,
                    player.getUniqueId(),
                    player.getLocation(),
                    item.craftOnce()
            )) {
                event.setCancelled(true);
                event.getInventory().setResult(null);
            }
        } catch (IOException exception) {
            event.setCancelled(true);
            plugin.getLogger().log(Level.SEVERE,
                    "Could not persist legendary craft for " + item.id() + "; craft was cancelled.", exception);
            player.sendMessage("The craft was cancelled because Galaxy SMP could not save its permanent craft record.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCrafterCraft(CrafterCraftEvent event) {
        LegendaryItem item = plugin.getLegendaryItems().getForRecipe(event.getRecipe()).orElse(null);
        if (item == null) {
            return;
        }
        if (!(event.getBlock().getState() instanceof Crafter crafter)
                || craftBlocked(item, crafter.getInventory().getContents())) {
            event.setCancelled(true);
            return;
        }

        ItemStack result = plugin.getLegendaryItems().createItem(item.id());
        var itemUuid = plugin.getLegendaryItems().getUniqueId(result).orElseThrow(
                () -> new IllegalStateException("Automated craft result has no UUID: " + item.id()));
        try {
            if (!plugin.getDataStore().recordCraftedItem(
                    item.id(),
                    item.maxCrafts(),
                    itemUuid,
                    null,
                    event.getBlock().getLocation(),
                    item.craftOnce()
            )) {
                event.setCancelled(true);
                return;
            }
            event.setResult(result);
        } catch (IOException exception) {
            event.setCancelled(true);
            plugin.getLogger().log(Level.SEVERE,
                    "Could not persist automated legendary craft for " + item.id()
                            + "; crafter output was cancelled.", exception);
        }
    }

    private boolean craftBlocked(LegendaryItem item, ItemStack[] matrix) {
        return item.craftOnce() && plugin.getDataStore().getCraftCount(item.id()) >= item.maxCrafts()
                || !HeadDropHead.allRecipeHeadsAreMarked(matrix);
    }
}
