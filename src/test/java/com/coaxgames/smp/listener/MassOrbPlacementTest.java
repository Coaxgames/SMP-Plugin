package com.coaxgames.smp.listener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.item.LegendaryItem;
import com.coaxgames.smp.item.LegendaryItemRegistry;
import java.util.Optional;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class MassOrbPlacementTest {
    @Test
    void preventsMassOrbPlacementWithoutBlockingOrdinaryItems() {
        GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
        LegendaryItemRegistry registry = mock(LegendaryItemRegistry.class);
        when(plugin.getLegendaryItems()).thenReturn(registry);
        LegendaryRulesListener listener = new LegendaryRulesListener(plugin);
        ItemStack orb = mock(ItemStack.class);
        ItemStack heavyCore = mock(ItemStack.class);
        ItemStack otherRegistered = mock(ItemStack.class);
        ItemStack protectedItem = mock(ItemStack.class);
        LegendaryItem orbDefinition = mock(LegendaryItem.class);
        LegendaryItem otherDefinition = mock(LegendaryItem.class);
        when(orbDefinition.id()).thenReturn("mass_orb");
        when(otherDefinition.id()).thenReturn("galaxy_string");
        when(registry.identify(orb)).thenReturn(Optional.of(orbDefinition));
        when(registry.identify(heavyCore)).thenReturn(Optional.empty());
        when(registry.identify(otherRegistered)).thenReturn(Optional.of(otherDefinition));
        when(registry.containsProtectedItem(protectedItem)).thenReturn(true);
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);

        when(event.getItemInHand()).thenReturn(orb);
        listener.onBlockPlace(event);
        verify(event).setCancelled(true);

        BlockPlaceEvent ordinaryEvent = mock(BlockPlaceEvent.class);
        when(ordinaryEvent.getItemInHand()).thenReturn(heavyCore, otherRegistered);
        listener.onBlockPlace(ordinaryEvent);
        listener.onBlockPlace(ordinaryEvent);
        verify(ordinaryEvent, never()).setCancelled(true);

        BlockPlaceEvent protectedEvent = mock(BlockPlaceEvent.class);
        when(protectedEvent.getItemInHand()).thenReturn(protectedItem);
        listener.onBlockPlace(protectedEvent);
        verify(protectedEvent).setCancelled(true);
    }
}
