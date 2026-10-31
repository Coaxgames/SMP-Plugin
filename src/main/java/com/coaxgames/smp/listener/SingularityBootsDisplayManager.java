package com.coaxgames.smp.listener;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.item.LegendaryItem;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemDisplay.ItemDisplayTransform;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class SingularityBootsDisplayManager {
    private static final String ITEM_ID = "singularity_boots";
    private static final double DISPLAY_HEIGHT = 0.28;
    private static final NamespacedKey WORN_MODEL =
            Objects.requireNonNull(NamespacedKey.fromString("galaxy:singularity_boots_worn"));

    private final GalaxySmpPlugin plugin;
    private final Map<UUID, ItemDisplay> displays = new HashMap<>();
    private final Set<UUID> maskedBoots = new HashSet<>();

    public SingularityBootsDisplayManager(GalaxySmpPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            if (!isWearingSingularityBoots(player)) {
                removeDisplay(playerId);
                restoreBoots(player);
                continue;
            }

            ItemDisplay display = displays.get(playerId);
            boolean respawned = display == null || !display.isValid() || display.getWorld() != player.getWorld();
            if (respawned) {
                removeDisplay(playerId);
                display = spawnDisplay(player);
                displays.put(playerId, display);
            }
            maskBoots(player, respawned);
            moveDisplay(player, display);
        }

        Iterator<Map.Entry<UUID, ItemDisplay>> iterator = displays.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ItemDisplay> entry = iterator.next();
            if (Bukkit.getPlayer(entry.getKey()) == null) {
                entry.getValue().remove();
                maskedBoots.remove(entry.getKey());
                iterator.remove();
            }
        }
    }

    private boolean isWearingSingularityBoots(Player player) {
        ItemStack boots = player.getInventory().getBoots();
        if (boots == null || boots.getType().isAir()) {
            return false;
        }
        return plugin.getLegendaryItems().identify(boots)
                .map(LegendaryItem::id)
                .filter(ITEM_ID::equals)
                .isPresent();
    }

    private ItemDisplay spawnDisplay(Player player) {
        ItemStack modelItem = new ItemStack(Material.PAPER);
        ItemMeta meta = modelItem.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Could not create metadata for the Singularity Boots display.");
        }
        meta.setItemModel(WORN_MODEL);
        if (!modelItem.setItemMeta(meta)) {
            throw new IllegalStateException("Could not apply the Singularity Boots worn model.");
        }

        Location displayLocation = displayLocation(player);
        ItemDisplay display = player.getWorld().spawn(displayLocation, ItemDisplay.class);
        display.setItemStack(modelItem);
        display.setItemDisplayTransform(ItemDisplayTransform.FIXED);
        display.setRotation(player.getLocation().getYaw(), 0.0F);
        display.setPersistent(false);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.setSilent(true);
        return display;
    }

    private void moveDisplay(Player player, ItemDisplay display) {
        Location target = displayLocation(player);
        Location current = display.getLocation();
        if (current.getWorld() != target.getWorld() || current.distanceSquared(target) > 0.0001) {
            display.teleport(target);
        }
        if (Float.compare(current.getYaw(), target.getYaw()) != 0) {
            display.setRotation(target.getYaw(), 0.0F);
        }
    }

    private Location displayLocation(Player player) {
        Location location = player.getLocation();
        location.add(0.0, DISPLAY_HEIGHT, 0.0);
        return location;
    }

    private void maskBoots(Player player, boolean refresh) {
        boolean newlyMasked = maskedBoots.add(player.getUniqueId());
        if (newlyMasked || refresh) {
            sendBootsToViewers(player, new ItemStack(Material.AIR));
        }
    }

    private void restoreBoots(Player player) {
        if (maskedBoots.remove(player.getUniqueId())) {
            sendBootsToViewers(player, bootsOrAir(player));
        }
    }

    private ItemStack bootsOrAir(Player player) {
        ItemStack boots = player.getInventory().getBoots();
        return boots == null ? new ItemStack(Material.AIR) : boots.clone();
    }

    private void sendBootsToViewers(Player wearer, ItemStack boots) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.sendEquipmentChange(wearer, EquipmentSlot.FEET, boots);
        }
    }

    private void removeDisplay(UUID playerId) {
        ItemDisplay display = displays.remove(playerId);
        if (display != null) {
            display.remove();
        }
    }

    public void shutdown() {
        for (Map.Entry<UUID, ItemDisplay> entry : displays.entrySet()) {
            entry.getValue().remove();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && maskedBoots.remove(entry.getKey())) {
                sendBootsToViewers(player, bootsOrAir(player));
            }
        }
        displays.clear();
        maskedBoots.clear();
    }
}
