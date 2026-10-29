package com.coaxgames.smp.listener;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.item.HeadDropHead;
import com.coaxgames.smp.item.LegendaryItem;
import com.coaxgames.smp.ritual.RitualManager;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

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
        String blockedReason = craftBlockReason(item, inventory.getMatrix());
        if (blockedReason != null) {
            inventory.setResult(blockedPreview(blockedReason));
            return;
        }
        if (item.craftOnce()) {
            ItemStack preview = new ItemStack(Material.BARRIER);
            var meta = preview.getItemMeta();
            meta.displayName(Component.text(formatDuration(ritualDurationSeconds()) + " ritual: ")
                    .append(plugin.getLegendaryItems().displayName(item.id())));
            preview.setItemMeta(meta);
            inventory.setResult(preview);
            return;
        }
        ItemStack result = plugin.getLegendaryItems().createItem(item.id());
        if (event.getView().getPlayer() instanceof Player player
                && !plugin.getRitualManager().hasCapacity(player, item.id(), result.getAmount())) {
            inventory.setResult(blockedPreview("You are carrying the maximum allowed " + item.id() + "."));
            return;
        }
        inventory.setResult(result);
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
        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            event.getInventory().setResult(null);
            return;
        }
        String blockedReason = craftBlockReason(item, event.getInventory().getMatrix());
        if (blockedReason != null) {
            event.setCancelled(true);
            event.getInventory().setResult(null);
            player.sendMessage(Component.text(blockedReason));
            return;
        }
        if (item.craftOnce()) {
            if (!plugin.getRitualManager().hasCapacity(player, item.id(), 1)) {
                event.setCancelled(true);
                event.getInventory().setResult(null);
                player.sendMessage(Component.text(
                        "You are carrying the maximum allowed " + item.id() + "."));
                return;
            }
            event.setCancelled(true);
            Location location = event.getInventory().getLocation();
            if (location == null || location.getWorld() == null) {
                location = player.getLocation();
            }
            Optional<UUID> ritualUuid = Optional.empty();
            ItemStack[] originalIngredients = copyContents(event.getInventory().getMatrix());
            Set<Integer> ingredientSlots = recipeIngredientSlots(event.getRecipe(), originalIngredients);
            if (ingredientSlots.isEmpty()) {
                event.getInventory().setResult(null);
                plugin.getLogger().severe("Could not match shaped recipe ingredients for ritual " + item.id()
                        + "; craft was cancelled without consuming ingredients.");
                return;
            }
            try {
                ritualUuid = plugin.getRitualManager().start(item, player, location);
                if (ritualUuid.isEmpty()) {
                    event.getInventory().setResult(null);
                    return;
                }
                consumeIngredients(originalIngredients, event.getInventory(), ingredientSlots);
                player.sendMessage(Component.text("Your ritual has begun. The item will appear after "
                        + formatDuration(ritualDurationSeconds()) + "."));
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
        ItemStack result = event.getCurrentItem();
        if (result == null || plugin.getLegendaryItems().identify(result)
                .filter(registered -> registered.id().equals(item.id())).isEmpty()) {
            result = plugin.getLegendaryItems().createItem(item.id());
        }
        if (!plugin.getRitualManager().hasCapacity(player, item.id(), result.getAmount())) {
            event.setCancelled(true);
            event.getInventory().setResult(null);
            player.sendMessage(Component.text(
                    "You are carrying the maximum allowed " + item.id() + "."));
            return;
        }
        event.setCurrentItem(result);
        if (!item.trackUniqueInstance()) {
            return;
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
                || craftBlockReason(item, crafter.getInventory().getContents()) != null) {
            event.setCancelled(true);
            return;
        }
        if (item.craftOnce()) {
            event.setCancelled(true);
            Optional<UUID> ritualUuid = Optional.empty();
            Inventory inventory = crafter.getInventory();
            ItemStack[] originalIngredients = copyContents(inventory.getContents());
            Set<Integer> ingredientSlots = recipeIngredientSlots(event.getRecipe(), originalIngredients);
            if (ingredientSlots.isEmpty()) {
                plugin.getLogger().severe("Could not match automated shaped recipe ingredients for ritual "
                        + item.id() + "; craft was cancelled without consuming ingredients.");
                return;
            }
            try {
                ritualUuid = plugin.getRitualManager().start(item, null, event.getBlock().getLocation());
                if (ritualUuid.isPresent()) {
                    consumeIngredients(originalIngredients, inventory, ingredientSlots);
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
        if (!item.trackUniqueInstance()) {
            event.setResult(result);
            return;
        }
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

    private String craftBlockReason(LegendaryItem item, ItemStack[] matrix) {
        if (item.craftOnce() && plugin.getDataStore().getCraftCount(item.id()) >= item.maxCrafts()) {
            return "The one-time craft for " + item.id() + " has already been used.";
        }
        if (!HeadDropHead.allRecipeHeadsAreMarked(matrix)) {
            return "Use player heads dropped by Head Drop in this recipe.";
        }
        if (item.id().equals("celestial_bow")
                && (matrix.length < 9 || !isItem(matrix[2], "galaxy_string")
                || !isItem(matrix[5], "galaxy_string")
                || !isItem(matrix[8], "galaxy_string"))) {
            return "The Celestial Bow requires Galaxy String in all three right-column slots.";
        }
        return null;
    }

    private ItemStack blockedPreview(String reason) {
        ItemStack preview = new ItemStack(Material.BARRIER);
        var meta = preview.getItemMeta();
        meta.displayName(Component.text(reason));
        preview.setItemMeta(meta);
        return preview;
    }

    private long ritualDurationSeconds() {
        return plugin.getConfig().getLong("rituals.duration-seconds", 180L);
    }

    private String formatDuration(long durationSeconds) {
        long minutes = durationSeconds / 60;
        long seconds = durationSeconds % 60;
        if (seconds == 0) {
            return minutes + (minutes == 1 ? " minute" : " minutes");
        }
        if (minutes == 0) {
            return seconds + (seconds == 1 ? " second" : " seconds");
        }
        return minutes + (minutes == 1 ? " minute " : " minutes ")
                + seconds + (seconds == 1 ? " second" : " seconds");
    }

    private boolean isItem(ItemStack stack, String itemId) {
        return plugin.getLegendaryItems().identify(stack)
                .map(definition -> definition.id().equals(itemId))
                .orElse(false);
    }

    private void consumeIngredients(ItemStack[] contents, Inventory inventory, Set<Integer> ingredientSlots) {
        ItemStack[] updated = copyContents(contents);
        for (int slot : ingredientSlots) {
            ItemStack ingredient = updated[slot];
            if (ingredient == null || ingredient.getType().isAir()) {
                throw new IllegalStateException("Matched recipe ingredient disappeared from slot " + slot + ".");
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

    private Set<Integer> recipeIngredientSlots(Recipe recipe, ItemStack[] matrix) {
        if (!(recipe instanceof ShapedRecipe shaped) || matrix.length != 9) {
            return Set.of();
        }
        String[] shape = shaped.getShape();
        int patternWidth = java.util.Arrays.stream(shape).mapToInt(String::length).max().orElse(0);
        if (patternWidth == 0 || patternWidth > 3 || shape.length > 3) {
            return Set.of();
        }
        for (int offsetY = 0; offsetY <= 3 - shape.length; offsetY++) {
            for (int offsetX = 0; offsetX <= 3 - patternWidth; offsetX++) {
                Set<Integer> slots = new java.util.HashSet<>();
                boolean matches = true;
                for (int row = 0; row < shape.length && matches; row++) {
                    for (int column = 0; column < shape[row].length(); column++) {
                        char symbol = shape[row].charAt(column);
                        if (symbol == ' ') {
                            continue;
                        }
                        RecipeChoice choice = shaped.getChoiceMap().get(symbol);
                        int slot = (offsetY + row) * 3 + offsetX + column;
                        if (choice == null || !choice.test(matrix[slot])) {
                            matches = false;
                            break;
                        }
                        slots.add(slot);
                    }
                }
                if (matches && !slots.isEmpty()) {
                    return Set.copyOf(slots);
                }
            }
        }
        return Set.of();
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
