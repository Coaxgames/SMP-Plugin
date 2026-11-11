package com.coaxgames.smp.listener;

import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class EnderPearlStackListener implements Listener {
    public static final int STACK_LIMIT = 8;
    private final JavaPlugin plugin;
    private final Set<UUID> pendingPlayers = new HashSet<>();

    public EnderPearlStackListener(JavaPlugin plugin) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLootGenerate(LootGenerateEvent event) {
        List<ItemStack> loot = new ArrayList<>();
        boolean hasPearls = false;
        for (ItemStack stack : event.getLoot()) {
            hasPearls |= stack.getType() == Material.ENDER_PEARL;
            loot.addAll(limitPearlStack(stack));
        }
        if (hasPearls) {
            event.setLoot(loot);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        ItemStack stack = event.getEntity().getItemStack();
        if (stack.getType() != Material.ENDER_PEARL) {
            return;
        }
        event.getEntity().setItemStack(withPearlLimit(stack));
        if (stack.getAmount() > STACK_LIMIT) {
            Item item = event.getEntity();
            plugin.getServer().getScheduler().runTask(plugin, () -> splitDroppedPearls(item));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInventoryChange(PlayerInventorySlotChangeEvent event) {
        ItemStack stack = event.getNewItemStack();
        if (stack == null || stack.getType() != Material.ENDER_PEARL
                || stack.getAmount() <= STACK_LIMIT && stack.getMaxStackSize() == STACK_LIMIT) {
            return;
        }
        Player player = event.getPlayer();
        if (pendingPlayers.add(player.getUniqueId())) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                pendingPlayers.remove(player.getUniqueId());
                if (player.isOnline()) {
                    normalizePlayerPearls(player);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreativeItem(InventoryCreativeEvent event) {
        ItemStack cursor = event.getCursor();
        if (cursor != null && cursor.getType() == Material.ENDER_PEARL) {
            if (cursor.getAmount() > STACK_LIMIT) {
                event.setCancelled(true);
                event.getWhoClicked().sendMessage("Ender Pearls can only be placed in stacks of 8.");
            } else {
                event.setCursor(withPearlLimit(cursor));
            }
        }
    }

    private static List<ItemStack> limitPearlStack(ItemStack stack) {
        if (stack.getType() != Material.ENDER_PEARL) {
            return List.of(stack);
        }
        List<ItemStack> result = new ArrayList<>();
        for (int amount : splitAmounts(stack.getAmount())) {
            ItemStack part = withPearlLimit(stack);
            part.setAmount(amount);
            result.add(part);
        }
        return result;
    }

    static List<Integer> splitAmounts(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Pearl amount cannot be negative.");
        }
        List<Integer> amounts = new ArrayList<>();
        while (amount > 0) {
            int part = Math.min(amount, STACK_LIMIT);
            amounts.add(part);
            amount -= part;
        }
        return amounts;
    }

    private static ItemStack withPearlLimit(ItemStack stack) {
        ItemStack result = stack.clone();
        var meta = result.getItemMeta();
        meta.setMaxStackSize(STACK_LIMIT);
        if (!result.setItemMeta(meta)) {
            throw new IllegalStateException("Could not set Ender Pearl stack size.");
        }
        return result;
    }

    private void normalizePlayerPearls(Player player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType() != Material.ENDER_PEARL) {
                continue;
            }
            stack = withPearlLimit(stack);
            while (stack.getAmount() > STACK_LIMIT) {
                int amount = Math.min(STACK_LIMIT, stack.getAmount() - STACK_LIMIT);
                ItemStack part = stack.clone();
                part.setAmount(amount);
                int empty = inventory.firstEmpty();
                if (empty >= 0) {
                    inventory.setItem(empty, part);
                } else {
                    Item dropped = player.getWorld().dropItem(player.getLocation(), part);
                    if (!dropped.isValid()) {
                        plugin.getLogger().severe("Could not split Ender Pearls for " + player.getName()
                                + "; the excess remains in the original stack.");
                        break;
                    }
                }
                stack.setAmount(stack.getAmount() - amount);
                inventory.setItem(slot, stack);
            }
            inventory.setItem(slot, stack);
        }
    }

    private void splitDroppedPearls(Item item) {
        if (!item.isValid()) {
            return;
        }
        ItemStack stack = item.getItemStack();
        while (stack.getType() == Material.ENDER_PEARL && stack.getAmount() > STACK_LIMIT) {
            int amount = Math.min(STACK_LIMIT, stack.getAmount() - STACK_LIMIT);
            ItemStack part = stack.clone();
            part.setAmount(amount);
            Item dropped = item.getWorld().dropItem(item.getLocation(), part);
            if (!dropped.isValid()) {
                plugin.getLogger().severe("Could not split dropped Ender Pearls at " + item.getLocation()
                        + "; the excess remains in the original stack.");
                return;
            }
            stack.setAmount(stack.getAmount() - amount);
            item.setItemStack(stack);
        }
    }
}
