package com.coaxgames.smp.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.item.LegendaryItem;
import com.coaxgames.smp.item.LegendaryItemRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;

class VoidSwordAbilityTest {
    @Test
    void fourthHitAffectsItsTargetEvenAfterLongPauseAndThenRestartsCount() throws InterruptedException {
        try (var bukkit = mockStatic(Bukkit.class)) {
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(scheduler.runTaskTimer(any(), any(Runnable.class), anyLong(), anyLong())).thenReturn(null);
            GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
            when(plugin.namespace()).thenReturn("galaxysmp");
            FileConfiguration config = mock(FileConfiguration.class);
            when(plugin.getConfig()).thenReturn(config);
            LegendaryItemRegistry registry = mock(LegendaryItemRegistry.class);
            when(plugin.getLegendaryItems()).thenReturn(registry);
            ItemStack sword = mock(ItemStack.class);
            LegendaryItem definition = mock(LegendaryItem.class);
            when(definition.id()).thenReturn("void_sword");
            when(registry.identify(sword)).thenReturn(Optional.of(definition));
            Player attacker = mock(Player.class);
            when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
            PlayerInventory inventory = mock(PlayerInventory.class);
            when(attacker.getInventory()).thenReturn(inventory);
            when(inventory.getItemInMainHand()).thenReturn(sword);
            LivingEntity firstTarget = mock(LivingEntity.class);
            LivingEntity fourthTarget = mock(LivingEntity.class);
            EntityDamageByEntityEvent hit = mock(EntityDamageByEntityEvent.class);
            when(hit.getDamager()).thenReturn(attacker);
            when(hit.getEntity()).thenReturn(firstTarget);
            List<LivingEntity> affected = new ArrayList<>();
            var listener = new PhaseFourAbilityListener(plugin, affected::add);

            for (int i = 0; i < 3; i++) {
                listener.onDamage(hit);
            }
            assertEquals(List.of(), affected);
            Thread.sleep(3_100);
            when(hit.getEntity()).thenReturn(fourthTarget);
            listener.onDamage(hit);
            assertEquals(List.of(fourthTarget), affected);

            for (int i = 0; i < 4; i++) {
                listener.onDamage(hit);
            }
            assertEquals(List.of(fourthTarget, fourthTarget), affected);
        }
    }
}
