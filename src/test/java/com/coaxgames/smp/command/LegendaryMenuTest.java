package com.coaxgames.smp.command;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.item.LegendaryItem;
import com.coaxgames.smp.item.LegendaryItemRegistry;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class LegendaryMenuTest {
    @Test
    void menuClicksRequireCurrentOperatorStatusNotPermissionNode() {
        GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
        LegendaryItemRegistry registry = mock(LegendaryItemRegistry.class);
        LegendaryItem item = mock(LegendaryItem.class);
        ItemStack icon = mock(ItemStack.class);
        Player player = mock(Player.class);
        Inventory inventory = mock(Inventory.class);
        InventoryView view = mock(InventoryView.class);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(plugin.getLegendaryItems()).thenReturn(registry);
        when(registry.getAll()).thenReturn(List.of(item));
        when(item.id()).thenReturn("astral_sword");
        when(registry.createItem("astral_sword")).thenReturn(icon);
        when(player.getName()).thenReturn("Operator");
        when(player.isOp()).thenReturn(true, false);
        when(player.hasPermission("galaxy.admin")).thenReturn(false, true);
        when(view.getTopInventory()).thenReturn(inventory);
        when(event.getView()).thenReturn(view);
        when(event.getClickedInventory()).thenReturn(inventory);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getSlot()).thenReturn(0);
        LegendaryMenu menu = new LegendaryMenu(plugin);

        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), eq(54), any(Component.class)))
                    .thenAnswer(invocation -> {
                        when(inventory.getHolder()).thenReturn(invocation.getArgument(0));
                        return inventory;
                    });
            menu.open(player, 0);
            menu.onClick(event);
            menu.onClick(event);
        }

        verify(player, times(1)).performCommand("galaxy give Operator astral_sword 1");
        verify(event, times(2)).setCancelled(true);
    }
}
