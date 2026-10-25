package com.coaxgames.smp.listener;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.storage.LegendaryDataStore.ItemRecord;
import com.coaxgames.smp.storage.LegendaryDataStore.ReclaimMarkerRecord;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class ReclaimableItemService {
    private final GalaxySmpPlugin plugin;
    private final NamespacedKey itemUuidKey;

    public ReclaimableItemService(GalaxySmpPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        itemUuidKey = new NamespacedKey(plugin, "reclaim-item-uuid");
    }

    boolean returnToReclaimBlock(Item entity) {
        ItemStack stack = entity.getItemStack();
        String itemId = plugin.getLegendaryItems().identify(stack).map(definition -> definition.id()).orElse(null);
        UUID itemUuid = plugin.getLegendaryItems().getUniqueId(stack).orElse(null);
        if (itemId == null || itemUuid == null || plugin.getDataStore().getTrackedItem(itemUuid) == null) {
            plugin.getLogger().severe("Cannot recover untracked or unidentified legendary drop; leaving it in the world.");
            return false;
        }

        Block target = findReclaimBlock(entity.getWorld());
        if (target == null) {
            plugin.getLogger().severe("No safe space is available for the reclaim barrel at world coordinates (0, 0); leaving the legendary item in the world.");
            return false;
        }

        target.setType(Material.BARREL);
        BlockState state = target.getState();
        if (!(state instanceof Barrel barrel)) {
            target.setType(Material.AIR);
            plugin.getLogger().severe("Could not create a reclaim barrel for legendary item " + itemUuid + ".");
            return false;
        }
        barrel.getPersistentDataContainer().set(itemUuidKey, PersistentDataType.STRING, itemUuid.toString());
        if (!barrel.update(true, false)) {
            target.setType(Material.AIR);
            plugin.getLogger().severe("Could not persist reclaim marker for legendary item " + itemUuid + ".");
            return false;
        }

        try {
            Location targetLocation = target.getLocation();
            plugin.getDataStore().recordReclaimMarker(
                    itemUuid,
                    targetLocation.getWorld().getName(),
                    targetLocation.getBlockX(),
                    targetLocation.getBlockY(),
                    targetLocation.getBlockZ());
        } catch (IOException exception) {
            target.setType(Material.AIR);
            plugin.getLogger().log(Level.SEVERE,
                    "Could not persist reclaim location for legendary item " + itemUuid + "; drop was retained.",
                    exception);
            return false;
        }
        entity.remove();
        return true;
    }

    boolean claim(Block block, Player player) {
        BlockState state = block.getState();
        if (!(state instanceof Barrel barrel)
                || !barrel.getPersistentDataContainer().has(itemUuidKey, PersistentDataType.STRING)) {
            return false;
        }
        String uuidText = barrel.getPersistentDataContainer().get(itemUuidKey, PersistentDataType.STRING);
        UUID itemUuid;
        try {
            itemUuid = UUID.fromString(uuidText);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().log(Level.SEVERE, "Invalid reclaim marker at " + block.getLocation() + ".", exception);
            return true;
        }
        ItemRecord record = plugin.getDataStore().getTrackedItem(itemUuid);
        if (record == null || plugin.getLegendaryItems().get(record.itemId()).isEmpty()) {
            plugin.getLogger().severe("Reclaim marker references an unknown legendary item: " + itemUuid);
            return true;
        }
        if (!plugin.getRitualManager().hasCapacity(player, record.itemId(), 1)) {
            player.sendMessage("You already hold the configured maximum of " + record.itemId() + ".");
            return true;
        }
        ItemStack item = plugin.getLegendaryItems().createItem(record.itemId(), itemUuid);
        barrel.getPersistentDataContainer().remove(itemUuidKey);
        if (!barrel.update(true, false)) {
            plugin.getLogger().severe("Could not clear claimed reclaim marker " + itemUuid + ".");
            return true;
        }
        try {
            plugin.getDataStore().setItemOwner(record.itemId(), itemUuid, player.getUniqueId());
            plugin.getDataStore().recordItemLocation(itemUuid, player.getLocation());
            plugin.getDataStore().clearReclaimMarker(itemUuid);
        } catch (IOException exception) {
            barrel.getPersistentDataContainer().set(itemUuidKey, PersistentDataType.STRING, itemUuid.toString());
            barrel.update(true, false);
            plugin.getLogger().log(Level.SEVERE,
                    "Could not persist reclaimed item ownership for " + itemUuid + ".", exception);
            return true;
        }
        player.getWorld().dropItemNaturally(player.getLocation(), item);
        player.sendMessage("You reclaimed " + record.itemId() + ".");
        return true;
    }

    boolean isReclaimBlock(Block block) {
        BlockState state = block.getState();
        return state instanceof Barrel barrel
                && barrel.getPersistentDataContainer().has(itemUuidKey, PersistentDataType.STRING);
    }

    public void rebuildSpawnBlockMarkers() {
        for (ReclaimMarkerRecord marker : plugin.getDataStore().getReclaimMarkers()) {
            var world = Bukkit.getWorld(marker.world());
            if (world == null) {
                plugin.getLogger().severe("Cannot rebuild reclaim marker for " + marker.itemUuid()
                        + ": world is not loaded: " + marker.world());
                continue;
            }
            if (marker.y() < world.getMinHeight() || marker.y() >= world.getMaxHeight()) {
                plugin.getLogger().severe("Cannot rebuild reclaim marker outside world height for "
                        + marker.itemUuid() + " at " + marker.world() + " (" + marker.x() + ", "
                        + marker.y() + ", " + marker.z() + ").");
                continue;
            }
            var chunk = world.getChunkAt(marker.x() >> 4, marker.z() >> 4);
            Block block = world.getBlockAt(marker.x(), marker.y(), marker.z());
            if (block.getState() instanceof Barrel existing
                    && marker.itemUuid().toString().equals(
                            existing.getPersistentDataContainer().get(itemUuidKey, PersistentDataType.STRING))) {
                removeDroppedDuplicate(chunk.getEntities(), marker.itemUuid());
                continue;
            }
            if (!block.getType().isAir()) {
                plugin.getLogger().severe("Cannot restore reclaim marker for " + marker.itemUuid()
                        + ": saved block location is occupied at " + block.getLocation() + ".");
                continue;
            }
            block.setType(Material.BARREL);
            BlockState state = block.getState();
            if (!(state instanceof Barrel barrel)) {
                block.setType(Material.AIR);
                plugin.getLogger().severe("Could not rebuild reclaim barrel for " + marker.itemUuid() + ".");
                continue;
            }
            barrel.getPersistentDataContainer().set(itemUuidKey, PersistentDataType.STRING,
                    marker.itemUuid().toString());
            if (!barrel.update(true, false)) {
                block.setType(Material.AIR);
                plugin.getLogger().severe("Could not persist rebuilt reclaim marker for "
                        + marker.itemUuid() + ".");
                continue;
            }
            removeDroppedDuplicate(chunk.getEntities(), marker.itemUuid());
        }
    }

    private void removeDroppedDuplicate(org.bukkit.entity.Entity[] entities, UUID itemUuid) {
        for (org.bukkit.entity.Entity entity : entities) {
            if (entity instanceof Item item
                    && plugin.getLegendaryItems().getUniqueId(item.getItemStack())
                            .filter(itemUuid::equals).isPresent()) {
                item.remove();
            }
        }
    }

    private Block findReclaimBlock(org.bukkit.World world) {
        Block surface = world.getHighestBlockAt(0, 0);
        for (int y = Math.max(surface.getY() + 1, world.getMinHeight()); y < world.getMaxHeight(); y++) {
            Block candidate = world.getBlockAt(0, y, 0);
            if (candidate.getType().isAir()) {
                return candidate;
            }
        }
        return null;
    }
}
