package com.coaxgames.smp.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.item.LegendaryItem;
import com.coaxgames.smp.item.LegendaryItemRegistry;
import com.coaxgames.smp.util.CooldownManager;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.Transformation;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

class PhaseFiveMaceAbilityListenerTest {
    @Test
    void refreshesExistingStarMaceMissCooldownLore() {
        LegendaryItemRegistry registry = mock(LegendaryItemRegistry.class, CALLS_REAL_METHODS);
        LegendaryItem definition = mock(LegendaryItem.class);
        when(definition.id()).thenReturn("star_mace");
        ItemStack mace = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        doReturn(Optional.of(definition)).when(registry).identify(mace);
        when(mace.getItemMeta()).thenReturn(meta);
        when(meta.lore()).thenReturn(List.of(
                Component.text("A miss resets Wind Burst to I and starts a 30-second cooldown.")));
        when(mace.setItemMeta(meta)).thenReturn(true);

        org.junit.jupiter.api.Assertions.assertTrue(registry.refreshStarMaceCooldownLore(mace));
        verify(meta).lore(List.of(Component.text(
                "A miss resets Wind Burst to I; at V, it starts a 30-second cooldown.", NamedTextColor.GRAY),
                Component.text("Wind Burst resets to I after 20 seconds without use.", NamedTextColor.GRAY)));
    }

    @Test
    void refreshesExistingKuiperBallLore() {
        LegendaryItemRegistry registry = mock(LegendaryItemRegistry.class, CALLS_REAL_METHODS);
        LegendaryItem definition = mock(LegendaryItem.class);
        when(definition.id()).thenReturn("kuiper_ball");
        ItemStack ball = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        doReturn(Optional.of(definition)).when(registry).identify(ball);
        when(ball.getItemMeta()).thenReturn(meta);
        when(meta.lore()).thenReturn(List.of(
                Component.text("Throw to freeze a hit target and launch configured nearby targets.")));
        when(ball.setItemMeta(meta)).thenReturn(true);

        org.junit.jupiter.api.Assertions.assertTrue(registry.refreshKuiperBallLore(ball));
        verify(meta).lore(List.of(Component.text(
                "Throw to freeze and launch configured nearby targets.", NamedTextColor.GRAY)));
    }

    @Test
    void starMaceInactivityFollowsItemAndResetsAtTwentySeconds() {
        LegendaryItemRegistry registry = mock(LegendaryItemRegistry.class, CALLS_REAL_METHODS);
        LegendaryItem definition = mock(LegendaryItem.class);
        when(definition.id()).thenReturn("star_mace");
        ItemStack mace = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        AtomicReference<Long> lastUse = new AtomicReference<>();
        doReturn(Optional.of(definition)).when(registry).identify(mace);
        doReturn(true).when(registry).setStarMaceWindBurstLevel(mace, 1);
        when(mace.getItemMeta()).thenReturn(meta);
        when(mace.setItemMeta(meta)).thenReturn(true);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        when(data.get(any(), eq(PersistentDataType.LONG))).thenAnswer(call -> lastUse.get());
        doAnswer(call -> {
            lastUse.set(call.getArgument(2));
            return null;
        }).when(data).set(any(), eq(PersistentDataType.LONG), anyLong());
        org.junit.jupiter.api.Assertions.assertTrue(registry.resetInactiveStarMace(mace, 1_000L));
        org.junit.jupiter.api.Assertions.assertFalse(registry.resetInactiveStarMace(mace, 1_001L));
        registry.recordStarMaceUse(mace, 1_000L);
        org.junit.jupiter.api.Assertions.assertFalse(registry.resetInactiveStarMace(mace, 20_999L));
        registry.recordStarMaceUse(mace, 20_999L);
        org.junit.jupiter.api.Assertions.assertFalse(registry.resetInactiveStarMace(mace, 40_998L));
        org.junit.jupiter.api.Assertions.assertTrue(registry.resetInactiveStarMace(mace, 40_999L));
        org.junit.jupiter.api.Assertions.assertFalse(registry.resetInactiveStarMace(mace, 41_000L));
        verify(registry, times(2)).setStarMaceWindBurstLevel(mace, 1);
    }

    @Test
    void pickupRefreshesInactiveMaceWithoutStartingCooldown() throws Exception {
        try (var items = mockConstruction(ItemStack.class, (item, context) ->
                    when(item.getItemMeta()).thenReturn(mock(ItemMeta.class)));
             var bukkit = mockStatic(Bukkit.class)) {
            Fixture fixture = new Fixture("star_mace", bukkit);
            Item dropped = mock(Item.class);
            EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
            when(event.getItem()).thenReturn(dropped);
            when(dropped.getItemStack()).thenReturn(fixture.mace);
            when(fixture.registry.resetInactiveStarMace(eq(fixture.mace), anyLong())).thenReturn(true);

            fixture.listener.onPickup(event);

            verify(dropped).setItemStack(fixture.mace);
            verify(fixture.cooldowns, never()).start(any(), any(), any());
        }
    }

    @Test
    void periodicRefreshUpdatesInventoryWithoutStartingCooldown() throws Exception {
        try (var items = mockConstruction(ItemStack.class, (item, context) ->
                    when(item.getItemMeta()).thenReturn(mock(ItemMeta.class)));
             var bukkit = mockStatic(Bukkit.class)) {
            Fixture fixture = new Fixture("star_mace", bukkit);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(fixture.player));
            when(fixture.inventory.getContents()).thenReturn(new ItemStack[] {fixture.mace});
            when(fixture.registry.resetInactiveStarMace(eq(fixture.mace), anyLong())).thenReturn(true);
            ArgumentCaptor<Runnable> refresh = ArgumentCaptor.forClass(Runnable.class);
            verify(fixture.scheduler).runTaskTimer(eq(fixture.plugin), refresh.capture(), eq(20L), eq(20L));

            refresh.getValue().run();

            verify(fixture.inventory).setItem(0, fixture.mace);
            verify(fixture.cooldowns, never()).start(any(), any(), any());
        }
    }

    @Test
    void refreshesExistingCollapsingStarCooldownLore() {
        LegendaryItemRegistry registry = mock(LegendaryItemRegistry.class, CALLS_REAL_METHODS);
        LegendaryItem definition = mock(LegendaryItem.class);
        when(definition.id()).thenReturn("collapsing_star_mace");
        ItemStack mace = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        doReturn(Optional.of(definition)).when(registry).identify(mace);
        when(mace.getItemMeta()).thenReturn(meta);
        when(meta.lore()).thenReturn(List.of(Component.text("Cooldown: 30 seconds after 2 successful hits.")));
        when(mace.setItemMeta(meta)).thenReturn(true);

        org.junit.jupiter.api.Assertions.assertTrue(registry.refreshCollapsingStarCooldownLore(mace));
        verify(meta).lore(List.of(Component.text(
                "Cooldown: 30 seconds after 3 successful hits; attacks are blocked.", NamedTextColor.GRAY)));
    }

    @Test
    void bothMacesCancelAttacksDuringTheirOwnCooldown() {
        try (var items = mockConstruction(ItemStack.class, (item, context) ->
                    when(item.getItemMeta()).thenReturn(mock(ItemMeta.class)));
             var bukkit = mockStatic(Bukkit.class)) {
            for (String id : List.of("star_mace", "collapsing_star_mace")) {
                Fixture fixture = new Fixture(id, bukkit);
                when(fixture.cooldowns.isOnCooldown(fixture.playerId, id)).thenReturn(true);

                fixture.listener.onMaceDamage(fixture.damage);
                verify(fixture.damage).setCancelled(true);
                verify(fixture.damage, never()).setDamage(anyDouble());
                verify(fixture.registry, never()).setStarMaceWindBurstLevel(any(), anyInt());
                verify(fixture.target, never()).getWorld();

                EntityDamageByEntityEvent nonLivingAttack = mock(EntityDamageByEntityEvent.class);
                when(nonLivingAttack.getDamager()).thenReturn(fixture.player);
                when(nonLivingAttack.getEntity()).thenReturn(mock(Entity.class));
                fixture.listener.onMaceDamage(nonLivingAttack);
                verify(nonLivingAttack).setCancelled(true);
            }
        }
    }

    @Test
    void starMaceCooldownDoesNotBlockCollapsingStarAttacks() {
        try (var items = mockConstruction(ItemStack.class, (item, context) ->
                    when(item.getItemMeta()).thenReturn(mock(ItemMeta.class)));
             var bukkit = mockStatic(Bukkit.class)) {
            Fixture fixture = new Fixture("collapsing_star_mace", bukkit);
            when(fixture.cooldowns.isOnCooldown(fixture.playerId, "star_mace")).thenReturn(true);
            World world = mock(World.class);
            when(fixture.target.getWorld()).thenReturn(world);
            when(fixture.target.getLocation()).thenReturn(new Location(world, 0, 0, 0));

            fixture.listener.onMaceDamage(fixture.damage);

            verify(fixture.damage, never()).setCancelled(true);
            verify(fixture.damage).setDamage(4.0);
        }
    }

    @Test
    void hitBeforeSwingKeepsCollapsingStreakAndStartsCooldownAfterThreeHits() throws Exception {
        try (var items = mockConstruction(ItemStack.class, (item, context) ->
                    when(item.getItemMeta()).thenReturn(mock(ItemMeta.class)));
             var bukkit = mockStatic(Bukkit.class)) {
            Fixture fixture = new Fixture("collapsing_star_mace", bukkit);
            when(fixture.cooldowns.start(fixture.playerId, "collapsing_star_mace")).thenReturn(true);
            when(fixture.target.isValid()).thenReturn(true);
            World world = mock(World.class);
            ItemDisplay display = mock(ItemDisplay.class);
            when(world.spawn(any(Location.class), eq(ItemDisplay.class))).thenReturn(display);
            when(fixture.target.getWorld()).thenReturn(world);
            when(fixture.target.getLocation()).thenReturn(new Location(world, 0, 0, 0));
            LivingEntity victim = mock(LivingEntity.class);
            when(victim.getLocation()).thenReturn(new Location(world, 0, 0, 0));
            when(victim.getLastDamage()).thenReturn(6.0);
            AtomicInteger invulnerability = new AtomicInteger(13);
            AtomicInteger appliedDamage = new AtomicInteger();
            when(victim.getNoDamageTicks()).thenAnswer(call -> invulnerability.get());
            doAnswer(call -> {
                invulnerability.set(call.getArgument(0));
                return null;
            }).when(victim).setNoDamageTicks(anyInt());
            doAnswer(call -> {
                if (invulnerability.get() == 0) {
                    appliedDamage.incrementAndGet();
                }
                return null;
            }).when(victim).damage(1.0, fixture.player);
            LivingEntity nearby = mock(LivingEntity.class);
            when(nearby.getLocation()).thenReturn(new Location(world, 1, 0, 0));
            LivingEntity outside = mock(LivingEntity.class);
            when(outside.getLocation()).thenReturn(new Location(world, 2, 0, 0));
            when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(List.of(victim, nearby, outside, fixture.player));

            fixture.listener.onMaceDamage(fixture.damage);
            fixture.listener.onArmSwing(fixture.swing);
            fixture.runTick();
            verify(world, times(2)).spawnParticle(eq(Particle.ITEM), any(Location.class), eq(5),
                    eq(0.35), eq(0.35), eq(0.35), eq(0.08), any(ItemStack.class));
            verify(victim, times(2)).damage(1.0, fixture.player);
            fixture.listener.onMaceDamage(fixture.damage);
            fixture.listener.onArmSwing(fixture.swing);
            fixture.runTick();

            verify(fixture.cooldowns, never()).start(fixture.playerId, "collapsing_star_mace");
            verify(world, times(5)).spawnParticle(eq(Particle.ITEM), any(Location.class), eq(5),
                    eq(0.35), eq(0.35), eq(0.35), eq(0.08), any(ItemStack.class));
            fixture.listener.onMaceDamage(fixture.damage);
            fixture.listener.onArmSwing(fixture.swing);
            fixture.runTick();

            verify(fixture.damage, never()).setCancelled(true);
            verify(fixture.cooldowns).start(fixture.playerId, "collapsing_star_mace");
            verify(world, times(9)).spawnParticle(eq(Particle.ITEM), any(Location.class), eq(5),
                    eq(0.35), eq(0.35), eq(0.35), eq(0.08), any(ItemStack.class));
            verify(world, times(9)).spawn(any(Location.class), eq(ItemDisplay.class));
            ArgumentCaptor<Transformation> transformation = ArgumentCaptor.forClass(Transformation.class);
            verify(display, times(9)).setTransformation(transformation.capture());
            org.junit.jupiter.api.Assertions.assertEquals(1.5F, transformation.getValue().getScale().x);
            ArgumentCaptor<Runnable> removal = ArgumentCaptor.forClass(Runnable.class);
            verify(fixture.scheduler, times(9)).runTaskLater(eq(fixture.plugin), removal.capture(), eq(10L));
            removal.getAllValues().get(0).run();
            verify(display).remove();
            fixture.listener.shutdown();
            verify(victim, times(9)).damage(1.0, fixture.player);
            verify(nearby, times(9)).damage(1.0, fixture.player);
            verify(outside, never()).damage(anyDouble(), any(Entity.class));
            verify(fixture.player, never()).damage(anyDouble(), any(Entity.class));
            org.junit.jupiter.api.Assertions.assertEquals(9, appliedDamage.get());
            org.junit.jupiter.api.Assertions.assertEquals(13, invulnerability.get());
            verify(victim, times(9)).setLastDamage(6.0);
        }
    }

    @Test
    void showsSparksAndDamagesNearbyEntitiesWhenHitKillsTarget() {
        try (var items = mockConstruction(ItemStack.class, (item, context) ->
                    when(item.getItemMeta()).thenReturn(mock(ItemMeta.class)));
             var bukkit = mockStatic(Bukkit.class)) {
            Fixture fixture = new Fixture("collapsing_star_mace", bukkit);
            World world = mock(World.class);
            when(world.spawn(any(Location.class), eq(ItemDisplay.class))).thenReturn(mock(ItemDisplay.class));
            when(fixture.target.getWorld()).thenReturn(world);
            when(fixture.target.getLocation()).thenReturn(new Location(world, 0, 0, 0));
            when(fixture.target.isDead()).thenReturn(true);
            LivingEntity nearby = mock(LivingEntity.class);
            when(nearby.getLocation()).thenReturn(new Location(world, 1, 0, 0));
            when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(List.of(nearby));

            fixture.listener.onMaceDamage(fixture.damage);
            fixture.runTick();

            verify(world, times(2)).spawnParticle(eq(Particle.ITEM), any(Location.class), eq(5),
                    eq(0.35), eq(0.35), eq(0.35), eq(0.08), any(ItemStack.class));
            verify(nearby, times(2)).damage(1.0, fixture.player);
        }
    }

    private static final class Fixture {
        final GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
        final LegendaryItemRegistry registry = mock(LegendaryItemRegistry.class);
        final CooldownManager cooldowns = mock(CooldownManager.class);
        final Player player = mock(Player.class);
        final PlayerInventory inventory = mock(PlayerInventory.class);
        final ItemStack mace = mock(ItemStack.class);
        final LivingEntity target = mock(LivingEntity.class);
        final EntityDamageByEntityEvent damage = mock(EntityDamageByEntityEvent.class);
        final PlayerArmSwingEvent swing = mock(PlayerArmSwingEvent.class);
        final UUID playerId = UUID.randomUUID();
        final List<Runnable> tasks = new ArrayList<>();
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        final PhaseFiveMaceAbilityListener listener;

        Fixture(String id, org.mockito.MockedStatic<Bukkit> bukkit) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong()))
                    .thenAnswer(call -> {
                        if (call.getArgument(2, Long.class) == 1L) {
                            tasks.add(call.getArgument(1));
                        }
                        return null;
                    });
            when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
                tasks.add(call.getArgument(1));
                return null;
            });
            LegendaryItem item = mock(LegendaryItem.class);
            when(item.id()).thenReturn(id);
            when(plugin.getLegendaryItems()).thenReturn(registry);
            when(plugin.getCooldownManager()).thenReturn(cooldowns);
            when(plugin.getConfig()).thenReturn(mock(FileConfiguration.class));
            when(registry.identify(mace)).thenReturn(Optional.of(item));
            when(cooldowns.getRemaining(any(), any())).thenReturn(Duration.ofSeconds(30));
            when(player.getUniqueId()).thenReturn(playerId);
            when(player.getInventory()).thenReturn(inventory);
            when(inventory.getItemInMainHand()).thenReturn(mace);
            when(damage.getDamager()).thenReturn(player);
            when(damage.getEntity()).thenReturn(target);
            when(damage.getDamage()).thenReturn(4.0);
            when(damage.getFinalDamage()).thenReturn(4.0);
            when(swing.getPlayer()).thenReturn(player);
            when(swing.getHand()).thenReturn(EquipmentSlot.HAND);
            listener = new PhaseFiveMaceAbilityListener(plugin);
        }

        void runTick() {
            List<Runnable> ready = List.copyOf(tasks);
            tasks.clear();
            ready.forEach(Runnable::run);
        }
    }
}
