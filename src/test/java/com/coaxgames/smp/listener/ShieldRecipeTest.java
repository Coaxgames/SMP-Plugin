package com.coaxgames.smp.listener;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class ShieldRecipeTest {
    @Test
    void requiresTheSameMaterialInEveryPlankSlot() {
        assertTrue(LegendaryCraftListener.sameShieldPlanks(
                Material.OAK_PLANKS, Material.OAK_PLANKS, Material.OAK_PLANKS, Material.OAK_PLANKS));
        assertTrue(LegendaryCraftListener.sameShieldPlanks(
                Material.BAMBOO_PLANKS, Material.BAMBOO_PLANKS, Material.BAMBOO_PLANKS, Material.BAMBOO_PLANKS));
        assertFalse(LegendaryCraftListener.sameShieldPlanks(
                Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.OAK_PLANKS, Material.OAK_PLANKS));
        assertFalse(LegendaryCraftListener.sameShieldPlanks(
                Material.OAK_PLANKS, Material.OAK_PLANKS, null, Material.OAK_PLANKS));
    }
}
