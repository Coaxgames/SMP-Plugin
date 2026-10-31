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
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class SingularityBootsDisplayManager {
    private static final String ITEM_ID = "singularity_boots";
    // The worn model's fixed display is 0.5 scale (a 0.33 block pair of boots); 1.6x brings it to foot size.
    private static final float DISPLAY_SCALE = 1.6F;
    private static final double DISPLAY_HEIGHT = 0.4;
    private static final NamespacedKey WORN_MODEL =
            Objects.requireNonNull(NamespacedKey.fromString("galaxy:singularity_boots_worn"));

    private final GalaxySmpPlugin plugin;
    private final Map<UUID, ItemDisplay> displays = new HashMap<>();

    public SingularityBootsDisplayManager(GalaxySmpPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            if (!isWearingSingularityBoots(player)) {
                removeDisplay(playerId);
                continue;
            }

            ItemDisplay display = displays.get(playerId);
            if (display == null || !display.isValid() || display.getWorld() != player.getWorld()) {
                removeDisplay(playerId);
                display = spawnDisplay(player);
                displays.put(playerId, display);
            }
            moveDisplay(player, display);
        }

        Iterator<Map.Entry<UUID, ItemDisplay>> iterator = displays.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ItemDisplay> entry = iterator.next();
            if (Bukkit.getPlayer(entry.getKey()) == null) {
                entry.getValue().remove();
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
        display.setTransformation(new Transformation(
                new Vector3f(), new Quaternionf(), new Vector3f(DISPLAY_SCALE), new Quaternionf()));
        display.setRotation(player.getLocation().getYaw(), 0.0F);
        display.setPersistent(false);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.setSilent(true);
        display.setTeleportDuration(1);
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

    private void removeDisplay(UUID playerId) {
        ItemDisplay display = displays.remove(playerId);
        if (display != null) {
            display.remove();
        }
    }

    public void shutdown() {
        for (ItemDisplay display : displays.values()) {
            display.remove();
        }
        displays.clear();
    }
}
