package com.coaxgames.smp.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.item.LegendaryItem;
import com.coaxgames.smp.item.LegendaryItemRegistry;
import com.coaxgames.smp.storage.LegendaryDataStore;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OperatorLegendaryRemovalTest {
    private final GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
    private final LegendaryItemRegistry registry = mock(LegendaryItemRegistry.class);
    private final LegendaryDataStore store = mock(LegendaryDataStore.class);
    private final Server server = mock(Server.class);
    private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    private final Player player = mock(Player.class);
    private final PlayerInventory inventory = mock(PlayerInventory.class);
    private final Inventory top = mock(Inventory.class);
    private final InventoryView view = mock(InventoryView.class);
    private final ItemStack stack = mock(ItemStack.class);
    private final LegendaryItem definition = mock(LegendaryItem.class);
    private final UUID itemId = UUID.randomUUID();
    private final UUID playerId = UUID.randomUUID();
    private final Location location = new Location(null, 1, 2, 3);
    private final LegendaryRulesListener listener;

    OperatorLegendaryRemovalTest() {
        when(plugin.getLegendaryItems()).thenReturn(registry);
        when(plugin.getDataStore()).thenReturn(store);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(server.getScheduler()).thenReturn(scheduler);
        when(player.isOp()).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Operator");
        when(player.getLocation()).thenReturn(location);
        when(view.getTopInventory()).thenReturn(top);
        when(view.getBottomInventory()).thenReturn(inventory);
        when(top.getSize()).thenReturn(27);
        when(registry.identify(stack)).thenReturn(Optional.of(definition));
        when(registry.getUniqueId(stack)).thenReturn(Optional.of(itemId));
        when(registry.containsProtectedItem(stack)).thenReturn(true);
        when(registry.isLegendary(stack)).thenReturn(true);
        when(definition.id()).thenReturn("astral_sword");
        when(definition.protectedFromLossAndStorage()).thenReturn(true);
        when(stack.clone()).thenReturn(stack);
        listener = new LegendaryRulesListener(plugin);
    }

    @Test
    void cursorTransferIntoContainerIsCancelledThenRemovedAndLogged() throws Exception {
        InventoryClickEvent event = click(top, InventoryAction.PLACE_ALL);
        when(event.getCursor()).thenReturn(stack);
        when(player.getItemOnCursor()).thenReturn(stack);

        listener.onInventoryClick(event);

        verify(event).setCancelled(true);
        runScheduledRemoval();
        verify(player).setItemOnCursor(null);
        verifyRemoval("operator_inventory_transfer");
    }

    @Test
    void creativeDropIsDiscardedAndAuditedEvenWithoutAStoredCursor() throws Exception {
        InventoryCreativeEvent event = mock(InventoryCreativeEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getRawSlot()).thenReturn(-999);
        when(event.getCursor()).thenReturn(stack);

        listener.onInventoryClick(event);

        verify(event).setCancelled(true);
        runScheduledRemoval();
        verifyRemoval("operator_drop");
    }

    @Test
    void creativeInventoryInsertionIsNotTreatedAsAnOutgoingTransfer() {
        InventoryCreativeEvent event = mock(InventoryCreativeEvent.class);
        when(event.getRawSlot()).thenReturn(0);
        when(event.getCursor()).thenReturn(stack);

        org.junit.jupiter.api.Assertions.assertFalse(listener.removeOperatorInventoryTransfer(event, player));
        verify(scheduler, never()).runTask(eq(plugin), any(Runnable.class));
    }

    @Test
    void droppingFromInventoryRemovesTheItem() throws Exception {
        InventoryClickEvent event = click(inventory, InventoryAction.DROP_ALL_SLOT);
        when(event.getCurrentItem()).thenReturn(stack);
        when(inventory.getSize()).thenReturn(1);
        when(inventory.getItem(0)).thenReturn(stack);

        listener.onInventoryClick(event);
        verify(event).setCancelled(true);
        runScheduledRemoval();
        verify(inventory).setItem(0, null);
        verifyRemoval("operator_inventory_transfer");
    }

    @Test
    void hotbarSwapIntoContainerIsRemoved() throws Exception {
        InventoryClickEvent event = click(top, InventoryAction.HOTBAR_SWAP);
        when(event.getHotbarButton()).thenReturn(0);
        when(inventory.getItem(0)).thenReturn(stack);
        when(inventory.getSize()).thenReturn(1);

        listener.onInventoryClick(event);
        verify(event).setCancelled(true);
        runScheduledRemoval();
        verify(inventory).setItem(0, null);
        verifyRemoval("operator_inventory_transfer");
    }

    @Test
    void draggingIntoContainerRemovesCursorItem() throws Exception {
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        when(event.getRawSlots()).thenReturn(Set.of(0));
        when(event.getOldCursor()).thenReturn(stack);
        when(player.getItemOnCursor()).thenReturn(stack);

        listener.onInventoryDrag(event);
        verify(event).setCancelled(true);
        runScheduledRemoval();
        verify(player).setItemOnCursor(null);
        verifyRemoval("operator_inventory_transfer");
    }

    @Test
    void movingWithinInventoryDoesNotRemoveItem() {
        InventoryClickEvent event = click(inventory, InventoryAction.PLACE_ALL);
        when(event.getCursor()).thenReturn(stack);

        org.junit.jupiter.api.Assertions.assertFalse(listener.removeOperatorInventoryTransfer(event, player));

        verify(event, never()).setCancelled(true);
        verify(scheduler, never()).runTask(eq(plugin), any(Runnable.class));
    }

    @Test
    void operatorDeathRemovesLegendaryDropsEvenWithKeepInventory() throws Exception {
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        List<ItemStack> drops = new ArrayList<>(List.of(stack));
        when(event.getEntity()).thenReturn(player);
        when(event.getDrops()).thenReturn(drops);
        when(event.getKeepInventory()).thenReturn(true);

        listener.onPlayerDeath(event);
        org.junit.jupiter.api.Assertions.assertTrue(drops.isEmpty());
        verifyRemoval("operator_death_drop");
    }

    @Test
    void keepInventoryItemRemainsHeldAndIsTracked() throws Exception {
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getDrops()).thenReturn(new ArrayList<>());
        when(event.getKeepInventory()).thenReturn(true);
        when(definition.trackUniqueInstance()).thenReturn(true);
        when(inventory.getStorageContents()).thenReturn(new ItemStack[] {stack});
        when(inventory.getArmorContents()).thenReturn(new ItemStack[0]);
        doReturn(List.of(player)).when(server).getOnlinePlayers();
        when(server.getWorlds()).thenReturn(List.of());

        listener.onPlayerDeath(event);
        listener.startTracking();
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskTimer(eq(plugin), task.capture(), eq(20L), eq(20L));
        task.getValue().run();

        verify(inventory, never()).setItem(any(EquipmentSlot.class), any());
        verify(store).updateItemTracking("astral_sword", itemId, playerId, true, location, true);
        verify(store, never()).recordItemRemoval(any(), any(), any(), any(), any(), any());
    }

    @Test
    void itemFrameTransferRemovesLegendaryEvenIfAuditSaveFails() throws Exception {
        PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getRightClicked()).thenReturn(mock(ItemFrame.class));
        when(inventory.getItem(EquipmentSlot.HAND)).thenReturn(stack);
        org.mockito.Mockito.doThrow(new IOException("disk failure")).when(store)
                .recordItemRemoval("astral_sword", itemId, playerId, "Operator", "operator_world_transfer", location);

        listener.onInteractEntity(event);

        verify(event).setCancelled(true);
        verify(inventory).setItem(EquipmentSlot.HAND, null);
        verifyRemoval("operator_world_transfer");
    }

    @Test
    void usableItemsAreNotRemoved() {
        InventoryClickEvent event = click(top, InventoryAction.PLACE_ALL);
        when(event.getCursor()).thenReturn(stack);
        when(definition.protectedFromLossAndStorage()).thenReturn(false);
        when(registry.containsProtectedItem(stack)).thenReturn(false);
        when(registry.isLegendary(stack)).thenReturn(false);

        org.junit.jupiter.api.Assertions.assertFalse(listener.removeOperatorInventoryTransfer(event, player));

        verify(scheduler, never()).runTask(eq(plugin), any(Runnable.class));
    }

    private InventoryClickEvent click(Inventory clicked, InventoryAction action) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        when(event.getClickedInventory()).thenReturn(clicked);
        when(event.getAction()).thenReturn(action);
        when(event.getHotbarButton()).thenReturn(-1);
        return event;
    }

    private void runScheduledRemoval() {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq(plugin), task.capture());
        task.getValue().run();
    }

    private void verifyRemoval(String reason) throws IOException {
        verify(store).recordItemRemoval("astral_sword", itemId, playerId, "Operator", reason, location);
    }
}
