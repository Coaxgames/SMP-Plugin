package com.coaxgames.smp.listener;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.item.HeadDropHead;
import com.coaxgames.smp.item.LegendaryItem;
import com.coaxgames.smp.ritual.RitualManager;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Crafter;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
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
                && !plugin.getRitualManager().hasCapacity(player, item.id(), 1)) {
            inventory.setResult(null);
            return;
        }
        if (item.craftOnce()) {
            ItemStack preview = new ItemStack(Material.BARRIER);
            var meta = preview.getItemMeta();
            meta.displayName(Component.text("15-minute ritual: ")
                    .append(plugin.getLegendaryItems().displayName(item.id())));
            preview.setItemMeta(meta);
            inventory.setResult(preview);
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
                || craftBlocked(item, event.getInventory().getMatrix())) {
            event.setCancelled(true);
            event.getInventory().setResult(null);
            return;
        }
        if (item.craftOnce()) {
            if (!plugin.getRitualManager().hasCapacity(player, item.id(), 1)) {
                event.setCancelled(true);
                event.getInventory().setResult(null);
                return;
            }
            event.setCancelled(true);
            Location location = event.getInventory().getLocation();
            if (location == null || location.getWorld() == null) {
                location = player.getLocation();
            }
            Optional<UUID> ritualUuid = Optional.empty();
            ItemStack[] originalIngredients = copyContents(event.getInventory().getMatrix());
            try {
                ritualUuid = plugin.getRitualManager().start(item, player, location);
                if (ritualUuid.isEmpty()) {
                    event.getInventory().setResult(null);
                    return;
                }
                consumeIngredients(event.getInventory().getMatrix(), event.getInventory());
                player.sendMessage(Component.text("Your ritual has begun. The item will appear after 15 minutes."));
            } catch (IOException | RuntimeException exception) {
                rollbackFailedRitualStart(
                        ritualUuid, item, exception, event.getInventory(), originalIngredients);
                event.getInventory().setResult(null);
                plugin.getLogger().log(Level.SEVERE,
                        "Could not start ritual for " + item.id()
                                + "; ingredient consumption or ritual persistence failed.",
                        exception);
                player.sendMessage("The craft could not be completed safely; no ritual item was issued.");
            }
            return;
        }
        if (!plugin.getRitualManager().hasCapacity(player, item.id(), 1)) {
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
        if (item.craftOnce()) {
            event.setCancelled(true);
            Optional<UUID> ritualUuid = Optional.empty();
            Inventory inventory = crafter.getInventory();
            ItemStack[] originalIngredients = copyContents(inventory.getContents());
            try {
                ritualUuid = plugin.getRitualManager().start(item, null, event.getBlock().getLocation());
                if (ritualUuid.isPresent()) {
                    consumeIngredients(inventory.getContents(), inventory);
                }
            } catch (IOException | RuntimeException exception) {
                rollbackFailedRitualStart(ritualUuid, item, exception, inventory, originalIngredients);
                plugin.getLogger().log(Level.SEVERE,
                        "Could not start automated ritual for " + item.id()
                                + "; ingredient consumption or ritual persistence failed.",
                        exception);
            }
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

    private void consumeIngredients(ItemStack[] contents, Inventory inventory) {
        ItemStack[] updated = new ItemStack[contents.length];
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack ingredient = contents[slot];
            if (ingredient == null || ingredient.getType().isAir()) {
                continue;
            }
            Material remainder = ingredient.getType().getCraftingRemainingItem();
            ItemStack consumed = ingredient.clone();
            consumed.setAmount(consumed.getAmount() - 1);
            if (consumed.getAmount() > 0) {
                updated[slot] = consumed;
            } else if (remainder != null && !remainder.isAir()) {
                updated[slot] = new ItemStack(remainder);
            }
        }
        if (inventory instanceof CraftingInventory craftingInventory) {
            craftingInventory.setMatrix(updated);
        } else {
            inventory.setContents(updated);
        }
    }

    private void rollbackFailedRitualStart(
            Optional<UUID> ritualUuid,
            LegendaryItem item,
            Exception originalException,
            Inventory inventory,
            ItemStack[] originalIngredients
    ) {
        if (ritualUuid.isEmpty()) {
            return;
        }
        try {
            plugin.getDataStore().abortRitual(ritualUuid.get());
        } catch (IOException | RuntimeException rollbackException) {
            originalException.addSuppressed(rollbackException);
            plugin.getLogger().log(Level.SEVERE,
                    "Could not roll back failed ingredient consumption for ritual " + ritualUuid.get()
                            + " (" + item.id() + "); manual data repair may be required.",
                    rollbackException);
            return;
        }
        try {
            if (inventory instanceof CraftingInventory craftingInventory) {
                craftingInventory.setMatrix(copyContents(originalIngredients));
            } else {
                inventory.setContents(copyContents(originalIngredients));
            }
        } catch (RuntimeException restoreException) {
            originalException.addSuppressed(restoreException);
            plugin.getLogger().log(Level.SEVERE,
                    "Could not restore ingredients after ritual startup failed for " + item.id() + ".",
                    restoreException);
        }
    }

    private ItemStack[] copyContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int slot = 0; slot < contents.length; slot++) {
            copy[slot] = contents[slot] == null ? null : contents[slot].clone();
        }
        return copy;
    }
}
