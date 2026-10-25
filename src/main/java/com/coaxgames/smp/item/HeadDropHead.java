package com.coaxgames.smp.item;

import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class HeadDropHead {
    private static final NamespacedKey DROPPED_KEY =
            Objects.requireNonNull(NamespacedKey.fromString("headdrop:dropped"), "droppedKey");
    private static final byte DROPPED_VALUE = 1;

    private HeadDropHead() {
    }

    public static boolean isMarked(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && isDroppedMarkerValue(
                meta.getPersistentDataContainer().get(DROPPED_KEY, PersistentDataType.BYTE));
    }

    public static boolean allRecipeHeadsAreMarked(ItemStack[] matrix) {
        for (ItemStack item : matrix) {
            if (item != null && item.getType() == Material.PLAYER_HEAD && !isMarked(item)) {
                return false;
            }
        }
        return true;
    }

    public static boolean usesFixedPlayerHeadChoice(Recipe recipe) {
        if (!(recipe instanceof ShapedRecipe shaped)) {
            return false;
        }
        for (RecipeChoice choice : shaped.getChoiceMap().values()) {
            if (choice instanceof RecipeChoice.ExactChoice exactChoice
                    && exactChoice.getChoices().stream().anyMatch(item -> item.getType() == Material.PLAYER_HEAD)) {
                return true;
            }
        }
        return false;
    }

    static boolean isDroppedMarkerValue(Byte value) {
        return value != null && value == DROPPED_VALUE;
    }
}
