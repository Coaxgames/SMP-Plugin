package com.coaxgames.smp.storage;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
        data.set(path + ".world", location.getWorld() == null ? null : location.getWorld().getName());
        data.set(path + ".x", location.getX());
        data.set(path + ".y", location.getY());
        data.set(path + ".z", location.getZ());
    }

    private String itemPath(UUID itemUuid) {
        return "items." + itemUuid;
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
}
