package com.coaxgames.smp.item;

import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.Plugin;

public final class PhaseFourItems {
    private PhaseFourItems() {
    }

    public static void register(Plugin plugin, LegendaryItemRegistry registry) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(registry, "registry");

        registry.register(repeatable(plugin, "galaxy_string", Material.STRING, 1,
                null, true, new String[] {"B", "R", "E"}, new Ingredient(
                        'B', Material.BLAZE_ROD), new Ingredient('R', Material.BREEZE_ROD),
                new Ingredient('E', Material.END_ROD)));
        registry.register(repeatable(plugin, "kuiper_ball", Material.SNOWBALL, 10,
                64, false, new String[] {"SBS", "PPP", "SBS"}, new Ingredient(
                        'S', Material.SNOWBALL), new Ingredient('B', Material.WIND_CHARGE),
                new Ingredient('P', Material.POWDER_SNOW_BUCKET)));
        registry.register(repeatable(plugin, "mass_orb", Material.HEAVY_CORE, 1,
                null, false, new String[] {"GBG", "NHN", "GBG"}, new Ingredient(
                        'G', Material.GOLD_BLOCK), new Ingredient('B', Material.BLUE_ICE),
                new Ingredient('N', Material.NETHERITE_BLOCK), new Ingredient('H', Material.HEAVY_CORE)));

        registry.register(legendary(plugin, "astral_sword", Material.NETHERITE_SWORD,
                new String[] {"HNH", "DND", "HBH"}, new Ingredient[] {new Ingredient(
                        'H', Material.PLAYER_HEAD), new Ingredient('N', Material.NETHERITE_INGOT),
                new Ingredient('D', Material.DIAMOND_BLOCK), new Ingredient('B', Material.BREEZE_ROD)},
                new EnchantmentLevel(Enchantment.SHARPNESS, 6),
                new EnchantmentLevel(Enchantment.LOOTING, 3),
                new EnchantmentLevel(Enchantment.FIRE_ASPECT, 1)));
        registry.register(legendary(plugin, "void_sword", Material.NETHERITE_SWORD,
                new String[] {"HWH", "WNW", "HWH"}, new Ingredient[] {new Ingredient(
                        'H', Material.PLAYER_HEAD), new Ingredient('W', Material.WITHER_ROSE),
                new Ingredient('N', Material.NETHERITE_SWORD)},
                new EnchantmentLevel(Enchantment.SHARPNESS, 4),
                new EnchantmentLevel(Enchantment.LOOTING, 3)));
        registry.register(legendary(plugin, "solstice_chestplate", Material.NETHERITE_CHESTPLATE,
                new String[] {"HEH", "SCS", "HEH"}, new Ingredient[] {new Ingredient(
                        'H', Material.PLAYER_HEAD), new Ingredient('E', Material.END_CRYSTAL),
                new Ingredient('S', Material.NETHER_STAR), new Ingredient('C', Material.NETHERITE_CHESTPLATE)},
                new EnchantmentLevel(Enchantment.PROTECTION, 4)));
        registry.register(legendary(plugin, "lunar_leggings", Material.NETHERITE_LEGGINGS,
                new String[] {"HRH", "RLR", "HRH"}, new Ingredient[] {new Ingredient(
                        'H', Material.PLAYER_HEAD), new Ingredient('R', Material.RESPAWN_ANCHOR),
                new Ingredient('L', Material.NETHERITE_LEGGINGS)},
                new EnchantmentLevel(Enchantment.PROTECTION, 4)));
    }

    private static PhaseFourItemDefinition repeatable(
            Plugin plugin,
            String id,
            Material material,
            int amount,
            Integer maxStackSize,
            boolean hasPerPlayerCarryLimit,
            String[] shape,
            Ingredient... ingredients
    ) {
        ItemStack item = new ItemStack(material, amount);
        if (maxStackSize != null) {
            var meta = item.getItemMeta();
            meta.setMaxStackSize(maxStackSize);
            item.setItemMeta(meta);
        }
        return definition(plugin, id, item, shape, false, false, hasPerPlayerCarryLimit, ingredients);
    }

    private static PhaseFourItemDefinition legendary(
            Plugin plugin,
            String id,
            Material material,
            String[] shape,
            Ingredient[] ingredients,
            EnchantmentLevel... enchantments
    ) {
        ItemStack item = new ItemStack(material);
        if (id.equals("astral_sword") || id.equals("void_sword")) {
            var meta = item.getItemMeta();
            meta.setItemModel(Objects.requireNonNull(NamespacedKey.fromString("galaxy:" + id)));
            item.setItemMeta(meta);
        }
        for (EnchantmentLevel enchantment : enchantments) {
            item.addUnsafeEnchantment(enchantment.enchantment(), enchantment.level());
        }
        return definition(plugin, id, item, shape, true, true, true, ingredients);
    }

    private static PhaseFourItemDefinition definition(
            Plugin plugin,
            String id,
            ItemStack item,
            String[] shape,
            boolean craftOnce,
            boolean protectedItem,
            boolean hasPerPlayerCarryLimit,
            Ingredient... ingredients
    ) {
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(plugin, id), item);
        recipe.shape(shape);
        for (Ingredient ingredient : ingredients) {
            if (ingredient.material() == Material.PLAYER_HEAD) {
                recipe.setIngredient(ingredient.symbol(), new RecipeChoice.MaterialChoice(Material.PLAYER_HEAD));
            } else {
                recipe.setIngredient(ingredient.symbol(), ingredient.material());
            }
        }
        return new PhaseFourItemDefinition(
                id, item, recipe, craftOnce, protectedItem, hasPerPlayerCarryLimit);
    }

    private record Ingredient(char symbol, Material material) {
    }

    private record EnchantmentLevel(Enchantment enchantment, int level) {
    }

    private static final class PhaseFourItemDefinition implements LegendaryItem {
        private final String id;
        private final ItemStack baseItem;
        private final ShapedRecipe recipe;
        private final boolean craftOnce;
        private final boolean protectedItem;
        private final boolean hasPerPlayerCarryLimit;

        private PhaseFourItemDefinition(
                String id,
                ItemStack baseItem,
                ShapedRecipe recipe,
                boolean craftOnce,
                boolean protectedItem,
                boolean hasPerPlayerCarryLimit
        ) {
            this.id = id;
            this.baseItem = baseItem.clone();
            this.recipe = recipe;
            this.craftOnce = craftOnce;
            this.protectedItem = protectedItem;
            this.hasPerPlayerCarryLimit = hasPerPlayerCarryLimit;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public ItemStack createBaseItem() {
            ItemStack item = baseItem.clone();
            if (craftOnce) {
                item.setAmount(1);
                var meta = item.getItemMeta();
                meta.setUnbreakable(true);
                meta.addItemFlags(
                        org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS,
                        org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES,
                        org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE);
                item.setItemMeta(meta);
            }
            return item;
        }

        @Override
        public java.util.Optional<org.bukkit.inventory.Recipe> recipe() {
            return java.util.Optional.of(recipe);
        }

        @Override
        public boolean craftOnce() {
            return craftOnce;
        }

        @Override
        public boolean protectedFromLossAndStorage() {
            return protectedItem;
        }

        @Override
        public boolean trackUniqueInstance() {
            return craftOnce;
        }

        @Override
        public boolean hasPerPlayerCarryLimit() {
            return hasPerPlayerCarryLimit;
        }
    }
}
