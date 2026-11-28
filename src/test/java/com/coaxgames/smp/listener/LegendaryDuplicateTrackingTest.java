package com.coaxgames.smp.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.item.LegendaryItem;
import com.coaxgames.smp.item.LegendaryItemRegistry;
import com.coaxgames.smp.storage.LegendaryDataStore;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LegendaryDuplicateTrackingTest {
    @Test
    void duplicatePickupIsLoggedPersistedAndRemoved() throws Exception {
        GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
        LegendaryItemRegistry registry = mock(LegendaryItemRegistry.class);
        LegendaryDataStore store = mock(LegendaryDataStore.class);
        Server server = mock(Server.class);
        LegendaryItem definition = mock(LegendaryItem.class);
        Player holder = mock(Player.class);
        Player recipient = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        Item entity = mock(Item.class);
        ItemStack existing = mock(ItemStack.class);
        ItemStack duplicate = mock(ItemStack.class);
        Location location = new Location(null, 1, 2, 3);
        UUID itemUuid = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        when(plugin.getLegendaryItems()).thenReturn(registry);
        when(plugin.getDataStore()).thenReturn(store);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(event.getItem()).thenReturn(entity);
        when(event.getEntity()).thenReturn(recipient);
        when(entity.getItemStack()).thenReturn(duplicate);
        when(entity.getLocation()).thenReturn(location);
        when(registry.identify(duplicate)).thenReturn(Optional.of(definition));
        when(registry.identify(existing)).thenReturn(Optional.of(definition));
        when(registry.getUniqueId(duplicate)).thenReturn(Optional.of(itemUuid));
        when(definition.id()).thenReturn("astral_sword");
        when(definition.protectedFromLossAndStorage()).thenReturn(true);
        doReturn(List.of(holder)).when(server).getOnlinePlayers();
        when(holder.getInventory()).thenReturn(inventory);
        when(inventory.getSize()).thenReturn(1);
        when(inventory.getItem(0)).thenReturn(existing);
        when(recipient.getUniqueId()).thenReturn(playerUuid);
        when(recipient.getName()).thenReturn("Recipient");

        new LegendaryRulesListener(plugin).onEntityPickup(event);

        verify(event).setCancelled(true);
        verify(store).recordItemRemoval("astral_sword", itemUuid, playerUuid,
                "Recipient", "duplicate_pickup", location);
        verify(entity).remove();
        when(holder.isOp()).thenReturn(true);
        new LegendaryRulesListener(plugin).onEntityPickup(event);
        verify(entity, times(1)).remove();
        verify(store, times(1)).recordItemRemoval("astral_sword", itemUuid, playerUuid,
                "Recipient", "duplicate_pickup", location);
        UUID offlineId = UUID.randomUUID();
        OfflinePlayer offline = mock(OfflinePlayer.class);
        doReturn(List.of()).when(server).getOnlinePlayers();
        when(store.getRecordedHolders("astral_sword")).thenReturn(List.of(offlineId));
        when(server.getOfflinePlayer(offlineId)).thenReturn(offline);
        new LegendaryRulesListener(plugin).onEntityPickup(event);
        verify(entity, times(2)).remove();
        verify(store, times(2)).recordItemRemoval("astral_sword", itemUuid, playerUuid,
                "Recipient", "duplicate_pickup", location);
    }

    @Test
    void inventoryDuplicateIsRecheckedBeforeRemoval() throws Exception {
        GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
        LegendaryItemRegistry registry = mock(LegendaryItemRegistry.class);
        LegendaryDataStore store = mock(LegendaryDataStore.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        LegendaryItem definition = mock(LegendaryItem.class);
        Player holder = mock(Player.class);
        Player recipient = mock(Player.class);
        PlayerInventory holderInventory = mock(PlayerInventory.class);
        PlayerInventory recipientInventory = mock(PlayerInventory.class);
        PlayerInventorySlotChangeEvent event = mock(PlayerInventorySlotChangeEvent.class);
        ItemStack existing = mock(ItemStack.class);
        ItemStack duplicate = mock(ItemStack.class);
        UUID itemUuid = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        Location location = new Location(null, 1, 2, 3);
        when(plugin.getLegendaryItems()).thenReturn(registry);
        when(plugin.getDataStore()).thenReturn(store);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(server.getScheduler()).thenReturn(scheduler);
        doReturn(List.of(holder, recipient)).when(server).getOnlinePlayers();
        when(holder.getInventory()).thenReturn(holderInventory);
        when(holderInventory.getSize()).thenReturn(1);
        when(holderInventory.getItem(0)).thenReturn(existing);
        when(recipient.getInventory()).thenReturn(recipientInventory);
        when(recipientInventory.getSize()).thenReturn(1);
        when(recipientInventory.getItem(0)).thenReturn(duplicate);
        when(recipient.isOnline()).thenReturn(true);
        when(recipient.getUniqueId()).thenReturn(playerUuid);
        when(recipient.getName()).thenReturn("Recipient");
        when(recipient.getLocation()).thenReturn(location);
        when(event.getPlayer()).thenReturn(recipient);
        when(event.getSlot()).thenReturn(0);
        when(event.getNewItemStack()).thenReturn(duplicate);
        when(registry.identify(existing)).thenReturn(Optional.of(definition));
        when(registry.identify(duplicate)).thenReturn(Optional.of(definition));
        when(registry.getUniqueId(duplicate)).thenReturn(Optional.of(itemUuid));
        when(definition.id()).thenReturn("astral_sword");
        when(definition.protectedFromLossAndStorage()).thenReturn(true);
        LegendaryRulesListener listener = new LegendaryRulesListener(plugin);
        listener.onPlayerInventorySlotChange(event);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq(plugin), task.capture());
        task.getValue().run();
        verify(store).recordItemRemoval("astral_sword", itemUuid, playerUuid,
                "Recipient", "duplicate_inventory", location);
        verify(recipientInventory).setItem(0, null);
    }

    @Test
    void joiningNonOperatorLosesDuplicateWhileOnlineHolderKeepsOriginal() throws Exception {
        GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
        LegendaryItemRegistry registry = mock(LegendaryItemRegistry.class);
        LegendaryDataStore store = mock(LegendaryDataStore.class);
        Server server = mock(Server.class);
        LegendaryItem definition = mock(LegendaryItem.class);
        Player holder = mock(Player.class);
        Player joining = mock(Player.class);
        PlayerInventory originalInventory = mock(PlayerInventory.class);
        PlayerInventory joiningInventory = mock(PlayerInventory.class);
        ItemStack original = mock(ItemStack.class);
        ItemStack duplicate = mock(ItemStack.class);
        UUID itemUuid = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        Location location = new Location(null, 1, 2, 3);
        when(plugin.getLegendaryItems()).thenReturn(registry);
        when(plugin.getDataStore()).thenReturn(store);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        doReturn(List.of(holder, joining)).when(server).getOnlinePlayers();
        when(holder.getInventory()).thenReturn(originalInventory);
        when(originalInventory.getSize()).thenReturn(1);
        when(originalInventory.getItem(0)).thenReturn(original);
        when(joining.getInventory()).thenReturn(joiningInventory);
        when(joiningInventory.getSize()).thenReturn(1);
        when(joiningInventory.getItem(0)).thenReturn(duplicate);
        when(joining.getUniqueId()).thenReturn(playerUuid);
        when(joining.getName()).thenReturn("Joining");
        when(joining.getLocation()).thenReturn(location);
        when(registry.identify(original)).thenReturn(Optional.of(definition));
        when(registry.identify(duplicate)).thenReturn(Optional.of(definition));
        when(registry.getUniqueId(duplicate)).thenReturn(Optional.of(itemUuid));
        when(definition.id()).thenReturn("astral_sword");
        when(definition.protectedFromLossAndStorage()).thenReturn(true);

        new LegendaryRulesListener(plugin).enforceJoiningPlayerUniqueness(joining);

        verify(store).recordItemRemoval("astral_sword", itemUuid, playerUuid,
                "Joining", "duplicate_join", location);
        verify(joiningInventory).setItem(0, null);
        verify(originalInventory, never()).setItem(anyInt(), any());
    }

    @Test
    void operatorDropIsRemovedAfterSuccessfulAuditUnlessDropWasCancelled() throws Exception {
        GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
        LegendaryItemRegistry registry = mock(LegendaryItemRegistry.class);
        LegendaryDataStore store = mock(LegendaryDataStore.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        LegendaryItem definition = mock(LegendaryItem.class);
        Player operator = mock(Player.class);
        PlayerDropItemEvent event = mock(PlayerDropItemEvent.class);
        Item entity = mock(Item.class);
        ItemStack stack = mock(ItemStack.class);
        Location location = new Location(null, 1, 2, 3);
        UUID itemUuid = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        when(plugin.getLegendaryItems()).thenReturn(registry);
        when(plugin.getDataStore()).thenReturn(store);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(server.getScheduler()).thenReturn(scheduler);
        when(event.getPlayer()).thenReturn(operator);
        when(event.getItemDrop()).thenReturn(entity);
        when(operator.isOp()).thenReturn(true);
        when(operator.getUniqueId()).thenReturn(playerUuid);
        when(operator.getName()).thenReturn("Operator");
        when(entity.getItemStack()).thenReturn(stack);
        when(entity.getLocation()).thenReturn(location);
        when(entity.isValid()).thenReturn(true);
        when(registry.identify(stack)).thenReturn(Optional.of(definition));
        when(registry.getUniqueId(stack)).thenReturn(Optional.of(itemUuid));
        when(definition.id()).thenReturn("astral_sword");
        when(definition.protectedFromLossAndStorage()).thenReturn(true);
        LegendaryRulesListener listener = new LegendaryRulesListener(plugin);

        listener.onPlayerDrop(event);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq(plugin), task.capture());
        when(event.isCancelled()).thenReturn(true);
        task.getValue().run();
        verify(entity, never()).remove();
        when(event.isCancelled()).thenReturn(false);
        task.getValue().run();
        verify(store).recordItemRemoval("astral_sword", itemUuid, playerUuid, "Operator", "operator_drop", location);
        verify(entity).remove();
        when(definition.protectedFromLossAndStorage()).thenReturn(false);
        listener.onPlayerDrop(event);
        verify(scheduler, times(1)).runTask(eq(plugin), any(Runnable.class));
    }
}
