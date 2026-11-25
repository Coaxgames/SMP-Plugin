package com.coaxgames.smp.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Breeze;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class BreezeDropListenerTest {
    @Test
    void doublesExistingRodDropsWithoutChangingOtherDrops() {
        Breeze breeze = mock(Breeze.class);
        EntityDeathEvent event = mock(EntityDeathEvent.class);
        ItemStack rods = mock(ItemStack.class);
        ItemStack charges = mock(ItemStack.class);
        when(rods.getType()).thenReturn(Material.BREEZE_ROD);
        when(rods.getAmount()).thenReturn(2);
        when(charges.getType()).thenReturn(Material.WIND_CHARGE);
        when(charges.getAmount()).thenReturn(1);
        List<ItemStack> drops = mock(ArrayList.class);
        when(drops.iterator()).thenReturn(List.of(rods, charges).iterator());
        when(event.getEntity()).thenReturn(breeze);
        when(event.getDrops()).thenReturn(drops);

        new BreezeDropListener().onDeath(event);

        verify(rods).setAmount(4);
        verify(charges, org.mockito.Mockito.never()).setAmount(org.mockito.Mockito.anyInt());
    }

    @Test
    void ignoresNonBreezesAndZeroRodDrops() {
        EntityDeathEvent event = mock(EntityDeathEvent.class);
        ItemStack charges = mock(ItemStack.class);
        when(charges.getType()).thenReturn(Material.WIND_CHARGE);
        List<ItemStack> drops = mock(ArrayList.class);
        when(drops.iterator()).thenReturn(List.of(charges).iterator());
        when(event.getDrops()).thenReturn(drops);

        new BreezeDropListener().onDeath(event);

        org.mockito.Mockito.verify(drops, org.mockito.Mockito.never()).add(org.mockito.Mockito.any());
    }
}
