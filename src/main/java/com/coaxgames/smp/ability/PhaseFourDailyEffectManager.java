package com.coaxgames.smp.ability;

import com.coaxgames.smp.GalaxySmpPlugin;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public final class PhaseFourDailyEffectManager {
    private static final String SOLSTICE = "solstice_chestplate";
    private static final String LUNAR = "lunar_leggings";
    private static final long RETRY_MILLIS = 5_000L;
    private static final long WARN_INTERVAL_MILLIS = 60_000L;

    private final GalaxySmpPlugin plugin;
    private final Map<EffectKey, Failure> failures = new HashMap<>();
    private BukkitTask task;

    public PhaseFourDailyEffectManager(GalaxySmpPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public void start() throws IOException {
        if (task != null) {
            return;
        }
        plugin.getDataStore().resumeActiveEffects(System.currentTimeMillis());
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        failures.clear();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        long solsticeTick = plugin.getConfig().getLong("phase-four.solstice-chestplate.trigger-tick", 6_000L);
        long lunarTick = plugin.getConfig().getLong("phase-four.daily-trigger-tick", 13_500L);
        long durationMillis = Math.multiplyExact(
                plugin.getConfig().getLong("phase-four.daily-effect-seconds", 300L), 1_000L);
        boolean catchUp = plugin.getConfig().getBoolean("phase-four.lunar-leggings.catch-up-if-offline", false);
        var onlinePlayers = Bukkit.getOnlinePlayers();
        if (!failures.isEmpty()) {
            var onlineIds = onlinePlayers.stream().map(Player::getUniqueId).collect(java.util.stream.Collectors.toSet());
            failures.keySet().removeIf(key -> !onlineIds.contains(key.player()));
        }
        for (Player player : onlinePlayers) {
            restoreActiveEffect(player, SOLSTICE, now);
            restoreActiveEffect(player, LUNAR, now);
            long worldTime = player.getWorld().getTime();
            long worldDay = player.getWorld().getFullTime() / 24_000L;
            if (inTriggerWindow(worldTime, solsticeTick, false)
                    && wears(player, Material.NETHERITE_CHESTPLATE, SOLSTICE)) {
                startDailyEffect(player, SOLSTICE, worldDay, now, durationMillis);
            }
            if (inTriggerWindow(worldTime, lunarTick, catchUp)
                    && wears(player, Material.NETHERITE_LEGGINGS, LUNAR)) {
                startDailyEffect(player, LUNAR, worldDay, now, durationMillis);
            }
        }
    }

    static boolean inTriggerWindow(long worldTime, long triggerTick, boolean catchUp) {
        return worldTime >= triggerTick && (catchUp || worldTime < triggerTick + 20L);
    }

    private boolean wears(Player player, Material material, String itemId) {
        ItemStack armor = switch (material) {
            case NETHERITE_CHESTPLATE -> player.getInventory().getChestplate();
            case NETHERITE_LEGGINGS -> player.getInventory().getLeggings();
            default -> null;
        };
        return armor != null && plugin.getLegendaryItems().identify(armor)
                .map(item -> item.id().equals(itemId)).orElse(false);
    }

    private void startDailyEffect(Player player, String itemId, long worldDay, long now, long durationMillis) {
        long expiresAt = Math.addExact(now, durationMillis);
        try {
            if (plugin.getDataStore().startDailyEffect(
                    player.getUniqueId(), itemId, player.getWorld().getUID(), worldDay, expiresAt)) {
                applyEffects(player, itemId, durationMillis);
                plugin.getLogger().info("Granted " + itemId + " daily effects to "
                        + player.getUniqueId() + " for " + durationMillis / 1_000L + " seconds.");
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Could not persist daily effect for " + player.getUniqueId() + "/" + itemId
                            + "; effect was not granted.", exception);
        }
    }

    private void restoreActiveEffect(Player player, String itemId, long now) {
        long expiresAt = plugin.getDataStore().getActiveEffectExpiry(player.getUniqueId(), itemId);
        if (expiresAt <= now) {
            failures.keySet().removeIf(key -> key.player().equals(player.getUniqueId())
                    && key.itemId().equals(itemId));
            try {
                plugin.getDataStore().clearActiveEffect(player.getUniqueId(), itemId);
            } catch (IOException exception) {
                plugin.getLogger().log(Level.SEVERE,
                        "Could not clear expired daily effect for " + player.getUniqueId() + "/" + itemId + ".",
                        exception);
            }
            return;
        }
        applyEffects(player, itemId, expiresAt - now);
    }

    private void applyEffects(Player player, String itemId, long remainingMillis) {
        int durationTicks = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, (remainingMillis + 49L) / 50L));
        long now = System.currentTimeMillis();
        if (SOLSTICE.equals(itemId)) {
            apply(player, itemId, PotionEffectType.SPEED, durationTicks, 2, now);
            apply(player, itemId, PotionEffectType.STRENGTH, durationTicks, 1, now);
            apply(player, itemId, PotionEffectType.RESISTANCE, durationTicks, 0, now);
        } else if (LUNAR.equals(itemId)) {
            apply(player, itemId, PotionEffectType.SPEED, durationTicks, 0, now);
            apply(player, itemId, PotionEffectType.STRENGTH, durationTicks, 0, now);
            apply(player, itemId, PotionEffectType.RESISTANCE, durationTicks, 1, now);
        }
    }

    void apply(Player player, String itemId, PotionEffectType type, int durationTicks, int amplifier, long now) {
        EffectKey key = new EffectKey(player.getUniqueId(), itemId, type);
        PotionEffect current = player.getPotionEffect(type);
        if (current != null && !needsRefresh(
                current.getAmplifier(), current.getDuration(), current.isInfinite(), amplifier, durationTicks)) {
            failures.remove(key);
            return;
        }
        Failure failure = failures.get(key);
        if (!retryDue(failure, now)) {
            return;
        }
        PotionEffect effect = new PotionEffect(type, durationTicks, amplifier, true, true, true);
        if (player.addPotionEffect(effect, true)) {
            failures.remove(key);
        } else {
            boolean warn = shouldWarn(failure, now);
            if (warn) {
                plugin.getLogger().warning("Could not apply " + type.getKey() + " for daily equipment effect to "
                        + player.getUniqueId() + "; retrying periodically.");
            }
            failures.put(key, new Failure(now + RETRY_MILLIS, warn ? now : failure.lastWarning()));
        }
    }

    static boolean needsRefresh(int currentAmplifier, int currentDuration, boolean infinite,
            int desiredAmplifier, int desiredDuration) {
        return currentAmplifier < desiredAmplifier
                || currentAmplifier == desiredAmplifier && !infinite && currentDuration + 20L < desiredDuration;
    }

    private record EffectKey(UUID player, String itemId, PotionEffectType type) {
    }

    static boolean retryDue(Failure failure, long now) {
        return failure == null || now >= failure.retryAt();
    }

    static boolean shouldWarn(Failure failure, long now) {
        return failure == null || now - failure.lastWarning() >= WARN_INTERVAL_MILLIS;
    }

    record Failure(long retryAt, long lastWarning) {
    }
}
