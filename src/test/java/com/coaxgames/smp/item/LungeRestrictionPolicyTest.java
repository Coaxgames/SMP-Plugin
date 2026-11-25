package com.coaxgames.smp.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class LungeRestrictionPolicyTest {
    @Test
    void recognizesOnlyTheLungeEnchantmentKey() {
        assertTrue(LegendaryItemRegistry.isLungeEnchantmentKey(NamespacedKey.minecraft("lunge")));
        assertFalse(LegendaryItemRegistry.isLungeEnchantmentKey(NamespacedKey.minecraft("unbreaking")));
    }

    @Test
    void recognizesEveryVanillaSpearMaterial() {
        for (Material material : new Material[] {
                Material.WOODEN_SPEAR,
                Material.STONE_SPEAR,
                Material.COPPER_SPEAR,
                Material.IRON_SPEAR,
                Material.GOLDEN_SPEAR,
                Material.DIAMOND_SPEAR,
                Material.NETHERITE_SPEAR
        }) {
            ItemStack spear = mock(ItemStack.class);
            when(spear.getType()).thenReturn(material);
            assertTrue(LegendaryItemRegistry.isOrdinarySpear(spear));
        }
        ItemStack trident = mock(ItemStack.class);
        when(trident.getType()).thenReturn(Material.TRIDENT);
        assertFalse(LegendaryItemRegistry.isOrdinarySpear(trident));
    }
}
