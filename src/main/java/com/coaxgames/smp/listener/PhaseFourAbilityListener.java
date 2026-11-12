package com.coaxgames.smp.listener;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.item.LegendaryItemRegistry;
import com.coaxgames.smp.util.MassOrbDamage;
import com.coaxgames.smp.util.ItemCooldownMarkers;
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
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
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.bukkit.util.BoundingBox;

public final class PhaseFourAbilityListener implements Listener {
    private static final long ASTRAL_EFFECT_MILLIS = 10_000L;
    private static final long ASTRAL_COOLDOWN_MILLIS = 30_000L;
    private static final int ASTRAL_MAX_HITS = 4;
    private final GalaxySmpPlugin plugin;
    private final LegendaryItemRegistry items;
    private final NamespacedKey kuiperProjectileKey;
    private final Map<UUID, Integer> voidHits = new HashMap<>();
    private final Consumer<LivingEntity> applyVoidEffects;
    private final Supplier<Attribute> attackSpeedAttribute;
    private final LongSupplier clock;
    private final NamespacedKey astralBoostKey;
    private final NamespacedKey astralExpiryKey;
    private final NamespacedKey astralLevelKey;
    private final NamespacedKey astralCooldownKey;
    private final Map<UUID, MassOrbFall> massOrbFalls = new HashMap<>();

    public PhaseFourAbilityListener(GalaxySmpPlugin plugin) {
        this(plugin, target -> {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 0, true, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 200, 0, true, true, true));
        });
    }

    PhaseFourAbilityListener(GalaxySmpPlugin plugin, Consumer<LivingEntity> applyVoidEffects) {
        this(plugin, applyVoidEffects, () -> Attribute.ATTACK_SPEED);
    }

    PhaseFourAbilityListener(GalaxySmpPlugin plugin, Consumer<LivingEntity> applyVoidEffects,
            Supplier<Attribute> attackSpeedAttribute) {
        this(plugin, applyVoidEffects, attackSpeedAttribute, System::currentTimeMillis);
    }

    PhaseFourAbilityListener(GalaxySmpPlugin plugin, Consumer<LivingEntity> applyVoidEffects,
            Supplier<Attribute> attackSpeedAttribute, LongSupplier clock) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.applyVoidEffects = Objects.requireNonNull(applyVoidEffects, "applyVoidEffects");
        this.attackSpeedAttribute = Objects.requireNonNull(attackSpeedAttribute, "attackSpeedAttribute");
        this.clock = Objects.requireNonNull(clock, "clock");
        items = plugin.getLegendaryItems();
        kuiperProjectileKey = new NamespacedKey(plugin, "kuiper-projectile");
        astralBoostKey = new NamespacedKey(plugin, "astral-sword-speed");
        astralExpiryKey = new NamespacedKey(plugin, "astral-sword-speed-expires");
        astralLevelKey = new NamespacedKey(plugin, "astral-sword-speed-hits");
        astralCooldownKey = new NamespacedKey(plugin, "astral-sword-speed-cooldown");
        Bukkit.getScheduler().runTaskTimer(plugin, () -> applyMassOrbPassive(), 1L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::expireHeldAstralBoosts, 1L, 1L);
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeLegacyAstralBoost(player);
            expireAstralInventoryBoosts(player);
        }
    }

    public int resetAbilityProgress(Player player) {
        UUID playerUuid = player.getUniqueId();
        voidHits.remove(playerUuid);
        massOrbFalls.remove(playerUuid);
        int cleared = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!isItem(stack, "astral_sword")) {
                continue;
            }
            ItemMeta meta = stack.getItemMeta();
            var data = meta.getPersistentDataContainer();
            Long expiry = data.get(astralExpiryKey, PersistentDataType.LONG);
            Long cooldown = data.get(astralCooldownKey, PersistentDataType.LONG);
            if (expiry == null && cooldown == null) {
                continue;
            }
            if (cooldown != null && cooldown > clock.getAsLong()) {
                cleared++;
            }
            removeAstralModifier(meta);
            data.remove(astralExpiryKey);
            data.remove(astralLevelKey);
            data.remove(astralCooldownKey);
            if (!stack.setItemMeta(meta)) {
                throw new IllegalStateException("Could not reset Astral Sword attack-speed state.");
            }
            inventory.setItem(slot, stack);
        }
        return cleared;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)
                || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        String itemId = items.identify(weapon)
                .map(definition -> definition.id()).orElse(null);
        if ("astral_sword".equals(itemId)) {
            voidHits.remove(attacker.getUniqueId());
            updateAstralSpeed(attacker, weapon);
        } else if ("void_sword".equals(itemId)) {
            if (voidHits.merge(attacker.getUniqueId(), 1, Integer::sum) >= 4) {
                voidHits.remove(attacker.getUniqueId());
                applyVoidEffects.accept(target);
            }
        } else {
            voidHits.remove(attacker.getUniqueId());
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
            if (nearby instanceof LivingEntity living) {
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
        voidHits.remove(event.getPlayer().getUniqueId());
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAstralHeldSlotChange(PlayerItemHeldEvent event) {
        var inventory = event.getPlayer().getInventory();
        ItemStack stack = inventory.getItem(event.getNewSlot());
        if (expireAstralBoost(stack)) {
            inventory.setItem(event.getNewSlot(), stack);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAstralPickup(EntityPickupItemEvent event) {
        ItemStack stack = event.getItem().getItemStack();
        if (expireAstralBoost(stack)) {
            event.getItem().setItemStack(stack);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        removeLegacyAstralBoost(event.getPlayer());
        expireAstralInventoryBoosts(event.getPlayer());
        applyMassOrbPassive(event.getPlayer());
    }

    private void updateAstralSpeed(Player player, ItemStack sword) {
        if (expireAstralBoost(sword)) {
            player.getInventory().setItemInMainHand(sword);
        }
        ItemMeta meta = sword.getItemMeta();
        var data = meta.getPersistentDataContainer();
        long now = clock.getAsLong();
        Long expiry = data.get(astralExpiryKey, PersistentDataType.LONG);
        Long cooldown = data.get(astralCooldownKey, PersistentDataType.LONG);
        if (expiry == null && cooldown != null && now < cooldown) {
            return;
        }
        Byte hits = data.get(astralLevelKey, PersistentDataType.BYTE);
        int nextHits = hits == null ? 1 : hits + 1;
        if (nextHits > ASTRAL_MAX_HITS) {
            return;
        }
        if (expiry == null) {
            expiry = Math.addExact(now, ASTRAL_EFFECT_MILLIS);
            data.set(astralExpiryKey, PersistentDataType.LONG, expiry);
            data.set(astralCooldownKey, PersistentDataType.LONG, Math.addExact(expiry, ASTRAL_COOLDOWN_MILLIS));
        }
        if (!meta.hasAttributeModifiers()) {
            meta.setAttributeModifiers(sword.getType().getDefaultAttributeModifiers());
        }
        removeAstralModifier(meta);
        AttributeModifier modifier = new AttributeModifier(
                astralBoostKey, Math.pow(1.35, nextHits) - 1.0,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.MAINHAND);
        if (!meta.addAttributeModifier(attackSpeedAttribute.get(), modifier)) {
            throw new IllegalStateException("Could not add Astral Sword attack-speed modifier.");
        }
        data.set(astralLevelKey, PersistentDataType.BYTE, (byte) nextHits);
        if (!sword.setItemMeta(meta)) {
            throw new IllegalStateException("Could not apply Astral Sword attack-speed boost.");
        }
        player.getInventory().setItemInMainHand(sword);
    }

    private boolean expireAstralBoost(ItemStack stack) {
        if (stack == null || stack.getType() != Material.NETHERITE_SWORD) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        Long expiry = meta.getPersistentDataContainer().get(astralExpiryKey, PersistentDataType.LONG);
        if (expiry == null || expiry > clock.getAsLong()
                && meta.getPersistentDataContainer().has(astralLevelKey, PersistentDataType.BYTE)) {
            return false;
        }
        removeAstralModifier(meta);
        meta.getPersistentDataContainer().remove(astralExpiryKey);
        meta.getPersistentDataContainer().remove(astralLevelKey);
        if (!stack.setItemMeta(meta)) {
            throw new IllegalStateException("Could not remove expired Astral Sword attack-speed boost.");
        }
        return true;
    }

    private void removeAstralModifier(ItemMeta meta) {
        Attribute attribute = attackSpeedAttribute.get();
        var modifiers = meta.getAttributeModifiers(attribute);
        if (modifiers != null) {
            for (AttributeModifier modifier : List.copyOf(modifiers)) {
                if (modifier.getKey().equals(astralBoostKey)) {
                    meta.removeAttributeModifier(attribute, modifier);
                }
            }
        }
    }

    private void expireHeldAstralBoosts() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (expireAstralBoost(held)) {
                player.getInventory().setItemInMainHand(held);
            }
        }
    }

    private void expireAstralInventoryBoosts(Player player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (expireAstralBoost(stack)) {
                inventory.setItem(slot, stack);
            }
        }
    }

    private void removeLegacyAstralBoost(Player player) {
        var attribute = player.getAttribute(attackSpeedAttribute.get());
        if (attribute == null) {
            return;
        }
        NamespacedKey key = new NamespacedKey(plugin, "astral-speed-" + player.getUniqueId());
        AttributeModifier existing = attribute.getModifier(key);
        if (existing != null) {
            attribute.removeModifier(key);
        }
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
