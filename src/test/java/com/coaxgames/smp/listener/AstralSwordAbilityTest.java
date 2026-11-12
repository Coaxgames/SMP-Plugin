package com.coaxgames.smp.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.item.LegendaryItem;
import com.coaxgames.smp.item.LegendaryItemRegistry;
import com.google.common.collect.HashMultimap;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AstralSwordAbilityTest {
    @Test
    void boostBelongsToSwordAndCannotRefreshUntilItExpires() {
        Attribute speed = (Attribute) Proxy.newProxyInstance(Attribute.class.getClassLoader(),
                new Class<?>[] {Attribute.class}, (proxy, method, args) -> {
                    if (method.getName().equals("hashCode")) {
                        return System.identityHashCode(proxy);
                    }
                    if (method.getName().equals("equals")) {
                        return proxy == args[0];
                    }
                    return null;
                });
        try (var bukkit = mockStatic(Bukkit.class)) {
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(scheduler.runTaskTimer(any(), any(Runnable.class), anyLong(), anyLong())).thenReturn(null);
            GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
            when(plugin.namespace()).thenReturn("galaxysmp");
            FileConfiguration config = mock(FileConfiguration.class);
            when(plugin.getConfig()).thenReturn(config);
            when(config.getLong("phase-four.combo-window-seconds", 3)).thenReturn(3L);
            when(config.getLong("phase-four.astral-sword.attack-speed-seconds", 30)).thenReturn(30L);
            LegendaryItemRegistry registry = mock(LegendaryItemRegistry.class);
            when(plugin.getLegendaryItems()).thenReturn(registry);
            ItemStack sword = mock(ItemStack.class);
            Material swordType = mock(Material.class);
            when(sword.getType()).thenReturn(swordType);
            var defaults = HashMultimap.<Attribute, AttributeModifier>create();
            defaults.put(speed, new AttributeModifier(new NamespacedKey("minecraft", "base_attack_speed"),
                    -2.4, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            when(swordType.getDefaultAttributeModifiers()).thenReturn(defaults);
            ItemMeta meta = mock(ItemMeta.class);
            when(sword.getItemMeta()).thenReturn(meta);
            when(sword.setItemMeta(meta)).thenReturn(true);
            when(meta.hasAttributeModifiers()).thenReturn(false, true);
            when(meta.addAttributeModifier(eq(speed), any(AttributeModifier.class))).thenReturn(true);
            PersistentDataContainer data = mock(PersistentDataContainer.class);
            when(meta.getPersistentDataContainer()).thenReturn(data);
            AtomicReference<Long> expiry = new AtomicReference<>();
            when(data.get(any(NamespacedKey.class), eq(PersistentDataType.LONG)))
                    .thenAnswer(call -> expiry.get());
            when(data.has(any(NamespacedKey.class), eq(PersistentDataType.LONG)))
                    .thenAnswer(call -> expiry.get() != null);
            doAnswer(call -> {
                expiry.set(call.getArgument(2));
                return null;
            }).when(data).set(any(NamespacedKey.class), eq(PersistentDataType.LONG), any(Long.class));
            doAnswer(call -> {
                expiry.set(null);
                return null;
            }).when(data).remove(any(NamespacedKey.class));
            LegendaryItem definition = mock(LegendaryItem.class);
            when(definition.id()).thenReturn("astral_sword");
            when(registry.identify(sword)).thenReturn(Optional.of(definition));
            Player attacker = mock(Player.class);
            when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
            PlayerInventory inventory = mock(PlayerInventory.class);
            when(attacker.getInventory()).thenReturn(inventory);
            when(inventory.getItemInMainHand()).thenReturn(sword);
            EntityDamageByEntityEvent hit = mock(EntityDamageByEntityEvent.class);
            when(hit.getDamager()).thenReturn(attacker);
            when(hit.getEntity()).thenReturn(mock(LivingEntity.class));
            var listener = new PhaseFourAbilityListener(plugin, target -> {}, () -> speed);

            long before = System.currentTimeMillis();
            for (int i = 0; i < 4; i++) {
                listener.onDamage(hit);
            }
            long after = System.currentTimeMillis();
            assertNotNull(expiry.get());
            assertTrue(expiry.get() >= before + 30_000 && expiry.get() <= after + 30_000);
            ArgumentCaptor<AttributeModifier> modifier = ArgumentCaptor.forClass(AttributeModifier.class);
            verify(meta).addAttributeModifier(eq(speed), modifier.capture());
            verify(meta).setAttributeModifiers(defaults);
            assertEquals(EquipmentSlotGroup.MAINHAND, modifier.getValue().getSlotGroup());
            assertEquals(AttributeModifier.Operation.MULTIPLY_SCALAR_1, modifier.getValue().getOperation());
            assertEquals(0.65, modifier.getValue().getAmount());
            verify(attacker, never()).getAttribute(speed);

            when(sword.getType()).thenReturn(Material.NETHERITE_SWORD);
            for (int i = 0; i < 4; i++) {
                listener.onDamage(hit);
            }
            verify(meta, times(1)).addAttributeModifier(eq(speed), any(AttributeModifier.class));

            PlayerItemHeldEvent switchItem = mock(PlayerItemHeldEvent.class);
            when(switchItem.getPlayer()).thenReturn(attacker);
            when(switchItem.getNewSlot()).thenReturn(2);
            when(inventory.getItem(2)).thenReturn(sword);
            listener.onAstralHeldSlotChange(switchItem);
            verify(inventory, never()).setItem(2, sword);
            expiry.set(System.currentTimeMillis() - 1);
            when(meta.getAttributeModifiers(speed)).thenReturn(List.of(modifier.getValue()));
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(attacker));
            ArgumentCaptor<Runnable> tasks = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler, times(2)).runTaskTimer(eq(plugin), tasks.capture(), eq(1L), anyLong());
            tasks.getAllValues().get(1).run();
            verify(meta).removeAttributeModifier(speed, modifier.getValue());
            verify(inventory, times(2)).setItemInMainHand(sword);
            for (int i = 0; i < 4; i++) {
                listener.onDamage(hit);
            }
            verify(meta, times(2)).addAttributeModifier(eq(speed), any(AttributeModifier.class));

            expiry.set(System.currentTimeMillis() - 1);
            listener.onAstralHeldSlotChange(switchItem);
            verify(inventory).setItem(2, sword);
        }
    }

    @Test
    void updatesExistingSwordLoreToDescribeTheWeaponBoost() {
        GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
        when(plugin.namespace()).thenReturn("galaxysmp");
        FileConfiguration config = mock(FileConfiguration.class);
        when(plugin.getConfig()).thenReturn(config);
        when(config.getLong("phase-four.astral-sword.attack-speed-seconds", 30)).thenReturn(30L);
        LegendaryItemRegistry registry = spy(new LegendaryItemRegistry(plugin));
        LegendaryItem definition = mock(LegendaryItem.class);
        when(definition.id()).thenReturn("astral_sword");
        ItemStack sword = mock(ItemStack.class);
        doReturn(Optional.of(definition)).when(registry).identify(sword);
        ItemMeta meta = mock(ItemMeta.class);
        when(sword.getItemMeta()).thenReturn(meta);
        when(meta.lore()).thenReturn(List.of(
                Component.text("Every 4 consecutive hits: gain increased attack speed for 30 seconds.")));
        when(sword.setItemMeta(meta)).thenReturn(true);

        assertTrue(registry.refreshAstralSwordSpeedLore(sword));
        verify(meta).lore(List.of(Component.text(
                "Every 4 consecutive hits: this sword gains attack speed for 30 seconds.", NamedTextColor.GRAY)));
    }
}
