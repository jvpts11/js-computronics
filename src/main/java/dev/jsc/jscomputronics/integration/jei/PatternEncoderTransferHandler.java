/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.integration.jei;

import dev.jsc.jscomputronics.integration.jei.logic.PatternGridFiller;
import dev.jsc.jscomputronics.integration.jei.payload.SetPatternPayload;
import dev.jsc.jscomputronics.module.computing.menu.PatternEncoderMenu;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
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
 * Transfers a vanilla crafting recipe from JEI into the Pattern Encoder's ghost grid
 * when the player clicks the "+" transfer button in the JEI recipe view.
 *
 * Collects INPUT-role ingredient slots in display order (left-to-right, top-to-bottom),
 * maps them to a fixed 9-cell grid via {@link PatternGridFiller}, and sends a
 * {@link SetPatternPayload} to the server for validation and application.
 */
public final class PatternEncoderTransferHandler
        implements IRecipeTransferHandler<PatternEncoderMenu, RecipeHolder<CraftingRecipe>> {

    @Override
    public Class<? extends PatternEncoderMenu> getContainerClass() {
        return PatternEncoderMenu.class;
    }

    @Override
    public Optional<MenuType<PatternEncoderMenu>> getMenuType() {
        return Optional.empty();
    }

    @Override
    public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    @Override
    public @Nullable IRecipeTransferError transferRecipe(
            final PatternEncoderMenu container,
            final RecipeHolder<CraftingRecipe> recipe,
            final IRecipeSlotsView recipeSlotsView,
            final Player player,
            final boolean maxTransfer,
            final boolean doTransfer) {

        if (!doTransfer) {
            // Simulation pass — no missing-items error for a ghost grid.
            return null;
        }

        final List<IRecipeSlotView> inputSlots =
                recipeSlotsView.getSlotViews(RecipeIngredientRole.INPUT);

        // For each input slot, pick the first non-empty displayed ItemStack.
        // Empty slots become ItemStack.EMPTY so the server clears those ghost cells.
        final List<ItemStack> ingredients = inputSlots.stream()
                .map(slot -> slot.getItemStacks()
                        .filter(s -> !s.isEmpty())
                        .findFirst()
                        .orElse(ItemStack.EMPTY))
                .toList();

        final List<ItemStack> grid = PatternGridFiller.fillGrid(ingredients, ItemStack.EMPTY);

        PacketDistributor.sendToServer(new SetPatternPayload(container.blockEntityPos(), grid));

        return null;
    }
}
