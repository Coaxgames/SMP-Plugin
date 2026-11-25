package com.coaxgames.smp.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
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
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.mockito.ArgumentCaptor;

class GalaxyCommandTest {
    @Test
    void locateLabelsMissingItemsUnknownAndProvidesBothActions() {
        GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
        LegendaryItemRegistry registry = mock(LegendaryItemRegistry.class);
        LegendaryDataStore store = mock(LegendaryDataStore.class);
        Server server = mock(Server.class);
        Player sender = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(sender.hasPermission("galaxy.admin")).thenReturn(true);
        when(sender.isOp()).thenReturn(true);
        when(plugin.getLegendaryItems()).thenReturn(registry);
        when(plugin.getDataStore()).thenReturn(store);
        when(plugin.getServer()).thenReturn(server);
        when(registry.get("astral_sword")).thenReturn(Optional.of(mock(LegendaryItem.class)));
        when(store.getTrackedItems("astral_sword")).thenReturn(List.of(
                new LegendaryDataStore.ItemRecord("astral_sword", id, null, "world",
                        1, 2, 3, false, true, 0)));
        when(server.getWorlds()).thenReturn(List.of());
        GalaxyCommand command = new GalaxyCommand(plugin, mock(LegendaryMenu.class));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            command.onCommand(sender, null, "galaxy", new String[] {"locate", "astral_sword"});
        }
        ArgumentCaptor<Component> message = ArgumentCaptor.forClass(Component.class);
        verify(sender).sendMessage(message.capture());
        String text = PlainTextComponentSerializer.plainText().serialize(message.getValue());
        assertTrue(text.contains("state: unknown"));
        assertTrue(text.contains("[remove]"));
        assertTrue(text.contains("[teleport]"));
        assertEquals(ClickEvent.runCommand("/galaxy find item " + id),
                message.getValue().children().getFirst().clickEvent());
        assertEquals(ClickEvent.runCommand("/galaxy remove-item " + id),
                message.getValue().children().get(1).clickEvent());
    }

    @Test
    void removalClearsHistoryAndMatchingLoadedItemsIncludingCursor() throws Exception {
        GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
        LegendaryItemRegistry registry = mock(LegendaryItemRegistry.class);
        LegendaryDataStore store = mock(LegendaryDataStore.class);
        Server server = mock(Server.class);
        World world = mock(World.class);
        Player operator = mock(Player.class);
        Player holder = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Item entity = mock(Item.class);
        ItemStack stack = mock(ItemStack.class);
        UUID id = UUID.randomUUID();
        when(operator.hasPermission("galaxy.admin")).thenReturn(true);
        when(operator.isOp()).thenReturn(true);
        when(plugin.getLegendaryItems()).thenReturn(registry);
        when(plugin.getDataStore()).thenReturn(store);
        when(plugin.getServer()).thenReturn(server);
        when(store.clearItemHistory(id)).thenReturn(true);
        when(server.getWorlds()).thenReturn(List.of(world));
        doReturn(List.of(holder)).when(server).getOnlinePlayers();
        when(world.getEntitiesByClass(Item.class)).thenReturn(List.of(entity));
        when(entity.getItemStack()).thenReturn(stack);
        when(registry.getUniqueId(stack)).thenReturn(Optional.of(id));
        when(holder.getInventory()).thenReturn(inventory);
        when(inventory.getSize()).thenReturn(2);
        when(inventory.getItem(1)).thenReturn(stack);
        when(holder.getItemOnCursor()).thenReturn(stack);

        new GalaxyCommand(plugin, mock(LegendaryMenu.class)).onCommand(
                operator, null, "galaxy", new String[] {"remove-item", id.toString()});

        verify(entity).remove();
        verify(inventory).setItem(1, null);
        verify(holder).setItemOnCursor(null);
        verify(operator).sendMessage(Component.text("History cleared. Removed 3 matching loaded drops/inventory or cursor stacks. "
                + "Offline players, unloaded chunks, containers, rituals and reclaim barrels were not searched."));
    }

    @Test
    void removalRejectsNonOperatorsAndActiveRituals() throws Exception {
        GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
        LegendaryDataStore store = mock(LegendaryDataStore.class);
        Player sender = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(sender.hasPermission("galaxy.admin")).thenReturn(true);
        when(plugin.getDataStore()).thenReturn(store);
        GalaxyCommand command = new GalaxyCommand(plugin, mock(LegendaryMenu.class));
        command.onCommand(sender, null, "galaxy", new String[] {"remove-item", id.toString()});
        verify(plugin, never()).getDataStore();
        when(sender.isOp()).thenReturn(true);
        when(store.hasActiveRitual(id)).thenReturn(true);
        command.onCommand(sender, null, "galaxy", new String[] {"remove-item", id.toString()});
        verify(store, never()).clearItemHistory(id);
    }

    @Test
    void historyClearDoesNotRemoveItemsAndTeleportRequiresOperator() throws Exception {
        GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
        LegendaryDataStore store = mock(LegendaryDataStore.class);
        Player sender = mock(Player.class);
        when(sender.hasPermission("galaxy.admin")).thenReturn(true);
        when(sender.isOp()).thenReturn(true);
        when(plugin.getDataStore()).thenReturn(store);
        when(store.clearItemHistory((String) null)).thenReturn(3);
        GalaxyCommand command = new GalaxyCommand(plugin, mock(LegendaryMenu.class));
        command.onCommand(sender, null, "galaxy", new String[] {"clear-history", "all"});
        verify(sender).sendMessage(Component.text(
                "Cleared 3 locate history entries. Items and craft limits were not removed; new sightings may appear again."));
        verify(plugin, never()).getServer();

        when(sender.isOp()).thenReturn(false);
        command.onCommand(sender, null, "galaxy",
                new String[] {"find", "item", UUID.randomUUID().toString()});
        verify(sender).sendMessage(Component.text("Only operators can teleport to items."));
        verify(sender, never()).teleportAsync(org.mockito.ArgumentMatchers.any());
    }

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
