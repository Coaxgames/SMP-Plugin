package com.coaxgames.smp.util;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ItemCooldownMarkers {
    public static final List<String> PERSISTED_ITEMS =
            List.of("kuiper_ball", "mass_orb", "star_mace", "collapsing_star_mace");
    private static final Set<String> ITEMS =
            Set.of("kuiper_ball", "mass_orb", "star_mace", "collapsing_star_mace", "nova_shield");

    private ItemCooldownMarkers() {
    }

    public static boolean supported(String itemId) {
        return ITEMS.contains(itemId);
    }

    public static NamespacedKey group(String itemId) {
        if (!supported(itemId)) {
            throw new IllegalArgumentException("No cooldown marker group for " + itemId);
        }
        return new NamespacedKey("galaxy", "cooldown/" + itemId);
    }

    public static boolean configure(ItemMeta meta, String itemId) {
        if (!supported(itemId)) {
            return false;
        }
        var cooldown = meta.getUseCooldown();
        NamespacedKey group = group(itemId);
        if (group.equals(cooldown.getCooldownGroup()) && cooldown.getCooldownSeconds() > 0) {
            return false;
        }
        cooldown.setCooldownSeconds(0.05F);
        cooldown.setCooldownGroup(group);
        meta.setUseCooldown(cooldown);
        return true;
    }

    public static void set(Player player, String itemId, int ticks) {
        player.setCooldown(group(itemId), ticks);
    }

    public static void sync(Player player, String itemId, CooldownManager cooldowns) {
        set(player, itemId, ticks(cooldowns.getRemaining(player.getUniqueId(), itemId)));
    }

    public static int ticks(Duration duration) {
        long millis = duration.toMillis();
        if (millis <= 0) {
            return 0;
        }
        long ticks = millis / 50 + (millis % 50 == 0 ? 0 : 1);
        return (int) Math.min(Integer.MAX_VALUE, ticks);
    }
}
