package com.coaxgames.smp.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.item.LegendaryItem;
import com.coaxgames.smp.item.LegendaryItemRegistry;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;

class CosmicCrownRetaliationTest {
    @Test
    void retaliatesOnlyOnEverySecondDamagingHitAgainstThatAttacker() {
        try (var items = mockConstruction(ItemStack.class, (item, context) ->
                    when(item.getItemMeta()).thenReturn(mock(ItemMeta.class)));
             var bukkit = mockStatic(Bukkit.class)) {
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(scheduler.runTaskTimer(any(), any(Runnable.class), anyLong(), anyLong())).thenReturn(null);

            GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
            when(plugin.namespace()).thenReturn("galaxysmp");
            when(plugin.getConfig()).thenReturn(mock(FileConfiguration.class));
            LegendaryItemRegistry registry = mock(LegendaryItemRegistry.class);
            when(plugin.getLegendaryItems()).thenReturn(registry);
            ItemStack crown = mock(ItemStack.class);
            LegendaryItem definition = mock(LegendaryItem.class);
            when(definition.id()).thenReturn("cosmic_crown");
            when(registry.identify(crown)).thenReturn(Optional.of(definition));
            when(registry.removeCrownVanillaThorns(crown)).thenReturn(true, false, false, false);

            Player wearer = mock(Player.class);
            PlayerInventory inventory = mock(PlayerInventory.class);
            when(wearer.getInventory()).thenReturn(inventory);
            when(inventory.getHelmet()).thenReturn(crown);
            when(wearer.getUniqueId()).thenReturn(UUID.randomUUID());
            LivingEntity first = mock(LivingEntity.class);
            LivingEntity second = mock(LivingEntity.class);
            EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
            when(event.getEntity()).thenReturn(wearer);
            when(event.getFinalDamage()).thenReturn(0.0, 4.0);
            when(event.getDamager()).thenReturn(first, second, first, second);

            PhaseFiveAbilityListener listener = new PhaseFiveAbilityListener(plugin);
            listener.onCrownHit(event);
            verify(registry, never()).removeCrownVanillaThorns(crown);
            listener.onCrownHit(event);
            verify(inventory).setHelmet(crown);
            verify(first, never()).damage(eq(2.0), eq(wearer));
            listener.onCrownHit(event);
            verify(second).damage(eq(2.0), eq(wearer));
            listener.onCrownHit(event);
            verify(first, never()).damage(eq(2.0), eq(wearer));
            listener.onCrownHit(event);
            verify(second, times(2)).damage(eq(2.0), eq(wearer));
            verify(registry, times(4)).removeCrownVanillaThorns(crown);
        }
    }
}
