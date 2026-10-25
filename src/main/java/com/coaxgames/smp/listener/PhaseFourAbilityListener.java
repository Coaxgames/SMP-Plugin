package com.coaxgames.smp.listener;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.item.LegendaryItemRegistry;
import com.coaxgames.smp.util.HitCounter;
import com.coaxgames.smp.util.MassOrbDamage;
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public final class PhaseFourAbilityListener implements Listener {
    private final GalaxySmpPlugin plugin;
    private final LegendaryItemRegistry items;
    private final NamespacedKey kuiperProjectileKey;
    private final HitCounter astralHits;
    private final HitCounter voidHits;
    private final Map<UUID, AttributeModifier> astralModifiers = new HashMap<>();

    public PhaseFourAbilityListener(GalaxySmpPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        items = plugin.getLegendaryItems();
        kuiperProjectileKey = new NamespacedKey(plugin, "kuiper-projectile");
        long comboWindow = positiveConfig("phase-four.combo-window-seconds", 3);
        astralHits = new HitCounter(Duration.ofSeconds(comboWindow));
        voidHits = new HitCounter(Duration.ofSeconds(comboWindow));
        Bukkit.getScheduler().runTaskTimer(plugin, () -> applyMassOrbPassive(), 1L, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)
                || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        String itemId = items.identify(attacker.getInventory().getItemInMainHand())
                .map(definition -> definition.id()).orElse(null);
        if ("astral_sword".equals(itemId)) {
            voidHits.clear(attacker.getUniqueId());
            if (astralHits.record(attacker.getUniqueId()) >= 4) {
                astralHits.clear(attacker.getUniqueId());
                applyAstralSpeed(attacker);
            }
        } else if ("void_sword".equals(itemId)) {
            astralHits.clear(attacker.getUniqueId());
            if (voidHits.record(attacker.getUniqueId()) >= 4) {
                voidHits.clear(attacker.getUniqueId());
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 0, true, true, true));
                target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 200, 0, true, true, true));
            }
        } else {
            astralHits.clear(attacker.getUniqueId());
            voidHits.clear(attacker.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileLaunch(PlayerLaunchProjectileEvent event) {
        Projectile projectile = event.getProjectile();
        if (!(projectile instanceof Snowball snowball)
                || !isItem(event.getItemStack(), "kuiper_ball")) {
            return;
        }
        snowball.getPersistentDataContainer().set(kuiperProjectileKey, PersistentDataType.BYTE, (byte) 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile instanceof Snowball)
                || !projectile.getPersistentDataContainer().has(kuiperProjectileKey, PersistentDataType.BYTE)) {
            return;
        }
        double radius = nonNegativeConfig("phase-four.kuiper-ball.burst-radius", 4.0);
        double strength = nonNegativeConfig("phase-four.kuiper-ball.burst-strength", 1.5);
        for (org.bukkit.entity.Entity nearby : projectile.getWorld().getNearbyEntities(
                projectile.getLocation(), radius, radius, radius)) {
            if (nearby.equals(projectile) || nearby.equals(projectile.getShooter())) {
                continue;
            }
            if (nearby instanceof LivingEntity living && event.getHitEntity() == living) {
                int freezeTicks = (int) Math.min(Integer.MAX_VALUE,
                        Math.multiplyExact(
                                positiveConfig("phase-four.kuiper-ball.freeze-seconds", 30), 20L));
                living.setFreezeTicks(Math.max(living.getFreezeTicks(), freezeTicks));
            }
            Vector direction = nearby.getLocation().toVector()
                    .subtract(projectile.getLocation().toVector());
            if (direction.lengthSquared() == 0) {
                direction = new Vector(0, 1, 0);
            } else {
                direction.normalize().setY(Math.max(0.35, direction.getY() + 0.35));
            }
            nearby.setVelocity(direction.multiply(strength));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMassOrbFall(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || event.getCause() != EntityDamageEvent.DamageCause.FALL
                || items.countForPlayer(player, "mass_orb") == 0) {
            return;
        }
        int featherFallingLevel = (int) positiveConfig("phase-four.mass-orb.feather-falling-level", 15);
        double scaledDamage = MassOrbDamage.calculate(
                player.getFallDistance(),
                nonNegativeConfig("phase-four.mass-orb.free-fall-distance", 3.0),
                nonNegativeConfig("phase-four.mass-orb.damage-per-block", 1.0),
                nonNegativeConfig("phase-four.mass-orb.maximum-damage", 40.0),
                featherFallingLevel,
                nonNegativeConfig("phase-four.mass-orb.feather-falling-reduction-per-level", 0.12),
                Math.min(1.0,
                        nonNegativeConfig("phase-four.mass-orb.maximum-feather-falling-reduction", 0.8)));
        if (scaledDamage == 0.0) {
            event.setCancelled(true);
        } else {
            event.setDamage(scaledDamage);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeldSlotChange(PlayerItemHeldEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> applyMassOrbPassive(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        applyMassOrbPassive(event.getPlayer());
    }

    private void applyAstralSpeed(Player player) {
        var attribute = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attribute == null) {
            plugin.getLogger().warning("Cannot apply Astral Sword attack-speed effect to " + player.getUniqueId()
                    + ": the attack-speed attribute is unavailable.");
            return;
        }
        AttributeModifier previous = astralModifiers.remove(player.getUniqueId());
        if (previous != null) {
            attribute.removeModifier(previous);
        }
        NamespacedKey key = new NamespacedKey(plugin, "astral-speed-" + player.getUniqueId());
        AttributeModifier existing = attribute.getModifier(key);
        if (existing != null) {
            attribute.removeModifier(key);
        }
        AttributeModifier modifier = new AttributeModifier(
                key, 0.65, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        attribute.addTransientModifier(modifier);
        astralModifiers.put(player.getUniqueId(), modifier);
        long duration = positiveConfig("phase-four.astral-sword.attack-speed-seconds", 30);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (astralModifiers.get(player.getUniqueId()) == modifier) {
                astralModifiers.remove(player.getUniqueId());
                var currentAttribute = player.getAttribute(Attribute.ATTACK_SPEED);
                if (currentAttribute != null) {
                    currentAttribute.removeModifier(modifier);
                }
            }
        }, Math.multiplyExact(duration, 20L));
    }

    private void applyMassOrbPassive() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyMassOrbPassive(player);
        }
    }

    private void applyMassOrbPassive(Player player) {
        boolean holding = isItem(player.getInventory().getItemInMainHand(), "mass_orb")
                || isItem(player.getInventory().getItemInOffHand(), "mass_orb");
        if (holding) {
            int duration = (int) Math.min(Integer.MAX_VALUE,
                    positiveConfig("phase-four.mass-orb.passive-refresh-ticks", 40));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 3, true, true, true));
        }
    }

    private boolean isItem(ItemStack stack, String itemId) {
        return items.identify(stack).map(definition -> definition.id().equals(itemId)).orElse(false);
    }

    private long positiveConfig(String path, long fallback) {
        long value = plugin.getConfig().getLong(path, fallback);
        if (value <= 0) {
            throw new IllegalStateException(path + " must be a positive integer.");
        }
        return value;
    }

    private double nonNegativeConfig(String path, double fallback) {
        double value = com.coaxgames.smp.util.ConfigNumbers.finiteNumber(plugin.getConfig(), path)
                .orElse(fallback);
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalStateException(path + " must be a non-negative number.");
        }
        return value;
    }
}
