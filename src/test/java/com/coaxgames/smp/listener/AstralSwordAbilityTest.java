package com.coaxgames.smp.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
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
    void firstHitStartsFixedWindowLevelsCompoundAndCooldownDoesNotBlockDamage() {
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
            when(meta.hasAttributeModifiers()).thenReturn(false, true, true, true, true);
            List<AttributeModifier> modifiers = new ArrayList<>();
            when(meta.getAttributeModifiers(speed)).thenAnswer(call -> List.copyOf(modifiers));
            when(meta.addAttributeModifier(eq(speed), any(AttributeModifier.class))).thenAnswer(call -> {
                modifiers.add(call.getArgument(1));
                return true;
            });
            when(meta.removeAttributeModifier(eq(speed), any(AttributeModifier.class))).thenAnswer(call ->
                    modifiers.remove(call.getArgument(1)));
            PersistentDataContainer data = mock(PersistentDataContainer.class);
            when(meta.getPersistentDataContainer()).thenReturn(data);
            Map<NamespacedKey, Object> values = new HashMap<>();
            when(data.get(any(NamespacedKey.class), eq(PersistentDataType.LONG)))
                    .thenAnswer(call -> values.get(call.getArgument(0)));
            when(data.get(any(NamespacedKey.class), eq(PersistentDataType.BYTE)))
                    .thenAnswer(call -> values.get(call.getArgument(0)));
            when(data.has(any(NamespacedKey.class), eq(PersistentDataType.BYTE)))
                    .thenAnswer(call -> values.containsKey(call.getArgument(0)));
            doAnswer(call -> {
                values.put(call.getArgument(0), call.getArgument(2));
                return null;
            }).when(data).set(any(NamespacedKey.class), eq(PersistentDataType.LONG), any(Long.class));
            doAnswer(call -> {
                values.put(call.getArgument(0), call.getArgument(2));
                return null;
            }).when(data).set(any(NamespacedKey.class), eq(PersistentDataType.BYTE), any(Byte.class));
            doAnswer(call -> {
                values.remove(call.getArgument(0));
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
            AtomicLong now = new AtomicLong(100_000);
            var listener = new PhaseFourAbilityListener(plugin, target -> {}, () -> speed, now::get);
            NamespacedKey expiry = new NamespacedKey(plugin, "astral-sword-speed-expires");
            NamespacedKey cooldown = new NamespacedKey(plugin, "astral-sword-speed-cooldown");

            listener.onDamage(hit);
            assertEquals(110_000L, values.get(expiry));
            assertEquals(140_000L, values.get(cooldown));
            verify(meta).setAttributeModifiers(defaults);
            when(sword.getType()).thenReturn(Material.NETHERITE_SWORD);
            for (int level = 2; level <= 4; level++) {
                now.addAndGet(2_000);
                listener.onDamage(hit);
            }
            assertEquals(110_000L, values.get(expiry));
            ArgumentCaptor<AttributeModifier> captured = ArgumentCaptor.forClass(AttributeModifier.class);
            verify(meta, times(4)).addAttributeModifier(eq(speed), captured.capture());
            for (int i = 0; i < 4; i++) {
                AttributeModifier modifier = captured.getAllValues().get(i);
                assertEquals(Math.pow(1.35, i + 1), modifier.getAmount() + 1.0, 0.000001);
                assertEquals(EquipmentSlotGroup.MAINHAND, modifier.getSlotGroup());
            }
            listener.onDamage(hit);
            verify(meta, times(4)).addAttributeModifier(eq(speed), any(AttributeModifier.class));

            now.set(110_000);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(attacker));
            ArgumentCaptor<Runnable> tasks = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler, times(2)).runTaskTimer(eq(plugin), tasks.capture(), eq(1L), anyLong());
            tasks.getAllValues().get(1).run();
            assertTrue(modifiers.isEmpty());
            assertFalse(values.containsKey(expiry));
            now.set(139_999);
            listener.onDamage(hit);
            verify(meta, times(4)).addAttributeModifier(eq(speed), any(AttributeModifier.class));
            verify(hit, never()).setCancelled(true);
            verify(attacker, never()).getAttribute(speed);
            now.set(140_000);
            listener.onDamage(hit);
            verify(meta, times(5)).addAttributeModifier(eq(speed), any(AttributeModifier.class));
            assertEquals(150_000L, values.get(expiry));

            now.set(150_000);
            PlayerItemHeldEvent switchItem = mock(PlayerItemHeldEvent.class);
            when(switchItem.getPlayer()).thenReturn(attacker);
            when(switchItem.getNewSlot()).thenReturn(2);
            when(inventory.getItem(2)).thenReturn(sword);
            listener.onAstralHeldSlotChange(switchItem);
            verify(inventory).setItem(2, sword);
            assertTrue(modifiers.isEmpty());
            now.set(179_999);
            listener.onDamage(hit);
            verify(meta, times(5)).addAttributeModifier(eq(speed), any(AttributeModifier.class));

            when(inventory.getSize()).thenReturn(1);
            when(inventory.getItem(0)).thenReturn(sword);
            assertEquals(1, listener.resetAbilityProgress(attacker));
            listener.onDamage(hit);
            verify(meta, times(6)).addAttributeModifier(eq(speed), any(AttributeModifier.class));
        }
    }

    @Test
    void updatesExistingSwordLore() {
        GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
        when(plugin.namespace()).thenReturn("galaxysmp");
        LegendaryItemRegistry registry = spy(new LegendaryItemRegistry(plugin));
        LegendaryItem definition = mock(LegendaryItem.class);
        when(definition.id()).thenReturn("astral_sword");
        ItemStack sword = mock(ItemStack.class);
        doReturn(Optional.of(definition)).when(registry).identify(sword);
        ItemMeta meta = mock(ItemMeta.class);
        when(sword.getItemMeta()).thenReturn(meta);
        when(meta.lore()).thenReturn(List.of(
                Component.text("Every 4 consecutive hits: this sword gains attack speed for 30 seconds.")));
        when(sword.setItemMeta(meta)).thenReturn(true);

        assertTrue(registry.refreshAstralSwordSpeedLore(sword));
        verify(meta).lore(List.of(Component.text(
                "Hits increase sword speed by 1.35x (max 4); 10s effect, then 30s cooldown.", NamedTextColor.GRAY)));
    }
}
