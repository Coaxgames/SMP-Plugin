package com.coaxgames.smp.storage;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class LegendaryDataStore {
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9]+(?:_[a-z0-9]+)*");

    private final Path file;
    private final YamlConfiguration data = new YamlConfiguration();

    public LegendaryDataStore(Path dataDirectory) throws IOException, InvalidConfigurationException {
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        Files.createDirectories(dataDirectory);
        file = dataDirectory.resolve("data.yml");
        if (Files.exists(file)) {
            data.load(file.toFile());
        } else {
            save();
        }
    }

    public synchronized int getCraftCount(String itemId) {
        validateId(itemId);
        String path = "crafts." + itemId;
        if (!data.contains(path)) {
            return 0;
        }
        long count = readWholeNumber(path);
        if (count < 0 || count > Integer.MAX_VALUE) {
            throw new IllegalStateException("Invalid craft count for " + itemId);
        }
        return (int) count;
    }

    public synchronized boolean hasBeenCrafted(String itemId) {
        return getCraftCount(itemId) > 0;
    }

    public synchronized boolean tryRecordCraft(String itemId, int maxCrafts) throws IOException {
        validateCraftLimit(itemId, maxCrafts);
        int count = getCraftCount(itemId);
        if (count >= maxCrafts) {
            return false;
        }
        data.set("crafts." + itemId, count + 1);
        save();
        return true;
    }

    public synchronized boolean recordCraftedItem(
            String itemId,
            int maxCrafts,
            UUID itemUuid,
            UUID ownerUuid,
            Location location
    ) throws IOException {
        return recordCraftedItem(itemId, maxCrafts, itemUuid, ownerUuid, location, true);
    }

    public synchronized boolean startRitual(
            String itemId,
            int maxCrafts,
            UUID itemUuid,
            UUID ownerUuid,
            String ownerName,
            String world,
            double x,
            double y,
            double z,
            double crafterX,
            double crafterY,
            double crafterZ,
            long startedAtMillis,
            long completesAtMillis,
            boolean enforceCraftLimit
    ) throws IOException {
        validateCraftLimit(itemId, maxCrafts);
        Objects.requireNonNull(itemUuid, "itemUuid");
        Objects.requireNonNull(world, "world");
        if (world.isBlank() || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Double.isFinite(crafterX) || !Double.isFinite(crafterY) || !Double.isFinite(crafterZ)
                || startedAtMillis < 0 || completesAtMillis <= startedAtMillis) {
            throw new IllegalArgumentException("Invalid ritual location or time range.");
        }
        int count = getCraftCount(itemId);
        if (enforceCraftLimit && count >= maxCrafts) {
            return false;
        }
        String itemPath = itemPath(itemUuid);
        String ritualPath = ritualPath(itemUuid);
        if (data.contains(itemPath + ".id") || data.contains(ritualPath)) {
            throw new IllegalStateException("Ritual item UUID is already tracked: " + itemUuid);
        }

        String craftPath = "crafts." + itemId;
        if (enforceCraftLimit) {
            data.set(craftPath, count + 1);
        }
        data.set(itemPath + ".id", itemId);
        data.set(itemPath + ".owner", ownerUuid == null ? null : ownerUuid.toString());
        setStoredLocation(itemPath, world, x, y, z);
        data.set(ritualPath + ".item_id", itemId);
        data.set(ritualPath + ".item_uuid", itemUuid.toString());
        data.set(ritualPath + ".crafter", ownerUuid == null ? null : ownerUuid.toString());
        data.set(ritualPath + ".crafter_name", ownerName);
        data.set(ritualPath + ".world", world);
        data.set(ritualPath + ".x", x);
        data.set(ritualPath + ".y", y);
        data.set(ritualPath + ".z", z);
        data.set(ritualPath + ".crafter_x", crafterX);
        data.set(ritualPath + ".crafter_y", crafterY);
        data.set(ritualPath + ".crafter_z", crafterZ);
        data.set(ritualPath + ".started_at", startedAtMillis);
        data.set(ritualPath + ".completes_at", completesAtMillis);
        data.set(ritualPath + ".reveals", 0);
        data.set(ritualPath + ".craft_counted", enforceCraftLimit);
        try {
            save();
        } catch (IOException exception) {
            if (enforceCraftLimit) {
                data.set(craftPath, count == 0 ? null : count);
            }
            data.set(itemPath, null);
            data.set(ritualPath, null);
            throw exception;
        }
        return true;
    }

    public synchronized List<RitualRecord> getActiveRituals() {
        ConfigurationSection section = data.getConfigurationSection("rituals");
        if (section == null) {
            return List.of();
        }
        List<RitualRecord> rituals = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            UUID itemUuid;
            try {
                itemUuid = UUID.fromString(key);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Invalid ritual item UUID: " + key, exception);
            }
            String path = ritualPath(itemUuid);
            String itemId = data.getString(path + ".item_id");
            String world = data.getString(path + ".world");
            String uuidText = data.getString(path + ".item_uuid");
            if (itemId == null || world == null || uuidText == null) {
                throw new IllegalStateException("Incomplete persisted ritual record: " + itemUuid);
            }
            validateId(itemId);
            UUID storedUuid;
            try {
                storedUuid = UUID.fromString(uuidText);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Invalid item UUID in ritual record: " + itemUuid, exception);
            }
            if (!storedUuid.equals(itemUuid)) {
                throw new IllegalStateException("Ritual key and item UUID do not match: " + itemUuid);
            }
            rituals.add(new RitualRecord(
                    itemId,
                    itemUuid,
                    parseUuid(data.getString(path + ".crafter")),
                    data.getString(path + ".crafter_name"),
                    world,
                    data.getDouble(path + ".x"),
                    data.getDouble(path + ".y"),
                    data.getDouble(path + ".z"),
                    data.getDouble(path + ".crafter_x"),
                    data.getDouble(path + ".crafter_y"),
                    data.getDouble(path + ".crafter_z"),
                    readWholeNumber(path + ".started_at"),
                    readWholeNumber(path + ".completes_at"),
                    (int) readWholeNumber(path + ".reveals")
            ));
        }
        rituals.sort(Comparator.comparingLong(RitualRecord::startedAtMillis));
        return List.copyOf(rituals);
    }

    public synchronized boolean advanceRitualReveal(UUID itemUuid, int expectedRevealCount) throws IOException {
        Objects.requireNonNull(itemUuid, "itemUuid");
        String path = ritualPath(itemUuid);
        if (!data.contains(path)) {
            return false;
        }
        int currentCount = Math.toIntExact(readWholeNumber(path + ".reveals"));
        if (currentCount != expectedRevealCount) {
            return false;
        }
        data.set(path + ".reveals", currentCount + 1);
        try {
            save();
        } catch (IOException exception) {
            data.set(path + ".reveals", currentCount);
            throw exception;
        }
        return true;
    }

    public synchronized void finishRitual(UUID itemUuid, String world, double x, double y, double z)
            throws IOException {
        Objects.requireNonNull(itemUuid, "itemUuid");
        Objects.requireNonNull(world, "world");
        String path = ritualPath(itemUuid);
        if (!data.contains(path)) {
            return;
        }
        String itemRecordPath = itemPath(itemUuid);
        String itemId = data.getString(itemRecordPath + ".id");
        if (itemId == null || !itemId.equals(data.getString(path + ".item_id"))) {
            throw new IllegalStateException("Ritual has no matching tracked item record: " + itemUuid);
        }
        String previousWorld = data.getString(itemRecordPath + ".world");
        double previousX = data.getDouble(itemRecordPath + ".x");
        double previousY = data.getDouble(itemRecordPath + ".y");
        double previousZ = data.getDouble(itemRecordPath + ".z");
        boolean craftCounted = data.getBoolean(path + ".craft_counted", true);
        RitualRecord previousRitual = getActiveRituals().stream()
                .filter(ritual -> ritual.itemUuid().equals(itemUuid))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing ritual record: " + itemUuid));
        setStoredLocation(itemRecordPath, world, x, y, z);
        data.set(path, null);
        try {
            save();
        } catch (IOException exception) {
            data.set(itemRecordPath + ".world", previousWorld);
            data.set(itemRecordPath + ".x", previousX);
            data.set(itemRecordPath + ".y", previousY);
            data.set(itemRecordPath + ".z", previousZ);
            setRitualRecord(path, previousRitual, craftCounted);
            throw exception;
        }
    }

    public synchronized boolean abortRitual(UUID itemUuid) throws IOException {
        Objects.requireNonNull(itemUuid, "itemUuid");
        String ritualPath = ritualPath(itemUuid);
        if (!data.contains(ritualPath)) {
            return false;
        }
        String itemRecordPath = itemPath(itemUuid);
        String itemId = data.getString(ritualPath + ".item_id");
        if (itemId == null || !itemId.equals(data.getString(itemRecordPath + ".id"))) {
            throw new IllegalStateException("Ritual has no matching tracked item record: " + itemUuid);
        }
        Map<String, Object> previousItem = sectionValues(itemRecordPath);
        Map<String, Object> previousRitual = sectionValues(ritualPath);
        String craftPath = "crafts." + itemId;
        int previousCraftCount = getCraftCount(itemId);
        boolean craftCounted = data.getBoolean(ritualPath + ".craft_counted", true);
        if (craftCounted && previousCraftCount <= 0) {
            throw new IllegalStateException("Cannot abort ritual with no matching craft count: " + itemUuid);
        }
        data.set(itemRecordPath, null);
        data.set(ritualPath, null);
        if (craftCounted) {
            data.set(craftPath, previousCraftCount == 1 ? null : previousCraftCount - 1);
        }
        try {
            save();
        } catch (IOException exception) {
            restoreSection(itemRecordPath, previousItem);
            restoreSection(ritualPath, previousRitual);
            if (craftCounted) {
                data.set(craftPath, previousCraftCount);
            }
            throw exception;
        }
        return true;
    }

    public synchronized boolean hasActiveRitual(UUID itemUuid) {
        Objects.requireNonNull(itemUuid, "itemUuid");
        return data.contains(ritualPath(itemUuid));
    }

    public synchronized void recordReclaimMarker(UUID itemUuid, String world, double x, double y, double z)
            throws IOException {
        Objects.requireNonNull(itemUuid, "itemUuid");
        Objects.requireNonNull(world, "world");
        if (world.isBlank() || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Invalid reclaim-marker location.");
        }
        String itemRecordPath = itemPath(itemUuid);
        if (!data.contains(itemRecordPath + ".id")) {
            throw new IllegalArgumentException("Cannot create a reclaim marker for an untracked item: " + itemUuid);
        }
        String reclaimPath = reclaimPath(itemUuid);
        Map<String, Object> previousReclaim = data.contains(reclaimPath)
                ? sectionValues(reclaimPath) : Map.of();
        String previousWorld = data.getString(itemRecordPath + ".world");
        double previousX = data.getDouble(itemRecordPath + ".x");
        double previousY = data.getDouble(itemRecordPath + ".y");
        double previousZ = data.getDouble(itemRecordPath + ".z");
        setStoredLocation(itemRecordPath, world, x, y, z);
        setStoredLocation(reclaimPath, world, x, y, z);
        try {
            save();
        } catch (IOException exception) {
            data.set(itemRecordPath + ".world", previousWorld);
            data.set(itemRecordPath + ".x", previousX);
            data.set(itemRecordPath + ".y", previousY);
            data.set(itemRecordPath + ".z", previousZ);
            if (previousReclaim.isEmpty()) {
                data.set(reclaimPath, null);
            } else {
                restoreSection(reclaimPath, previousReclaim);
            }
            throw exception;
        }
    }

    public synchronized List<ReclaimMarkerRecord> getReclaimMarkers() {
        ConfigurationSection section = data.getConfigurationSection("reclaims");
        if (section == null) {
            return List.of();
        }
        List<ReclaimMarkerRecord> markers = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            UUID itemUuid;
            try {
                itemUuid = UUID.fromString(key);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Invalid reclaim marker item UUID: " + key, exception);
            }
            String path = reclaimPath(itemUuid);
            String world = data.getString(path + ".world");
            if (world == null || world.isBlank()) {
                throw new IllegalStateException("Missing reclaim marker world for " + itemUuid);
            }
            markers.add(new ReclaimMarkerRecord(
                    itemUuid, world,
                    (int) Math.floor(data.getDouble(path + ".x")),
                    (int) Math.floor(data.getDouble(path + ".y")),
                    (int) Math.floor(data.getDouble(path + ".z"))));
        }
        return List.copyOf(markers);
    }

    public synchronized void clearReclaimMarker(UUID itemUuid) throws IOException {
        Objects.requireNonNull(itemUuid, "itemUuid");
        String path = reclaimPath(itemUuid);
        if (!data.contains(path)) {
            return;
        }
        Object previous = data.get(path);
        data.set(path, null);
        try {
            save();
        } catch (IOException exception) {
            data.set(path, previous);
            throw exception;
        }
    }

    public synchronized boolean recordCraftedItem(
            String itemId,
            int maxCrafts,
            UUID itemUuid,
            UUID ownerUuid,
            Location location,
            boolean enforceCraftLimit
    ) throws IOException {
        validateCraftLimit(itemId, maxCrafts);
        Objects.requireNonNull(itemUuid, "itemUuid");
        Objects.requireNonNull(location, "location");
        int count = getCraftCount(itemId);
        if (enforceCraftLimit && count >= maxCrafts) {
            return false;
        }
        String itemPath = itemPath(itemUuid);
        if (data.contains(itemPath + ".id")) {
            throw new IllegalStateException("Item UUID is already tracked: " + itemUuid);
        }

        String craftPath = "crafts." + itemId;
        if (enforceCraftLimit) {
            data.set(craftPath, count + 1);
        }
        setItemRecord(itemId, itemUuid, ownerUuid, location);
        try {
            save();
        } catch (IOException exception) {
            if (enforceCraftLimit) {
                data.set(craftPath, count == 0 ? null : count);
            }
            data.set(itemPath, null);
            throw exception;
        }
        return true;
    }

    public synchronized void recordAdministrativeGrant(
            String itemId,
            int maxCrafts,
            UUID itemUuid,
            UUID ownerUuid,
            Location location
    ) throws IOException {
        recordAdministrativeGrant(itemId, maxCrafts, itemUuid, ownerUuid, location, true);
    }

    public synchronized void recordAdministrativeGrant(
            String itemId,
            int maxCrafts,
            UUID itemUuid,
            UUID ownerUuid,
            Location location,
            boolean consumeCraftLimit
    ) throws IOException {
        validateCraftLimit(itemId, maxCrafts);
        Objects.requireNonNull(itemUuid, "itemUuid");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(location, "location");
        if (data.contains(itemPath(itemUuid) + ".id")) {
            throw new IllegalStateException("Item UUID is already tracked: " + itemUuid);
        }

        if (consumeCraftLimit) {
            data.set("crafts." + itemId, maxCrafts);
        }
        setItemRecord(itemId, itemUuid, ownerUuid, location);
        save();
    }

    public synchronized void setItemOwner(String itemId, UUID itemUuid, UUID ownerUuid) throws IOException {
        validateId(itemId);
        Objects.requireNonNull(itemUuid, "itemUuid");
        Objects.requireNonNull(ownerUuid, "ownerUuid");

        String path = itemPath(itemUuid);
        validateExistingItemId(itemId, itemUuid);
        data.set(path + ".id", itemId);
        data.set(path + ".owner", ownerUuid.toString());
        save();
    }

    public synchronized void recordItemLocation(UUID itemUuid, Location location) throws IOException {
        Objects.requireNonNull(itemUuid, "itemUuid");
        Objects.requireNonNull(location, "location");
        String path = itemPath(itemUuid);
        if (!data.contains(path + ".id")) {
            throw new IllegalArgumentException("Cannot record a location for an untracked item: " + itemUuid);
        }
        data.set(path + ".world", location.getWorld() == null ? null : location.getWorld().getName());
        data.set(path + ".x", location.getX());
        data.set(path + ".y", location.getY());
        data.set(path + ".z", location.getZ());
        save();
    }

    public synchronized List<ItemRecord> getTrackedItems(String itemId) {
        validateId(itemId);
        ConfigurationSection section = data.getConfigurationSection("items");
        if (section == null) {
            return List.of();
        }

        List<ItemRecord> records = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            UUID itemUuid;
            try {
                itemUuid = UUID.fromString(key);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Invalid item UUID in persistent data: " + key, exception);
            }

            String path = itemPath(itemUuid);
            String trackedItemId = data.getString(path + ".id");
            if (trackedItemId == null) {
                throw new IllegalStateException("Missing item id in persistent data for " + itemUuid);
            }
            validateId(trackedItemId);
            if (!itemId.equals(trackedItemId)) {
                continue;
            }
            records.add(new ItemRecord(
                    itemId,
                    itemUuid,
                    parseUuid(data.getString(path + ".owner")),
                    data.getString(path + ".world"),
                    data.getDouble(path + ".x"),
                    data.getDouble(path + ".y"),
                    data.getDouble(path + ".z")
            ));
        }
        records.sort(Comparator.comparing(record -> record.itemUuid().toString()));
        return List.copyOf(records);
    }

    public synchronized ItemRecord getTrackedItem(UUID itemUuid) {
        Objects.requireNonNull(itemUuid, "itemUuid");
        String path = itemPath(itemUuid);
        String itemId = data.getString(path + ".id");
        if (itemId == null) {
            return null;
        }
        validateId(itemId);
        return new ItemRecord(
                itemId,
                itemUuid,
                parseUuid(data.getString(path + ".owner")),
                data.getString(path + ".world"),
                data.getDouble(path + ".x"),
                data.getDouble(path + ".y"),
                data.getDouble(path + ".z")
        );
    }

    public synchronized long getCooldownExpiry(UUID playerUuid, String cooldownId) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        validateId(cooldownId);
        String path = cooldownPath(playerUuid, cooldownId);
        if (!data.contains(path)) {
            return 0L;
        }
        long expiry = readWholeNumber(path);
        if (expiry < 0) {
            throw new IllegalStateException("Invalid negative cooldown expiry for " + playerUuid + "/" + cooldownId);
        }
        return expiry;
    }

    public synchronized void setCooldownExpiry(UUID playerUuid, String cooldownId, long expiresAtMillis)
            throws IOException {
        Objects.requireNonNull(playerUuid, "playerUuid");
        validateId(cooldownId);
        data.set(cooldownPath(playerUuid, cooldownId), expiresAtMillis);
        save();
    }

    public synchronized void clearCooldown(UUID playerUuid, String cooldownId) throws IOException {
        Objects.requireNonNull(playerUuid, "playerUuid");
        validateId(cooldownId);
        data.set(cooldownPath(playerUuid, cooldownId), null);
        save();
    }

    public synchronized int clearCooldownsForItem(String cooldownId) throws IOException {
        validateId(cooldownId);
        ConfigurationSection players = data.getConfigurationSection("cooldowns");
        if (players == null) {
            return 0;
        }

        int removed = 0;
        for (String playerUuid : List.copyOf(players.getKeys(false))) {
            String path = "cooldowns." + playerUuid + "." + cooldownId;
            if (data.contains(path)) {
                data.set(path, null);
                removed++;
            }
            ConfigurationSection playerCooldowns = data.getConfigurationSection("cooldowns." + playerUuid);
            if (playerCooldowns != null && playerCooldowns.getKeys(false).isEmpty()) {
                data.set("cooldowns." + playerUuid, null);
            }
        }
        if (removed > 0) {
            save();
        }
        return removed;
    }

    public synchronized void save() throws IOException {
        Path temporary = Files.createTempFile(file.getParent(), "data-", ".yml.tmp");
        try {
            data.save(temporary.toFile());
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void setItemRecord(String itemId, UUID itemUuid, UUID ownerUuid, Location location) {
        validateExistingItemId(itemId, itemUuid);
        String path = itemPath(itemUuid);
        data.set(path + ".id", itemId);
        data.set(path + ".owner", ownerUuid == null ? null : ownerUuid.toString());
        setStoredLocation(path, location.getWorld() == null ? null : location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ());
    }

    private void setStoredLocation(String path, String world, double x, double y, double z) {
        data.set(path + ".world", world);
        data.set(path + ".x", x);
        data.set(path + ".y", y);
        data.set(path + ".z", z);
    }

    private void setRitualRecord(String path, RitualRecord ritual, boolean craftCounted) {
        data.set(path + ".item_id", ritual.itemId());
        data.set(path + ".item_uuid", ritual.itemUuid().toString());
        data.set(path + ".crafter", ritual.crafterUuid() == null ? null : ritual.crafterUuid().toString());
        data.set(path + ".crafter_name", ritual.crafterName());
        data.set(path + ".world", ritual.world());
        data.set(path + ".x", ritual.x());
        data.set(path + ".y", ritual.y());
        data.set(path + ".z", ritual.z());
        data.set(path + ".crafter_x", ritual.crafterX());
        data.set(path + ".crafter_y", ritual.crafterY());
        data.set(path + ".crafter_z", ritual.crafterZ());
        data.set(path + ".started_at", ritual.startedAtMillis());
        data.set(path + ".completes_at", ritual.completesAtMillis());
        data.set(path + ".reveals", ritual.reveals());
        data.set(path + ".craft_counted", craftCounted);
    }

    private Map<String, Object> sectionValues(String path) {
        ConfigurationSection section = data.getConfigurationSection(path);
        if (section == null) {
            throw new IllegalStateException("Missing data section: " + path);
        }
        return section.getValues(true);
    }

    private void restoreSection(String path, Map<String, Object> values) {
        data.set(path, null);
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            data.set(path + "." + entry.getKey(), entry.getValue());
        }
    }

    private String itemPath(UUID itemUuid) {
        return "items." + itemUuid;
    }

    private String ritualPath(UUID itemUuid) {
        return "rituals." + itemUuid;
    }

    private String reclaimPath(UUID itemUuid) {
        return "reclaims." + itemUuid;
    }

    private String cooldownPath(UUID playerUuid, String cooldownId) {
        return "cooldowns." + playerUuid + "." + cooldownId;
    }

    private UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid owner UUID in persistent item data: " + value, exception);
        }
    }

    private void validateExistingItemId(String itemId, UUID itemUuid) {
        String existingId = data.getString(itemPath(itemUuid) + ".id");
        if (existingId != null && !existingId.equals(itemId)) {
            throw new IllegalStateException("Item UUID " + itemUuid + " is already tracked as " + existingId);
        }
    }

    private long readWholeNumber(String path) {
        Object value = data.get(path);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("Expected a whole number in persistent data at " + path);
        }
        double numericValue = number.doubleValue();
        if (!Double.isFinite(numericValue) || numericValue != Math.rint(numericValue)) {
            throw new IllegalStateException("Expected a whole number in persistent data at " + path);
        }
        return number.longValue();
    }

    private void validateCraftLimit(String itemId, int maxCrafts) {
        validateId(itemId);
        if (maxCrafts < 1) {
            throw new IllegalArgumentException("maxCrafts must be at least 1.");
        }
    }

    private void validateId(String id) {
        Objects.requireNonNull(id, "id");
        if (!VALID_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid item or cooldown id: " + id);
        }
    }

    public record ItemRecord(
            String itemId,
            UUID itemUuid,
            UUID ownerUuid,
            String world,
            double x,
            double y,
            double z
    ) {
    }

    public record RitualRecord(
            String itemId,
            UUID itemUuid,
            UUID crafterUuid,
            String crafterName,
            String world,
            double x,
            double y,
            double z,
            double crafterX,
            double crafterY,
            double crafterZ,
            long startedAtMillis,
            long completesAtMillis,
            int reveals
    ) {
    }

    public record ReclaimMarkerRecord(UUID itemUuid, String world, int x, int y, int z) {
    }
}
