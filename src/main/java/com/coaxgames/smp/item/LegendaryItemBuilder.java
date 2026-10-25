package com.coaxgames.smp.item;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

public final class LegendaryItemBuilder {
    private final ItemStack baseItem;
    private final EnumSet<ItemFlag> hiddenFlags = EnumSet.of(
            ItemFlag.HIDE_ENCHANTS,
            ItemFlag.HIDE_ATTRIBUTES,
            ItemFlag.HIDE_UNBREAKABLE
    );
    private Component displayName;
    private List<Component> lore;
    private Integer customModelData;
    private NamespacedKey itemModel;

    public LegendaryItemBuilder(ItemStack baseItem) {
        Objects.requireNonNull(baseItem, "baseItem");
        if (baseItem.getType().isAir()) {
            throw new IllegalArgumentException("Legendary item base cannot be air.");
        }
        this.baseItem = baseItem.clone();
    }

    public LegendaryItemBuilder displayName(Component displayName) {
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        return this;
    }

    public LegendaryItemBuilder gradientName(String text, String startColor, String endColor) {
        this.displayName = gradientComponent(text, startColor, endColor);
        return this;
    }

    public static Component gradientComponent(String text, String startColor, String endColor) {
        Objects.requireNonNull(text, "text");
        TextColor start = parseColor(startColor, "startColor");
        TextColor end = parseColor(endColor, "endColor");
        int[] codePoints = text.codePoints().toArray();
        if (codePoints.length == 0) {
            throw new IllegalArgumentException("Gradient name cannot be empty.");
        }
        if (codePoints.length == 1) {
            return Component.text(text, start);
        }
        Component gradient = Component.empty();
        for (int index = 0; index < codePoints.length; index++) {
            double progress = (double) index / (codePoints.length - 1);
            int red = interpolate(start.red(), end.red(), progress);
            int green = interpolate(start.green(), end.green(), progress);
            int blue = interpolate(start.blue(), end.blue(), progress);
            TextColor color = TextColor.color(red, green, blue);
            gradient = gradient.append(Component.text(new String(Character.toChars(codePoints[index])), color));
        }
        return gradient;
    }

    private static TextColor parseColor(String value, String parameterName) {
        Objects.requireNonNull(value, parameterName);
        TextColor color = TextColor.fromHexString(value);
        if (color == null) {
            throw new IllegalArgumentException(parameterName + " must be a valid hex color such as #6A00F4.");
        }
        return color;
    }

    private static int interpolate(int start, int end, double progress) {
        return (int) Math.round(start + (end - start) * progress);
    }

    public LegendaryItemBuilder lore(List<Component> lore) {
        this.lore = new ArrayList<>(Objects.requireNonNull(lore, "lore"));
        this.lore.forEach(line -> Objects.requireNonNull(line, "lore line"));
        return this;
    }

    public LegendaryItemBuilder hideFlags(ItemFlag... flags) {
        for (ItemFlag flag : flags) {
            hiddenFlags.add(Objects.requireNonNull(flag, "flag"));
        }
        return this;
    }

    public LegendaryItemBuilder showFlags(ItemFlag... flags) {
        for (ItemFlag flag : flags) {
            hiddenFlags.remove(Objects.requireNonNull(flag, "flag"));
        }
        return this;
    }

    public LegendaryItemBuilder customModelData(int customModelData) {
        if (customModelData < 0) {
            throw new IllegalArgumentException("Custom model data cannot be negative.");
        }
        this.customModelData = customModelData;
        return this;
    }

    public LegendaryItemBuilder itemModel(NamespacedKey itemModel) {
        this.itemModel = Objects.requireNonNull(itemModel, "itemModel");
        return this;
    }

    public ItemStack build() {
        ItemStack result = baseItem.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Legendary item base has no item metadata.");
        }

        meta.setUnbreakable(true);
        meta.removeItemFlags(ItemFlag.values());
        meta.addItemFlags(hiddenFlags.toArray(ItemFlag[]::new));
        if (displayName != null) {
            meta.displayName(displayName);
        }
        if (lore != null) {
            meta.lore(List.copyOf(lore));
        }
        if (customModelData != null) {
            CustomModelDataComponent modelData = meta.getCustomModelDataComponent();
            modelData.setFloats(List.of(customModelData.floatValue()));
            meta.setCustomModelDataComponent(modelData);
        }
        if (itemModel != null) {
            meta.setItemModel(itemModel);
        }
        if (!result.setItemMeta(meta)) {
            throw new IllegalStateException("Could not apply metadata to legendary item.");
        }
        return result;
    }
}
