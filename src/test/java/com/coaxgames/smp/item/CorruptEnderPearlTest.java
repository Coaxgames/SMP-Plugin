package com.coaxgames.smp.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.stream.IntStream;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

class CorruptEnderPearlTest {
    @Test
    void exactlyOneOfTwentyRollsDropsAPearl() {
        assertEquals(1, IntStream.range(0, 20).filter(PhaseFiveItems::isCorruptPearlDropRoll).count());
        assertThrows(IllegalArgumentException.class, () -> PhaseFiveItems.isCorruptPearlDropRoll(-1));
        assertThrows(IllegalArgumentException.class, () -> PhaseFiveItems.isCorruptPearlDropRoll(20));
    }

    @Test
    void corruptPearlUsesItsCustomModelAndEightItemStackLimit() {
        ItemMeta meta = mock(ItemMeta.class);
        try (var construction = mockConstruction(ItemStack.class, (item, context) -> {
            assertEquals(Material.ENDER_PEARL, context.arguments().get(0));
            when(item.getItemMeta()).thenReturn(meta);
            when(item.setItemMeta(meta)).thenReturn(true);
        })) {
            var pearl = new PhaseFiveItems.CorruptEnderPearl(mock(LegendaryItemRegistry.class));
            ItemStack item = pearl.createBaseItem();

            verify(meta).setItemModel(new NamespacedKey("galaxy", "corrupt_ender_pearl"));
            verify(meta).setMaxStackSize(8);
            verify(item).setItemMeta(meta);
        }
    }
}
