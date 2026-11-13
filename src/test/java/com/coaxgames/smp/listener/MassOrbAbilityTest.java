package com.coaxgames.smp.listener;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.item.LegendaryItem;
import com.coaxgames.smp.item.LegendaryItemRegistry;
import com.coaxgames.smp.storage.LegendaryDataStore;
import com.coaxgames.smp.util.CooldownManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MassOrbAbilityTest {
    @Test
    void veryHighSlamIsCappedAndStartsCooldown() throws Exception {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
            when(plugin.namespace()).thenReturn("galaxysmp");
            YamlConfiguration config = new YamlConfiguration();
            config.set("cooldowns.mass_orb", 120);
            when(plugin.getConfig()).thenReturn(config);
            LegendaryItemRegistry registry = mock(LegendaryItemRegistry.class);
            LegendaryDataStore store = mock(LegendaryDataStore.class);
            CooldownManager cooldowns = new CooldownManager(config, store, registry);
            when(plugin.getLegendaryItems()).thenReturn(registry);
            when(plugin.getCooldownManager()).thenReturn(cooldowns);
            Player player = mock(Player.class);
            PlayerInventory inventory = mock(PlayerInventory.class);
            UUID id = UUID.randomUUID();
            when(player.getUniqueId()).thenReturn(id);
            when(player.getInventory()).thenReturn(inventory);
            when(player.getWidth()).thenReturn(0.6);
            when(player.getFallDistance()).thenReturn(1000F);
            ItemStack orb = mock(ItemStack.class);
            LegendaryItem definition = mock(LegendaryItem.class);
            when(definition.id()).thenReturn("mass_orb");
            when(definition.cooldown()).thenReturn(Optional.empty());
            when(registry.get("mass_orb")).thenReturn(Optional.of(definition));
            when(registry.identify(orb)).thenReturn(Optional.of(definition));
            when(inventory.getItemInMainHand()).thenReturn(orb);
            World world = mock(World.class);
            when(player.getWorld()).thenReturn(world);
            LivingEntity target = mock(LivingEntity.class);
            when(target.getUniqueId()).thenReturn(UUID.randomUUID());
            when(target.getBoundingBox()).thenReturn(new BoundingBox(-0.3, 118, -0.3, 0.3, 120, 0.3));
            when(world.getNearbyEntities(any(BoundingBox.class))).thenReturn(List.of(target));
            PlayerMoveEvent event = mock(PlayerMoveEvent.class);
            when(event.getPlayer()).thenReturn(player);
            when(event.getFrom()).thenReturn(new Location(world, 0, 121, 0));
            when(event.getTo()).thenReturn(new Location(world, 0, 119, 0));
            AtomicLong expiresAt = new AtomicLong();
            when(store.getCooldownExpiry(id, "mass_orb")).thenAnswer(call -> expiresAt.get());
            org.mockito.Mockito.doAnswer(call -> {
                expiresAt.set(call.getArgument(2));
                return null;
            }).when(store).setCooldownExpiry(org.mockito.ArgumentMatchers.eq(id),
                    org.mockito.ArgumentMatchers.eq("mass_orb"), org.mockito.ArgumentMatchers.anyLong());

            PhaseFourAbilityListener listener = new PhaseFourAbilityListener(plugin);
            long before = System.currentTimeMillis();
            listener.onMassOrbMove(event);

            verify(target).damage(10.0, player);
            assertTrue(expiresAt.get() >= before + 120_000L);
            assertTrue(expiresAt.get() <= System.currentTimeMillis() + 120_000L);
            ArgumentCaptor<Integer> ticks = ArgumentCaptor.forClass(Integer.class);
            verify(player).setCooldown(org.mockito.ArgumentMatchers.eq(
                    com.coaxgames.smp.util.ItemCooldownMarkers.group("mass_orb")), ticks.capture());
            assertTrue(ticks.getValue() > 0 && ticks.getValue() <= 2400);
            listener.onMassOrbMove(event);
            verify(target, org.mockito.Mockito.times(1)).damage(10.0, player);
        }
    }

    @Test
    void upgradesOldOrbsToSingleItemStacksAndCorrectsLoreWithoutChangingOtherItems() {
        GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
        when(plugin.namespace()).thenReturn("galaxysmp");
        YamlConfiguration config = new YamlConfiguration();
        config.set("cooldowns.mass_orb", 120);
        when(plugin.getConfig()).thenReturn(config);
        LegendaryItemRegistry registry = spy(new LegendaryItemRegistry(plugin));
        ItemStack orb = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        LegendaryItem definition = mock(LegendaryItem.class);
        when(definition.id()).thenReturn("mass_orb");
        doReturn(Optional.of(definition)).when(registry).identify(orb);
        when(orb.getItemMeta()).thenReturn(meta);
        when(orb.setItemMeta(meta)).thenReturn(true);
        when(meta.lore()).thenReturn(List.of(
                Component.text("While held, falling onto enemies deals 1/4 heart per block, up to 10 hearts."),
                Component.text("A slam attempt starts a 20-second cooldown."),
                Component.text("Fall damage is capped at 40 health points.")));

        assertTrue(registry.refreshMassOrb(orb));
        verify(meta).setMaxStackSize(1);
        verify(meta).lore(List.of(
                Component.text("Enemy slam (while held): 1/4 heart per block, up to 5 hearts.", NamedTextColor.GRAY),
                Component.text("A slam starts a 120-second cooldown.", NamedTextColor.GRAY),
                Component.text("Holder fall damage: up to 20 hearts before reduction.", NamedTextColor.GRAY)));

        ItemStack ordinary = mock(ItemStack.class);
        doReturn(Optional.empty()).when(registry).identify(ordinary);
        org.junit.jupiter.api.Assertions.assertFalse(registry.refreshMassOrb(ordinary));
        verify(ordinary, never()).setItemMeta(any());
    }

    @Test
    void refreshesHolderLoreUsingConfiguredHealthPointsAndTheirHeartEquivalent() {
        GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
        when(plugin.namespace()).thenReturn("galaxysmp");
        YamlConfiguration config = new YamlConfiguration();
        config.set("phase-four.mass-orb.maximum-damage", 35.0);
        when(plugin.getConfig()).thenReturn(config);
        LegendaryItemRegistry registry = spy(new LegendaryItemRegistry(plugin));
        ItemStack orb = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        LegendaryItem definition = mock(LegendaryItem.class);
        when(definition.id()).thenReturn("mass_orb");
        doReturn(Optional.of(definition)).when(registry).identify(orb);
        when(orb.getItemMeta()).thenReturn(meta);
        when(orb.setItemMeta(meta)).thenReturn(true);
        when(meta.hasMaxStackSize()).thenReturn(true);
        when(meta.getMaxStackSize()).thenReturn(1);
        when(meta.lore()).thenReturn(List.of(
                Component.text("Holder fall damage is capped at 40.0 health points before reduction.")));

        assertTrue(registry.refreshMassOrb(orb));
        verify(meta).lore(List.of(Component.text(
                "Holder fall damage: up to 17.5 hearts before reduction.", NamedTextColor.GRAY)));
    }
}
