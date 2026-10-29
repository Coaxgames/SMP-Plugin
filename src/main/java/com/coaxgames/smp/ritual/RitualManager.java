package com.coaxgames.smp.ritual;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.item.LegendaryItem;
import com.coaxgames.smp.storage.LegendaryDataStore;
import com.coaxgames.smp.storage.LegendaryDataStore.RitualRecord;
import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

public final class RitualManager {
    private static final long TICKS_PER_SECOND = 20L;
    private static final long MILLIS_PER_SECOND = 1_000L;
    private static final long DEFAULT_DURATION_SECONDS = 3 * 60L;
    private static final long DEFAULT_REVEAL_INTERVAL_SECONDS = 3 * 60L;

    private final GalaxySmpPlugin plugin;
    private final Set<UUID> capacityWarnings = new HashSet<>();
    private final Set<String> missingDefinitionsWarned = new HashSet<>();
    private final Set<UUID> completionWarnings = new HashSet<>();
    private final java.util.Map<UUID, BossBar> bossBars = new java.util.HashMap<>();
    private BukkitTask task;

    public RitualManager(GalaxySmpPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public void resume() {
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 10L);
        }
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        bossBars.values().forEach(BossBar::removeAll);
        bossBars.clear();
    }

    public boolean hasCapacity(Player player, String itemId, int additionalItems) {
        return hasCapacity(player, itemId, additionalItems, null);
    }

    public boolean hasCapacityForDelivery(Player player, String itemId, UUID ritualItemUuid) {
        return hasCapacity(player, itemId, 0, ritualItemUuid);
    }

    public boolean isPending(UUID ritualItemUuid) {
        return plugin.getDataStore().hasActiveRitual(ritualItemUuid);
    }

    public Optional<UUID> start(LegendaryItem item, Player crafter, Location ritualLocation) throws IOException {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(ritualLocation, "ritualLocation");
        if (ritualLocation.getWorld() == null) {
            throw new IllegalArgumentException("Ritual location must have a world.");
        }
        if (crafter != null && !hasCapacity(crafter, item.id(), 1)) {
            return Optional.empty();
        }
        long now = System.currentTimeMillis();
        long durationMillis = configuredSeconds("rituals.duration-seconds", DEFAULT_DURATION_SECONDS)
                * MILLIS_PER_SECOND;
        long completesAt = Math.addExact(now, durationMillis);
        UUID ownerUuid = crafter == null ? null : crafter.getUniqueId();
        String ownerName = crafter == null ? "Crafter" : crafter.getName();
        Location crafterLocation = crafter == null ? ritualLocation : crafter.getLocation();
        UUID itemUuid = UUID.randomUUID();
        boolean started = plugin.getDataStore().startRitual(
                item.id(),
                item.maxCrafts(),
                itemUuid,
                ownerUuid,
                ownerName,
                ritualLocation.getWorld().getName(),
                ritualLocation.getX(),
                ritualLocation.getY(),
                ritualLocation.getZ(),
                crafterLocation.getX(),
                crafterLocation.getY(),
                crafterLocation.getZ(),
                now,
                completesAt,
                item.craftOnce());
        if (started && task == null) {
            resume();
        }
        return started ? Optional.of(itemUuid) : Optional.empty();
    }

    private boolean hasCapacity(Player player, String itemId, int additionalItems, UUID excludedRitual) {
        int limit = plugin.getLegendaryItems().maxPerPlayer(itemId);
        if (limit == 0) {
            return true;
        }
        int reserved = 0;
        for (RitualRecord ritual : plugin.getDataStore().getActiveRituals()) {
            if (itemId.equals(ritual.itemId()) && player.getUniqueId().equals(ritual.crafterUuid())
                    && !ritual.itemUuid().equals(excludedRitual)) {
                reserved++;
            }
        }
        return plugin.getLegendaryItems().countForPlayer(player, itemId) + reserved + additionalItems <= limit;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        long revealInterval = configuredSeconds("rituals.reveal-interval-seconds", DEFAULT_REVEAL_INTERVAL_SECONDS)
                * MILLIS_PER_SECOND;
        for (RitualRecord ritual : plugin.getDataStore().getActiveRituals()) {
            updateBossBar(ritual, now);
            World world = Bukkit.getWorld(ritual.world());
            if (world == null) {
                continue;
            }
            if (now >= ritual.completesAtMillis()) {
                complete(ritual, world);
                continue;
            }
            animate(ritual, world);
            long nextRevealAt = ritual.startedAtMillis() + (ritual.reveals() + 1L) * revealInterval;
            if (now >= nextRevealAt) {
                reveal(ritual);
            }
        }
    }

    private void animate(RitualRecord ritual, World world) {
        int blockX = Location.locToBlock(ritual.x());
        int blockY = Location.locToBlock(ritual.y());
        int blockZ = Location.locToBlock(ritual.z());
        if (!world.isChunkLoaded(blockX >> 4, blockZ >> 4)) {
            return;
        }
        double centerX = blockX + 0.5;
        double centerY = blockY + 1.25;
        double centerZ = blockZ + 0.5;
        double angle = (System.currentTimeMillis() % 4_000L) * Math.PI / 2_000.0;
        for (int index = 0; index < 8; index++) {
            double point = angle + index * Math.PI / 4.0;
            world.spawnParticle(Particle.PORTAL,
                    centerX + Math.cos(point) * 0.65,
                    centerY + Math.sin(point * 2.0) * 0.12,
                    centerZ + Math.sin(point) * 0.65,
                    2, 0.03, 0.03, 0.03, 0.01);
        }
        world.spawnParticle(Particle.END_ROD, centerX, centerY, centerZ, 1, 0.08, 0.08, 0.08, 0.0);
    }

    private void reveal(RitualRecord ritual) {
        String path = "rituals.reveal-message";
        String message = plugin.getConfig().getString(path,
                "{item} crafted by {crafter} at ({x}, {y}, {z})");
        String formatted = message
                .replace("{crafter}", ritual.crafterName() == null ? "Crafter" : ritual.crafterName())
                .replace("{x}", formatCoordinate(ritual.crafterX()))
                .replace("{y}", formatCoordinate(ritual.crafterY()))
                .replace("{z}", formatCoordinate(ritual.crafterZ()));
        Component itemName = plugin.getLegendaryItems().displayName(ritual.itemId());
        Component announcement = replaceItemPlaceholder(formatted, itemName);
        try {
            if (!plugin.getDataStore().advanceRitualReveal(ritual.itemUuid(), ritual.reveals())) {
                return;
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Could not persist ritual reveal progress for " + ritual.itemUuid()
                            + "; this reveal was withheld to prevent repeated announcements.", exception);
            return;
        }
        Bukkit.broadcast(announcement);
    }

    private Component replaceItemPlaceholder(String message, Component itemName) {
        Component result = Component.empty();
        int index = 0;
        int placeholder;
        while ((placeholder = message.indexOf("{item}", index)) >= 0) {
            result = result.append(Component.text(message.substring(index, placeholder))).append(itemName);
            index = placeholder + "{item}".length();
        }
        return result.append(Component.text(message.substring(index)));
    }

    private void complete(RitualRecord ritual, World world) {
        if (plugin.getLegendaryItems().get(ritual.itemId()).isEmpty()) {
            if (missingDefinitionsWarned.add(ritual.itemId())) {
                plugin.getLogger().severe("Cannot complete persisted ritual for unregistered item "
                        + ritual.itemId() + "; keeping it pending until the item definition is registered.");
            }
            return;
        }
        Player owner = ritual.crafterUuid() == null ? null : Bukkit.getPlayer(ritual.crafterUuid());
        int blockX = Location.locToBlock(ritual.x());
        int blockY = Location.locToBlock(ritual.y());
        int blockZ = Location.locToBlock(ritual.z());
        var chunk = world.getChunkAt(blockX >> 4, blockZ >> 4);
        Item existing = findRitualItem(chunk.getEntities(), ritual);
        if (existing != null) {
            finish(ritual, existing);
            return;
        }
        if (owner != null && !hasCapacityForDelivery(owner, ritual.itemId(), ritual.itemUuid())) {
            if (capacityWarnings.add(ritual.itemUuid())) {
                owner.sendMessage("Your ritual for " + ritual.itemId()
                        + " is complete, but delivery is waiting until you have room for that item.");
            }
            return;
        }
        Location dropLocation = new Location(world, blockX + 0.5, blockY + 1.0, blockZ + 0.5);
        ItemStack item = plugin.getLegendaryItems().createItem(ritual.itemId(), ritual.itemUuid());
        Item dropped = world.dropItem(dropLocation, item);
        try {
            plugin.getDataStore().finishRitual(
                    ritual.itemUuid(),
                    world.getName(),
                    dropped.getLocation().getX(),
                    dropped.getLocation().getY(),
                    dropped.getLocation().getZ());
            capacityWarnings.remove(ritual.itemUuid());
            completionWarnings.remove(ritual.itemUuid());
            removeBossBar(ritual.itemUuid());
            announceCompletion(ritual);
        } catch (IOException exception) {
            dropped.setPickupDelay(Integer.MAX_VALUE);
            if (completionWarnings.add(ritual.itemUuid())) {
                plugin.getLogger().log(Level.SEVERE,
                        "Could not persist completion for ritual " + ritual.itemUuid()
                                + "; will reconcile the dropped item before retrying.", exception);
            }
        }
    }

    private Item findRitualItem(org.bukkit.entity.Entity[] entities, RitualRecord ritual) {
        for (org.bukkit.entity.Entity entity : entities) {
            if (!(entity instanceof Item item)) {
                continue;
            }
            if (plugin.getLegendaryItems().getUniqueId(item.getItemStack())
                    .filter(ritual.itemUuid()::equals).isPresent()) {
                return item;
            }
        }
        return null;
    }

    private void finish(RitualRecord ritual, Item item) {
        Location location = item.getLocation();
        try {
            plugin.getDataStore().finishRitual(
                    ritual.itemUuid(),
                    location.getWorld().getName(),
                    location.getX(),
                    location.getY(),
                    location.getZ());
            capacityWarnings.remove(ritual.itemUuid());
            completionWarnings.remove(ritual.itemUuid());
            item.setPickupDelay(0);
            removeBossBar(ritual.itemUuid());
            announceCompletion(ritual);
        } catch (IOException exception) {
            if (completionWarnings.add(ritual.itemUuid())) {
                plugin.getLogger().log(Level.SEVERE,
                        "Could not reconcile already-dropped ritual item " + ritual.itemUuid() + ".", exception);
            }
        }
    }

    private void updateBossBar(RitualRecord ritual, long now) {
        if (ritual.crafterUuid() == null) {
            return;
        }
        Player crafter = Bukkit.getPlayer(ritual.crafterUuid());
        BossBar bossBar = bossBars.get(ritual.itemUuid());
        if (crafter == null || !crafter.isOnline()) {
            if (bossBar != null) {
                bossBar.removeAll();
            }
            return;
        }
        if (bossBar == null) {
            bossBar = Bukkit.createBossBar(
                    "Ritual: " + PlainTextComponentSerializer.plainText()
                            .serialize(plugin.getLegendaryItems().displayName(ritual.itemId())),
                    BarColor.PURPLE,
                    BarStyle.SEGMENTED_10);
            bossBars.put(ritual.itemUuid(), bossBar);
        }
        if (!bossBar.getPlayers().contains(crafter)) {
            bossBar.addPlayer(crafter);
        }
        long duration = Math.max(1L, ritual.completesAtMillis() - ritual.startedAtMillis());
        double progress = 1.0 - (double) (ritual.completesAtMillis() - now) / duration;
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
    }

    private void removeBossBar(UUID ritualItemUuid) {
        BossBar bossBar = bossBars.remove(ritualItemUuid);
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    private void announceCompletion(RitualRecord ritual) {
        Bukkit.broadcast(Component.text("The ritual is complete: ")
                .append(plugin.getLegendaryItems().displayName(ritual.itemId())));
    }

    private long configuredSeconds(String path, long defaultValue) {
        long value = plugin.getConfig().getLong(path, defaultValue);
        if (value <= 0) {
            throw new IllegalStateException(path + " must be a positive number of seconds.");
        }
        return value;
    }

    private String formatCoordinate(double coordinate) {
        return String.format(java.util.Locale.ROOT, "%.1f", coordinate);
    }
}
