package com.coaxgames.smp.listener;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.item.LegendaryItemRegistry;
import com.coaxgames.smp.util.ConfigNumbers;
import com.coaxgames.smp.util.MaceDamage;
import io.papermc.paper.event.entity.EntityLungeEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.FluidCollisionMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

public final class PhaseFiveAbilityListener implements Listener {
    private static final String NEBULA_SPEAR = "nebula_spear";
    private static final String WORMHOLE_TRIDENT = "wormhole_trident";
    private static final String CELESTIAL_BOW = "celestial_bow";
    private static final String COSMIC_CROWN = "cosmic_crown";
    private static final String SINGULARITY_BOOTS = "singularity_boots";
    private static final String NOVA_SHIELD = "nova_shield";

    private final GalaxySmpPlugin plugin;
    private final LegendaryItemRegistry items;
    private final NamespacedKey celestialArrowKey;
    private final Map<UUID, Integer> lungeCounts = new HashMap<>();
    private final Map<UUID, Integer> riptideCounts = new HashMap<>();
    private final Map<UUID, Integer> crownHitCounts = new HashMap<>();
    private final Map<UUID, Integer> shieldAxeHitCounts = new HashMap<>();
    private final Set<UUID> absorptionUpgradeInProgress = new HashSet<>();
    private final List<Rift> rifts = new ArrayList<>();
    private boolean applyingThorns;

    public PhaseFiveAbilityListener(GalaxySmpPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        items = plugin.getLegendaryItems();
        celestialArrowKey = new NamespacedKey(plugin, "celestial-bow-arrow");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickRifts, 1L, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSuccessfulLunge(EntityLungeEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !isItem(player.getInventory().getItemInMainHand(), NEBULA_SPEAR)) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (plugin.getCooldownManager().isOnCooldown(playerId, NEBULA_SPEAR)) {
            lungeCounts.remove(playerId);
            return;
        }
        int count = lungeCounts.merge(playerId, 1, Integer::sum);
        if (count < 5) {
            return;
        }
        lungeCounts.remove(playerId);
        try {
            if (!plugin.getCooldownManager().start(playerId, NEBULA_SPEAR)) {
                return;
            }
        } catch (IOException | ArithmeticException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not persist Nebula Spear cooldown for "
                    + playerId + "; the fifth lunge thrust was not applied.", exception);
            return;
        }
        applyNebulaThrust(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRiptide(PlayerRiptideEvent event) {
        if (!isItem(event.getItem(), WORMHOLE_TRIDENT)) {
            return;
        }
        UUID playerId = event.getPlayer().getUniqueId();
        int count = riptideCounts.merge(playerId, 1, Integer::sum);
        if (count >= 5) {
            riptideCounts.remove(playerId);
            rifts.add(new Rift(playerId, event.getPlayer().getLocation()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCelestialBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)
                || event.getForce() < 1.0F
                || !isItem(event.getBow(), CELESTIAL_BOW)
                || !(event.getProjectile() instanceof Arrow arrow)) {
            return;
        }
        arrow.getPersistentDataContainer().set(celestialArrowKey, PersistentDataType.BYTE, (byte) 1);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCelestialArrowHit(org.bukkit.event.entity.ProjectileHitEvent event) {
        Entity projectile = event.getEntity();
        if (!(projectile instanceof Arrow arrow)
                || !arrow.getPersistentDataContainer().has(celestialArrowKey, PersistentDataType.BYTE)) {
            return;
        }
        Location impact = projectile.getLocation();
        World world = impact.getWorld();
        if (world == null) {
            return;
        }
        double radius = number("phase-five.celestial-bow.splash-radius", 4.0);
        double damage = number("phase-five.celestial-bow.splash-damage", 2.0);
        Entity shooter = arrow.getShooter() instanceof Entity entity ? entity : null;
        for (Entity entity : world.getNearbyEntities(impact, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living)
                    || living.equals(shooter)
                    || living.getLocation().distanceSquared(impact) > radius * radius) {
                continue;
            }
            if (shooter == null) {
                living.damage(damage);
            } else {
                living.damage(damage, shooter);
            }
        }
        createCelestialCloud(world, impact);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrownHit(EntityDamageByEntityEvent event) {
        if (applyingThorns || !(event.getEntity() instanceof Player wearer)
                || event.getFinalDamage() <= 0.0
                || !wears(wearer, COSMIC_CROWN)) {
            return;
        }
        LivingEntity attacker = damagingLivingEntity(event.getDamager());
        if (attacker == null || attacker.equals(wearer)) {
            return;
        }
        UUID wearerId = wearer.getUniqueId();
        int count = crownHitCounts.merge(wearerId, 1, Integer::sum);
        if (count < 2) {
            return;
        }
        crownHitCounts.remove(wearerId);
        double retaliation = number("phase-five.cosmic-crown.thorns-damage", 2.0);
        applyingThorns = true;
        try {
            attacker.damage(retaliation, wearer);
        } finally {
            applyingThorns = false;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAbsorptionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !wears(player, COSMIC_CROWN)
                || event.getAction() != EntityPotionEffectEvent.Action.ADDED
                && event.getAction() != EntityPotionEffectEvent.Action.CHANGED
                || event.getNewEffect() == null
                || !PotionEffectType.ABSORPTION.equals(event.getNewEffect().getType())
                || event.getNewEffect().getAmplifier() >= 4
                || absorptionUpgradeInProgress.contains(player.getUniqueId())) {
            return;
        }
        PotionEffect current = event.getNewEffect();
        int boostedAmplifier = current.getAmplifier() + 1;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !wears(player, COSMIC_CROWN)) {
                return;
            }
            absorptionUpgradeInProgress.add(player.getUniqueId());
            try {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.ABSORPTION,
                        current.getDuration(),
                        boostedAmplifier,
                        current.isAmbient(),
                        current.hasParticles(),
                        current.hasIcon()), true);
            } finally {
                absorptionUpgradeInProgress.remove(player.getUniqueId());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCrownConsume(PlayerItemConsumeEvent event) {
        if (!wears(event.getPlayer(), COSMIC_CROWN)
                || event.getItem().getType() != Material.GOLDEN_APPLE
                && event.getItem().getType() != Material.ENCHANTED_GOLDEN_APPLE) {
            return;
        }
        Player player = event.getPlayer();
        double amount = event.getItem().getType() == Material.GOLDEN_APPLE ? 8.0 : 20.0;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && wears(player, COSMIC_CROWN)) {
                player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), amount));
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSingularityFall(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || event.getCause() != EntityDamageEvent.DamageCause.FALL
                || !wears(player, SINGULARITY_BOOTS)) {
            return;
        }
        event.setCancelled(true);
        if (player.getFallDistance() < number("phase-five.singularity-boots.explosion-fall-distance", 50.0)) {
            return;
        }
        Location impact = player.getLocation().clone();
        Bukkit.getScheduler().runTask(plugin, () -> {
            World world = impact.getWorld();
            if (world != null) {
                world.createExplosion(impact, 3.0F, false, false, player);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShieldAxeHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player defender)
                || !(event.getDamager() instanceof Player attacker)
                || !isAxe(attacker.getInventory().getItemInMainHand().getType())
                || !hasHeldItem(defender, NOVA_SHIELD)) {
            return;
        }
        UUID defenderId = defender.getUniqueId();
        if (shieldAxeHitCounts.merge(defenderId, 1, Integer::sum) < 2) {
            return;
        }
        shieldAxeHitCounts.remove(defenderId);
        long ticks = Math.multiplyExact(
                (long) number("phase-five.nova-shield.disable-seconds", 5.0), 20L);
        defender.setCooldown(Material.SHIELD, Math.toIntExact(ticks));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNovaRescue(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || event.getFinalDamage() < player.getHealth() + player.getAbsorptionAmount()
                || !hasItem(player, NOVA_SHIELD)
                || plugin.getCooldownManager().isOnCooldown(player.getUniqueId(), NOVA_SHIELD)) {
            return;
        }
        try {
            if (!plugin.getCooldownManager().start(player.getUniqueId(), NOVA_SHIELD)) {
                return;
            }
        } catch (IOException | ArithmeticException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not persist Nova Shield rescue cooldown for "
                    + player.getUniqueId() + "; lethal damage will apply.", exception);
            return;
        }
        event.setCancelled(true);
        player.setHealth(1.0);
        player.setFireTicks(0);
        player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0F, 1.0F);
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 900, 1, true, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1, true, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 800, 0, true, true, true));
        player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), 4.0));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lungeCounts.remove(id);
        riptideCounts.remove(id);
        crownHitCounts.remove(id);
        shieldAxeHitCounts.remove(id);
        for (Rift rift : rifts) {
            rift.inside.remove(id);
        }
    }

    private void applyNebulaThrust(Player player) {
        Vector direction = player.getLocation().getDirection().normalize();
        direction.multiply(number("phase-five.nebula-spear.thrust-velocity", 2.5));
        player.setVelocity(direction);
        double radius = number("phase-five.nebula-spear.thrust-radius", 2.5);
        double damage = number("phase-five.nebula-spear.thrust-damage-hearts", 4.0) * 2.0;
        Vector forward = player.getLocation().getDirection().normalize();
        Location center = player.getLocation().add(forward.clone().multiply(2.0));
        for (Entity entity : player.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || living.equals(player)) {
                continue;
            }
            Vector offset = living.getLocation().toVector().subtract(player.getLocation().toVector());
            if (offset.lengthSquared() > 0 && offset.normalize().dot(forward) > 0.35) {
                living.damage(damage, player);
            }
        }
    }

    private void tickRifts() {
        long now = System.currentTimeMillis();
        Iterator<Rift> iterator = rifts.iterator();
        while (iterator.hasNext()) {
            Rift rift = iterator.next();
            if (rift.expiresAtMillis <= now) {
                iterator.remove();
                continue;
            }
            Player creator = Bukkit.getPlayer(rift.creator);
            if (!rift.creatorPassed && (creator == null || !creator.isOnline())) {
                continue;
            }
            double radius = number("phase-five.wormhole-trident.interaction-radius", 2.0);
            World world = Bukkit.getWorld(rift.location.getWorld().getUID());
            if (world == null) {
                continue;
            }
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.getWorld().equals(world)) {
                    rift.inside.remove(online.getUniqueId());
                }
            }
            world.spawnParticle(Particle.PORTAL, rift.location, 4, radius / 2, 0.5, radius / 2, 0.02);
            for (Player player : world.getPlayers()) {
                boolean inRift = player.getLocation().distanceSquared(rift.location) <= radius * radius;
                if (!inRift) {
                    boolean wasInside = rift.inside.remove(player.getUniqueId());
                    if (player.equals(creator) && !rift.creatorPassed && wasInside) {
                        launchRiftCreator(rift, player, now);
                    }
                    continue;
                }
                rift.inside.add(player.getUniqueId());
                if (player.equals(creator) && !rift.creatorPassed) {
                    continue;
                }
                if (rift.creatorPassed && !player.equals(creator)
                        && rift.affected.add(player.getUniqueId())) {
                    player.damage(number("phase-five.wormhole-trident.opponent-damage-hearts", 2.0) * 2.0,
                            creator);
                    player.addPotionEffect(new PotionEffect(
                            PotionEffectType.WITHER,
                            (int) Math.min(Integer.MAX_VALUE,
                                    number("phase-five.wormhole-trident.opponent-wither-seconds", 20.0) * 20.0),
                            0,
                            true,
                            true,
                            true));
                }
            }
        }
    }

    private void createCelestialCloud(World world, Location impact) {
        AreaEffectCloud cloud = world.spawn(impact, AreaEffectCloud.class);
        cloud.setRadius((float) number("phase-five.celestial-bow.cloud-radius", 3.0));
        cloud.setDuration((int) Math.min(Integer.MAX_VALUE,
                number("phase-five.celestial-bow.cloud-duration-seconds", 10.0) * 20.0));
        cloud.setWaitTime(0);
        cloud.setRadiusPerTick(-0.005F);
        PotionEffectType effect = switch (plugin.getConfig().getString(
                "phase-five.celestial-bow.cloud-effect", "WITHER").toUpperCase(java.util.Locale.ROOT)) {
            case "POISON" -> PotionEffectType.POISON;
            case "WITHER" -> PotionEffectType.WITHER;
            default -> throw new IllegalStateException(
                    "phase-five.celestial-bow.cloud-effect must be WITHER or POISON.");
        };
        int effectTicks = (int) Math.min(Integer.MAX_VALUE,
                number("phase-five.celestial-bow.cloud-effect-seconds", 5.0) * 20.0);
        int amplifier = (int) number("phase-five.celestial-bow.cloud-effect-amplifier", 0.0);
        cloud.addCustomEffect(new PotionEffect(effect, effectTicks, amplifier, true, true, true), true);
    }

    private void launchRiftCreator(Rift rift, Player creator, long now) {
        rift.creatorPassed = true;
        rift.expiresAtMillis = now + Math.multiplyExact(
                (long) number("phase-five.wormhole-trident.lifetime-after-passage-seconds", 50.0),
                1_000L);
        Location origin = creator.getLocation();
        Vector direction = origin.getDirection().normalize();
        double distance = number("phase-five.wormhole-trident.creator-launch-distance", 20.0);
        Location destination = origin.clone().add(direction.clone().multiply(distance));
        var obstruction = origin.getWorld().rayTraceBlocks(
                origin, direction, distance, FluidCollisionMode.NEVER, true);
        if (obstruction != null) {
            double safeDistance = Math.max(0.0,
                    origin.toVector().distance(obstruction.getHitPosition()) - 0.5);
            destination = origin.clone().add(direction.multiply(safeDistance));
        }
        if (!creator.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
            plugin.getLogger().warning("Could not launch Wormhole Trident creator " + creator.getUniqueId()
                    + " through the rift.");
        }
    }

    private boolean isAxe(Material material) {
        return switch (material) {
            case WOODEN_AXE, STONE_AXE, IRON_AXE, GOLDEN_AXE, DIAMOND_AXE, NETHERITE_AXE -> true;
            default -> false;
        };
    }

    private LivingEntity damagingLivingEntity(Entity damager) {
        if (damager instanceof LivingEntity living) {
            return living;
        }
        if (damager instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    private boolean isItem(ItemStack stack, String itemId) {
        return items.identify(stack).map(item -> item.id().equals(itemId)).orElse(false);
    }

    private boolean wears(Player player, String itemId) {
        ItemStack item = switch (itemId) {
            case COSMIC_CROWN -> player.getInventory().getHelmet();
            case SINGULARITY_BOOTS -> player.getInventory().getBoots();
            case NOVA_SHIELD -> player.getInventory().getItemInOffHand();
            default -> null;
        };
        return isItem(item, itemId);
    }

    private boolean hasItem(Player player, String itemId) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isItem(stack, itemId)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasHeldItem(Player player, String itemId) {
        return isItem(player.getInventory().getItemInMainHand(), itemId)
                || isItem(player.getInventory().getItemInOffHand(), itemId);
    }

    private double number(String path, double fallback) {
        double value = ConfigNumbers.finiteNumber(plugin.getConfig(), path).orElse(fallback);
        if (value < 0.0) {
            throw new IllegalStateException(path + " must be a non-negative number.");
        }
        return value;
    }

    private static final class Rift {
        private final UUID creator;
        private final Location location;
        private final Set<UUID> inside = new HashSet<>();
        private final Set<UUID> affected = new HashSet<>();
        private boolean creatorPassed;
        private long expiresAtMillis = Long.MAX_VALUE;

        private Rift(UUID creator, Location location) {
            this.creator = creator;
            this.location = location.clone();
            inside.add(creator);
        }
    }
}
