package com.coaxgames.smp.listener;

import com.coaxgames.smp.item.LegendaryItemRegistry;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class DroppedItemClumpListener implements Listener {
    static final int MAX_CLUMP = 128;
    private static final double RADIUS = 2.0;
    private final JavaPlugin plugin;
    private final LegendaryItemRegistry items;
    private final NamespacedKey countKey;

    public DroppedItemClumpListener(JavaPlugin plugin, LegendaryItemRegistry items) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.items = Objects.requireNonNull(items, "items");
        countKey = new NamespacedKey(plugin, "ground-clump-count");
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (var world : plugin.getServer().getWorlds()) {
                for (Item item : world.getEntitiesByClass(Item.class)) {
                    clumpNearby(item);
                }
            }
        }, 1L, 20L);
    }

    void clumpNearby(Item source) {
        if (!source.isValid() || !eligible(source.getItemStack())) {
            return;
        }
        for (var entity : source.getNearbyEntities(RADIUS, RADIUS, RADIUS)) {
            if (entity instanceof Item target && target.isValid()) {
                merge(source, target);
                if (!source.isValid()) {
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMerge(ItemMergeEvent event) {
        Item source = event.getEntity();
        Item target = event.getTarget();
        if (!eligible(source.getItemStack()) || !eligible(target.getItemStack())) {
            return;
        }
        event.setCancelled(true);
        merge(source, target);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        Item item = event.getItem();
        if (!isClump(item)) {
            return;
        }
        event.setCancelled(true);
        if (event.getEntity() instanceof Player player) {
            collect(item, player.getInventory(), MAX_CLUMP);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        if (isClump(event.getItem())) {
            event.setCancelled(true);
            collect(event.getItem(), event.getInventory(), 1);
        }
    }

    private boolean eligible(ItemStack stack) {
        if (stack.getType() == Material.ENDER_PEARL) {
            return !items.isProtectedItem(stack);
        }
        return items.identify(stack)
                .map(item -> !item.protectedFromLossAndStorage()
                        && !item.trackUniqueInstance() && !item.hasPerPlayerCarryLimit())
                .orElse(false);
    }

    private void merge(Item source, Item target) {
        if (!source.isValid() || !target.isValid() || source.equals(target)
                || !eligible(target.getItemStack())
                || !source.getItemStack().isSimilar(target.getItemStack())
                || !Objects.equals(source.getOwner(), target.getOwner())
                || !Objects.equals(source.getThrower(), target.getThrower())
                || source.canMobPickup() != target.canMobPickup()
                || source.canPlayerPickup() != target.canPlayerPickup()
                || source.isUnlimitedLifetime() != target.isUnlimitedLifetime()
                || source.willAge() != target.willAge()
                || source.isInvulnerable() != target.isInvulnerable()
                || source.getHealth() != target.getHealth()
                || !Objects.equals(source.customName(), target.customName())
                || source.isCustomNameVisible() != target.isCustomNameVisible()
                || source.getPersistentDataContainer().getKeys().stream().anyMatch(key -> !key.equals(countKey))
                || target.getPersistentDataContainer().getKeys().stream().anyMatch(key -> !key.equals(countKey))
                || !source.isPersistent() || !target.isPersistent()) {
            return;
        }
        if (source.getItemStack().getAmount() > MAX_CLUMP || target.getItemStack().getAmount() > MAX_CLUMP) {
            plugin.getLogger().warning("Skipped oversized dropped stack while clumping at " + source.getLocation());
            return;
        }
        int sourceCount = count(source);
        int targetCount = count(target);
        int moved = transferable(sourceCount, targetCount);
        if (moved == 0) {
            return;
        }
        setCount(target, targetCount + moved);
        target.setPickupDelay(Math.max(source.getPickupDelay(), target.getPickupDelay()));
        target.setTicksLived(Math.max(source.getTicksLived(), target.getTicksLived()));
        if (moved == sourceCount) {
            source.remove();
        } else {
            setCount(source, sourceCount - moved);
        }
    }

    static int transferable(int source, int target) {
        if (source < 1 || target < 1 || source > MAX_CLUMP || target > MAX_CLUMP) {
            throw new IllegalArgumentException("Invalid ground clump count.");
        }
        return source == MAX_CLUMP ? 0 : Math.min(source, MAX_CLUMP - target);
    }

    private boolean isClump(Item item) {
        return item.getPersistentDataContainer().has(countKey, PersistentDataType.INTEGER);
    }

    private int count(Item item) {
        Integer stored = item.getPersistentDataContainer().get(countKey, PersistentDataType.INTEGER);
        int count = stored == null ? item.getItemStack().getAmount() : stored;
        if (count < 1 || count > MAX_CLUMP || count < item.getItemStack().getAmount()) {
            throw new IllegalStateException("Invalid ground clump count for " + item.getUniqueId() + ": " + count);
        }
        return count;
    }

    private void setCount(Item item, int count) {
        if (count < 1 || count > MAX_CLUMP) {
            throw new IllegalArgumentException("Invalid ground clump count: " + count);
        }
        ItemStack visible = item.getItemStack();
        visible.setAmount(Math.min(count, visible.getMaxStackSize()));
        item.setItemStack(visible);
        item.getPersistentDataContainer().set(countKey, PersistentDataType.INTEGER, count);
    }

    private void collect(Item item, Inventory inventory, int maxItems) {
        int remaining = count(item);
        int requested = Math.min(remaining, maxItems);
        int uncollected = requested;
        while (uncollected > 0) {
            ItemStack stack = item.getItemStack().clone();
            items.refreshCooldownGroup(stack);
            int amount = Math.min(uncollected, stack.getMaxStackSize());
            stack.setAmount(amount);
            int leftovers = inventory.addItem(stack).values().stream().mapToInt(ItemStack::getAmount).sum();
            int added = amount - leftovers;
            remaining -= added;
            uncollected -= added;
            if (remaining == 0) {
                item.remove();
                return;
            }
            if (added > 0) {
                setCount(item, remaining);
            }
            if (leftovers > 0) {
                return;
            }
        }
    }
}
