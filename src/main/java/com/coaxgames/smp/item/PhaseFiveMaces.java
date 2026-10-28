package com.coaxgames.smp.item;

import java.time.Duration;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.Plugin;

public final class PhaseFiveMaces {
    private PhaseFiveMaces() {
    }

    public static void register(Plugin plugin, LegendaryItemRegistry registry) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(registry, "registry");

        registry.register(mace(plugin, "star_mace",
                new String[] {"HNH", "WMW", "HIH"},
                new Ingredient[] {new Ingredient('H', Material.PLAYER_HEAD),
                new Ingredient('N', Material.NETHER_STAR),
                new Ingredient('W', Material.WITHER_SKELETON_SKULL),
                new Ingredient('M', Material.HEAVY_CORE),
                new Ingredient('I', Material.NETHERITE_INGOT)},
                new EnchantmentLevel(Enchantment.WIND_BURST, 3),
                new EnchantmentLevel(Enchantment.DENSITY, 2)));
        registry.register(mace(plugin, "collapsing_star_mace",
                new String[] {"HCH", "DND", "HBH"},
                new Ingredient[] {new Ingredient('H', Material.PLAYER_HEAD),
                new Ingredient('C', Material.DEEPSLATE_COAL_ORE),
                new Ingredient('D', Material.HEAVY_CORE),
                new Ingredient('N', Material.NETHER_STAR),
                new Ingredient('B', Material.BREEZE_ROD)},
                new EnchantmentLevel(Enchantment.BREACH, 3),
                new EnchantmentLevel(Enchantment.WIND_BURST, 2)));
    }

    private static MaceDefinition mace(
            Plugin plugin,
            String id,
            String[] shape,
            Ingredient[] ingredients,
            EnchantmentLevel... enchantments
    ) {
        ItemStack item = new ItemStack(Material.MACE);
        if (id.equals("collapsing_star_mace")) {
            var meta = item.getItemMeta();
            meta.setItemModel(Objects.requireNonNull(NamespacedKey.fromString("galaxy:" + id)));
            item.setItemMeta(meta);
        }
        for (EnchantmentLevel enchantment : enchantments) {
            item.addUnsafeEnchantment(enchantment.enchantment(), enchantment.level());
        }

        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(plugin, id), item);
        recipe.shape(shape);
        for (Ingredient ingredient : ingredients) {
            if (ingredient.material() == Material.PLAYER_HEAD) {
                recipe.setIngredient(ingredient.symbol(),
                        new RecipeChoice.MaterialChoice(Material.PLAYER_HEAD));
            } else {
                recipe.setIngredient(ingredient.symbol(), ingredient.material());
            }
        }
        return new MaceDefinition(id, item, recipe);
    }

    private record Ingredient(char symbol, Material material) {
    }

    private record EnchantmentLevel(Enchantment enchantment, int level) {
    }

    private static final class MaceDefinition implements LegendaryItem {
        private final String id;
        private final ItemStack baseItem;
        private final ShapedRecipe recipe;

        private MaceDefinition(String id, ItemStack baseItem, ShapedRecipe recipe) {
            this.id = id;
            this.baseItem = baseItem.clone();
            this.recipe = recipe;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public ItemStack createBaseItem() {
            ItemStack item = baseItem.clone();
            var meta = item.getItemMeta();
            meta.setUnbreakable(true);
            meta.addItemFlags(
                    org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS,
                    org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES,
                    org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
            return item;
        }

        @Override
        public java.util.Optional<org.bukkit.inventory.Recipe> recipe() {
            return java.util.Optional.of(recipe);
        }

        @Override
        public java.util.Optional<Duration> cooldown() {
            return java.util.Optional.of(Duration.ofSeconds(30));
        }
    }
}
