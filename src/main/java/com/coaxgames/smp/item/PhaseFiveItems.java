package com.coaxgames.smp.item;

import java.util.Objects;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class PhaseFiveItems {
    public static final int CORRUPT_PEARL_STACK_LIMIT =
            com.coaxgames.smp.listener.EnderPearlStackListener.STACK_LIMIT;

    private PhaseFiveItems() {
    }

    public static void register(JavaPlugin plugin, LegendaryItemRegistry registry) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(registry, "registry");

        registry.register(new CorruptEnderPearl(registry));
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
                new EnchantmentLevel(Enchantment.UNBREAKING, 3),
                new EnchantmentLevel(Enchantment.MENDING, 1)));
        registry.register(item(plugin, "singularity_boots", Material.NETHERITE_BOOTS,
                new String[] {"PHP", "B0B", "PHP"},
                new Ingredient[] {
                        new Ingredient('H', Material.PLAYER_HEAD),
                        new Ingredient('B', Material.BREEZE_ROD),
                        new Ingredient('P', Material.ENDER_PEARL),
                        new Ingredient('0', Material.NETHERITE_BOOTS)
                },
                new EnchantmentLevel(Enchantment.PROTECTION, 4),
                new EnchantmentLevel(Enchantment.DEPTH_STRIDER, 3)));
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
            var equippable = modelMeta.getEquippable();
            equippable.setSlot(org.bukkit.inventory.EquipmentSlot.FEET);
            equippable.setModel(NamespacedKey.fromString("galaxy:singularity_boots"));
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
            if ("nova_shield".equals(id) && ingredient.symbol() == 'P') {
                recipe.setIngredient('P', new RecipeChoice.MaterialChoice(
                        java.util.List.copyOf(Tag.PLANKS.getValues())));
            } else if (ingredient.material() == Material.PLAYER_HEAD) {
                recipe.setIngredient(ingredient.symbol(),
                        new RecipeChoice.MaterialChoice(Material.PLAYER_HEAD));
            } else {
                recipe.setIngredient(ingredient.symbol(), ingredient.material());
            }
        }
        return new PhaseFiveItemDefinition(id, result, recipe);
    }

    public static final class CorruptEnderPearl implements LegendaryItem {
        private static final NamespacedKey PROJECTILE_KEY = new NamespacedKey("galaxy", "corrupt_pearl_projectile");
        private final LegendaryItemRegistry registry;
        private final IntSupplier landingRoll;
        private final Consumer<Player> applyWither;

        CorruptEnderPearl(LegendaryItemRegistry registry) {
            this(registry, () -> ThreadLocalRandom.current().nextInt(100),
                    player -> player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 200, 1)));
        }

        CorruptEnderPearl(LegendaryItemRegistry registry, IntSupplier landingRoll, Consumer<Player> applyWither) {
            this.registry = Objects.requireNonNull(registry, "registry");
            this.landingRoll = Objects.requireNonNull(landingRoll, "landingRoll");
            this.applyWither = Objects.requireNonNull(applyWither, "applyWither");
        }

        @Override
        public String id() {
            return "corrupt_ender_pearl";
        }

        @Override
        public ItemStack createBaseItem() {
            ItemStack item = new ItemStack(Material.ENDER_PEARL);
            var meta = item.getItemMeta();
            meta.setMaxStackSize(CORRUPT_PEARL_STACK_LIMIT);
            meta.setItemModel(Objects.requireNonNull(NamespacedKey.fromString("galaxy:corrupt_ender_pearl")));
            if (!item.setItemMeta(meta)) {
                throw new IllegalStateException("Could not apply Corrupt Ender Pearl metadata.");
            }
            return item;
        }

        @Override
        public boolean craftOnce() {
            return false;
        }

        @Override
        public boolean protectedFromLossAndStorage() {
            return false;
        }

        @Override
        public boolean hasPerPlayerCarryLimit() {
            return false;
        }

        @EventHandler
        public void onEndermanDeath(EntityDeathEvent event) {
            if (event.getEntity() instanceof Enderman
                    && isCorruptPearlDropRoll(ThreadLocalRandom.current().nextInt(20))) {
                event.getDrops().add(registry.createItem(id()));
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onPearlLaunch(PlayerLaunchProjectileEvent event) {
            if (event.getProjectile() instanceof EnderPearl pearl
                    && registry.identify(event.getItemStack()).map(item -> id().equals(item.id())).orElse(false)) {
                pearl.getPersistentDataContainer().set(PROJECTILE_KEY, PersistentDataType.BYTE, (byte) 1);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onPearlLanding(ProjectileHitEvent event) {
            if (!(event.getEntity() instanceof EnderPearl pearl)) {
                return;
            }
            var data = pearl.getPersistentDataContainer();
            if (!Byte.valueOf((byte) 1).equals(data.get(PROJECTILE_KEY, PersistentDataType.BYTE))) {
                return;
            }
            data.remove(PROJECTILE_KEY);
            if (pearl.getShooter() instanceof Player thrower
                    && isCorruptPearlWitherRoll(landingRoll.getAsInt())) {
                applyWither.accept(thrower);
            }
        }
    }

    static boolean isCorruptPearlWitherRoll(int roll) {
        if (roll < 0 || roll >= 100) {
            throw new IllegalArgumentException("Wither roll must be between 0 and 99.");
        }
        return roll < 15;
    }

    static boolean isCorruptPearlDropRoll(int roll) {
        if (roll < 0 || roll >= 20) {
            throw new IllegalArgumentException("Drop roll must be between 0 and 19.");
        }
        return roll == 0;
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
