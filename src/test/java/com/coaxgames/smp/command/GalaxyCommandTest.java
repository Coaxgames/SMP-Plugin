package com.coaxgames.smp.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.item.LegendaryItem;
import com.coaxgames.smp.item.LegendaryItemRegistry;
import com.coaxgames.smp.ritual.RitualManager;
import com.coaxgames.smp.storage.LegendaryDataStore;
import io.papermc.paper.plugin.configuration.PluginMeta;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import net.kyori.adventure.text.Component;

class GalaxyCommandTest {
    @Test
    void versionReportsRunningPluginMetadataAndCompletes() {
        GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
        PluginMeta metadata = mock(PluginMeta.class);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("galaxy.admin")).thenReturn(true);
        when(plugin.getPluginMeta()).thenReturn(metadata);
        when(metadata.getVersion()).thenReturn("2.22.99");
        GalaxyCommand command = new GalaxyCommand(plugin, mock(LegendaryMenu.class));

        command.onCommand(sender, null, "galaxy", new String[] {"version"});

        verify(sender).sendMessage(Component.text("Running Galaxy SMP version: 2.22.99"));
        assertEquals(List.of("version"), command.onTabComplete(sender, null, "galaxy", new String[] {"ver"}));
    }

    @Test
    void versionRequiresAdminAndRejectsExtraArguments() {
        GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
        CommandSender sender = mock(CommandSender.class);
        GalaxyCommand command = new GalaxyCommand(plugin, mock(LegendaryMenu.class));
        command.onCommand(sender, null, "galaxy", new String[] {"version"});
        verify(sender).sendMessage(Component.text("You do not have permission to use this command."));
        verify(plugin, never()).getPluginMeta();

        when(sender.hasPermission("galaxy.admin")).thenReturn(true);
        command.onCommand(sender, null, "galaxy", new String[] {"version", "extra"});
        verify(sender).sendMessage(Component.text("Usage: /galaxy version"));
        verify(plugin, never()).getPluginMeta();
    }

    @Test
    void giveAllTargetsTheNamedPlayerAndKeepsOperatorGrantsExempt() throws Exception {
        GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
        LegendaryItemRegistry registry = mock(LegendaryItemRegistry.class);
        RitualManager rituals = mock(RitualManager.class);
        LegendaryDataStore store = mock(LegendaryDataStore.class);
        CommandSender sender = mock(CommandSender.class);
        Player target = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        LegendaryItem mace = mock(LegendaryItem.class);
        LegendaryItem ball = mock(LegendaryItem.class);
        ItemStack maceStack = mock(ItemStack.class);
        ItemStack ballStack = mock(ItemStack.class);
        UUID itemId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        Location location = mock(Location.class);

        when(sender.hasPermission("galaxy.admin")).thenReturn(true);
        when(plugin.getLegendaryItems()).thenReturn(registry);
        when(plugin.getRitualManager()).thenReturn(rituals);
        when(plugin.getDataStore()).thenReturn(store);
        when(registry.getAll()).thenReturn(List.of(mace, ball));
        when(mace.id()).thenReturn("star_mace");
        when(mace.trackUniqueInstance()).thenReturn(true);
        when(mace.maxCrafts()).thenReturn(1);
        when(ball.id()).thenReturn("kuiper_ball");
        when(registry.createItem("star_mace")).thenReturn(maceStack);
        when(registry.createItem("kuiper_ball")).thenReturn(ballStack);
        when(maceStack.getAmount()).thenReturn(1);
        when(ballStack.getAmount()).thenReturn(10);
        when(rituals.hasCapacity(target, "star_mace", 1)).thenReturn(true);
        when(rituals.hasCapacity(target, "kuiper_ball", 10)).thenReturn(true);
        when(target.getInventory()).thenReturn(inventory);
        when(target.getName()).thenReturn("Owner");
        when(target.isOp()).thenReturn(true);
        when(target.getUniqueId()).thenReturn(playerId);
        when(target.getLocation()).thenReturn(location);
        when(inventory.firstEmpty()).thenReturn(4);
        when(inventory.addItem(ballStack)).thenReturn(new HashMap<>());
        when(registry.getUniqueId(maceStack)).thenReturn(Optional.of(itemId));

        GalaxyCommand command = new GalaxyCommand(plugin, mock(LegendaryMenu.class));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayerExact("Owner")).thenReturn(target);
            command.onCommand(sender, null, "galaxy", new String[] {"give", "Owner", "all"});
        }

        verify(inventory).addItem(ballStack);
        verify(inventory).setItem(4, maceStack);
        verify(store).recordAdministrativeGrant("star_mace", 1, itemId, playerId, location, false);
        verify(store, never()).recordCraftedItem("star_mace", 1, itemId, playerId, location, true);
    }

    @Test
    void giveAllRespectsTheTargetsCapacity() {
        GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
        LegendaryItemRegistry registry = mock(LegendaryItemRegistry.class);
        RitualManager rituals = mock(RitualManager.class);
        CommandSender sender = mock(CommandSender.class);
        Player target = mock(Player.class);
        LegendaryItem mace = mock(LegendaryItem.class);
        ItemStack stack = mock(ItemStack.class);
        when(sender.hasPermission("galaxy.admin")).thenReturn(true);
        when(plugin.getLegendaryItems()).thenReturn(registry);
        when(plugin.getRitualManager()).thenReturn(rituals);
        when(registry.getAll()).thenReturn(List.of(mace));
        when(mace.id()).thenReturn("star_mace");
        when(registry.createItem("star_mace")).thenReturn(stack);
        when(stack.getAmount()).thenReturn(1);
        when(target.getName()).thenReturn("Player");

        GalaxyCommand command = new GalaxyCommand(plugin, mock(LegendaryMenu.class));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayerExact("Player")).thenReturn(target);
            command.onCommand(sender, null, "galaxy", new String[] {"give", "Player", "all"});
        }

        verify(rituals).hasCapacity(target, "star_mace", 1);
        verify(target, never()).getInventory();
    }

    @Test
    void completesAllForTheItemArgument() {
        GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
        LegendaryItemRegistry registry = mock(LegendaryItemRegistry.class);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("galaxy.admin")).thenReturn(true);
        when(plugin.getLegendaryItems()).thenReturn(registry);
        when(registry.getAll()).thenReturn(List.of());
        GalaxyCommand command = new GalaxyCommand(plugin, mock(LegendaryMenu.class));

        assertEquals(List.of("all"), command.onTabComplete(
                sender, null, "galaxy", new String[] {"give", "Player", "a"}));
    }
}
