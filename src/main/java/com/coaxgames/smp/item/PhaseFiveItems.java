package com.coaxgames.smp.item;

import java.util.Objects;
import java.time.Duration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

public final class PhaseFiveItems {
    private PhaseFiveItems() {
    }

    public static void register(JavaPlugin plugin, LegendaryItemRegistry registry) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(registry, "registry");

        registry.register(item(plugin, "nebula_spear", Material.NETHERITE_SPEAR,
                new String[] {"HTH", "GSG", "HRH"},
                new Ingredient[] {
                        new Ingredient('H', Material.PLAYER_HEAD),
                        new Ingredient('T', Material.TRIDENT),
                        new Ingredient('G', Material.GOLD_BLOCK),
                        new Ingredient('S', Material.NETHERITE_SPEAR),
                        new Ingredient('R', Material.REDSTONE_BLOCK)
                },
                new EnchantmentLevel(Enchantment.LUNGE, 3)));
        registry.register(item(plugin, "wormhole_trident", Material.TRIDENT,
                new String[] {"HTH", "WNW", "HTH"},
                new Ingredient[] {
                        new Ingredient('H', Material.PLAYER_HEAD),
                        new Ingredient('T', Material.TRIDENT),
                        new Ingredient('W', Material.WITHER_SKELETON_SKULL),
                        new Ingredient('N', Material.NETHERITE_INGOT)
                },
                new EnchantmentLevel(Enchantment.RIPTIDE, 3)));
        registry.register(item(plugin, "celestial_bow", Material.BOW,
                new String[] {"HBG", "ECG", "HLG"},
                new Ingredient[] {
                        new Ingredient('H', Material.PLAYER_HEAD),
                        new Ingredient('B', Material.BREEZE_ROD),
                        new Ingredient('G', Material.STRING),
                        new Ingredient('E', Material.END_ROD),
                        new Ingredient('C', Material.END_CRYSTAL),
                        new Ingredient('L', Material.BLAZE_ROD)
                },
                new EnchantmentLevel(Enchantment.POWER, 5),
                new EnchantmentLevel(Enchantment.FLAME, 1),
                new EnchantmentLevel(Enchantment.PUNCH, 2),
                new EnchantmentLevel(Enchantment.INFINITY, 1)));
        registry.register(item(plugin, "cosmic_crown", Material.NETHERITE_HELMET,
                new String[] {"NAN", "HCH", "NSN"},
                new Ingredient[] {
                        new Ingredient('N', Material.ENCHANTED_GOLDEN_APPLE),
                        new Ingredient('A', Material.AXOLOTL_BUCKET),
                        new Ingredient('H', Material.PLAYER_HEAD),
                        new Ingredient('C', Material.NETHERITE_HELMET),
                        new Ingredient('S', Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE)
                },
                new EnchantmentLevel(Enchantment.PROTECTION, 4),
                new EnchantmentLevel(Enchantment.RESPIRATION, 3),
                new EnchantmentLevel(Enchantment.AQUA_AFFINITY, 1),
                new EnchantmentLevel(Enchantment.THORNS, 3),
                new EnchantmentLevel(Enchantment.UNBREAKING, 3),
                new EnchantmentLevel(Enchantment.MENDING, 1)));
        // The undecided corner ingredient positions intentionally remain empty until supplied by the owner.
        registry.register(item(plugin, "singularity_boots", Material.NETHERITE_BOOTS,
                new String[] {" H ", "B0B", " H "},
                new Ingredient[] {
                        new Ingredient('H', Material.PLAYER_HEAD),
                        new Ingredient('B', Material.BREEZE_ROD),
                        new Ingredient('0', Material.NETHERITE_BOOTS)
                },
                new EnchantmentLevel(Enchantment.PROTECTION, 4)));
        registry.register(item(plugin, "nova_shield", Material.SHIELD,
                new String[] {"PNP", "PTP", "HNH"},
                new Ingredient[] {
                        new Ingredient('P', Material.PALE_OAK_PLANKS),
                        new Ingredient('N', Material.NETHERITE_BLOCK),
                        new Ingredient('T', Material.TOTEM_OF_UNDYING),
                        new Ingredient('H', Material.PLAYER_HEAD)
                }));
    }

    private static PhaseFiveItemDefinition item(
            JavaPlugin plugin,
            String id,
            Material baseMaterial,
            String[] shape,
            Ingredient[] ingredients,
            EnchantmentLevel... enchantments
    ) {
        ItemStack result = new ItemStack(baseMaterial);
        for (EnchantmentLevel enchantment : enchantments) {
            result.addUnsafeEnchantment(enchantment.enchantment(), enchantment.level());
        }
        var modelMeta = result.getItemMeta();
        if (modelMeta == null) {
            throw new IllegalStateException(id + " has no item metadata.");
        }
        modelMeta.setItemModel(Objects.requireNonNull(NamespacedKey.fromString("galaxy:" + id)));
        if ("singularity_boots".equals(id)) {
            // The worn 3D model is shown by a display entity, so the vanilla boots render nothing.
            var equippable = modelMeta.getEquippable();
            equippable.setSlot(org.bukkit.inventory.EquipmentSlot.FEET);
            equippable.setModel(NamespacedKey.fromString("galaxy:invisible"));
            modelMeta.setEquippable(equippable);
        }
        if ("cosmic_crown".equals(id)) {
            // Without an armor asset the helmet renders its own 3D item model (head display) when worn.
            var equippable = modelMeta.getEquippable();
            equippable.setSlot(org.bukkit.inventory.EquipmentSlot.HEAD);
            equippable.setModel(null);
            modelMeta.setEquippable(equippable);
        }
        if (!result.setItemMeta(modelMeta)) {
            throw new IllegalStateException("Could not apply the " + id + " item model.");
        }
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(plugin, id), result);
        recipe.shape(shape);
        for (Ingredient ingredient : ingredients) {
            if (ingredient.material() == Material.PLAYER_HEAD) {
                recipe.setIngredient(ingredient.symbol(),
                        new RecipeChoice.MaterialChoice(Material.PLAYER_HEAD));
            } else {
                recipe.setIngredient(ingredient.symbol(), ingredient.material());
            }
        }
        return new PhaseFiveItemDefinition(id, result, recipe);
    }

    private record Ingredient(char symbol, Material material) {
    }

    private record EnchantmentLevel(Enchantment enchantment, int level) {
    }

    private static final class PhaseFiveItemDefinition implements LegendaryItem {
        private final String id;
        private final ItemStack baseItem;
        private final ShapedRecipe recipe;

        private PhaseFiveItemDefinition(String id, ItemStack baseItem, ShapedRecipe recipe) {
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
            if (id.equals("celestial_bow")) {
                meta.setItemModel(Objects.requireNonNull(NamespacedKey.fromString("galaxy:celestial_bow")));
            }
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
            return switch (id) {
                case "nebula_spear" -> java.util.Optional.of(Duration.ofSeconds(45));
                case "nova_shield" -> java.util.Optional.of(Duration.ofMinutes(10));
                default -> java.util.Optional.empty();
            };
        }
    }
}
