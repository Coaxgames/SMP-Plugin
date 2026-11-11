package com.coaxgames.smp.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
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
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;

class PhaseFiveMaceAbilityListenerTest {
    @Test
    void hitBeforeSwingKeepsCollapsingStreakAndStartsCooldownAfterTwoHits() throws Exception {
        try (var items = mockConstruction(ItemStack.class, (item, context) ->
                    when(item.getItemMeta()).thenReturn(mock(ItemMeta.class)));
             var bukkit = mockStatic(Bukkit.class)) {
            Fixture fixture = new Fixture("collapsing_star_mace", bukkit);
            when(fixture.cooldowns.start(fixture.playerId, "collapsing_star_mace")).thenReturn(true);
            when(fixture.target.isValid()).thenReturn(true);
            World world = mock(World.class);
            when(fixture.target.getWorld()).thenReturn(world);
            when(fixture.target.getLocation()).thenReturn(new Location(world, 0, 0, 0));
            when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(List.of());

            fixture.listener.onMaceDamage(fixture.damage);
            fixture.listener.onArmSwing(fixture.swing);
            fixture.runTick();
            fixture.listener.onMaceDamage(fixture.damage);
            fixture.listener.onArmSwing(fixture.swing);
            fixture.runTick();

            verify(fixture.cooldowns).start(fixture.playerId, "collapsing_star_mace");
            verify(world, times(5)).spawnParticle(eq(Particle.ITEM), any(Location.class), eq(1),
                    anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(ItemStack.class));
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
        final PhaseFiveMaceAbilityListener listener;

        Fixture(String id, org.mockito.MockedStatic<Bukkit> bukkit) {
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
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
