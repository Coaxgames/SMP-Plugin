package com.coaxgames.smp.listener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.item.LegendaryItem;
import com.coaxgames.smp.item.LegendaryItemRegistry;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

class GalaxyStringModelRefreshTest {
    @Test
    void refreshesExistingInventoryAndPickedUpStringsWithoutChangingVanillaString() {
        GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
        LegendaryItemRegistry registry = mock(LegendaryItemRegistry.class);
        when(plugin.getLegendaryItems()).thenReturn(registry);
        LegendaryRulesListener listener = new LegendaryRulesListener(plugin);
        LegendaryItem definition = mock(LegendaryItem.class);
        ItemStack base = mock(ItemStack.class);
        ItemMeta expected = mock(ItemMeta.class);
        NamespacedKey model = new NamespacedKey("galaxy", "galaxy_string");
        when(definition.id()).thenReturn("galaxy_string");
        when(definition.createBaseItem()).thenReturn(base);
        when(base.getItemMeta()).thenReturn(expected);
        when(expected.hasItemModel()).thenReturn(true);
        when(expected.getItemModel()).thenReturn(model);

        Material string = mock(Material.class);
        ItemStack oldString = mock(ItemStack.class);
        ItemMeta oldMeta = mock(ItemMeta.class);
        when(oldString.getType()).thenReturn(string);
        when(oldString.getItemMeta()).thenReturn(oldMeta);
        when(oldString.setItemMeta(oldMeta)).thenReturn(true);
        when(registry.identify(oldString)).thenReturn(Optional.of(definition));
        ItemStack vanilla = mock(ItemStack.class);
        when(vanilla.getType()).thenReturn(string);
        when(registry.identify(vanilla)).thenReturn(Optional.empty());
        ItemStack currentString = mock(ItemStack.class);
        ItemMeta currentMeta = mock(ItemMeta.class);
        when(currentString.getType()).thenReturn(string);
        when(currentString.getItemMeta()).thenReturn(currentMeta);
        when(currentMeta.hasItemModel()).thenReturn(true);
        when(currentMeta.getItemModel()).thenReturn(model);
        when(registry.identify(currentString)).thenReturn(Optional.of(definition));
        Inventory inventory = mock(Inventory.class);
        when(inventory.getSize()).thenReturn(3);
        when(inventory.getItem(0)).thenReturn(oldString);
        when(inventory.getItem(1)).thenReturn(vanilla);
        when(inventory.getItem(2)).thenReturn(currentString);

        listener.refreshItemModels(inventory);

        verify(oldMeta).setItemModel(model);
        verify(inventory).setItem(0, oldString);
        verify(inventory, never()).setItem(1, vanilla);
        verify(inventory, never()).setItem(2, currentString);

        ItemStack pickedUp = mock(ItemStack.class);
        ItemMeta pickedUpMeta = mock(ItemMeta.class);
        when(pickedUp.getItemMeta()).thenReturn(pickedUpMeta);
        when(pickedUp.setItemMeta(pickedUpMeta)).thenReturn(true);
        when(registry.identify(pickedUp)).thenReturn(Optional.of(definition));
        Item dropped = mock(Item.class);
        when(dropped.getItemStack()).thenReturn(pickedUp);
        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        when(event.getItem()).thenReturn(dropped);

        listener.onEntityPickup(event);

        verify(pickedUpMeta).setItemModel(model);
        verify(dropped).setItemStack(pickedUp);
    }
}
