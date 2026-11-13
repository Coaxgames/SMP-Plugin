package com.coaxgames.smp.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
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
import com.coaxgames.smp.util.CooldownManager;
import com.coaxgames.smp.util.ItemCooldownMarkers;
import io.papermc.paper.event.entity.EntityLungeEvent;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class NebulaSpearAbilityTest {
    @Test
    void retainsLungeCountAcrossWeaponSwapsAndBlocksLungesForCooldown() throws Exception {
        try (var items = mockConstruction(ItemStack.class, (item, context) ->
                    when(item.getItemMeta()).thenReturn(mock(ItemMeta.class)));
             var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
            GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
            when(plugin.namespace()).thenReturn("galaxysmp");
            when(plugin.getConfig()).thenReturn(mock(FileConfiguration.class));
            LegendaryItemRegistry registry = mock(LegendaryItemRegistry.class);
            CooldownManager cooldowns = mock(CooldownManager.class);
            when(plugin.getLegendaryItems()).thenReturn(registry);
            when(plugin.getCooldownManager()).thenReturn(cooldowns);
            Player player = mock(Player.class);
            PlayerInventory inventory = mock(PlayerInventory.class);
            UUID playerId = UUID.randomUUID();
            when(player.getUniqueId()).thenReturn(playerId);
            when(player.getInventory()).thenReturn(inventory);
            ItemStack spear = mock(ItemStack.class);
            ItemStack other = mock(ItemStack.class);
            LegendaryItem definition = mock(LegendaryItem.class);
            when(definition.id()).thenReturn("nebula_spear");
            when(registry.identify(spear)).thenReturn(Optional.of(definition));
            when(registry.identify(other)).thenReturn(Optional.empty());
            when(inventory.getItemInMainHand()).thenReturn(spear);
            World world = mock(World.class);
            when(player.getWorld()).thenReturn(world);
            when(player.getLocation()).thenAnswer(call -> new Location(world, 0, 0, 0));
            when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(List.of());
            AtomicBoolean cooling = new AtomicBoolean();
            when(cooldowns.isOnCooldown(playerId, "nebula_spear")).thenAnswer(call -> cooling.get());
            when(cooldowns.start(playerId, "nebula_spear")).thenAnswer(call -> {
                cooling.set(true);
                return true;
            });
            when(cooldowns.getRemaining(playerId, "nebula_spear")).thenReturn(Duration.ofSeconds(5));
            EntityLungeEvent event = mock(EntityLungeEvent.class);
            when(event.getEntity()).thenReturn(player);
            PhaseFiveAbilityListener listener = new PhaseFiveAbilityListener(plugin);

            for (int i = 0; i < 3; i++) {
                listener.onSuccessfulLunge(event);
            }
            when(inventory.getItemInMainHand()).thenReturn(other);
            listener.onSuccessfulLunge(event);
            when(inventory.getItemInMainHand()).thenReturn(spear);
            verify(cooldowns, never()).start(playerId, "nebula_spear");
            listener.onSuccessfulLunge(event);
            verify(cooldowns, never()).start(playerId, "nebula_spear");
            listener.onSuccessfulLunge(event);

            verify(cooldowns).start(playerId, "nebula_spear");
            verify(player).setCooldown(ItemCooldownMarkers.group("nebula_spear"), 100);
            verify(player).setVelocity(any(Vector.class));
            listener.onSuccessfulLunge(event);
            verify(event).setCancelled(true);
            verify(cooldowns, times(1)).start(playerId, "nebula_spear");

            cooling.set(false);
            for (int i = 0; i < 5; i++) {
                listener.onSuccessfulLunge(event);
            }
            verify(cooldowns, times(2)).start(playerId, "nebula_spear");
            verify(player, times(2)).setVelocity(any(Vector.class));
        }
    }

    @Test
    void refreshesExistingSpearCooldownLore() {
        GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
        when(plugin.namespace()).thenReturn("galaxysmp");
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        LegendaryItemRegistry registry = org.mockito.Mockito.spy(new LegendaryItemRegistry(plugin));
        ItemStack spear = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        LegendaryItem definition = mock(LegendaryItem.class);
        when(definition.id()).thenReturn("nebula_spear");
        doReturn(Optional.of(definition)).when(registry).identify(spear);
        when(spear.getItemMeta()).thenReturn(meta);
        when(spear.setItemMeta(meta)).thenReturn(true);
        when(meta.lore()).thenReturn(List.of(Component.text("Cooldown: 45 seconds.")));

        org.junit.jupiter.api.Assertions.assertTrue(registry.refreshNebulaCooldownLore(spear));
        verify(meta).lore(List.of(Component.text("Cooldown: 5 seconds.", NamedTextColor.GRAY)));
    }
}
