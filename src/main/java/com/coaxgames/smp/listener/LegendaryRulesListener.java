package com.coaxgames.smp.listener;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.item.LegendaryItemRegistry;
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

    public LegendaryRulesListener(GalaxySmpPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        items = plugin.getLegendaryItems();
        reclaim = new ReclaimableItemService(plugin);
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
                && (event.getClickedInventory() == null
                || event.getClickedInventory() == event.getView().getBottomInventory()
                && isLegendary(event.getCurrentItem())
                && (event.isShiftClick() || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY)
                || event.getAction() == InventoryAction.COLLECT_TO_CURSOR
                && (containsProtectedItem(event.getView().getTopInventory().getContents())
                || containsProtectedItem(event.getView().getBottomInventory().getContents())))) {
            event.setCancelled(true);
            return;
        }
        if (event.getWhoClicked() instanceof Player player
                && event.getClickedInventory() == event.getView().getTopInventory()
                && event.getClickedInventory() != player.getInventory()
                && exceedsItemLimit(player, event)) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (containsProtectedItem(event.getInventory().getContents())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("Legendary items cannot be stored in containers.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getRawSlots().stream().anyMatch(rawSlot -> rawSlot >= event.getView().getTopInventory().getSize())
                && event.getWhoClicked() instanceof Player player) {
            var definition = items.identify(event.getOldCursor()).orElse(null);
            if (definition != null) {
                int incomingAmount = event.getNewItems().values().stream()
                        .filter(stack -> items.identify(stack)
                                .map(item -> item.id().equals(definition.id())).orElse(false))
                        .mapToInt(ItemStack::getAmount)
                        .sum();
                if (incomingAmount > 0
                        && !items.hasCapacity(player, definition.id(), incomingAmount)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
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
        if (items.containsProtectedItem(stack) && !items.isLegendary(stack)) {
            event.setCancelled(true);
            return;
        }
        var definition = items.identify(stack).orElse(null);
        if (definition == null) {
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
        if (!items.hasCapacity(player, definition.id(), stack.getAmount())) {
            event.setCancelled(true);
            return;
        }
        if (!items.isLegendary(stack)) {
            return;
        }
        if (!persistOwnerAndLocation(stack, player.getUniqueId(), player.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDrop(PlayerDropItemEvent event) {
        ItemStack stack = event.getItemDrop().getItemStack();
        if (items.containsProtectedItem(stack) && !items.isLegendary(stack)) {
            event.setCancelled(true);
            return;
        }
        if (items.isLegendary(stack) && !persistLocation(stack, event.getItemDrop().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        for (ItemStack stack : event.getDrops()) {
            if (items.isLegendary(stack)) {
                persistLocation(stack, event.getEntity().getLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        for (ItemStack stack : allCarriedItems(player)) {
            if (items.isLegendary(stack)) {
                persistOwnerAndLocation(stack, player.getUniqueId(), player.getLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
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
        enforceCarryingCaps(player);
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
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (isLegendary(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (containsProtectedItem(event.getInventory().getContents())) {
            event.getInventory().setResult(null);
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
        if (isLegendary(event.getItemInHand())) {
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

    private boolean exceedsItemLimit(Player player, InventoryClickEvent event) {
        var definition = items.identify(event.getCurrentItem()).orElse(null);
        if (definition == null) {
            return false;
        }
        int incomingAmount = switch (event.getAction()) {
            case PICKUP_ALL, MOVE_TO_OTHER_INVENTORY -> event.getCurrentItem().getAmount();
            case PICKUP_HALF -> (event.getCurrentItem().getAmount() + 1) / 2;
            case PICKUP_ONE -> 1;
            case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD, SWAP_WITH_CURSOR -> event.getCurrentItem().getAmount();
            case PICKUP_SOME -> {
                ItemStack cursor = event.getCursor();
                int cursorAmount = cursor != null && items.identify(cursor)
                        .filter(item -> item.id().equals(definition.id())).isPresent()
                        ? cursor.getAmount() : 0;
                yield Math.min(event.getCurrentItem().getAmount(),
                        Math.max(0, event.getCurrentItem().getMaxStackSize() - cursorAmount));
            }
            case COLLECT_TO_CURSOR -> {
                ItemStack cursor = event.getCursor();
                int cursorAmount = cursor != null && items.identify(cursor)
                        .filter(item -> item.id().equals(definition.id())).isPresent()
                        ? cursor.getAmount() : 0;
                int available = Math.max(0, event.getCurrentItem().getMaxStackSize() - cursorAmount);
                int inTopInventory = items.countItems(
                        event.getView().getTopInventory().getContents(), definition.id());
                yield Math.min(available, inTopInventory);
            }
            default -> 0;
        };
        int outgoingAmount = 0;
        if (event.getAction() == InventoryAction.HOTBAR_SWAP) {
            outgoingAmount = matchingAmount(hotbarItem(event), definition.id());
        } else if (event.getAction() == InventoryAction.SWAP_WITH_CURSOR) {
            outgoingAmount = matchingAmount(event.getCursor(), definition.id());
        }
        int limit = items.maxPerPlayer(definition.id());
        return limit > 0 && items.countForPlayer(player, definition.id())
                + incomingAmount - outgoingAmount > limit;
    }

    private int matchingAmount(ItemStack stack, String itemId) {
        return items.identify(stack)
                .filter(definition -> definition.id().equals(itemId))
                .map(ignored -> stack.getAmount())
                .orElse(0);
    }

    private void enforceCarryingCaps(Player player) {
        for (var definition : items.getAll()) {
            int limit = items.maxPerPlayer(definition.id());
            if (limit == 0) {
                continue;
            }
            int excess = items.countForPlayer(player, definition.id()) - limit;
            if (excess <= 0) {
                continue;
            }
            for (int slot = player.getInventory().getSize() - 1; slot >= 0 && excess > 0; slot--) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (items.identify(stack).filter(item -> item.id().equals(definition.id())).isEmpty()) {
                    continue;
                }
                int removeAmount = Math.min(excess, stack.getAmount());
                ItemStack removed = stack.clone();
                removed.setAmount(removeAmount);
                if (removeAmount == stack.getAmount()) {
                    player.getInventory().setItem(slot, null);
                } else {
                    ItemStack remaining = stack.clone();
                    remaining.setAmount(stack.getAmount() - removeAmount);
                    player.getInventory().setItem(slot, remaining);
                }
                if (items.isProtectedItem(removed)) {
                    Item dropped = player.getWorld().dropItem(player.getLocation(), removed);
                    if (!reclaim.returnToReclaimBlock(dropped)) {
                        dropped.remove();
                        player.getInventory().addItem(removed);
                        plugin.getLogger().severe("Could not safely enforce the configured per-player cap for "
                                + definition.id() + " held by " + player.getUniqueId() + ".");
                    }
                } else {
                    player.getWorld().dropItemNaturally(player.getLocation(), removed);
                }
                excess -= removeAmount;
            }
        }
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
        if (!items.isLegendary(stack)) {
            return true;
        }
        String itemId = items.identify(stack).map(definition -> definition.id()).orElse(null);
        UUID itemUuid = items.getUniqueId(stack).orElse(null);
        if (itemId == null || itemUuid == null) {
            plugin.getLogger().severe("Encountered a legendary item with invalid identity; transfer was denied.");
            return false;
        }
        try {
            plugin.getDataStore().setItemOwner(itemId, itemUuid, ownerUuid);
            plugin.getDataStore().recordItemLocation(itemUuid, location);
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
