package com.coaxgames.smp.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.item.LegendaryItemRegistry;
import com.coaxgames.smp.storage.LegendaryDataStore;
import com.coaxgames.smp.util.CooldownManager;
import com.coaxgames.smp.util.ItemCooldownMarkers;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CooldownCommandTest {
    @TempDir
    Path dataDirectory;

    @Test
    void operatorTogglesOnlyOwnCooldownsAndStatusDoesNotChangeThem() throws Exception {
        LegendaryDataStore store = new LegendaryDataStore(dataDirectory);
        GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
        when(plugin.getDataStore()).thenReturn(store);
        when(plugin.bypassesCooldowns(any(Player.class))).thenCallRealMethod();
        Player first = mock(Player.class);
        Player second = mock(Player.class);
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        when(first.getUniqueId()).thenReturn(firstId);
        when(second.getUniqueId()).thenReturn(secondId);
        when(first.isOp()).thenReturn(true);
        when(second.isOp()).thenReturn(true);
        CooldownManager manager = new CooldownManager(new YamlConfiguration(), store,
                mock(LegendaryItemRegistry.class), id ->
                    id.equals(firstId) ? plugin.bypassesCooldowns(first) : plugin.bypassesCooldowns(second));
        when(plugin.getCooldownManager()).thenReturn(manager);
        CooldownCommand command = new CooldownCommand(plugin);

        assertTrue(manager.start(firstId, "star_mace", Duration.ofSeconds(30)));
        command.onCommand(first, null, "cooldown", new String[] {"status"});
        verify(first).sendMessage(Component.text("Your item cooldowns are enabled."));
        assertTrue(manager.isOnCooldown(firstId, "star_mace"));
        command.onCommand(first, null, "cooldown", new String[0]);
        verify(first).sendMessage(Component.text("Your item cooldowns are disabled."));
        verify(first).setCooldown(ItemCooldownMarkers.group("star_mace"), 0);
        verify(first).setCooldown(Material.ENDER_PEARL, 0);
        assertFalse(manager.isOnCooldown(firstId, "star_mace"));
        assertEquals(Duration.ZERO, manager.getRemaining(firstId, "star_mace"));
        assertTrue(manager.start(firstId, "collapsing_star_mace", Duration.ofSeconds(30)));
        assertEquals(0, store.getCooldownExpiry(firstId, "collapsing_star_mace"));
        assertTrue(store.areOperatorCooldownsEnabled(secondId));
        assertTrue(manager.start(secondId, "star_mace", Duration.ofSeconds(30)));
        assertTrue(manager.isOnCooldown(secondId, "star_mace"));

        command.onCommand(first, null, "cooldown", new String[0]);
        assertTrue(manager.isOnCooldown(firstId, "star_mace"));
        assertTrue(new LegendaryDataStore(dataDirectory).areOperatorCooldownsEnabled(firstId));
        assertEquals(List.of("status"), command.onTabComplete(first, null, "cooldown", new String[] {"st"}));
    }

    @Test
    void nonOperatorsCannotToggleOrBypassEvenWithSavedDisabledSetting() throws Exception {
        LegendaryDataStore store = new LegendaryDataStore(dataDirectory);
        UUID id = UUID.randomUUID();
        store.setOperatorCooldownsEnabled(id, false);
        GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
        when(plugin.getDataStore()).thenReturn(store);
        when(plugin.bypassesCooldowns(any(Player.class))).thenCallRealMethod();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOp()).thenReturn(false);
        CooldownCommand command = new CooldownCommand(plugin);

        command.onCommand(player, null, "cooldown", new String[0]);
        assertFalse(plugin.bypassesCooldowns(player));
        CooldownManager manager = new CooldownManager(new YamlConfiguration(), store,
                mock(LegendaryItemRegistry.class), ignored -> plugin.bypassesCooldowns(player));
        assertTrue(manager.start(id, "mass_orb", Duration.ofSeconds(120)));
        assertTrue(manager.isOnCooldown(id, "mass_orb"));
        assertFalse(store.areOperatorCooldownsEnabled(id));
        verify(player).sendMessage(Component.text("Only operators can use this command for themselves."));
        verify(player, never()).setCooldown(any(org.bukkit.NamespacedKey.class), org.mockito.ArgumentMatchers.anyInt());
        CommandSender console = mock(CommandSender.class);
        command.onCommand(console, null, "cooldown", new String[0]);
        verify(console).sendMessage(Component.text("Only operators can use this command for themselves."));
    }
}
