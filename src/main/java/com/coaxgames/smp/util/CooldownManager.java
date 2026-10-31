package com.coaxgames.smp.util;

import com.coaxgames.smp.item.LegendaryItem;
import com.coaxgames.smp.item.LegendaryItemRegistry;
import com.coaxgames.smp.storage.LegendaryDataStore;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.configuration.file.FileConfiguration;

public final class CooldownManager {
    private final FileConfiguration config;
    private final LegendaryDataStore dataStore;
    private final LegendaryItemRegistry items;

    public CooldownManager(
            FileConfiguration config,
            LegendaryDataStore dataStore,
            LegendaryItemRegistry items
    ) {
        this.config = Objects.requireNonNull(config, "config");
        this.dataStore = Objects.requireNonNull(dataStore, "dataStore");
        this.items = Objects.requireNonNull(items, "items");
    }

    public Duration getConfiguredDuration(String itemId) {
        LegendaryItem item = items.get(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown legendary item: " + itemId));
        long defaultSeconds = item.cooldown().map(Duration::toSeconds)
                .orElse(readSeconds("cooldowns.default-seconds", 0L));
        long seconds = readSeconds("cooldowns." + itemId, defaultSeconds);
        return Duration.ofSeconds(seconds);
    }

    public boolean isOnCooldown(UUID playerUuid, String itemId) {
        long expiresAtMillis = dataStore.getCooldownExpiry(playerUuid, itemId);
        return expiresAtMillis > System.currentTimeMillis();
    }

    public Duration getRemaining(UUID playerUuid, String itemId) {
        long remainingMillis = dataStore.getCooldownExpiry(playerUuid, itemId) - System.currentTimeMillis();
        return Duration.ofMillis(Math.max(0L, remainingMillis));
    }

    public boolean start(UUID playerUuid, String itemId) throws IOException {
        return start(playerUuid, itemId, getConfiguredDuration(itemId));
    }

    public boolean start(UUID playerUuid, String itemId, Duration duration) throws IOException {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("Cooldown duration cannot be negative.");
        }
        long durationMillis = duration.toMillis();
        if (durationMillis <= 0 || isOnCooldown(playerUuid, itemId)) {
            return false;
        }
        long expiresAtMillis = Math.addExact(System.currentTimeMillis(), durationMillis);
        dataStore.setCooldownExpiry(playerUuid, itemId, expiresAtMillis);
        return true;
    }

    public void clear(UUID playerUuid, String itemId) throws IOException {
        dataStore.clearCooldown(playerUuid, itemId);
    }

    public int clearForItem(String itemId) throws IOException {
        return dataStore.clearCooldownsForItem(itemId);
    }

    public int clearAll(UUID playerUuid) throws IOException {
        return dataStore.clearCooldownsForPlayer(playerUuid);
    }

    private long readSeconds(String path, long fallback) {
        if (!config.contains(path)) {
            return fallback;
        }
        Object value = config.get(path);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("Configured cooldown must be a whole number of seconds: " + path);
        }
        double numericValue = number.doubleValue();
        if (!Double.isFinite(numericValue) || numericValue != Math.rint(numericValue)) {
            throw new IllegalStateException("Configured cooldown must be a whole number of seconds: " + path);
        }
        long seconds = number.longValue();
        if (seconds < 0 || seconds > Long.MAX_VALUE / 1_000) {
            throw new IllegalStateException("Configured cooldown is outside the supported range: " + path);
        }
        return seconds;
    }
}
