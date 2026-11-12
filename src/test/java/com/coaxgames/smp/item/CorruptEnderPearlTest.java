package com.coaxgames.smp.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

class CorruptEnderPearlTest {
    @Test
    void exactlyOneOfTwentyRollsDropsAPearl() {
        assertEquals(1, IntStream.range(0, 20).filter(PhaseFiveItems::isCorruptPearlDropRoll).count());
        assertThrows(IllegalArgumentException.class, () -> PhaseFiveItems.isCorruptPearlDropRoll(-1));
        assertThrows(IllegalArgumentException.class, () -> PhaseFiveItems.isCorruptPearlDropRoll(20));
    }

    @Test
    void exactlyFifteenOfOneHundredLandingsTriggerWither() {
        assertEquals(15, IntStream.range(0, 100).filter(PhaseFiveItems::isCorruptPearlWitherRoll).count());
        assertThrows(IllegalArgumentException.class, () -> PhaseFiveItems.isCorruptPearlWitherRoll(-1));
        assertThrows(IllegalArgumentException.class, () -> PhaseFiveItems.isCorruptPearlWitherRoll(100));
    }

    @Test
    void onlyCorruptPearlLandingAppliesEffectOnceToThrower() {
        LegendaryItemRegistry registry = mock(LegendaryItemRegistry.class);
        LegendaryItem definition = mock(LegendaryItem.class);
        when(definition.id()).thenReturn("corrupt_ender_pearl");
        ItemStack corrupt = mock(ItemStack.class);
        ItemStack ordinary = mock(ItemStack.class);
        when(registry.identify(corrupt)).thenReturn(Optional.of(definition));
        when(registry.identify(ordinary)).thenReturn(Optional.empty());
        List<Player> affected = new ArrayList<>();
        AtomicInteger rolls = new AtomicInteger();
        var ability = new PhaseFiveItems.CorruptEnderPearl(registry, () -> {
            rolls.incrementAndGet();
            return 14;
        }, affected::add);
        EnderPearl pearl = mock(EnderPearl.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(pearl.getPersistentDataContainer()).thenReturn(data);
        AtomicReference<Byte> marker = new AtomicReference<>();
        doAnswer(call -> {
            marker.set(call.getArgument(2));
            return null;
        }).when(data).set(any(NamespacedKey.class), eq(PersistentDataType.BYTE), any(Byte.class));
        when(data.get(any(NamespacedKey.class), eq(PersistentDataType.BYTE)))
                .thenAnswer(call -> marker.get());
        doAnswer(call -> {
            marker.set(null);
            return null;
        }).when(data).remove(any(NamespacedKey.class));
        Player thrower = mock(Player.class);
        when(pearl.getShooter()).thenReturn(thrower);
        PlayerLaunchProjectileEvent launch = mock(PlayerLaunchProjectileEvent.class);
        when(launch.getProjectile()).thenReturn(pearl);
        ProjectileHitEvent landing = mock(ProjectileHitEvent.class);
        when(landing.getEntity()).thenReturn(pearl);

        when(launch.getItemStack()).thenReturn(ordinary);
        ability.onPearlLaunch(launch);
        ability.onPearlLanding(landing);
        assertTrue(affected.isEmpty());
        when(launch.getItemStack()).thenReturn(corrupt);
        ability.onPearlLaunch(launch);
        ability.onPearlLanding(landing);
        ability.onPearlLanding(landing);
        assertEquals(List.of(thrower), affected);
        assertEquals(1, rolls.get());
    }

    @Test
    void unsuccessfulRollDoesNotApplyWither() {
        LegendaryItemRegistry registry = mock(LegendaryItemRegistry.class);
        var affected = new ArrayList<Player>();
        var ability = new PhaseFiveItems.CorruptEnderPearl(registry, () -> 15, affected::add);
        EnderPearl pearl = mock(EnderPearl.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(pearl.getPersistentDataContainer()).thenReturn(data);
        when(data.get(any(NamespacedKey.class), eq(PersistentDataType.BYTE))).thenReturn((byte) 1);
        when(pearl.getShooter()).thenReturn(mock(Player.class));
        ProjectileHitEvent landing = mock(ProjectileHitEvent.class);
        when(landing.getEntity()).thenReturn(pearl);
        ability.onPearlLanding(landing);
        assertTrue(affected.isEmpty());
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
