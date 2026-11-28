package com.coaxgames.smp.listener;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

class EnderPearlStackListenerTest {
    @Test
    void removesOldEightItemLimitWithoutChangingOtherStacks() {
        ItemStack pearl = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(pearl.getType()).thenReturn(Material.ENDER_PEARL);
        when(pearl.getItemMeta()).thenReturn(meta);
        when(meta.hasMaxStackSize()).thenReturn(true);
        when(meta.getMaxStackSize()).thenReturn(8);
        when(pearl.setItemMeta(meta)).thenReturn(true);
        assertTrue(EnderPearlStackListener.clearLegacyLimit(pearl));
        verify(meta).setMaxStackSize(null);
        when(meta.getMaxStackSize()).thenReturn(16);
        assertFalse(EnderPearlStackListener.clearLegacyLimit(pearl));
    }
}
