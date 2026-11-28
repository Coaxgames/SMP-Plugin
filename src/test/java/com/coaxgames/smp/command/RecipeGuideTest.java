package com.coaxgames.smp.command;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.item.LegendaryItemRegistry;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

class RecipeGuideTest {
    @Test
    void specialIngredientsUseCustomItemsAndExplainHeadAndPlankRequirements() {
        GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
        LegendaryItemRegistry registry = mock(LegendaryItemRegistry.class);
        when(plugin.getLegendaryItems()).thenReturn(registry);
        RecipeGuide guide = new RecipeGuide(plugin);
        RecipeChoice choice = mock(RecipeChoice.class);
        ItemStack custom = mock(ItemStack.class);
        ItemStack customCopy = mock(ItemStack.class);
        when(custom.clone()).thenReturn(customCopy);
        when(registry.createItem("galaxy_string")).thenReturn(custom);
        when(registry.createItem("corrupt_ender_pearl")).thenReturn(custom);
        assertSame(customCopy, guide.ingredient(new NamespacedKey("galaxy", "celestial_bow"), 1, 2, choice));
        assertSame(customCopy, guide.ingredient(new NamespacedKey("galaxy", "singularity_boots"), 0, 0, choice));
        verify(registry).createItem("galaxy_string");
        verify(registry).createItem("corrupt_ender_pearl");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<ItemMeta>> modelEdit = ArgumentCaptor.forClass(Consumer.class);
        verify(customCopy, org.mockito.Mockito.times(2)).editMeta(modelEdit.capture());
        ItemMeta fallbackMeta = mock(ItemMeta.class);
        modelEdit.getValue().accept(fallbackMeta);
        verify(fallbackMeta).setItemModel(null);

        ItemStack stack = mock(ItemStack.class);
        ItemStack head = mock(ItemStack.class);
        when(stack.clone()).thenReturn(head);
        when(head.getType()).thenReturn(Material.PLAYER_HEAD);
        when(choice.getItemStack()).thenReturn(stack);
        guide.ingredient(new NamespacedKey("galaxy", "astral_sword"), 0, 0, choice);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<ItemMeta>> edits = ArgumentCaptor.forClass(Consumer.class);
        verify(head, org.mockito.Mockito.times(2)).editMeta(edits.capture());
        ItemMeta meta = mock(ItemMeta.class);
        edits.getAllValues().get(1).accept(meta);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Component>> lore = ArgumentCaptor.forClass(List.class);
        verify(meta).lore(lore.capture());
        assertTrue(PlainTextComponentSerializer.plainText().serialize(lore.getValue().getFirst())
                .contains("dropped by Head Drop"));

        RecipeChoice.MaterialChoice planks = mock(RecipeChoice.MaterialChoice.class);
        when(planks.getChoices()).thenReturn(List.of(Material.OAK_PLANKS, Material.BIRCH_PLANKS));
        when(planks.getItemStack()).thenReturn(stack);
        ItemStack plankIcon = mock(ItemStack.class);
        when(stack.clone()).thenReturn(plankIcon);
        when(plankIcon.getType()).thenReturn(Material.OAK_PLANKS);
        guide.ingredient(new NamespacedKey("galaxy", "nova_shield"), 0, 0, planks);
        verify(plankIcon, org.mockito.Mockito.times(2)).editMeta(edits.capture());
        ItemMeta plankMeta = mock(ItemMeta.class);
        edits.getAllValues().getLast().accept(plankMeta);
        verify(plankMeta).lore(lore.capture());
        assertTrue(PlainTextComponentSerializer.plainText().serialize(lore.getValue().getFirst())
                .contains("all four must match"));
    }

    @Test
    void guideDisplaysRegisteredShapeAndPreventsInventoryInteraction() {
        GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
        LegendaryItemRegistry registry = mock(LegendaryItemRegistry.class);
        Player player = mock(Player.class);
        Inventory list = mock(Inventory.class);
        Inventory detail = mock(Inventory.class);
        InventoryView view = mock(InventoryView.class);
        InventoryClickEvent click = mock(InventoryClickEvent.class);
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        NamespacedKey key = new NamespacedKey("galaxy", "example");
        ShapedRecipe recipe = mock(ShapedRecipe.class);
        RecipeChoice choice = mock(RecipeChoice.class);
        ItemStack result = mock(ItemStack.class);
        ItemStack resultCopy = mock(ItemStack.class);
        ItemStack ingredient = mock(ItemStack.class);
        ItemStack ingredientCopy = mock(ItemStack.class);
        when(result.clone()).thenReturn(resultCopy);
        when(ingredient.clone()).thenReturn(ingredientCopy);
        when(ingredientCopy.getType()).thenReturn(Material.IRON_INGOT);
        when(choice.getItemStack()).thenReturn(ingredient);
        when(recipe.getResult()).thenReturn(result);
        when(recipe.getShape()).thenReturn(new String[] {" I ", "I I"});
        when(recipe.getChoiceMap()).thenReturn(Map.of('I', choice));
        when(plugin.getLegendaryItems()).thenReturn(registry);
        when(registry.getRecipeKeys()).thenReturn(List.of(key));
        when(view.getTopInventory()).thenReturn(list);
        when(click.getView()).thenReturn(view);
        when(click.getClickedInventory()).thenReturn(list);
        when(click.getWhoClicked()).thenReturn(player);
        when(click.getRawSlot()).thenReturn(0);
        when(drag.getView()).thenReturn(view);
        RecipeGuide guide = new RecipeGuide(plugin);

        try (var bukkit = mockStatic(Bukkit.class); var buttons = mockConstruction(ItemStack.class)) {
            bukkit.when(() -> Bukkit.getRecipe(key)).thenReturn(recipe);
            bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), org.mockito.ArgumentMatchers.eq(54),
                    any(Component.class))).thenAnswer(invocation -> {
                        InventoryHolder holder = invocation.getArgument(0);
                        Inventory inventory = holder.getInventory() == null && list.getHolder() == null ? list : detail;
                        when(inventory.getHolder()).thenReturn(holder);
                        return inventory;
                    });
            guide.open(player, 0);
            guide.onClick(click);
            guide.onDrag(drag);
        }

        verify(click).setCancelled(true);
        verify(drag).setCancelled(true);
        verify(detail).setItem(11, ingredientCopy);
        verify(detail).setItem(19, ingredientCopy);
        verify(detail).setItem(21, ingredientCopy);
        verify(detail).setItem(24, resultCopy);
        assertSame(detail, detail.getHolder().getInventory());
    }
}
