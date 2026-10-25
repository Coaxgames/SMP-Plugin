package com.coaxgames.smp.item;

import java.time.Duration;
import java.util.Optional;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

public interface LegendaryItem extends Listener {
    String id();

    ItemStack createBaseItem();

    default Optional<Recipe> recipe() {
        return Optional.empty();
    }

    default Optional<Duration> cooldown() {
        return Optional.empty();
    }

    default int maxCrafts() {
        return 1;
    }

    default boolean craftOnce() {
        return true;
    }

    default boolean protectedFromLossAndStorage() {
        return true;
    }

    default boolean trackUniqueInstance() {
        return craftOnce();
    }

    default boolean hasPerPlayerCarryLimit() {
        return true;
    }
}
