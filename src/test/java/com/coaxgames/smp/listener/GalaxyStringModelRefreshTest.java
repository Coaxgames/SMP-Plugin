package com.coaxgames.smp.listener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.item.LegendaryItem;
import com.coaxgames.smp.item.LegendaryItemRegistry;
import com.coaxgames.smp.ritual.RitualManager;
import com.coaxgames.smp.storage.LegendaryDataStore;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

class GalaxyStringModelRefreshTest {
    @Test
    void refreshesExistingLegendaryChestplateItemAndWornModelsOnInventoryAndPickup() {
        GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
        LegendaryItemRegistry registry = mock(LegendaryItemRegistry.class);
        when(plugin.getLegendaryItems()).thenReturn(registry);
        when(plugin.getRitualManager()).thenReturn(mock(RitualManager.class));
        when(plugin.getDataStore()).thenReturn(mock(LegendaryDataStore.class));
        LegendaryRulesListener listener = new LegendaryRulesListener(plugin);
        LegendaryItem definition = mock(LegendaryItem.class);
        when(definition.id()).thenReturn("solstice_chestplate");
        when(definition.trackUniqueInstance()).thenReturn(true);
        ItemStack base = mock(ItemStack.class);
        ItemMeta expected = mock(ItemMeta.class, RETURNS_DEEP_STUBS);
        NamespacedKey model = new NamespacedKey("galaxy", "solstice_chestplate");
        when(definition.createBaseItem()).thenReturn(base);
        when(base.getItemMeta()).thenReturn(expected);
        when(expected.hasItemModel()).thenReturn(true);
        when(expected.getItemModel()).thenReturn(model);
        when(expected.hasEquippable()).thenReturn(true);
        when(expected.getEquippable().getModel()).thenReturn(model);

        ItemStack chestplate = mock(ItemStack.class);
        ItemMeta oldMeta = mock(ItemMeta.class);
        when(chestplate.getType()).thenReturn(mock(Material.class));
        when(chestplate.getItemMeta()).thenReturn(oldMeta);
        when(chestplate.setItemMeta(oldMeta)).thenReturn(true);
        when(registry.identify(chestplate)).thenReturn(Optional.of(definition));
        Inventory inventory = mock(Inventory.class);
        when(inventory.getSize()).thenReturn(1);
        when(inventory.getItem(0)).thenReturn(chestplate);

        listener.refreshItemModels(inventory);
        verify(oldMeta).setItemModel(model);
        verify(oldMeta).setEquippable(expected.getEquippable());
        verify(inventory).setItem(0, chestplate);

        ItemStack pickedUp = mock(ItemStack.class);
        ItemMeta pickedUpMeta = mock(ItemMeta.class);
        when(pickedUp.getItemMeta()).thenReturn(pickedUpMeta);
        when(pickedUp.setItemMeta(pickedUpMeta)).thenReturn(true);
        when(registry.identify(pickedUp)).thenReturn(Optional.of(definition));
        when(registry.isLegendary(pickedUp)).thenReturn(true);
        when(registry.getUniqueId(pickedUp)).thenReturn(Optional.of(UUID.randomUUID()));
        Item dropped = mock(Item.class);
        when(dropped.getItemStack()).thenReturn(pickedUp);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        when(event.getItem()).thenReturn(dropped);
        when(event.getEntity()).thenReturn(player);

        listener.onEntityPickup(event);
        verify(pickedUpMeta).setItemModel(model);
        verify(pickedUpMeta).setEquippable(expected.getEquippable());
        verify(dropped).setItemStack(pickedUp);
    }

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
