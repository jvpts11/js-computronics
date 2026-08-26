/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.integration.jei;

import dev.jsc.jscomputronics.integration.jei.payload.SetProcessingPatternPayload;
import dev.jsc.jscomputronics.module.computing.client.PatternEncoderScreen;
import dev.jsc.jscomputronics.module.computing.menu.PatternEncoderMenu;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Transfers any non-crafting JEI recipe (smelting, mod machine categories, ...) into the Pattern Encoder's
 * PROCESSING draft when the player clicks the "+" transfer button: INPUT-role stacks become the processing
 * inputs, OUTPUT-role stacks the outputs. Vanilla crafting recipes keep their dedicated grid handler; JEI
 * prefers a category-specific handler over this universal one, so the two never compete.
 */
public final class PatternEncoderProcessingTransferHandler
        implements IUniversalRecipeTransferHandler<PatternEncoderMenu> {

    @Override
    public Class<? extends PatternEncoderMenu> getContainerClass() {
        return PatternEncoderMenu.class;
    }

    @Override
    public Optional<MenuType<PatternEncoderMenu>> getMenuType() {
        return Optional.empty();
    }

    @Override
    public @Nullable IRecipeTransferError transferRecipe(
            final PatternEncoderMenu container,
            final Object recipe,
            final IRecipeSlotsView recipeSlotsView,
            final Player player,
            final boolean maxTransfer,
            final boolean doTransfer) {

        if (!doTransfer) {
            // Simulation pass — a ghost draft has no missing-items error.
            return null;
        }

        final List<ItemStack> inputs = displayedStacks(recipeSlotsView, RecipeIngredientRole.INPUT);
        final List<ItemStack> outputs = displayedStacks(recipeSlotsView, RecipeIngredientRole.OUTPUT);
        PacketDistributor.sendToServer(new SetProcessingPatternPayload(
                container.blockEntityPos(), inputs, outputs));

        // Flip the open screen to the PROCESSING tab so the transferred recipe is immediately visible.
        if (Minecraft.getInstance().screen instanceof PatternEncoderScreen screen) {
            screen.showProcessingTab();
        }
        return null;
    }

    /** The first displayed stack of each slot with the given role, in display order; empty slots are skipped. */
    private static List<ItemStack> displayedStacks(final IRecipeSlotsView view, final RecipeIngredientRole role) {
        return view.getSlotViews(role).stream()
                .map(PatternEncoderProcessingTransferHandler::firstStack)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static ItemStack firstStack(final IRecipeSlotView slot) {
        return slot.getItemStacks().filter(s -> !s.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
    }
}
