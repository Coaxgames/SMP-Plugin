package com.coaxgames.smp.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class CobwebRecipeTest {
    @Test
    void acceptsOrdinaryStringButRejectsGalaxyString() {
        LegendaryItemRegistry registry = mock(LegendaryItemRegistry.class);
        ItemStack ordinary = mock(ItemStack.class);
        ItemStack galaxy = mock(ItemStack.class);
        LegendaryItem definition = mock(LegendaryItem.class);
        when(ordinary.getType()).thenReturn(Material.STRING);
        when(galaxy.getType()).thenReturn(Material.STRING);
        when(registry.identify(ordinary)).thenReturn(Optional.empty());
        when(registry.identify(galaxy)).thenReturn(Optional.of(definition));

        assertTrue(PhaseFourItems.isOrdinaryString(ordinary, registry));
        assertFalse(PhaseFourItems.isOrdinaryString(galaxy, registry));
    }
}
