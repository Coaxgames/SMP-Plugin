package com.coaxgames.smp.command;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.item.LegendaryItem;
import com.coaxgames.smp.storage.LegendaryDataStore.ItemRecord;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class GalaxyCommand implements TabExecutor {
    private static final String PERMISSION = "galaxy.admin";

    private final GalaxySmpPlugin plugin;

    public GalaxyCommand(GalaxySmpPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Component.text("You do not have permission to use this command."));
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        try {
            switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
                case "give" -> give(sender, label, args);
                case "reset" -> reset(sender, label, args);
                case "reset-abilities" -> resetAbilities(sender, label, args);
                case "list" -> list(sender);
                case "locate" -> locate(sender, label, args);
                default -> sendUsage(sender, label);
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not update persistent data for /" + label + ".", exception);
            sender.sendMessage(Component.text("The command failed because plugin data could not be saved. Check the server log."));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return matching(List.of("give", "reset", "reset-abilities", "list", "locate"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reset-abilities")) {
            List<String> targets = new ArrayList<>(List.of("@s", "@p", "@a"));
            targets.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            return matching(targets, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return matching(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if ((args.length == 3 && args[0].equalsIgnoreCase("give"))
                || (args.length == 2 && (args[0].equalsIgnoreCase("reset")
                || args[0].equalsIgnoreCase("locate")))) {
            return matching(plugin.getLegendaryItems().getAll().stream().map(LegendaryItem::id).toList(),
                    args[args.length - 1]);
        }
        return List.of();
    }

    private void give(CommandSender sender, String label, String[] args) throws IOException {
        if (args.length != 3) {
            sender.sendMessage(Component.text("Usage: /" + label + " give <player> <item>"));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("That player must be online."));
            return;
        }
        LegendaryItem definition = plugin.getLegendaryItems().get(args[2]).orElse(null);
        if (definition == null) {
            sender.sendMessage(Component.text("Unknown legendary item: " + args[2]));
            return;
        }
        ItemStack item = plugin.getLegendaryItems().createItem(definition.id());
        if (!plugin.getRitualManager().hasCapacity(target, definition.id(), item.getAmount())) {
            sender.sendMessage(Component.text(target.getName() + " already holds the configured maximum of "
                    + definition.id() + " (" + plugin.getLegendaryItems().maxPerPlayer(definition.id()) + ")."));
            return;
        }
        if (definition.trackUniqueInstance()) {
            int slot = target.getInventory().firstEmpty();
            if (slot < 0) {
                sender.sendMessage(Component.text(target.getName() + " has no empty inventory slot."));
                return;
            }
            UUID itemUuid = plugin.getLegendaryItems().getUniqueId(item)
                    .orElseThrow(() -> new IllegalStateException("New legendary item has no unique identity."));
            plugin.getDataStore().recordAdministrativeGrant(
                    definition.id(),
                    definition.maxCrafts(),
                    itemUuid,
                    target.getUniqueId(),
                    target.getLocation(),
                    definition.craftOnce()
            );
            target.getInventory().setItem(slot, item);
        } else {
            var leftovers = target.getInventory().addItem(item);
            if (!leftovers.isEmpty()) {
                sender.sendMessage(Component.text(target.getName() + "'s inventory has no room for the full stack."));
                return;
            }
        }
        sender.sendMessage(Component.text("Gave " + definition.id() + " to " + target.getName()
                + (definition.craftOnce() ? ". Its craft quota is now permanently used." : ".")));
    }

    private void reset(CommandSender sender, String label, String[] args) throws IOException {
        if (args.length != 2) {
            sender.sendMessage(Component.text("Usage: /" + label + " reset <item>"));
            return;
        }
        if (plugin.getLegendaryItems().get(args[1]).isEmpty()) {
            sender.sendMessage(Component.text("Unknown legendary item: " + args[1]));
            return;
        }
        int removed = plugin.getCooldownManager().clearForItem(args[1]);
        sender.sendMessage(Component.text("Cleared " + removed + " cooldown(s) for " + args[1]
                + ". Its permanent crafted-ever record was not changed."));
    }

    private void resetAbilities(CommandSender sender, String label, String[] args) throws IOException {
        if (args.length != 2) {
            sender.sendMessage(Component.text("Usage: /" + label + " reset-abilities <player|selector>"));
            return;
        }

        List<Player> targets = new ArrayList<>();
        if (args[1].startsWith("@")) {
            try {
                for (var entity : Bukkit.selectEntities(sender, args[1])) {
                    if (entity instanceof Player player) {
                        targets.add(player);
                    }
                }
            } catch (IllegalArgumentException exception) {
                sender.sendMessage(Component.text("Invalid player selector: " + args[1]));
                return;
            }
        } else {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target != null) {
                targets.add(target);
            }
        }
        if (targets.isEmpty()) {
            sender.sendMessage(Component.text("No online players matched: " + args[1]));
            return;
        }

        int clearedCooldowns = 0;
        for (Player target : targets) {
            clearedCooldowns += plugin.resetAbilities(target);
        }
        sender.sendMessage(Component.text("Reset ability cooldowns and hit progress for "
                + targets.size() + " player(s); cleared " + clearedCooldowns
                + " cooldown(s) and removed shield disable cooldowns."));
    }

    private void list(CommandSender sender) {
        List<LegendaryItem> items = plugin.getLegendaryItems().getAll().stream()
                .sorted(java.util.Comparator.comparing(LegendaryItem::id))
                .toList();
        if (items.isEmpty()) {
            sender.sendMessage(Component.text("No legendary items are registered yet."));
            return;
        }
        sender.sendMessage(Component.text("Registered legendary items:"));
        for (LegendaryItem item : items) {
            if (!item.craftOnce()) {
                sender.sendMessage(Component.text("- " + item.id() + " (repeatable)"));
                continue;
            }
            int crafted = plugin.getDataStore().getCraftCount(item.id());
            sender.sendMessage(Component.text("- " + item.id() + " (crafted " + crafted
                    + "/" + item.maxCrafts() + ")"));
        }
    }

    private void locate(CommandSender sender, String label, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(Component.text("Usage: /" + label + " locate <item>"));
            return;
        }
        if (plugin.getLegendaryItems().get(args[1]).isEmpty()) {
            sender.sendMessage(Component.text("Unknown legendary item: " + args[1]));
            return;
        }
        List<ItemRecord> records = plugin.getDataStore().getTrackedItems(args[1]);
        if (records.isEmpty()) {
            sender.sendMessage(Component.text("No tracked copies of " + args[1] + "."));
            return;
        }
        for (ItemRecord record : records) {
            String owner = playerName(record.ownerUuid());
            String location = record.world() == null
                    ? "location unknown"
                    : String.format(java.util.Locale.ROOT, "%s (%.1f, %.1f, %.1f)",
                            record.world(), record.x(), record.y(), record.z());
            sender.sendMessage(Component.text(record.itemUuid() + " | owner: " + owner + " | " + location));
        }
    }

    private String playerName(UUID playerUuid) {
        if (playerUuid == null) {
            return "unknown";
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
        return player.getName() == null ? playerUuid.toString() : player.getName();
    }

    private List<String> matching(List<String> candidates, String prefix) {
        String normalizedPrefix = prefix.toLowerCase(java.util.Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(java.util.Locale.ROOT).startsWith(normalizedPrefix)) {
                matches.add(candidate);
            }
        }
        return matches;
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(Component.text("Usage: /" + label
                + " give <player> <item> | reset <item> | reset-abilities <player|selector>"
                + " | list | locate <item>"));
    }
}
