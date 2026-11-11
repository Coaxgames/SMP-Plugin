package com.coaxgames.smp.listener;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.item.LegendaryItemRegistry;
import com.coaxgames.smp.util.HitCounter;
import com.coaxgames.smp.util.MassOrbDamage;
import com.coaxgames.smp.util.ItemCooldownMarkers;
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
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
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
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
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.bukkit.util.BoundingBox;

public final class PhaseFourAbilityListener implements Listener {
    private final GalaxySmpPlugin plugin;
    private final LegendaryItemRegistry items;
    private final NamespacedKey kuiperProjectileKey;
    private final HitCounter astralHits;
    private final HitCounter voidHits;
    private final Map<UUID, AttributeModifier> astralModifiers = new HashMap<>();
    private final Map<UUID, MassOrbFall> massOrbFalls = new HashMap<>();

    public PhaseFourAbilityListener(GalaxySmpPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        items = plugin.getLegendaryItems();
        kuiperProjectileKey = new NamespacedKey(plugin, "kuiper-projectile");
        long comboWindow = positiveConfig("phase-four.combo-window-seconds", 3);
        astralHits = new HitCounter(Duration.ofSeconds(comboWindow));
        voidHits = new HitCounter(Duration.ofSeconds(comboWindow));
        Bukkit.getScheduler().runTaskTimer(plugin, () -> applyMassOrbPassive(), 1L, 20L);
    }

    public void resetAbilityProgress(UUID playerUuid) {
        astralHits.clear(playerUuid);
        voidHits.clear(playerUuid);
        massOrbFalls.remove(playerUuid);
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
        Player player = event.getPlayer();
        if (plugin.getCooldownManager().isOnCooldown(player.getUniqueId(), "kuiper_ball")
                || !startKuiperCooldown(player)) {
            event.setCancelled(true);
            event.setShouldConsume(false);
            return;
        }
        snowball.getPersistentDataContainer().set(kuiperProjectileKey, PersistentDataType.BYTE, (byte) 1);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                ItemCooldownMarkers.sync(player, "kuiper_ball", plugin.getCooldownManager());
            }
        });
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
        double verticalBoost = nonNegativeConfig("phase-four.kuiper-ball.burst-vertical-boost", 0.35);
        double minimumVertical = nonNegativeConfig("phase-four.kuiper-ball.burst-minimum-vertical", 0.35);
        for (org.bukkit.entity.Entity nearby : projectile.getWorld().getNearbyEntities(
                projectile.getLocation(), radius, radius, radius)) {
            if (nearby.equals(projectile) || nearby.equals(projectile.getShooter())
                    || !isKuiperBurstTarget(nearby)) {
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
                direction.normalize().setY(Math.max(
                        minimumVertical, direction.getY() + verticalBoost));
            }
            nearby.setVelocity(direction.multiply(strength));
        }
    }

    private boolean startKuiperCooldown(Player player) {
        long cooldownMillis = positiveConfig("phase-four.kuiper-ball.cooldown-millis", 1_500L);
        try {
            boolean started = plugin.getCooldownManager().start(
                    player.getUniqueId(), "kuiper_ball", Duration.ofMillis(cooldownMillis));
            if (started) {
                ItemCooldownMarkers.sync(player, "kuiper_ball", plugin.getCooldownManager());
            }
            return started;
        } catch (IOException | ArithmeticException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Could not persist Kuiper Ball cooldown for " + player.getUniqueId()
                            + "; the projectile launch was cancelled.",
                    exception);
            return false;
        }
    }

    private boolean isKuiperBurstTarget(org.bukkit.entity.Entity entity) {
        if (entity instanceof Player) {
            return booleanConfig("phase-four.kuiper-ball.target-players", true);
        }
        if (entity instanceof Mob) {
            return booleanConfig("phase-four.kuiper-ball.target-mobs", true);
        }
        if (entity instanceof Item) {
            return booleanConfig("phase-four.kuiper-ball.target-items", false);
        }
        if (entity instanceof Projectile) {
            return booleanConfig("phase-four.kuiper-ball.target-projectiles", false);
        }
        return false;
    }

    private boolean booleanConfig(String path, boolean fallback) {
        if (!plugin.getConfig().contains(path)) {
            return fallback;
        }
        Object value = plugin.getConfig().get(path);
        if (!(value instanceof Boolean enabled)) {
            throw new IllegalStateException(path + " must be true or false.");
        }
        return enabled;
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
    public void onMassOrbMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        Location from = event.getFrom();
        Location to = event.getTo();
        if (event instanceof PlayerTeleportEvent || to == null || !from.getWorld().equals(to.getWorld())
                || player.isFlying() || player.isGliding() || player.isInsideVehicle()
                || player.isInWater()
                || (!isItem(player.getInventory().getItemInMainHand(), "mass_orb")
                && !isItem(player.getInventory().getItemInOffHand(), "mass_orb"))) {
            massOrbFalls.remove(playerId);
            return;
        }
        if (plugin.getCooldownManager().isOnCooldown(playerId, "mass_orb")) {
            massOrbFalls.remove(playerId);
            return;
        }
        double descent = from.getY() - to.getY();
        if (descent < 0) {
            massOrbFalls.remove(playerId);
            return;
        }
        if (descent == 0) {
            if (player.isOnGround()) {
                massOrbFalls.remove(playerId);
            }
            return;
        }

        MassOrbFall fall = massOrbFalls.computeIfAbsent(playerId,
                ignored -> new MassOrbFall(Math.max(0.0, player.getFallDistance() - descent)));
        double halfWidth = player.getWidth() / 2.0;
        BoundingBox path = new BoundingBox(
                Math.min(from.getX(), to.getX()) - halfWidth,
                to.getY() - 0.01,
                Math.min(from.getZ(), to.getZ()) - halfWidth,
                Math.max(from.getX(), to.getX()) + halfWidth,
                from.getY() + 0.01,
                Math.max(from.getZ(), to.getZ()) + halfWidth);
        for (var entity : player.getWorld().getNearbyEntities(path)) {
            if (!(entity instanceof LivingEntity target) || target.equals(player)
                    || fall.hitEntities.contains(target.getUniqueId())) {
                continue;
            }
            if (plugin.getCooldownManager().isOnCooldown(playerId, "mass_orb")) {
                break;
            }
            double fraction = MassOrbDamage.impactFraction(
                    from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ(),
                    player.getWidth(), target.getBoundingBox());
            if (fraction < 0) {
                continue;
            }
            double damage = MassOrbDamage.slamDamage(fall.distance + descent * fraction);
            if (damage > 0) {
                if (!startMassOrbCooldown(player)) {
                    break;
                }
                fall.hitEntities.add(target.getUniqueId());
                target.damage(damage, player);
            }
        }
        fall.distance += descent;
        if (player.isOnGround()) {
            massOrbFalls.remove(playerId);
        }
    }

    private boolean startMassOrbCooldown(Player player) {
        try {
            boolean started = plugin.getCooldownManager().start(player.getUniqueId(), "mass_orb");
            if (started) {
                ItemCooldownMarkers.sync(player, "mass_orb", plugin.getCooldownManager());
            }
            return started;
        } catch (IOException | ArithmeticException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Could not persist Mass Orb cooldown for " + player.getUniqueId()
                            + "; the slam was cancelled.",
                    exception);
            return false;
        }
    }

    @EventHandler
    public void onMassOrbQuit(PlayerQuitEvent event) {
        massOrbFalls.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMassOrbTeleport(PlayerTeleportEvent event) {
        massOrbFalls.remove(event.getPlayer().getUniqueId());
    }

    private static final class MassOrbFall {
        private double distance;
        private final Set<UUID> hitEntities = new HashSet<>();

        private MassOrbFall(double distance) {
            this.distance = distance;
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
