package com.coaxgames.smp.command;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.item.LegendaryItem;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** Op-only chest menu listing every legendary item; clicking grants it through /galaxy give. */
public final class LegendaryMenu implements Listener {
    private static final String PERMISSION = "galaxy.admin";
    private static final int PAGE_SIZE = 45;
    private static final int PREVIOUS_SLOT = 45;
    private static final int NEXT_SLOT = 53;
    private static final int SHIFT_COUNT = 8;

    private final GalaxySmpPlugin plugin;

    public LegendaryMenu(GalaxySmpPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public void open(Player player, int page) {
        List<LegendaryItem> items = List.copyOf(plugin.getLegendaryItems().getAll());
        int pages = Math.max(1, (items.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.max(0, Math.min(page, pages - 1));

        Holder holder = new Holder(current);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("Galaxy Items (" + (current + 1) + "/" + pages + ")"));
        holder.inventory = inventory;

        int start = current * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < items.size(); i++) {
            LegendaryItem definition = items.get(start + i);
            ItemStack display = plugin.getLegendaryItems().createItem(definition.id());
            display.editMeta(meta -> {
                List<Component> lore = meta.lore() == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(meta.lore());
                lore.add(Component.empty());
                lore.add(Component.text("Click: give 1", NamedTextColor.YELLOW));
                lore.add(Component.text("Shift-click: give " + SHIFT_COUNT, NamedTextColor.YELLOW));
                meta.lore(lore);
            });
            inventory.setItem(i, display);
        }
        if (current > 0) {
            inventory.setItem(PREVIOUS_SLOT, navigation("Previous page"));
        }
        if (current < pages - 1) {
            inventory.setItem(NEXT_SLOT, navigation("Next page"));
        }
        player.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.hasPermission(PERMISSION)) {
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        int slot = event.getSlot();
        if (slot == PREVIOUS_SLOT && event.getCurrentItem() != null) {
            open(player, holder.page - 1);
            return;
        }
        if (slot == NEXT_SLOT && event.getCurrentItem() != null) {
            open(player, holder.page + 1);
            return;
        }
        if (slot >= PAGE_SIZE) {
            return;
        }

        List<LegendaryItem> items = List.copyOf(plugin.getLegendaryItems().getAll());
        int index = holder.page * PAGE_SIZE + slot;
        if (index >= items.size()) {
            return;
        }
        int count = event.isShiftClick() ? SHIFT_COUNT : 1;
        player.performCommand("galaxy give " + player.getName() + " " + items.get(index).id() + " " + count);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private static ItemStack navigation(String name) {
        ItemStack arrow = new ItemStack(Material.ARROW);
        arrow.editMeta(meta -> meta.displayName(Component.text(name, NamedTextColor.GREEN)));
        return arrow;
    }

    private static final class Holder implements InventoryHolder {
        private final int page;
        private Inventory inventory;

        private Holder(int page) {
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
