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
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
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

    private final LegendaryMenu menu;

    public GalaxyCommand(GalaxySmpPlugin plugin, LegendaryMenu menu) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.menu = Objects.requireNonNull(menu, "menu");
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
                case "help" -> help(sender, label, args);
                case "list" -> list(sender);
                case "locate" -> locate(sender, label, args);
                case "find" -> find(sender, label, args);
                case "clear-history" -> clearHistory(sender, label, args);
                case "remove-item" -> removeItem(sender, label, args);
                case "version" -> {
                    if (args.length != 1) {
                        sender.sendMessage(Component.text("Usage: /" + label + " version"));
                    } else {
                        sender.sendMessage(Component.text(
                                "Running Galaxy SMP version: " + plugin.getPluginMeta().getVersion()));
                    }
                }
                case "menu" -> {
                    if (sender instanceof Player player) {
                        menu.open(player, 0);
                    } else {
                        sender.sendMessage(Component.text("Only players can open the menu."));
                    }
                }
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
            return matching(List.of("give", "reset", "reset-abilities", "list",
                    "locate", "find", "clear-history", "remove-item", "menu", "version", "help"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("clear-history")) {
            List<String> ids = new ArrayList<>(plugin.getLegendaryItems().getAll().stream()
                    .map(LegendaryItem::id).toList());
            ids.add("all");
            return matching(ids, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("help")) {
            return matching(List.of("1", "2", "3"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("find")) {
            return matching(List.of("item"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("find") && args[1].equalsIgnoreCase("item")) {
            return matching(findableItemIds(), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reset-abilities")) {
            List<String> targets = new ArrayList<>(List.of("@s", "@p", "@a"));
            targets.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            return matching(targets, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> targets = new ArrayList<>(List.of("@p", "all"));
            targets.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            return matching(targets, args[1]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give") && !args[2].equalsIgnoreCase("all")) {
            return matching(List.of("1", "8", "16", "32", "64"), args[3]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            List<String> itemIds = new ArrayList<>(plugin.getLegendaryItems().getAll().stream()
                    .map(LegendaryItem::id).toList());
            itemIds.add("all");
            return matching(itemIds, args[2]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("reset")
                || args[0].equalsIgnoreCase("locate"))) {
            return matching(plugin.getLegendaryItems().getAll().stream().map(LegendaryItem::id).toList(),
                    args[args.length - 1]);
        }
        return List.of();
    }

    private void give(CommandSender sender, String label, String[] args) throws IOException {
        if (args.length == 2 && args[1].equalsIgnoreCase("all")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only a player can use /" + label + " give all."));
                return;
            }
            giveAll(sender, player);
            return;
        }
        if (args.length < 3 || args.length > 4) {
            sender.sendMessage(Component.text("Usage: /" + label
                    + " give all | give <player|@p> all | give <player|@p> <item> [count]"));
            return;
        }

        Player target;
        if (args[1].equalsIgnoreCase("@p")) {
            target = Bukkit.selectEntities(sender, args[1]).stream()
                    .filter(Player.class::isInstance)
                    .map(Player.class::cast)
                    .findFirst()
                    .orElse(null);
        } else {
            target = Bukkit.getPlayerExact(args[1]);
        }
        if (target == null) {
            sender.sendMessage(Component.text(args[1].equalsIgnoreCase("@p")
                    ? "No players matched @p."
                    : "That player must be online."));
            return;
        }
        if (args[2].equalsIgnoreCase("all")) {
            if (args.length != 3) {
                sender.sendMessage(Component.text("Usage: /" + label + " give <player|@p> all"));
                return;
            }
            giveAll(sender, target);
            return;
        }
        LegendaryItem definition = plugin.getLegendaryItems().get(args[2]).orElse(null);
        if (definition == null) {
            sender.sendMessage(Component.text("Unknown legendary item: " + args[2]));
            return;
        }
        int count = 1;
        if (args.length == 4) {
            try {
                count = Integer.parseInt(args[3]);
            } catch (NumberFormatException exception) {
                count = 0;
            }
            if (count < 1 || count > 2304) {
                sender.sendMessage(Component.text("Count must be a number from 1 to 2304."));
                return;
            }
        }

        giveItem(sender, target, definition, count);
    }

    private void giveAll(CommandSender sender, Player target) throws IOException {
        List<LegendaryItem> items = plugin.getLegendaryItems().getAll().stream()
                .sorted(java.util.Comparator.comparing(LegendaryItem::id))
                .toList();
        if (items.isEmpty()) {
            sender.sendMessage(Component.text("No legendary items are registered yet."));
            return;
        }
        for (LegendaryItem item : items) {
            giveItem(sender, target, item, 1);
        }
    }

    private void giveItem(CommandSender sender, Player target, LegendaryItem definition, int count) throws IOException {
        int given = 0;
        String problem = null;
        for (int i = 0; i < count; i++) {
            ItemStack item = plugin.getLegendaryItems().createItem(definition.id());
            if (!plugin.getRitualManager().hasCapacity(target, definition.id(), item.getAmount())) {
                problem = target.getName() + " already holds the maximum of two legendary items.";
                break;
            }
            if (definition.trackUniqueInstance()) {
                int slot = target.getInventory().firstEmpty();
                if (slot < 0) {
                    problem = target.getName() + " has no empty inventory slot.";
                    break;
                }
                UUID itemUuid = plugin.getLegendaryItems().getUniqueId(item)
                        .orElseThrow(() -> new IllegalStateException("New legendary item has no unique identity."));
                if (!target.isOp() && definition.craftOnce()) {
                    if (!plugin.getDataStore().recordCraftedItem(definition.id(), definition.maxCrafts(),
                            itemUuid, target.getUniqueId(), target.getLocation(), true)) {
                        problem = "The server-wide limit for " + definition.id() + " has already been reached.";
                        break;
                    }
                } else {
                    plugin.getDataStore().recordAdministrativeGrant(definition.id(), definition.maxCrafts(),
                            itemUuid, target.getUniqueId(), target.getLocation(), false);
                }
                target.getInventory().setItem(slot, item);
            } else if (!target.getInventory().addItem(item).isEmpty()) {
                problem = target.getName() + "'s inventory has no room for the full stack.";
                break;
            }
            given++;
        }
        if (problem != null) {
            sender.sendMessage(Component.text(problem + " Gave " + given + " of " + count + "."));
        }
        if (given > 0) {
            sender.sendMessage(Component.text("Gave " + given + "x " + definition.id() + " to " + target.getName()
                    + (definition.craftOnce() && !target.isOp()
                    ? ". Its craft quota is now permanently used." : ".")));
        }
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
        if (com.coaxgames.smp.util.ItemCooldownMarkers.PERSISTED_ITEMS.contains(args[1])) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                com.coaxgames.smp.util.ItemCooldownMarkers.set(online, args[1], 0);
            }
        }
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

    private void help(CommandSender sender, String label, int page) {
        int safePage = Math.max(1, Math.min(3, page));
        sender.sendMessage(Component.text("Galaxy commands — Page " + safePage + "/3",
                NamedTextColor.GOLD));
        List<Component> lines = switch (safePage) {
            case 1 -> List.of(
                    helpLine("/" + label + " give all",
                            "Give every registered Galaxy item to yourself."),
                    helpLine("/" + label + " give <player|@p> all",
                            "Give every item to one online player."),
                    helpLine("/" + label + " give <player|@p> <item> [count]",
                            "Give one item, with limits and craft quotas enforced."),
                    helpLine("/" + label + " list",
                            "List registered items and permanent craft counts."),
                    helpLine("/" + label + " menu",
                            "Open the operator item menu."));
            case 2 -> List.of(
                    helpLine("/" + label + " locate <item>",
                            "Show item history and whether each UUID is currently found."),
                    helpLine("/" + label + " find",
                            "List current legendary item drops in loaded worlds."),
                    helpLine("/" + label + " find item <uuid>",
                            "Re-resolve a listed item and teleport to its current position."),
                    helpLine("/" + label + " clear-history <item|all>",
                            "Clear locate history without removing items or craft limits."),
                    helpLine("Locate/Find [remove]",
                            "Clear one entry and remove matching loaded drops or online inventory items."));
            default -> List.of(
                    helpLine("/" + label + " reset <item>",
                            "Clear its cooldowns without resetting permanent craft history."),
                    helpLine("/" + label + " reset-abilities <player|selector>",
                            "Reset matched players' ability cooldowns and progress."),
                    helpLine("/" + label + " version",
                            "Show the running plugin version."),
                    helpLine("/cooldown",
                            "Toggle this operator's cooldown enforcement."),
                    helpLine("/cooldown status",
                            "Report the current operator cooldown setting."),
                    helpLine("/checkforupdate",
                            "Cancel a pending restart and check for a newer release."));
        };
        lines.forEach(sender::sendMessage);
        String previous = safePage > 1 ? "[Previous]" : "";
        String next = safePage < 3 ? "[Next]" : "";
        Component navigation = Component.empty();
        if (!previous.isEmpty()) {
            navigation = navigation.append(Component.text(previous, NamedTextColor.AQUA)
                    .clickEvent(ClickEvent.runCommand("/" + label + " help " + (safePage - 1))));
        }
        if (!previous.isEmpty() && !next.isEmpty()) {
            navigation = navigation.append(Component.text("  "));
        }
        if (!next.isEmpty()) {
            navigation = navigation.append(Component.text(next, NamedTextColor.AQUA)
                    .clickEvent(ClickEvent.runCommand("/" + label + " help " + (safePage + 1))));
        }
        if (navigation != Component.empty()) {
            sender.sendMessage(navigation);
        }
    }

    private void help(CommandSender sender, String label, String[] args) {
        if (args.length > 2) {
            sender.sendMessage(Component.text("Usage: /" + label + " help [1-3]"));
            return;
        }
        if (args.length == 1) {
            help(sender, label, 1);
            return;
        }
        try {
            help(sender, label, Integer.parseInt(args[1]));
        } catch (NumberFormatException exception) {
            sender.sendMessage(Component.text("Help page must be 1, 2, or 3."));
        }
    }

    private Component helpLine(String command, String description) {
        return Component.text(command, NamedTextColor.AQUA)
                .append(Component.text(" — " + description, NamedTextColor.GRAY));
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
            LiveItem live = findLiveItem(record.itemUuid());
            String owner = playerName(record.ownerUuid())
                    + (record.ownerOperator() ? " (operator-owned)" : "");
            String location = record.world() == null
                    ? "last location unknown"
                    : String.format(java.util.Locale.ROOT, "%s (%.1f, %.1f, %.1f)",
                            record.world(), record.x(), record.y(), record.z());
            String updated = record.updatedAtMillis() <= 0L
                    ? "never"
                    : java.time.Instant.ofEpochMilli(record.updatedAtMillis())
                            .atZone(java.time.ZoneId.systemDefault())
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            Component message = Component.text(record.itemUuid()
                    + " | state: " + (live == null ? "unknown (not found in loaded worlds or online inventories)" : "found")
                    + " | last owner: " + owner + " | last location: " + location + " | updated: " + updated);
            if (sender instanceof Player player && player.isOp()) {
                message = message.append(teleportButton(label, record.itemUuid()))
                        .append(removeButton(label, record.itemUuid()));
            }
            sender.sendMessage(message);
        }
    }

    private void find(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only a player can teleport to an item."));
            return;
        }
        if (args.length == 1) {
            listWorldItems(player);
            return;
        }
        if (args.length != 3 || !args[1].equalsIgnoreCase("item")) {
            sender.sendMessage(Component.text("Usage: /" + label + " find [item <uuid>]"));
            return;
        }
        if (!player.isOp()) {
            sender.sendMessage(Component.text("Only operators can teleport to items."));
            return;
        }
        UUID key;
        try {
            key = UUID.fromString(args[2]);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(Component.text("That is not a valid UUID."));
            return;
        }
        LiveItem live = findLiveItem(key);
        if (live == null || live.location().getWorld() == null) {
            sender.sendMessage(Component.text("No current world or inventory location found for UUID " + key + "."));
            return;
        }
        org.bukkit.Location item = live.location();
        org.bukkit.World world = item.getWorld();
        org.bukkit.Location view = inspectionPoint(item);
        view.setDirection(item.toVector().subtract(view.toVector()));
        player.teleportAsync(view).thenAccept(success -> player.sendMessage(Component.text(success
                ? String.format(java.util.Locale.ROOT, "Viewing %s (%s) live at %.1f, %.1f, %.1f in %s.",
                        live.name(), live.itemId() == null ? "unidentified" : live.itemId(),
                        item.getX(), item.getY(), item.getZ(), world.getName())
                : "The teleport was cancelled.")));
    }

    private void listWorldItems(Player operator) {
        java.util.Map<UUID, LiveItem> found = new java.util.LinkedHashMap<>();
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            for (org.bukkit.entity.Item item : world.getEntitiesByClass(org.bukkit.entity.Item.class)) {
                String itemId = plugin.getLegendaryItems().identify(item.getItemStack())
                        .map(LegendaryItem::id).orElse(null);
                if (itemId == null) {
                    continue;
                }
                UUID key = plugin.getLegendaryItems().getUniqueId(item.getItemStack()).orElse(item.getUniqueId());
                found.put(key, new LiveItem(
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                                .serialize(item.getItemStack().effectiveName()),
                        itemId,
                        item.getLocation().clone()));
            }
        }
        if (found.isEmpty()) {
            operator.sendMessage(Component.text("No legendary items are currently found as world entities."));
            return;
        }
        for (var entry : found.entrySet()) {
            UUID uuid = entry.getKey();
            LiveItem item = entry.getValue();
            Component message = Component.text(item.name() + " (" + item.itemId() + ") at "
                            + item.location().getBlockX() + ", " + item.location().getBlockY() + ", "
                            + item.location().getBlockZ() + " in " + item.location().getWorld().getName());
            if (operator.isOp()) {
                message = message.append(teleportButton("galaxy", uuid)).append(removeButton("galaxy", uuid));
            }
            operator.sendMessage(message);
        }
    }

    private Component teleportButton(String label, UUID uuid) {
        return Component.text(" [teleport]", NamedTextColor.AQUA)
                .clickEvent(ClickEvent.runCommand("/" + label + " find item " + uuid))
                .hoverEvent(Component.text("Resolve the current live position and teleport"));
    }

    private Component removeButton(String label, UUID uuid) {
        return Component.text(" [remove]", NamedTextColor.RED)
                .clickEvent(ClickEvent.runCommand("/" + label + " remove-item " + uuid))
                .hoverEvent(Component.text("Clear history and remove matching loaded drops and online inventory items"));
    }

    private boolean mayManageHistory(CommandSender sender) {
        if (sender instanceof Player player && !player.isOp()) {
            sender.sendMessage(Component.text("Only operators can clear history or remove items."));
            return false;
        }
        return true;
    }

    private void clearHistory(CommandSender sender, String label, String[] args) throws IOException {
        if (!mayManageHistory(sender)) {
            return;
        }
        if (args.length != 2) {
            sender.sendMessage(Component.text("Usage: /" + label + " clear-history <item|all>"));
            return;
        }
        String itemId = args[1].equalsIgnoreCase("all") ? null : args[1];
        if (itemId != null && plugin.getLegendaryItems().get(itemId).isEmpty()) {
            sender.sendMessage(Component.text("Unknown legendary item: " + itemId));
            return;
        }
        int count = plugin.getDataStore().clearItemHistory(itemId);
        sender.sendMessage(Component.text("Cleared " + count
                + " locate history entries. Items and craft limits were not removed; new sightings may appear again."));
    }

    private void removeItem(CommandSender sender, String label, String[] args) throws IOException {
        if (!mayManageHistory(sender)) {
            return;
        }
        if (args.length != 2) {
            sender.sendMessage(Component.text("Usage: /" + label + " remove-item <uuid>"));
            return;
        }
        UUID key;
        try {
            key = UUID.fromString(args[1]);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(Component.text("That is not a valid UUID."));
            return;
        }
        if (plugin.getDataStore().hasActiveRitual(key)
                || plugin.getDataStore().getReclaimMarkers().stream()
                        .anyMatch(marker -> marker.itemUuid().equals(key))) {
            sender.sendMessage(Component.text(
                    "Cannot remove an item in an active ritual or reclaim barrel; its protected state was not changed."));
            return;
        }
        boolean cleared = plugin.getDataStore().clearItemHistory(key);
        int removed = 0;
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            for (org.bukkit.entity.Item item : world.getEntitiesByClass(org.bukkit.entity.Item.class)) {
                ItemStack stack = item.getItemStack();
                if (matchesItem(key, stack) || key.equals(item.getUniqueId())
                        && plugin.getLegendaryItems().identify(stack).isPresent()) {
                    item.remove();
                    removed++;
                }
            }
        }
        for (Player holder : plugin.getServer().getOnlinePlayers()) {
            var inventory = holder.getInventory();
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                if (matchesItem(key, inventory.getItem(slot))) {
                    inventory.setItem(slot, null);
                    removed++;
                }
            }
            if (matchesItem(key, holder.getItemOnCursor())) {
                holder.setItemOnCursor(null);
                removed++;
            }
        }
        sender.sendMessage(Component.text((cleared ? "History cleared. " : "No visible history entry. ")
                + "Removed " + removed + " matching loaded drops/inventory or cursor stacks. "
                + "Offline players, unloaded chunks, containers, rituals and reclaim barrels were not searched."));
    }

    private boolean matchesItem(UUID key, ItemStack stack) {
        return stack != null && plugin.getLegendaryItems().getUniqueId(stack).filter(key::equals).isPresent();
    }

    private LiveItem findLiveItem(UUID key) {
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            for (org.bukkit.entity.Item item : world.getEntitiesByClass(org.bukkit.entity.Item.class)) {
                UUID itemUuid = plugin.getLegendaryItems().getUniqueId(item.getItemStack()).orElse(null);
                if (key.equals(itemUuid) || key.equals(item.getUniqueId())) {
                    return liveItem(item.getItemStack(), item.getLocation());
                }
            }
        }
        for (Player holder : Bukkit.getOnlinePlayers()) {
            if (matchesItem(key, holder.getItemOnCursor())) {
                return liveItem(holder.getItemOnCursor(), holder.getLocation());
            }
            ItemStack[] inventory = holder.getInventory().getContents();
            ItemStack[] armor = holder.getInventory().getArmorContents();
            for (ItemStack stack : java.util.stream.Stream.concat(
                    java.util.Arrays.stream(inventory), java.util.Arrays.stream(armor)).toList()) {
                if (stack != null && key.equals(plugin.getLegendaryItems().getUniqueId(stack).orElse(null))) {
                    return liveItem(stack, holder.getLocation());
                }
            }
        }
        return null;
    }

    private LiveItem liveItem(ItemStack stack, org.bukkit.Location location) {
        String itemId = plugin.getLegendaryItems().identify(stack)
                .map(LegendaryItem::id).orElse(null);
        String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(stack.effectiveName());
        return new LiveItem(name, itemId, location.clone());
    }

    private List<String> findableItemIds() {
        java.util.Set<UUID> ids = new java.util.HashSet<>(
                plugin.getReclaimableItems().getLoggedItems().keySet());
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            for (org.bukkit.entity.Item item : world.getEntitiesByClass(org.bukkit.entity.Item.class)) {
                UUID itemUuid = plugin.getLegendaryItems().getUniqueId(item.getItemStack()).orElse(null);
                if (itemUuid != null) {
                    ids.add(itemUuid);
                }
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (ItemStack stack : player.getInventory().getContents()) {
                plugin.getLegendaryItems().getUniqueId(stack).ifPresent(ids::add);
            }
        }
        return ids.stream().map(UUID::toString).sorted().toList();
    }

    private record LiveItem(String name, String itemId, org.bukkit.Location location) {
    }

    /** A spot a few blocks above and to the side of the item, falling back to straight above if that is blocked. */
    private org.bukkit.Location inspectionPoint(org.bukkit.Location item) {
        org.bukkit.Location above = item.clone().add(0, 4, 0);
        org.bukkit.Location side = item.clone().add(3, 4, 3);
        return isClear(side) ? side : above;
    }

    private boolean isClear(org.bukkit.Location location) {
        return location.getBlock().isPassable() && location.clone().add(0, 1, 0).getBlock().isPassable();
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
                + " give all | give <player|@p> all | give <player|@p> <item> [count] | reset <item>"
                + " | reset-abilities <player|selector>"
                + " | list | locate <item> | find [item <uuid>] | clear-history <item|all>"
                + " | remove-item <uuid> | version | help [1-3]"));
    }
}
