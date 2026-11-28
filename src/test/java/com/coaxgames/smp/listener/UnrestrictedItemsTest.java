package com.coaxgames.smp.listener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.item.LegendaryItem;
import com.coaxgames.smp.item.LegendaryItemRegistry;
import com.coaxgames.smp.storage.LegendaryDataStore;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class UnrestrictedItemsTest {
    @Test
    void operatorDropIsNotRemovedAndStillTracksItsLocation() throws Exception {
        GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
        LegendaryItemRegistry registry = mock(LegendaryItemRegistry.class);
        LegendaryDataStore store = mock(LegendaryDataStore.class);
        Player player = mock(Player.class);
        PlayerDropItemEvent event = mock(PlayerDropItemEvent.class);
        Item entity = mock(Item.class);
        ItemStack stack = mock(ItemStack.class);
        LegendaryItem definition = mock(LegendaryItem.class);
        UUID itemId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        Location location = new Location(null, 1, 2, 3);
        when(plugin.getLegendaryItems()).thenReturn(registry);
        when(plugin.getDataStore()).thenReturn(store);
        when(event.getPlayer()).thenReturn(player);
        when(event.getItemDrop()).thenReturn(entity);
        when(entity.getItemStack()).thenReturn(stack);
        when(entity.getLocation()).thenReturn(location);
        when(player.isOp()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getLocation()).thenReturn(location);
        when(registry.isLegendary(stack)).thenReturn(true);
        when(registry.identify(stack)).thenReturn(Optional.of(definition));
        when(registry.getUniqueId(stack)).thenReturn(Optional.of(itemId));
        when(definition.id()).thenReturn("astral_sword");
        when(definition.trackUniqueInstance()).thenReturn(true);

        new LegendaryRulesListener(plugin).onPlayerDrop(event);

        verify(entity, never()).remove();
        verify(event, never()).setCancelled(true);
        verify(store).updateItemTracking("astral_sword", itemId, playerId, true, location, true);
        verify(store).updateItemTracking("astral_sword", itemId, null, false, location, true);
    }
}
