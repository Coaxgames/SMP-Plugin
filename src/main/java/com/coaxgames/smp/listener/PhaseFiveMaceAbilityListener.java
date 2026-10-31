package com.coaxgames.smp.listener;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.util.MaceDamage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public final class PhaseFiveMaceAbilityListener implements Listener {
    private static final String STAR_MACE = "star_mace";
    private static final String COLLAPSING_STAR_MACE = "collapsing_star_mace";
    private static final ItemStack SPARK_PARTICLE_ITEM = createSparkParticleItem();

    private final GalaxySmpPlugin plugin;
    private final Map<UUID, Map<String, Integer>> hitCounts = new HashMap<>();

    public PhaseFiveMaceAbilityListener(GalaxySmpPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public void resetAbilityProgress(UUID playerUuid) {
        hitCounts.remove(playerUuid);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMaceDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)
                || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        String itemId = plugin.getLegendaryItems()
                .identify(attacker.getInventory().getItemInMainHand())
                .map(item -> item.id())
                .orElse(null);
        if (!STAR_MACE.equals(itemId) && !COLLAPSING_STAR_MACE.equals(itemId)) {
            return;
        }

        UUID playerId = attacker.getUniqueId();
        boolean onCooldown = plugin.getCooldownManager().isOnCooldown(playerId, itemId);
        if (onCooldown) {
            clearHitCount(playerId, itemId);
        }

        double finalDamageBeforeCap = event.getFinalDamage();
        double cap = nonNegativeConfig("phase-five.mace-damage-cap-hearts", 7.0) * 2.0;
        event.setDamage(MaceDamage.capBaseDamage(event.getDamage(), cap));

        if (!onCooldown && finalDamageBeforeCap > 0.0 && incrementHitCount(playerId, itemId) >= 2) {
            clearHitCount(playerId, itemId);
            try {
                if (!plugin.getCooldownManager().start(playerId, itemId)) {
                    event.setCancelled(true);
                    plugin.getLogger().severe("Could not start the 30-second " + itemId
                            + " cooldown for " + playerId + "; the second hit was cancelled.");
                }
            } catch (IOException | ArithmeticException exception) {
                event.setCancelled(true);
                plugin.getLogger().log(Level.SEVERE, "Could not persist the " + itemId
                        + " cooldown for " + playerId + "; the second hit was cancelled.", exception);
            }
        }

        if (!onCooldown && COLLAPSING_STAR_MACE.equals(itemId)
                && MaceDamage.exceedsSparkThreshold(finalDamageBeforeCap)) {
            scheduleSparks(target, attacker);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hitCounts.remove(event.getPlayer().getUniqueId());
    }

    private int incrementHitCount(UUID playerId, String itemId) {
        Map<String, Integer> byItem = hitCounts.computeIfAbsent(playerId, ignored -> new HashMap<>());
        return byItem.merge(itemId, 1, Integer::sum);
    }

    private void clearHitCount(UUID playerId, String itemId) {
        Map<String, Integer> byItem = hitCounts.get(playerId);
        if (byItem == null) {
            return;
        }
        byItem.remove(itemId);
        if (byItem.isEmpty()) {
            hitCounts.remove(playerId);
        }
    }

    private void scheduleSparks(LivingEntity target, Player attacker) {
        int sparkCount = plugin.getConfig().getInt(
                "phase-five.collapsing-star-mace.spark-count-per-hit", 1);
        double radius = nonNegativeConfig("phase-five.collapsing-star-mace.spark-radius", 1.5);
        double damage = Math.min(2.0,
                nonNegativeConfig("phase-five.collapsing-star-mace.spark-damage", 2.0));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!target.isValid() || target.isDead()) {
                return;
            }
            for (int spark = 0; spark < sparkCount; spark++) {
                var location = target.getLocation().add(0.0, target.getHeight() / 2.0, 0.0);
                target.getWorld().spawnParticle(
                        Particle.ITEM, location, 1, 0.0, 0.0, 0.0, 0.0,
                        SPARK_PARTICLE_ITEM);
                double radiusSquared = radius * radius;
                for (var entity : target.getWorld().getNearbyEntities(location, radius, radius, radius)) {
                    if (!(entity instanceof LivingEntity living)
                            || living.equals(attacker)
                            || living.getLocation().distanceSquared(location) > radiusSquared) {
                        continue;
                    }
                    living.damage(damage);
                }
            }
        });
    }

    private static ItemStack createSparkParticleItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        var meta = item.getItemMeta();
        meta.setItemModel(Objects.requireNonNull(NamespacedKey.fromString("galaxy:ability_spark")));
        item.setItemMeta(meta);
        return item;
    }

    private double nonNegativeConfig(String path, double fallback) {
        double value = com.coaxgames.smp.util.ConfigNumbers.finiteNumber(plugin.getConfig(), path)
                .orElse(fallback);
        if (value < 0.0) {
            throw new IllegalStateException(path + " must be a non-negative number.");
        }
        return value;
    }
}
