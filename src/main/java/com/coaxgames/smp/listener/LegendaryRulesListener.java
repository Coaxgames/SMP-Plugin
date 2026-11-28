package com.coaxgames.smp.listener;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.item.LegendaryItemRegistry;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class LegendaryRulesListener implements Listener {
    private static final Set<EntityDamageEvent.DamageCause> RECOVERY_DAMAGE_CAUSES = EnumSet.of(
            EntityDamageEvent.DamageCause.BLOCK_EXPLOSION,
            EntityDamageEvent.DamageCause.CONTACT,
            EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,
            EntityDamageEvent.DamageCause.FIRE,
            EntityDamageEvent.DamageCause.FIRE_TICK,
            EntityDamageEvent.DamageCause.HOT_FLOOR,
            EntityDamageEvent.DamageCause.LAVA,
            EntityDamageEvent.DamageCause.VOID
    );

    private final GalaxySmpPlugin plugin;
    private final LegendaryItemRegistry items;
    private final ReclaimableItemService reclaim;
    private final java.util.Map<UUID, String> trackedPositions = new java.util.HashMap<>();
    private org.bukkit.scheduler.BukkitTask trackingTask;

    public LegendaryRulesListener(GalaxySmpPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        items = plugin.getLegendaryItems();
        reclaim = plugin.getReclaimableItems();
    }

    public void startTracking() {
        if (trackingTask == null) {
            trackingTask = plugin.getServer().getScheduler().runTaskTimer(
                    plugin, this::refreshTrackedLocations, 20L, 20L);
        }
    }

    public void shutdown() {
        if (trackingTask != null) {
            trackingTask.cancel();
            trackingTask = null;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event instanceof CraftItemEvent craftEvent
                && items.getForRecipe(craftEvent.getRecipe()).isPresent()) {
            return;
        }
        if ((isBundle(event.getCurrentItem()) || isBundle(event.getCursor()))
                && (items.containsProtectedItem(event.getCurrentItem())
                || items.containsProtectedItem(event.getCursor()))) {
            event.setCancelled(true);
            return;
        }
        if (!items.isLegendary(event.getCurrentItem()) && !items.isLegendary(event.getCursor())
                && !items.isLegendary(hotbarItem(event))
                && !items.containsRegisteredItem(event.getCurrentItem())
                && !items.containsRegisteredItem(event.getCursor())
                && !items.containsRegisteredItem(hotbarItem(event))) {
            return;
        }
        boolean clickedTop = event.getClickedInventory() == event.getView().getTopInventory();
        if (clickedTop && (isLegendary(event.getCurrentItem()) || isLegendary(event.getCursor())
                || isLegendary(hotbarItem(event)))) {
            event.setCancelled(true);
            return;
        }
        if (isExternalInventory(event.getView().getTopInventory())
                && (event.getClickedInventory() == event.getView().getBottomInventory()
                && isLegendary(event.getCurrentItem())
                && (event.isShiftClick() || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY)
                || event.getAction() == InventoryAction.COLLECT_TO_CURSOR
                && (containsProtectedItem(event.getView().getTopInventory().getContents())
                || containsProtectedItem(event.getView().getBottomInventory().getContents())))) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (containsProtectedItem(event.getInventory().getContents())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("Legendary items cannot be stored in containers.");
            return;
        }
        if (event.getPlayer() instanceof Player player
                && (event.getInventory().getType() == InventoryType.CRAFTING
                || event.getInventory().getType() == InventoryType.WORKBENCH)) {
            player.discoverRecipes(items.getRecipeKeys());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (isLegendary(event.getOldCursor())
                && event.getRawSlots().stream().anyMatch(rawSlot -> rawSlot < event.getView().getTopInventory().getSize())) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (isLegendary(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        if (isLegendary(event.getItem().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPickup(EntityPickupItemEvent event) {
        ItemStack stack = event.getItem().getItemStack();
        if (items.removeForbiddenLunge(stack)) {
            event.getItem().setItemStack(stack);
        }
        var definition = items.identify(stack).orElse(null);
        if (definition == null) {
            if (items.containsProtectedItem(stack) && !items.isLegendary(stack)) {
                event.setCancelled(true);
            }
            return;
        }
        if (!definition.protectedFromLossAndStorage()
                && !definition.trackUniqueInstance()
                && !definition.hasPerPlayerCarryLimit()) {
            boolean itemUpdated = items.refreshCooldownGroup(stack);
            itemUpdated |= items.refreshMassOrb(stack);
            itemUpdated |= refreshItemModel(stack);
            if (itemUpdated) {
                event.getItem().setItemStack(stack);
            }
            return;
        }
        if (definition.trackUniqueInstance() && isPendingRitualItem(stack)) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            if (items.containsProtectedItem(stack)) {
                event.setCancelled(true);
            }
            return;
        }
        if (!items.isLegendary(stack)) {
            return;
        }
        if (!persistOwnerLocationAndStatus(
                stack, player.getUniqueId(), player.isOp(), player.getLocation(), true)) {
            event.setCancelled(true);
            return;
        }
        boolean itemUpdated = items.removeForbiddenLunge(stack);
        itemUpdated |= items.upgradeVoidSword(stack);
        itemUpdated |= items.upgradeLunarLeggings(stack);
        itemUpdated |= items.removeWeaponFireEnchantments(stack);
        itemUpdated |= items.removeCrownVanillaThorns(stack);
        itemUpdated |= items.refreshDailyEffectLore(stack);
        itemUpdated |= items.refreshAstralSwordSpeedLore(stack);
        itemUpdated |= items.initializeStarMaceProgression(stack);
        itemUpdated |= items.refreshStarMaceCooldownLore(stack);
        itemUpdated |= items.refreshKuiperBallLore(stack);
        itemUpdated |= items.refreshCollapsingStarCooldownLore(stack);
        itemUpdated |= items.refreshNebulaCooldownLore(stack);
        itemUpdated |= items.refreshCooldownGroup(stack);
        itemUpdated |= refreshItemModel(stack);
        if (itemUpdated) {
            event.getItem().setItemStack(stack);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDrop(PlayerDropItemEvent event) {
        ItemStack stack = event.getItemDrop().getItemStack();
        if (items.containsProtectedItem(stack) && !items.isLegendary(stack)) {
            event.setCancelled(true);
            return;
        }
        if (items.isLegendary(stack)) {
            Player player = event.getPlayer();
            if (!persistOwnerLocationAndStatus(
                    stack, player.getUniqueId(), player.isOp(), player.getLocation(), true)
                    || !persistOwnerLocationAndStatus(
                            stack, null, false, event.getItemDrop().getLocation(), true)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        var drops = event.getDrops().iterator();
        while (drops.hasNext()) {
            ItemStack stack = drops.next();
            if (items.isLegendary(stack)) {
                persistLocation(stack, player.getLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Set<UUID> held = new java.util.HashSet<>();
        for (ItemStack stack : allCarriedItems(player)) {
            if (items.isLegendary(stack)) {
                items.getUniqueId(stack).ifPresent(held::add);
                persistOwnerLocationAndStatus(
                        stack, player.getUniqueId(), player.isOp(), player.getLocation(), true);
            }
        }
        ItemStack cursor = player.getItemOnCursor();
        if (items.isLegendary(cursor)) {
            items.getUniqueId(cursor).ifPresent(held::add);
            persistOwnerLocationAndStatus(
                    cursor, player.getUniqueId(), player.isOp(), player.getLocation(), true);
        }
        try {
            plugin.getDataStore().clearMissingHeldItems(player.getUniqueId(), held);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not reconcile item ownership on logout for "
                    + player.getUniqueId() + ".", exception);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.discoverRecipes(items.getRecipeKeys());
            }
        });
        Inventory enderChest = player.getEnderChest();
        for (int slot = 0; slot < enderChest.getSize(); slot++) {
            ItemStack stack = enderChest.getItem(slot);
            if (!isLegendary(stack)) {
                continue;
            }
            Item dropped = player.getWorld().dropItem(player.getLocation(), stack.clone());
            if (reclaim.returnToReclaimBlock(dropped)) {
                enderChest.setItem(slot, null);
            } else {
                dropped.remove();
                plugin.getLogger().severe("Could not move a legendary item out of " + player.getName()
                        + "'s ender chest; the item remains in place.");
            }
        }
        refreshItemModels(player.getInventory());
        for (String id : com.coaxgames.smp.util.ItemCooldownMarkers.PERSISTED_ITEMS) {
            com.coaxgames.smp.util.ItemCooldownMarkers.sync(player, id, plugin.getCooldownManager());
        }
    }

    // Items created before a model was assigned keep stale metadata, so backfill the expected item model.
    public void refreshItemModels(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            boolean itemUpdated = items.upgradeVoidSword(stack);
            itemUpdated |= items.upgradeLunarLeggings(stack);
            itemUpdated |= items.removeWeaponFireEnchantments(stack);
            itemUpdated |= items.removeCrownVanillaThorns(stack);
            itemUpdated |= items.refreshDailyEffectLore(stack);
            itemUpdated |= items.refreshAstralSwordSpeedLore(stack);
            itemUpdated |= items.initializeStarMaceProgression(stack);
            itemUpdated |= items.refreshStarMaceCooldownLore(stack);
            itemUpdated |= items.refreshKuiperBallLore(stack);
            itemUpdated |= items.refreshCollapsingStarCooldownLore(stack);
            itemUpdated |= items.refreshNebulaCooldownLore(stack);
            itemUpdated |= items.refreshCooldownGroup(stack);
            itemUpdated |= items.refreshMassOrb(stack);
            itemUpdated |= refreshItemModel(stack);
            if (itemUpdated) {
                inventory.setItem(slot, stack);
            }
        }
    }

    private boolean refreshItemModel(ItemStack stack) {
        var definition = items.identify(stack).orElse(null);
        if (definition == null) {
            return false;
        }
        var expected = definition.createBaseItem().getItemMeta();
        var meta = stack.getItemMeta();
        if (expected == null || meta == null || !expected.hasItemModel()) {
            return false;
        }
        boolean changed = false;
        if (!expected.getItemModel().equals(meta.hasItemModel() ? meta.getItemModel() : null)) {
            meta.setItemModel(expected.getItemModel());
            changed = true;
        }
        if (expected.hasEquippable()
                && (!meta.hasEquippable()
                || !Objects.equals(expected.getEquippable().getModel(), meta.getEquippable().getModel()))) {
            meta.setEquippable(expected.getEquippable());
            changed = true;
        }
        if (changed && !stack.setItemMeta(meta)) {
            throw new IllegalStateException("Could not update item model for " + definition.id() + ".");
        }
        return changed;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof ItemFrame itemFrame && isLegendary(itemFrame.getItem())) {
            event.setCancelled(true);
            return;
        }
        if (event.getEntity() instanceof org.bukkit.entity.ArmorStand armorStand
                && armorStand.getEquipment() != null
                && containsProtectedItem(armorStand.getEquipment().getArmorContents())) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getEntity() instanceof Item item) || !isLegendary(item.getItemStack())) {
            return;
        }
        if (isPendingRitualItem(item.getItemStack())) {
            event.setCancelled(true);
            return;
        }
        if (RECOVERY_DAMAGE_CAUSES.contains(event.getCause())) {
            event.setCancelled(true);
            reclaim.returnToReclaimBlock(item);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemCombust(EntityCombustEvent event) {
        if (event.getEntity() instanceof Item item && isLegendary(item.getItemStack())) {
            event.setCancelled(true);
            if (isPendingRitualItem(item.getItemStack())) {
                return;
            }
            reclaim.returnToReclaimBlock(item);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDespawn(ItemDespawnEvent event) {
        if (isLegendary(event.getEntity().getItemStack())) {
            event.setCancelled(true);
            if (isPendingRitualItem(event.getEntity().getItemStack())) {
                return;
            }
            reclaim.returnToReclaimBlock(event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemMerge(ItemMergeEvent event) {
        if (event.getEntity() instanceof Item first && isLegendary(first.getItemStack())
                || event.getTarget() instanceof Item second && isLegendary(second.getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (isLegendary(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemMend(PlayerItemMendEvent event) {
        if (isLegendary(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        if (isLegendary(event.getItem())) {
            event.setCancelled(true);
            return;
        }
        var offers = event.getOffers();
        for (int i = 0; i < offers.length; i++) {
            if (offers[i] != null
                    && LegendaryItemRegistry.isLungeEnchantment(offers[i].getEnchantment())) {
                offers[i] = null;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (isLegendary(event.getItem())) {
            event.setCancelled(true);
            return;
        }
        event.getEnchantsToAdd().keySet()
                .removeIf(LegendaryItemRegistry::isLungeEnchantment);
        if (event.getEnchantsToAdd().isEmpty()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (containsProtectedItem(event.getInventory().getContents())) {
            event.getInventory().setResult(null);
            return;
        }
        ItemStack result = event.getInventory().getResult();
        if (items.removeForbiddenLunge(result)) {
            event.getInventory().setResult(result);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInventorySlotChange(PlayerInventorySlotChangeEvent event) {
        ItemStack stack = event.getNewItemStack();
        updateInventoryTracking(event, stack);
        if (items.removeForbiddenLunge(stack)) {
            event.getPlayer().getInventory().setItem(event.getSlot(), stack);
        }
    }

    private void updateInventoryTracking(PlayerInventorySlotChangeEvent event, ItemStack newStack) {
        if (items.isLegendary(newStack)) {
            persistOwnerLocationAndStatus(
                    newStack, event.getPlayer().getUniqueId(), event.getPlayer().isOp(),
                    event.getPlayer().getLocation(), true);
        }
    }

    private void refreshTrackedLocations() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            for (ItemStack stack : allCarriedItems(player)) {
                persistOwnerLocationAndStatus(
                        stack, player.getUniqueId(), player.isOp(), player.getLocation(), true);
            }
        }
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            for (org.bukkit.entity.Item item : world.getEntitiesByClass(org.bukkit.entity.Item.class)) {
                persistOwnerLocationAndStatus(item.getItemStack(), null, false, item.getLocation(), true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        if (containsProtectedItem(event.getInventory().getContents())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        ItemStack handItem = event.getPlayer().getInventory().getItem(event.getHand());
        if (event.getRightClicked() instanceof ItemFrame && isLegendary(handItem)
                || items.containsProtectedItem(handItem) && !items.isProtectedItem(handItem)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUseContainerItem(PlayerInteractEvent event) {
        ItemStack handItem = event.getItem();
        if (items.containsProtectedItem(handItem) && !items.isProtectedItem(handItem)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (isLegendary(event.getPlayerItem()) || isLegendary(event.getArmorStandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onReclaimInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null
                && (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR)
                && reclaim.claim(event.getClickedBlock(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onReclaimBreak(BlockBreakEvent event) {
        if (reclaim.isReclaimBlock(event.getBlock()) || event.getBlock().getState() instanceof org.bukkit.block.Container container
                && containsProtectedItem(container.getInventory().getContents())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (isLegendary(item)
                || items.identify(item)
                        .map(definition -> Set.of("mass_orb", "galaxy_string").contains(definition.id()))
                        .orElse(false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> reclaim.isReclaimBlock(block) || containsProtectedItem(block));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> reclaim.isReclaimBlock(block) || containsProtectedItem(block));
    }

    private boolean isLegendary(ItemStack stack) {
        return items.containsProtectedItem(stack);
    }

    private boolean isPendingRitualItem(ItemStack stack) {
        return items.getUniqueId(stack)
                .filter(plugin.getRitualManager()::isPending).isPresent();
    }

    private boolean isBundle(ItemStack stack) {
        return stack != null && stack.getItemMeta() instanceof org.bukkit.inventory.meta.BundleMeta;
    }

    private boolean containsProtectedItem(ItemStack[] stacks) {
        for (ItemStack stack : stacks) {
            if (items.containsProtectedItem(stack)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsProtectedItem(Block block) {
        return block.getState() instanceof org.bukkit.block.Container container
                && containsProtectedItem(container.getInventory().getContents());
    }

    private boolean isExternalInventory(Inventory inventory) {
        return inventory.getType() != InventoryType.PLAYER && inventory.getType() != InventoryType.CRAFTING;
    }

    private ItemStack hotbarItem(InventoryClickEvent event) {
        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            return event.getWhoClicked().getInventory().getItemInOffHand();
        }
        int button = event.getHotbarButton();
        return button < 0 ? null : event.getWhoClicked().getInventory().getItem(button);
    }

    private ItemStack[] allCarriedItems(Player player) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        ItemStack[] armor = player.getInventory().getArmorContents();
        ItemStack[] all = java.util.Arrays.copyOf(contents, contents.length + armor.length + 1);
        System.arraycopy(armor, 0, all, contents.length, armor.length);
        all[all.length - 1] = player.getInventory().getItemInOffHand();
        return all;
    }

    private boolean persistOwnerAndLocation(ItemStack stack, UUID ownerUuid, org.bukkit.Location location) {
        return persistOwnerLocationAndStatus(stack, ownerUuid, false, location, true);
    }

    private boolean persistOwnerLocationAndStatus(
            ItemStack stack,
            UUID ownerUuid,
            boolean ownerOperator,
            org.bukkit.Location location,
            boolean active
    ) {
        if (!items.isLegendary(stack)) {
            return true;
        }
        var definition = items.identify(stack);
        if (definition.isPresent() && !definition.get().trackUniqueInstance()) {
            return true;
        }
        String itemId = definition.map(item -> item.id()).orElse(null);
        UUID itemUuid = items.getUniqueId(stack).orElse(null);
        if (itemId == null || itemUuid == null) {
            plugin.getLogger().severe("Encountered a legendary item with invalid identity; transfer was denied.");
            return false;
        }
        try {
            String signature = (location == null || location.getWorld() == null)
                    ? "unknown" : location.getWorld().getName()
                            + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
            String status = ownerUuid + ":" + ownerOperator + ":" + active + ":" + signature;
            if (!status.equals(trackedPositions.get(itemUuid))) {
                plugin.getDataStore().updateItemTracking(
                        itemId, itemUuid, ownerUuid, ownerOperator, location, active);
                trackedPositions.put(itemUuid, status);
            }
            return true;
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not persist ownership/location for " + itemUuid + ".", exception);
            return false;
        }
    }

    private boolean persistLocation(ItemStack stack, org.bukkit.Location location) {
        if (!items.isLegendary(stack)) {
            return true;
        }
        var definition = items.identify(stack);
        if (definition.isPresent() && !definition.get().trackUniqueInstance()) {
            return true;
        }
        UUID itemUuid = items.getUniqueId(stack).orElse(null);
        if (itemUuid == null) {
            plugin.getLogger().severe("Encountered a legendary item without a valid UUID; transfer was denied.");
            return false;
        }
        try {
            plugin.getDataStore().recordItemLocation(itemUuid, location);
            return true;
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not persist location for " + itemUuid + ".", exception);
            return false;
        }
    }
}
