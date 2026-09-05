/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.integration.jei;

import dev.jstech.computronics.client.os.PatternStudioApp;
import dev.jstech.computronics.integration.jei.logic.PatternGridFiller;
import dev.jstech.computronics.integration.jei.payload.SetPatternPayload;
import dev.jstech.computronics.menu.DesktopMenu;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Transfers a crafting recipe from the viewer into the Pattern Studio's bench when the player clicks the
 * viewer's transfer button with the Studio in front. Collects the input slots in display order, lays them on
 * the fixed 3x3 grid and sends them to the server, which owns the workbench.
 */
public final class StudioBenchTransferHandler
        implements IRecipeTransferHandler<DesktopMenu, RecipeHolder<CraftingRecipe>> {

    private final IRecipeTransferHandlerHelper helper;

    StudioBenchTransferHandler(final IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    @Override
    public Class<? extends DesktopMenu> getContainerClass() {
        return DesktopMenu.class;
    }

    @Override
    public Optional<MenuType<DesktopMenu>> getMenuType() {
        return Optional.empty();
    }

    @Override
    public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    @Override
    public @Nullable IRecipeTransferError transferRecipe(final DesktopMenu container,
                                                         final RecipeHolder<CraftingRecipe> recipe,
                                                         final IRecipeSlotsView recipeSlotsView, final Player player,
                                                         final boolean maxTransfer, final boolean doTransfer) {
        final PatternStudioApp studio = PatternStudioApp.active();
        if (studio == null || !JscJeiPlugin.studioInFront(container)) {
            return helper.createUserErrorWithTooltip(Component.literal("Open the Pattern Studio to transfer recipes"));
        }
        if (!doTransfer) {
            return null; // a ghost draft has no missing-items error
        }
        final List<IRecipeSlotView> inputSlots = recipeSlotsView.getSlotViews(RecipeIngredientRole.INPUT);
        final List<ItemStack> ingredients = inputSlots.stream()
                .map(slot -> slot.getItemStacks().filter(s -> !s.isEmpty()).findFirst().orElse(ItemStack.EMPTY))
                .toList();
        final List<ItemStack> grid = PatternGridFiller.fillGrid(ingredients, ItemStack.EMPTY);
        PacketDistributor.sendToServer(new SetPatternPayload(studio.host(), studio.monitorPos(), grid,
                recipe.id().toString()));
        studio.showTab(PatternStudioApp.TAB_BENCH);
        return null;
    }
}
