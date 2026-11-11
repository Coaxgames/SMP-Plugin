package com.coaxgames.smp.item;

import java.util.HashMap;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.Plugin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.Recipe;

public final class LegendaryItemRegistry {
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9]+(?:_[a-z0-9]+)*");

    private final NamespacedKey itemIdKey;
    private final NamespacedKey itemUuidKey;
    private final NamespacedKey starMaceProgressionKey;
    private final Plugin plugin;
    private final Map<String, LegendaryItem> items = new HashMap<>();
    private final Map<NamespacedKey, LegendaryItem> recipes = new HashMap<>();

    public LegendaryItemRegistry(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        itemIdKey = Objects.requireNonNull(NamespacedKey.fromString("galaxy:item_id"), "itemIdKey");
        itemUuidKey = Objects.requireNonNull(NamespacedKey.fromString("galaxy:uuid"), "itemUuidKey");
        starMaceProgressionKey = new NamespacedKey(plugin, "star-mace-progression-v1");
    }

    public void register(LegendaryItem item) {
        Objects.requireNonNull(item, "item");
        String id = Objects.requireNonNull(item.id(), "item.id()");
        if (!VALID_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid legendary item id: " + id);
        }
        if (items.containsKey(id)) {
            throw new IllegalStateException("Legendary item is already registered: " + id);
        }
        if (item.maxCrafts() < 1) {
            throw new IllegalArgumentException("Legendary item maxCrafts must be at least 1: " + id);
        }
        item.cooldown().ifPresent(cooldown -> {
            if (cooldown.isNegative()) {
                throw new IllegalArgumentException("Legendary item cooldown cannot be negative: " + id);
            }
        });
        Recipe recipe = item.recipe().orElse(null);
        NamespacedKey recipeKey = null;
        if (recipe != null) {
            if (HeadDropHead.usesFixedPlayerHeadChoice(recipe)) {
                throw new IllegalArgumentException(
                        "Player-head recipe ingredients must match the Head Drop marker, not fixed ItemStacks: " + id);
            }
            if (!(recipe instanceof Keyed keyed)) {
                throw new IllegalArgumentException("Legendary recipe must have a key: " + id);
            }
            recipeKey = keyed.getKey();
            if (recipe instanceof org.bukkit.inventory.ShapedRecipe shapedRecipe) {
                ItemStack recipeResult = shapedRecipe.getResult().clone();
                applyDisplayName(id, recipeResult);
                org.bukkit.inventory.ShapedRecipe namedRecipe =
                        new org.bukkit.inventory.ShapedRecipe(recipeKey, recipeResult);
                namedRecipe.shape(shapedRecipe.getShape());
                shapedRecipe.getChoiceMap().forEach(namedRecipe::setIngredient);
                recipe = namedRecipe;
            }
            if (recipes.containsKey(recipeKey) || Bukkit.getRecipe(recipeKey) != null) {
                throw new IllegalStateException("Recipe key is already registered: " + recipeKey);
            }
            if (!Bukkit.addRecipe(recipe)) {
                throw new IllegalStateException("Could not register recipe for legendary item: " + id);
            }
        }
        plugin.getServer().getPluginManager().registerEvents(item, plugin);
        items.put(id, item);
        if (recipeKey != null) {
            recipes.put(recipeKey, item);
        }
    }

    public Optional<LegendaryItem> get(String id) {
        return Optional.ofNullable(items.get(id));
    }

    public Collection<LegendaryItem> getAll() {
        return java.util.List.copyOf(items.values());
    }

    public Collection<NamespacedKey> getRecipeKeys() {
        return java.util.List.copyOf(recipes.keySet());
    }

    public Optional<LegendaryItem> getForRecipe(Recipe recipe) {
        if (!(recipe instanceof Keyed keyed)) {
            return Optional.empty();
        }
        return Optional.ofNullable(recipes.get(keyed.getKey()));
    }

    public boolean isLegendary(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(itemIdKey, PersistentDataType.STRING);
    }

    public boolean isProtectedItem(ItemStack stack) {
        if (!isLegendary(stack)) {
            return false;
        }
        String id = stack.getItemMeta().getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);
        return get(id).map(LegendaryItem::protectedFromLossAndStorage).orElse(true);
    }

    public boolean containsProtectedItem(ItemStack stack) {
        if (isProtectedItem(stack)) {
            return true;
        }
        if (stack != null && stack.getItemMeta() instanceof org.bukkit.inventory.meta.BundleMeta bundle) {
            return bundle.getItems().stream().anyMatch(this::containsProtectedItem);
        }
        if (stack != null && stack.getItemMeta() instanceof BlockStateMeta blockStateMeta
                && blockStateMeta.getBlockState() instanceof ShulkerBox shulker) {
            for (ItemStack item : shulker.getInventory().getContents()) {
                if (containsProtectedItem(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean containsRegisteredItem(ItemStack stack) {
        if (identify(stack).isPresent()) {
            return true;
        }
        if (stack != null && stack.getItemMeta() instanceof BundleMeta bundle) {
            return bundle.getItems().stream().anyMatch(this::containsRegisteredItem);
        }
        if (stack != null && stack.getItemMeta() instanceof BlockStateMeta blockStateMeta
                && blockStateMeta.getBlockState() instanceof ShulkerBox shulker) {
            for (ItemStack item : shulker.getInventory().getContents()) {
                if (containsRegisteredItem(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    public int maxPerPlayer(String itemId) {
        LegendaryItem item = get(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown legendary item: " + itemId));
        if (!item.hasPerPlayerCarryLimit()) {
            return 0;
        }
        org.bukkit.plugin.java.JavaPlugin javaPlugin = (org.bukkit.plugin.java.JavaPlugin) plugin;
        String path = "legendary-items.max-per-player.items." + itemId;
        return javaPlugin.getConfig().getInt(path,
                javaPlugin.getConfig().getInt("legendary-items.max-per-player.default", 1));
    }

    public Component displayName(String itemId) {
        get(itemId).orElseThrow(() -> new IllegalArgumentException("Unknown legendary item: " + itemId));
        return configuredDisplayName(itemId);
    }

    private Component configuredDisplayName(String itemId) {
        org.bukkit.plugin.java.JavaPlugin javaPlugin = (org.bukkit.plugin.java.JavaPlugin) plugin;
        String namePath = "legendary-items.display-names." + itemId;
        if (!javaPlugin.getConfig().contains(namePath + ".text")) {
            if (itemId.equals("corrupt_ender_pearl")) {
                return Component.text("Corrupt Ender Pearl");
            }
            return Component.text(itemId);
        }
        return LegendaryItemBuilder.gradientComponent(
                javaPlugin.getConfig().getString(namePath + ".text"),
                javaPlugin.getConfig().getString(namePath + ".start-color"),
                javaPlugin.getConfig().getString(namePath + ".end-color"));
    }

    public int countForPlayer(Player player, String itemId) {
        int count = countItems(player.getInventory().getStorageContents(), itemId);
        count += countItems(player.getInventory().getArmorContents(), itemId);
        count += countItems(new ItemStack[] {
                player.getInventory().getItemInOffHand(),
                player.getItemOnCursor()
        }, itemId);
        return count;
    }

    public boolean hasCapacity(Player player, String itemId, int additionalItems) {
        if (additionalItems < 0) {
            throw new IllegalArgumentException("additionalItems cannot be negative.");
        }
        LegendaryItem definition = get(itemId).orElseThrow();
        return LegendaryCarryLimit.allows(player.isOp(), definition.protectedFromLossAndStorage(),
                countLegendaryForPlayer(player), 0, additionalItems);
    }

    public int countLegendaryForPlayer(Player player) {
        int total = 0;
        for (LegendaryItem definition : getAll()) {
            if (definition.protectedFromLossAndStorage()) {
                total += countForPlayer(player, definition.id());
            }
        }
        return total;
    }

    public int countItems(ItemStack[] stacks, String itemId) {
        int count = 0;
        for (ItemStack stack : stacks) {
            count += countItems(stack, itemId);
        }
        return count;
    }

    private int countItems(ItemStack stack, String itemId) {
        if (stack == null || stack.getType().isAir()) {
            return 0;
        }
        if (isLegendary(stack)) {
            String id = stack.getItemMeta().getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);
            return itemId.equals(id) ? stack.getAmount() : 0;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof BundleMeta bundle) {
            return countItems(bundle.getItems().toArray(ItemStack[]::new), itemId);
        }
        if (meta instanceof BlockStateMeta blockStateMeta
                && blockStateMeta.getBlockState() instanceof ShulkerBox shulker) {
            return countItems(shulker.getInventory().getContents(), itemId);
        }
        return 0;
    }

    public Optional<LegendaryItem> identify(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return Optional.empty();
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }

        String id = meta.getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);
        return id == null ? Optional.empty() : get(id);
    }

    public Optional<UUID> getUniqueId(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return Optional.empty();
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }

        String value = meta.getPersistentDataContainer().get(itemUuidKey, PersistentDataType.STRING);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public ItemStack createItem(String id) {
        return createItem(id, UUID.randomUUID());
    }

    public boolean upgradeVoidSword(ItemStack item) {
        if (identify(item).map(definition -> !definition.id().equals("void_sword")).orElse(true)) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        int level = meta.getEnchantLevel(Enchantment.SHARPNESS);
        if (level != 4) {
            return false;
        }
        meta.addEnchant(Enchantment.SHARPNESS, 5, true);
        List<Component> lore = meta.lore();
        if (lore != null) {
            List<Component> updatedLore = new ArrayList<>(lore);
            for (int i = 0; i < updatedLore.size(); i++) {
                if (net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(updatedLore.get(i)).equals("- Sharpness IV")) {
                    updatedLore.set(i, Component.text("- Sharpness V", NamedTextColor.GRAY));
                }
            }
            meta.lore(updatedLore);
        }
        if (!item.setItemMeta(meta)) {
            throw new IllegalStateException("Could not upgrade Void Sword enchantment.");
        }
        return true;
    }

    public boolean removeWeaponFireEnchantments(ItemStack item) {
        LegendaryItem definition = identify(item).orElse(null);
        if (definition == null || !definition.protectedFromLossAndStorage()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        boolean fireAspect = meta.hasEnchant(Enchantment.FIRE_ASPECT);
        boolean flame = meta.hasEnchant(Enchantment.FLAME);
        if (!fireAspect && !flame) {
            return false;
        }
        meta.removeEnchant(Enchantment.FIRE_ASPECT);
        meta.removeEnchant(Enchantment.FLAME);
        List<Component> lore = meta.lore();
        if (lore != null) {
            List<Component> updatedLore = new ArrayList<>(lore);
            updatedLore.removeIf(line -> {
                String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(line);
                return text.startsWith("- Fire Aspect ") || text.startsWith("- Flame ");
            });
            meta.lore(updatedLore);
        }
        if (!item.setItemMeta(meta)) {
            throw new IllegalStateException("Could not remove fire enchantments from " + definition.id() + ".");
        }
        return true;
    }

    public boolean upgradeLunarLeggings(ItemStack item) {
        if (identify(item).map(definition -> !definition.id().equals("lunar_leggings")).orElse(true)) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta.getEnchantLevel(Enchantment.SWIFT_SNEAK) >= 3) {
            return false;
        }
        meta.addEnchant(Enchantment.SWIFT_SNEAK, 3, true);
        List<Component> lore = meta.lore();
        if (lore != null) {
            List<Component> updatedLore = new ArrayList<>(lore);
            boolean replaced = false;
            for (int i = 0; i < updatedLore.size(); i++) {
                String line = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(updatedLore.get(i));
                if (line.startsWith("- Swift Sneak ")) {
                    updatedLore.set(i, Component.text("- Swift Sneak III", NamedTextColor.GRAY));
                    replaced = true;
                }
            }
            if (!replaced) {
                updatedLore.add(Component.text("- Swift Sneak III", NamedTextColor.GRAY));
            }
            meta.lore(updatedLore);
        }
        if (!item.setItemMeta(meta)) {
            throw new IllegalStateException("Could not upgrade Lunar Leggings enchantment.");
        }
        return true;
    }

    public boolean refreshDailyEffectLore(ItemStack item) {
        String id = identify(item).map(LegendaryItem::id).orElse("");
        if (!id.equals("solstice_chestplate") && !id.equals("lunar_leggings")) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.lore();
        if (lore == null) {
            return false;
        }
        String newLine = abilityLore(id).getFirst();
        List<Component> updatedLore = new ArrayList<>(lore);
        boolean changed = false;
        for (int i = 0; i < updatedLore.size(); i++) {
            String line = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(updatedLore.get(i));
            if (line.startsWith("At world time ") && line.endsWith(" seconds while worn.")
                    && !line.equals(newLine)) {
                updatedLore.set(i, Component.text(newLine, NamedTextColor.GRAY));
                changed = true;
            }
        }
        if (changed) {
            meta.lore(updatedLore);
            if (!item.setItemMeta(meta)) {
                throw new IllegalStateException("Could not update daily effect lore for " + id + ".");
            }
        }
        return changed;
    }

    public boolean initializeStarMaceProgression(ItemStack item) {
        if (identify(item).map(definition -> !definition.id().equals("star_mace")).orElse(true)) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta.getPersistentDataContainer().has(starMaceProgressionKey, PersistentDataType.BYTE)) {
            return false;
        }
        meta.addEnchant(Enchantment.WIND_BURST, 1, true);
        meta.getPersistentDataContainer().set(starMaceProgressionKey, PersistentDataType.BYTE, (byte) 1);
        if (!item.setItemMeta(meta)) {
            throw new IllegalStateException("Could not initialize Star Mace Wind Burst progression.");
        }
        updateStarMaceWindBurstLore(item, 1);
        return true;
    }

    public boolean refreshStarMaceCooldownLore(ItemStack item) {
        if (identify(item).map(definition -> !definition.id().equals("star_mace")).orElse(true)) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.lore();
        if (lore == null) {
            return false;
        }
        List<Component> updatedLore = new ArrayList<>(lore);
        boolean removed = updatedLore.removeIf(line ->
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(line).equals("A successful swing at Wind Burst V starts a 30-second cooldown."));
        if (removed) {
            meta.lore(updatedLore);
            if (!item.setItemMeta(meta)) {
                throw new IllegalStateException("Could not update Star Mace cooldown lore.");
            }
        }
        return removed;
    }

    public boolean refreshCooldownGroup(ItemStack item) {
        String id = identify(item).map(LegendaryItem::id).orElse("");
        if (!com.coaxgames.smp.util.ItemCooldownMarkers.supported(id)) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (!com.coaxgames.smp.util.ItemCooldownMarkers.configure(meta, id)) {
            return false;
        }
        if (!item.setItemMeta(meta)) {
            throw new IllegalStateException("Could not update cooldown group for " + id + ".");
        }
        return true;
    }

    public boolean setStarMaceWindBurstLevel(ItemStack item, int level) {
        if (identify(item).map(definition -> !definition.id().equals("star_mace")).orElse(true)) {
            return false;
        }
        if (level < 1 || level > 5) {
            throw new IllegalArgumentException("Star Mace Wind Burst level must be between I and V.");
        }
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(Enchantment.WIND_BURST, level, true);
        meta.getPersistentDataContainer().set(starMaceProgressionKey, PersistentDataType.BYTE, (byte) 1);
        if (!item.setItemMeta(meta)) {
            throw new IllegalStateException("Could not update Star Mace Wind Burst level.");
        }
        updateStarMaceWindBurstLore(item, level);
        return true;
    }

    private void updateStarMaceWindBurstLore(ItemStack item, int level) {
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.lore();
        if (lore == null) {
            return;
        }
        List<Component> updatedLore = new ArrayList<>(lore);
        boolean changed = false;
        for (int i = 0; i < updatedLore.size(); i++) {
            String line = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(updatedLore.get(i));
            if (line.startsWith("- Wind Burst ")) {
                updatedLore.set(i, Component.text("- Wind Burst " + romanNumeral(level), NamedTextColor.GRAY));
                changed = true;
            }
        }
        if (changed) {
            meta.lore(updatedLore);
            if (!item.setItemMeta(meta)) {
                throw new IllegalStateException("Could not update Star Mace Wind Burst lore.");
            }
        }
    }

    public ItemStack createItem(String id, UUID itemUuid) {
        LegendaryItem definition = get(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown legendary item: " + id));
        Objects.requireNonNull(itemUuid, "itemUuid");
        ItemStack item = Objects.requireNonNull(definition.createBaseItem(), "createBaseItem() returned null").clone();
        if (item.getType().isAir()) {
            throw new IllegalStateException("Legendary item cannot use an air base item: " + id);
        }
        if (definition.craftOnce()) {
            item.setAmount(1);
        }

        applyDisplayName(id, item);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Legendary item has no item metadata after naming: " + id);
        }
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, id);
        if (definition.trackUniqueInstance()) {
            meta.getPersistentDataContainer().set(itemUuidKey, PersistentDataType.STRING, itemUuid.toString());
        } else {
            meta.getPersistentDataContainer().remove(itemUuidKey);
        }
        if (!item.setItemMeta(meta)) {
            throw new IllegalStateException("Could not apply identity metadata to legendary item: " + id);
        }
        return item;
    }

    private void applyDisplayName(String id, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Legendary item has no metadata for display name: " + id);
        }
        Component displayName = configuredDisplayName(id);
        meta.itemName(displayName);
        meta.displayName(displayName);
        com.coaxgames.smp.util.ItemCooldownMarkers.configure(meta, id);
        List<Component> lore = new ArrayList<>();
        abilityLore(id).forEach(line -> lore.add(Component.text(line, NamedTextColor.GRAY)));
        if (!item.getEnchantments().isEmpty()) {
            lore.add(Component.text("Enchantments", NamedTextColor.LIGHT_PURPLE));
            item.getEnchantments().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(
                            enchantment -> enchantment.getKey().toString())))
                    .forEach(entry -> lore.add(Component.text(
                            "- " + enchantmentName(entry.getKey()) + " " + romanNumeral(entry.getValue()),
                            NamedTextColor.GRAY)));
        }
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        if (!item.setItemMeta(meta)) {
            throw new IllegalStateException("Could not apply display name to legendary item: " + id);
        }
    }

    private List<String> abilityLore(String id) {
        return switch (id) {
            case "galaxy_string" -> List.of("Special crafting ingredient for the Celestial Bow.");
            case "corrupt_ender_pearl" -> List.of("Crafting ingredient for Singularity Boots.");
            case "kuiper_ball" -> List.of(
                    "Throw to freeze a hit target and launch configured nearby targets.",
                    "Burst targeting is configurable for players, mobs, dropped items, and projectiles.",
                    "Cooldown: " + configLong("phase-four.kuiper-ball.cooldown-millis", 1_500)
                            + " milliseconds per throw.");
            case "mass_orb" -> List.of(
                    "While carried, greatly reduces fall damage.",
                    "While held, falling onto enemies deals 1/4 heart per block, up to 10 hearts.",
                    "A slam attempt starts a " + configLong("cooldowns.mass_orb", 20) + "-second cooldown.",
                    "Fall damage is capped at " + configNumber("phase-four.mass-orb.maximum-damage", 40.0)
                            + " health points.");
            case "astral_sword" -> List.of(
                    "Every 4 consecutive hits: gain increased attack speed for "
                            + configLong("phase-four.astral-sword.attack-speed-seconds", 30) + " seconds.");
            case "void_sword" -> List.of(
                    "Every 4 consecutive hits: inflict Slowness I and Wither I for 10 seconds.");
            case "solstice_chestplate" -> List.of(
                    "At world time " + configLong("phase-four.solstice-chestplate.trigger-tick", 6_000)
                            + ": Speed III, Strength II, and Resistance I for "
                            + configLong("phase-four.daily-effect-seconds", 300) + " seconds while worn.");
            case "lunar_leggings" -> List.of(
                    "At world time " + configLong("phase-four.daily-trigger-tick", 13_500)
                            + ": Speed I, Strength I, and Resistance II for "
                            + configLong("phase-four.daily-effect-seconds", 300) + " seconds while worn.");
            case "star_mace" -> List.of(
                    "Wind Burst rises by one on each hit, up to V.",
                    "A miss resets Wind Burst to I and starts a 30-second cooldown.",
                    "Damage cap: " + configNumber("phase-five.mace-damage-cap-hearts", 7.0) + " hearts.");
            case "collapsing_star_mace" -> List.of(
                    "One spark per full heart of mace damage; consecutive hits add sparks.",
                    "Each spark deals 1 damage point in a small splash radius.",
                    "Damage cap: " + configNumber("phase-five.mace-damage-cap-hearts", 7.0) + " hearts.",
                    "Cooldown: 30 seconds after 2 successful hits.");
            case "nebula_spear" -> List.of(
                    "Every 5 successful Lunge activations: thrust forward and damage targets ahead.",
                    "Cooldown: " + configLong("cooldowns.nebula_spear", 45) + " seconds.");
            case "wormhole_trident" -> List.of(
                    "Every 5 successful Riptides: open two reusable rifts for "
                            + configLong("phase-five.wormhole-trident.lifetime-after-passage-seconds", 50) + " seconds.",
                    "Travel between the rifts; non-owners take damage and Wither I.");
            case "celestial_bow" -> List.of(
                    "Fully charged shots deal splash damage and create a lingering "
                            + plugin.getConfig().getString("phase-five.celestial-bow.cloud-effect", "Wither")
                            + " cloud.");
            case "cosmic_crown" -> List.of(
                    "Every 2 hits taken: retaliate against the attacker.",
                    "Boosts absorption; golden apples grant 4 hearts, enchanted golden apples 10.");
            case "singularity_boots" -> List.of(
                    "Cancel fall damage.",
                    "Falls of " + configNumber("phase-five.singularity-boots.explosion-fall-distance", 50.0)
                            + " blocks or more cause an explosion.");
            case "nova_shield" -> List.of(
                    "2 fully charged axe critical hits disable blocking for 10 seconds.",
                    "In the offhand, prevents one lethal hit; rescue cooldown: "
                            + configLong("cooldowns.nova_shield", 600) + " seconds.");
            default -> List.of();
        };
    }

    private long configLong(String path, long fallback) {
        return plugin.getConfig().getLong(path, fallback);
    }

    private double configNumber(String path, double fallback) {
        return plugin.getConfig().getDouble(path, fallback);
    }

    private String enchantmentName(Enchantment enchantment) {
        String key = enchantment.getKey().getKey();
        String[] words = key.split("_");
        StringBuilder name = new StringBuilder();
        for (String word : words) {
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.toString();
    }

    private String romanNumeral(int level) {
        String[] numerals = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        if (level > 0 && level < numerals.length) {
            return numerals[level];
        }
        return Integer.toString(level);
    }

    public static void disableVanillaMaceRecipe() {
        NamespacedKey vanillaMaceRecipe = NamespacedKey.minecraft("mace");
        Bukkit.removeRecipe(vanillaMaceRecipe);
        for (Recipe recipe : Bukkit.getRecipesFor(new ItemStack(Material.MACE))) {
            if (recipe instanceof Keyed keyed && keyed.getKey().equals(vanillaMaceRecipe)) {
                Bukkit.removeRecipe(keyed.getKey());
            }
        }
    }
}
