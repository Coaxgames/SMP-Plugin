package com.coaxgames.smp.command;

import com.coaxgames.smp.GalaxySmpPlugin;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

public final class RecipeGuide implements Listener {
    private static final int PAGE_SIZE = 45;
    private static final int PREVIOUS = 45;
    private static final int BACK = 49;
    private static final int NEXT = 53;
    private final GalaxySmpPlugin plugin;

    public RecipeGuide(GalaxySmpPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public void open(Player player, int page) {
        List<NamespacedKey> keys = recipeKeys();
        int pages = Math.max(1, (keys.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.max(0, Math.min(page, pages - 1));
        Holder holder = new Holder(current, null, keys);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("Galaxy Recipes (" + (current + 1) + "/" + pages + ")"));
        holder.inventory = inventory;
        int start = current * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < keys.size(); i++) {
            Recipe recipe = requireRecipe(keys.get(start + i));
            ItemStack icon = fallbackModel(recipe.getResult());
            addLore(icon, "Click to view recipe");
            inventory.setItem(i, icon);
        }
        if (current > 0) {
            inventory.setItem(PREVIOUS, button(Material.ARROW, "Previous page"));
        }
        if (current + 1 < pages) {
            inventory.setItem(NEXT, button(Material.ARROW, "Next page"));
        }
        player.openInventory(inventory);
    }

    private void openRecipe(Player player, Holder source, NamespacedKey key) {
        Recipe recipe = requireRecipe(key);
        Holder holder = new Holder(source.page, key, source.keys);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("Recipe: " + key.getKey().replace('_', ' ')));
        holder.inventory = inventory;
        ItemStack result = fallbackModel(recipe.getResult());
        if (key.getKey().equals("nova_shield")) {
            addLore(result, "All four planks must be the same type.");
        } else if (key.getKey().equals("ordinary_string_cobweb")) {
            addLore(result, "Use ordinary string, not Galaxy String.");
        }
        if (recipe instanceof ShapedRecipe shaped) {
            String[] shape = shaped.getShape();
            for (int row = 0; row < shape.length; row++) {
                for (int col = 0; col < shape[row].length(); col++) {
                    char symbol = shape[row].charAt(col);
                    if (symbol == ' ') {
                        continue;
                    }
                    RecipeChoice choice = shaped.getChoiceMap().get(symbol);
                    if (choice == null) {
                        throw new IllegalStateException("Missing ingredient " + symbol + " for " + key);
                    }
                    inventory.setItem(10 + row * 9 + col, ingredient(key, row, col, choice));
                }
            }
        } else {
            addLore(result, "Recipe type is not supported by this guide.");
            plugin.getLogger().warning("Recipe guide cannot display recipe type for " + key);
        }
        inventory.setItem(24, result);
        inventory.setItem(BACK, button(Material.ARROW, "Back to recipes"));
        player.openInventory(inventory);
    }

    ItemStack ingredient(NamespacedKey key, int row, int col, RecipeChoice choice) {
        ItemStack icon;
        String id = key.getKey();
        if (id.equals("celestial_bow") && col == 2) {
            icon = fallbackModel(plugin.getLegendaryItems().createItem("galaxy_string"));
        } else if (id.equals("singularity_boots") && row != 1 && col != 1) {
            icon = fallbackModel(plugin.getLegendaryItems().createItem("corrupt_ender_pearl"));
        } else {
            icon = fallbackModel(choice.getItemStack());
            if (icon.getType() == Material.PLAYER_HEAD) {
                addLore(icon, "Requires a player head dropped by Head Drop.");
            }
            if (id.equals("nova_shield") && choice instanceof RecipeChoice.MaterialChoice materials
                    && materials.getChoices().size() > 1) {
                addLore(icon, "Any plank type; all four must match.");
            }
            if (id.equals("ordinary_string_cobweb")) {
                addLore(icon, "Ordinary string only; Galaxy String does not work.");
            }
        }
        return icon;
    }

    private List<NamespacedKey> recipeKeys() {
        return plugin.getLegendaryItems().getRecipeKeys().stream()
                .sorted(Comparator.comparing(NamespacedKey::asString)).toList();
    }

    private Recipe requireRecipe(NamespacedKey key) {
        Recipe recipe = Bukkit.getRecipe(key);
        if (recipe == null) {
            throw new IllegalStateException("Registered Galaxy recipe is missing: " + key);
        }
        return recipe;
    }

    private static ItemStack fallbackModel(ItemStack original) {
        ItemStack copy = original.clone();
        copy.editMeta(meta -> meta.setItemModel(null));
        return copy;
    }

    private static void addLore(ItemStack item, String text) {
        item.editMeta(meta -> {
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Component.text(text, NamedTextColor.YELLOW));
            meta.lore(lore);
        });
    }

    private static ItemStack button(Material type, String name) {
        ItemStack item = new ItemStack(type);
        item.editMeta(meta -> meta.displayName(Component.text(name, NamedTextColor.GREEN)));
        return item;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getRawSlot();
        if (holder.recipe != null) {
            if (slot == BACK) {
                open(player, holder.page);
            }
        } else if (slot == PREVIOUS && holder.page > 0) {
            open(player, holder.page - 1);
        } else if (slot == NEXT && (holder.page + 1) * PAGE_SIZE < holder.keys.size()) {
            open(player, holder.page + 1);
        } else if (slot >= 0 && slot < PAGE_SIZE) {
            int index = holder.page * PAGE_SIZE + slot;
            if (index < holder.keys.size()) {
                openRecipe(player, holder, holder.keys.get(index));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private static final class Holder implements InventoryHolder {
        private final int page;
        private final NamespacedKey recipe;
        private final List<NamespacedKey> keys;
        private Inventory inventory;

        private Holder(int page, NamespacedKey recipe, List<NamespacedKey> keys) {
            this.page = page;
            this.recipe = recipe;
            this.keys = List.copyOf(keys);
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
