package com.coaxgames.smp.ability;

import com.coaxgames.smp.GalaxySmpPlugin;
import java.io.IOException;
import java.util.Objects;
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

    private final GalaxySmpPlugin plugin;
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
    }

    private void tick() {
        long now = System.currentTimeMillis();
        long triggerTick = plugin.getConfig().getLong("phase-four.daily-trigger-tick", 13_500L);
        long durationMillis = Math.multiplyExact(
                plugin.getConfig().getLong("phase-four.daily-effect-seconds", 300L), 1_000L);
        boolean catchUp = plugin.getConfig().getBoolean("phase-four.lunar-leggings.catch-up-if-offline", false);
        for (Player player : Bukkit.getOnlinePlayers()) {
            restoreActiveEffect(player, SOLSTICE, now);
            restoreActiveEffect(player, LUNAR, now);
            long worldTime = player.getWorld().getTime();
            long worldDay = player.getWorld().getFullTime() / 24_000L;
            boolean exactTriggerWindow = worldTime >= triggerTick && worldTime < triggerTick + 20L;
            boolean lunarTriggerWindow = catchUp ? worldTime >= triggerTick : exactTriggerWindow;
            if (exactTriggerWindow && wears(player, Material.NETHERITE_CHESTPLATE, SOLSTICE)) {
                startDailyEffect(player, SOLSTICE, worldDay, now, durationMillis);
            }
            if (lunarTriggerWindow && wears(player, Material.NETHERITE_LEGGINGS, LUNAR)) {
                startDailyEffect(player, LUNAR, worldDay, now, durationMillis);
            }
        }
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
        if (SOLSTICE.equals(itemId)) {
            apply(player, PotionEffectType.SPEED, durationTicks, 2);
            apply(player, PotionEffectType.STRENGTH, durationTicks, 1);
            apply(player, PotionEffectType.RESISTANCE, durationTicks, 0);
        } else if (LUNAR.equals(itemId)) {
            apply(player, PotionEffectType.SPEED, durationTicks, 0);
            apply(player, PotionEffectType.STRENGTH, durationTicks, 0);
            apply(player, PotionEffectType.RESISTANCE, durationTicks, 1);
        }
    }

    private void apply(Player player, PotionEffectType type, int durationTicks, int amplifier) {
        PotionEffect current = player.getPotionEffect(type);
        if (current != null && current.getAmplifier() > amplifier) {
            return;
        }
        PotionEffect effect = new PotionEffect(type, durationTicks, amplifier, true, true, true);
        if (!player.addPotionEffect(effect, true)) {
            plugin.getLogger().warning("Could not apply " + type.getKey() + " for daily equipment effect to "
                    + player.getUniqueId() + ".");
        }
    }
}
