package com.coaxgames.smp.listener;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.util.MaceDamage;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class PhaseFiveMaceAbilityListener implements Listener {
    private static final String STAR_MACE = "star_mace";
    private static final String COLLAPSING_STAR_MACE = "collapsing_star_mace";
    private static final ItemStack SPARK_PARTICLE_ITEM = createSparkParticleItem();

    private final GalaxySmpPlugin plugin;
    private final Map<UUID, Map<String, Integer>> hitCounts = new HashMap<>();
    private final Map<UUID, Integer> collapsingHitStreaks = new HashMap<>();
    private final Map<UUID, PendingStarSwing> pendingStarSwings = new HashMap<>();
    private final Map<UUID, Boolean> pendingCollapsingSwings = new HashMap<>();
    private final Set<UUID> applyingSparkDamage = new HashSet<>();

    public PhaseFiveMaceAbilityListener(GalaxySmpPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public void resetAbilityProgress(UUID playerUuid) {
        hitCounts.remove(playerUuid);
        collapsingHitStreaks.remove(playerUuid);
        pendingStarSwings.remove(playerUuid);
        pendingCollapsingSwings.remove(playerUuid);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmSwing(PlayerArmSwingEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack mace = player.getInventory().getItemInMainHand();
        String itemId = plugin.getLegendaryItems().identify(mace)
                .map(item -> item.id()).orElse(null);
        if (STAR_MACE.equals(itemId)) {
            onStarMaceSwing(player, mace);
            return;
        }
        if (!COLLAPSING_STAR_MACE.equals(itemId)) {
            return;
        }
        if (plugin.getCooldownManager().isOnCooldown(player.getUniqueId(), COLLAPSING_STAR_MACE)) {
            return;
        }
        UUID playerId = player.getUniqueId();
        pendingCollapsingSwings.put(playerId, Boolean.FALSE);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (Boolean.FALSE.equals(pendingCollapsingSwings.remove(playerId))) {
                collapsingHitStreaks.remove(playerId);
            }
        }, 1L);
    }

    private void onStarMaceSwing(Player player, ItemStack mace) {
        if (plugin.getLegendaryItems().initializeStarMaceProgression(mace)) {
            player.getInventory().setItemInMainHand(mace);
        }
        int level = mace.getEnchantmentLevel(Enchantment.WIND_BURST);
        UUID maceUuid = plugin.getLegendaryItems().getUniqueId(mace).orElse(null);
        PendingStarSwing swing = new PendingStarSwing(maceUuid, level);
        pendingStarSwings.put(player.getUniqueId(), swing);
        if (plugin.getCooldownManager().isOnCooldown(player.getUniqueId(), STAR_MACE)) {
            com.coaxgames.smp.util.ItemCooldownMarkers.sync(player, STAR_MACE, plugin.getCooldownManager());
            pendingStarSwings.remove(player.getUniqueId(), swing);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingStarSwings.remove(player.getUniqueId(), swing)
                    && MaceDamage.startsStarMaceCooldown(swing.level, swing.hit)) {
                updateStarMaceLevel(player, swing.maceUuid,
                        MaceDamage.nextStarMaceWindBurst(swing.level, false));
                startStarMaceCooldown(player);
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMaceDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)
                || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        if (applyingSparkDamage.contains(attacker.getUniqueId())) {
            return;
        }
        String itemId = plugin.getLegendaryItems()
                .identify(attacker.getInventory().getItemInMainHand())
                .map(item -> item.id())
                .orElse(null);
        if (!STAR_MACE.equals(itemId) && !COLLAPSING_STAR_MACE.equals(itemId)) {
            collapsingHitStreaks.remove(attacker.getUniqueId());
            return;
        }

        UUID playerId = attacker.getUniqueId();
        boolean onCooldown = plugin.getCooldownManager().isOnCooldown(playerId, itemId);

        double cap = nonNegativeConfig("phase-five.mace-damage-cap-hearts", 7.0) * 2.0;
        event.setDamage(MaceDamage.capBaseDamage(event.getDamage(), cap));
        double damageDealt = Math.max(0.0, event.getFinalDamage());

        if (STAR_MACE.equals(itemId)) {
            handleStarMaceHit(attacker, damageDealt, onCooldown);
            return;
        }
        pendingCollapsingSwings.computeIfPresent(attacker.getUniqueId(), (ignored, hit) -> Boolean.TRUE);
        if (onCooldown) {
            clearHitCount(playerId, itemId);
            collapsingHitStreaks.remove(playerId);
            return;
        }
        int sparkCount = 0;
        if (damageDealt > 0.0) {
            int streak = collapsingHitStreaks.merge(playerId, 1, Integer::sum);
            sparkCount = MaceDamage.collapsingSparkCount(damageDealt, streak);
        }
        if (damageDealt > 0.0 && incrementHitCount(playerId, itemId) >= 2) {
            clearHitCount(playerId, itemId);
            try {
                if (!plugin.getCooldownManager().start(playerId, itemId)) {
                    event.setCancelled(true);
                    plugin.getLogger().severe("Could not start the 30-second " + itemId
                            + " cooldown for " + playerId + "; the second hit was cancelled.");
                    collapsingHitStreaks.remove(playerId);
                    return;
                }
            } catch (IOException | ArithmeticException exception) {
                event.setCancelled(true);
                collapsingHitStreaks.remove(playerId);
                plugin.getLogger().log(Level.SEVERE, "Could not persist the " + itemId
                        + " cooldown for " + playerId + "; the second hit was cancelled.", exception);
                return;
            }
            com.coaxgames.smp.util.ItemCooldownMarkers.sync(attacker, itemId, plugin.getCooldownManager());
            collapsingHitStreaks.remove(playerId);
        }
        if (sparkCount > 0) {
            scheduleSparks(target, attacker, sparkCount);
        }
    }

    private void handleStarMaceHit(Player attacker, double damageDealt, boolean onCooldown) {
        PendingStarSwing swing = pendingStarSwings.get(attacker.getUniqueId());
        ItemStack mace = attacker.getInventory().getItemInMainHand();
        UUID maceUuid = plugin.getLegendaryItems().getUniqueId(mace).orElse(null);
        if (swing != null && Objects.equals(swing.maceUuid, maceUuid)) {
            swing.hit = true;
        }
        if (onCooldown || damageDealt <= 0.0) {
            return;
        }
        int level = mace.getEnchantmentLevel(Enchantment.WIND_BURST);
        updateStarMaceLevel(attacker, maceUuid, MaceDamage.nextStarMaceWindBurst(level, true));
    }

    private void startStarMaceCooldown(Player player) {
        UUID playerId = player.getUniqueId();
        try {
            plugin.getCooldownManager().start(playerId, STAR_MACE, Duration.ofSeconds(30));
            com.coaxgames.smp.util.ItemCooldownMarkers.sync(player, STAR_MACE, plugin.getCooldownManager());
        } catch (IOException | ArithmeticException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Could not persist Star Mace cooldown for " + playerId + ".", exception);
        }
    }

    private void updateStarMaceLevel(Player player, UUID maceUuid, int level) {
        if (maceUuid == null) {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (plugin.getLegendaryItems().setStarMaceWindBurstLevel(held, level)) {
                player.getInventory().setItemInMainHand(held);
            }
            return;
        }
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (plugin.getLegendaryItems().getUniqueId(mainHand).filter(maceUuid::equals).isPresent()) {
            plugin.getLegendaryItems().setStarMaceWindBurstLevel(mainHand, level);
            player.getInventory().setItemInMainHand(mainHand);
            return;
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (plugin.getLegendaryItems().getUniqueId(offhand).filter(maceUuid::equals).isPresent()) {
            plugin.getLegendaryItems().setStarMaceWindBurstLevel(offhand, level);
            player.getInventory().setItemInOffHand(offhand);
            return;
        }
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            if (plugin.getLegendaryItems().getUniqueId(storage[slot]).filter(maceUuid::equals).isPresent()) {
                plugin.getLegendaryItems().setStarMaceWindBurstLevel(storage[slot], level);
                player.getInventory().setItem(slot, storage[slot]);
                return;
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hitCounts.remove(event.getPlayer().getUniqueId());
        collapsingHitStreaks.remove(event.getPlayer().getUniqueId());
        pendingStarSwings.remove(event.getPlayer().getUniqueId());
        pendingCollapsingSwings.remove(event.getPlayer().getUniqueId());
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

    private void scheduleSparks(LivingEntity target, Player attacker, int sparkCount) {
        double radius = nonNegativeConfig("phase-five.collapsing-star-mace.spark-radius", 1.5);
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
                    applyingSparkDamage.add(attacker.getUniqueId());
                    try {
                        living.damage(1.0, attacker);
                    } finally {
                        applyingSparkDamage.remove(attacker.getUniqueId());
                    }
                }
            }
        });
    }

    private static final class PendingStarSwing {
        private final UUID maceUuid;
        private final int level;
        private boolean hit;

        private PendingStarSwing(UUID maceUuid, int level) {
            this.maceUuid = maceUuid;
            this.level = level;
        }
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
