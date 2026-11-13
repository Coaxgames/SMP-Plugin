package com.coaxgames.smp.command;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.util.ItemCooldownMarkers;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class CooldownCommand implements TabExecutor {
    private final GalaxySmpPlugin plugin;

    public CooldownCommand(GalaxySmpPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player) || !player.isOp()) {
            sender.sendMessage(Component.text("Only operators can use this command for themselves."));
            return true;
        }
        if (args.length > 1 || args.length == 1 && !args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(Component.text("Usage: /" + label + " [status]"));
            return true;
        }
        boolean enabled = plugin.getDataStore().areOperatorCooldownsEnabled(player.getUniqueId());
        if (args.length == 0) {
            enabled = !enabled;
            try {
                plugin.getDataStore().setOperatorCooldownsEnabled(player.getUniqueId(), enabled);
            } catch (IOException exception) {
                plugin.getLogger().log(Level.SEVERE,
                        "Could not save cooldown setting for " + player.getUniqueId() + ".", exception);
                sender.sendMessage(Component.text("Could not save your cooldown setting; check the server log."));
                return true;
            }
            for (String id : ItemCooldownMarkers.PERSISTED_ITEMS) {
                ItemCooldownMarkers.sync(player, id, plugin.getCooldownManager());
            }
            if (!enabled) {
                ItemCooldownMarkers.set(player, "nova_shield", 0);
                player.setCooldown(Material.ENDER_PEARL, 0);
            }
        }
        sender.sendMessage(Component.text("Your item cooldowns are " + (enabled ? "enabled." : "disabled.")));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || !player.isOp() || args.length != 1
                || !"status".startsWith(args[0].toLowerCase(java.util.Locale.ROOT))) {
            return List.of();
        }
        return List.of("status");
    }
}
