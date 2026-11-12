package com.coaxgames.smp.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coaxgames.smp.GalaxySmpPlugin;
import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

class NovaShieldRescueVisualTest {
    @Test
    void rescueVisualUsesVanillaTotemOverlayAndKeepsShieldDisplay() {
        try (var items = mockConstruction(ItemStack.class, (item, context) ->
                    when(item.getItemMeta()).thenReturn(mock(ItemMeta.class)));
             var bukkit = mockStatic(Bukkit.class)) {
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(scheduler.runTaskTimer(any(), any(Runnable.class), anyLong(), anyLong()))
                    .thenReturn(mock(BukkitTask.class));
            GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
            when(plugin.namespace()).thenReturn("galaxysmp");
            World world = mock(World.class);
            Player player = mock(Player.class);
            when(player.getWorld()).thenReturn(world);
            when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
            when(player.getEyeLocation()).thenReturn(new Location(world, 0, 65.6, 0));
            ItemDisplay display = mock(ItemDisplay.class);
            when(world.spawn(any(Location.class), eq(ItemDisplay.class))).thenReturn(display);

            var listener = new PhaseFiveAbilityListener(plugin);
            listener.playNovaShieldRescueEffect(player);

            verify(player).playEffect(EntityEffect.PROTECTED_FROM_DEATH);
            verify(world).spawn(any(Location.class), eq(ItemDisplay.class));
        }
    }
}
