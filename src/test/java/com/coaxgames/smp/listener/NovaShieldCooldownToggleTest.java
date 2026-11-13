package com.coaxgames.smp.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.item.LegendaryItem;
import com.coaxgames.smp.item.LegendaryItemRegistry;
import com.coaxgames.smp.util.ItemCooldownMarkers;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;

class NovaShieldCooldownToggleTest {
    @Test
    void shieldDisableIsSkippedOnlyWhileCooldownsAreBypassed() {
        try (var items = mockConstruction(ItemStack.class, (item, context) ->
                    when(item.getItemMeta()).thenReturn(mock(ItemMeta.class)));
             var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
            GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
            when(plugin.namespace()).thenReturn("galaxysmp");
            LegendaryItemRegistry registry = mock(LegendaryItemRegistry.class);
            when(plugin.getLegendaryItems()).thenReturn(registry);
            Player defender = mock(Player.class);
            Player attacker = mock(Player.class);
            when(defender.getUniqueId()).thenReturn(UUID.randomUUID());
            when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
            when(defender.hasActiveItem()).thenReturn(true);
            ItemStack shield = mock(ItemStack.class);
            when(defender.getActiveItem()).thenReturn(shield);
            LegendaryItem definition = mock(LegendaryItem.class);
            when(definition.id()).thenReturn("nova_shield");
            when(registry.identify(shield)).thenReturn(Optional.of(definition));
            PlayerInventory inventory = mock(PlayerInventory.class);
            ItemStack axe = mock(ItemStack.class);
            when(attacker.getInventory()).thenReturn(inventory);
            when(inventory.getItemInMainHand()).thenReturn(axe);
            when(axe.getType()).thenReturn(Material.NETHERITE_AXE);
            when(attacker.getCooledAttackStrength(0.0F)).thenReturn(1.0F);
            PrePlayerAttackEntityEvent preAttack = mock(PrePlayerAttackEntityEvent.class);
            when(preAttack.willAttack()).thenReturn(true);
            when(preAttack.getPlayer()).thenReturn(attacker);
            when(preAttack.getAttacked()).thenReturn(defender);
            EntityDamageByEntityEvent hit = mock(EntityDamageByEntityEvent.class);
            when(hit.getEntity()).thenReturn(defender);
            when(hit.getDamager()).thenReturn(attacker);
            when(hit.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)).thenReturn(true);
            when(hit.getDamage(EntityDamageEvent.DamageModifier.BLOCKING)).thenReturn(-1.0);
            when(hit.isCritical()).thenReturn(true);
            PhaseFiveAbilityListener listener = new PhaseFiveAbilityListener(plugin);

            when(plugin.bypassesCooldowns(defender)).thenReturn(true);
            for (int i = 0; i < 2; i++) {
                listener.onPreAttack(preAttack);
                listener.onShieldAxeHit(hit);
            }
            verify(defender, never()).clearActiveItem();
            verify(defender, never()).setCooldown(any(org.bukkit.NamespacedKey.class),
                    org.mockito.ArgumentMatchers.anyInt());

            when(plugin.bypassesCooldowns(defender)).thenReturn(false);
            for (int i = 0; i < 2; i++) {
                listener.onPreAttack(preAttack);
                listener.onShieldAxeHit(hit);
            }
            verify(defender).clearActiveItem();
            verify(defender).setCooldown(ItemCooldownMarkers.group("nova_shield"), NovaShieldRules.DISABLE_TICKS);
        }
    }
}
