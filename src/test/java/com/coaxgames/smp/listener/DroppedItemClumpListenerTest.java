package com.coaxgames.smp.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coaxgames.smp.item.LegendaryItem;
import com.coaxgames.smp.item.LegendaryItemRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

class DroppedItemClumpListenerTest {
    @Test
    void transferRespectsThe128ItemLimit() {
        assertEquals(120, DroppedItemClumpListener.transferable(120, 8));
        assertEquals(1, DroppedItemClumpListener.transferable(64, 127));
        assertEquals(0, DroppedItemClumpListener.transferable(1, 128));
        assertEquals(0, DroppedItemClumpListener.transferable(128, 1));
        assertThrows(IllegalArgumentException.class,
                () -> DroppedItemClumpListener.transferable(129, 1));
    }

    @Test
    void periodicScanClumpsTwoFullKuiperStacksWithoutAVanillaMergeEvent() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        World world = mock(World.class);
        LegendaryItemRegistry registry = mock(LegendaryItemRegistry.class);
        LegendaryItem definition = mock(LegendaryItem.class);
        when(plugin.namespace()).thenReturn("galaxysmp");
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.getWorlds()).thenReturn(List.of(world));

        Item source = mock(Item.class);
        Item target = mock(Item.class);
        ItemStack sourceStack = mock(ItemStack.class);
        ItemStack targetStack = mock(ItemStack.class);
        PersistentDataContainer sourceData = mock(PersistentDataContainer.class);
        PersistentDataContainer targetData = mock(PersistentDataContainer.class);
        when(world.getEntitiesByClass(Item.class)).thenReturn(List.of(source));
        when(source.isValid()).thenReturn(true, true, false);
        when(target.isValid()).thenReturn(true);
        when(source.getNearbyEntities(2.0, 2.0, 2.0)).thenReturn(List.of(target));
        when(source.getItemStack()).thenReturn(sourceStack);
        when(target.getItemStack()).thenReturn(targetStack);
        when(sourceStack.getType()).thenReturn(Material.SNOWBALL);
        when(targetStack.getType()).thenReturn(Material.SNOWBALL);
        when(sourceStack.getAmount()).thenReturn(64);
        when(targetStack.getAmount()).thenReturn(64);
        when(targetStack.getMaxStackSize()).thenReturn(64);
        when(sourceStack.isSimilar(targetStack)).thenReturn(true);
        when(registry.identify(sourceStack)).thenReturn(Optional.of(definition));
        when(registry.identify(targetStack)).thenReturn(Optional.of(definition));
        when(source.isPersistent()).thenReturn(true);
        when(target.isPersistent()).thenReturn(true);
        when(source.getPersistentDataContainer()).thenReturn(sourceData);
        when(target.getPersistentDataContainer()).thenReturn(targetData);
        when(sourceData.getKeys()).thenReturn(Set.of());
        when(targetData.getKeys()).thenReturn(Set.of());

        DroppedItemClumpListener listener = new DroppedItemClumpListener(plugin, registry);
        listener.start();
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskTimer(eq(plugin), task.capture(), eq(1L), eq(20L));
        task.getValue().run();

        verify(targetStack).setAmount(64);
        verify(targetData).set(any(NamespacedKey.class), eq(PersistentDataType.INTEGER), eq(128));
        verify(source).remove();
    }

    @Test
    void partialPickupLeavesExcessInTheClump() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.namespace()).thenReturn("galaxysmp");
        LegendaryItemRegistry registry = mock(LegendaryItemRegistry.class);
        DroppedItemClumpListener listener = new DroppedItemClumpListener(plugin, registry);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Item item = mock(Item.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        ItemStack visible = mock(ItemStack.class);
        ItemStack pickup = mock(ItemStack.class);
        ItemStack leftover = mock(ItemStack.class);
        when(player.getInventory()).thenReturn(inventory);
        when(item.getPersistentDataContainer()).thenReturn(data);
        when(data.has(any(NamespacedKey.class), eq(PersistentDataType.INTEGER))).thenReturn(true);
        when(data.get(any(NamespacedKey.class), eq(PersistentDataType.INTEGER))).thenReturn(128);
        when(item.getItemStack()).thenReturn(visible);
        when(visible.getAmount()).thenReturn(64);
        when(visible.getMaxStackSize()).thenReturn(64);
        when(visible.clone()).thenReturn(pickup);
        when(pickup.getMaxStackSize()).thenReturn(64);
        when(leftover.getAmount()).thenReturn(32);
        when(inventory.addItem(any(ItemStack.class)))
                .thenReturn(new HashMap<>())
                .thenReturn(new HashMap<>(Map.of(0, leftover)));

        EntityPickupItemEvent event = new EntityPickupItemEvent(player, item, 0);
        listener.onPickup(event);

        assertTrue(event.isCancelled());
        verify(data).set(any(NamespacedKey.class), eq(PersistentDataType.INTEGER), eq(32));
        verify(item, never()).remove();
    }
}
