/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.integration.jei;

import dev.jsc.jscomputronics.integration.jei.payload.SetProcessingPatternPayload;
import dev.jsc.jscomputronics.module.computing.blockentity.PatternEncoderBlockEntity;
import dev.jsc.jscomputronics.module.computing.client.PatternEncoderScreen;
import dev.jsc.jscomputronics.module.computing.menu.PatternEncoderMenu;
import dev.jsc.jscomputronics.module.computing.storage.ChemicalBridge;
import dev.jsc.jscomputronics.module.computing.storage.ChemicalBridges;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Sends any recipe shown in JEI to the Pattern Encoder's PROCESSING tab as a draft: every input and output slot
 * becomes a data cell — an item with its count, a fluid or a chemical with its millibuckets. A recipe that
 * meters its chemical per tick (as some machines do) shows the per-tick figure, so the transfer multiplies it by
 * the machine's base duration and marks the cell as an estimate the author can confirm or edit.
 */
public final class PatternEncoderProcessingTransferHandler
        implements IUniversalRecipeTransferHandler<PatternEncoderMenu> {

    /**
     * The duration a per-tick recipe runs for without speed upgrades, in ticks (ten seconds); the same across the
     * machines that meter their input that way. Upgrades shorten it, which is why the result is an estimate.
     */
    public static final int PER_TICK_BASE_TICKS = 200;

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

        final boolean perTick = ChemicalBridges.perTickUsage(recipe);
        final List<PatternEncoderBlockEntity.DataCell> inputs = cells(recipeSlotsView, RecipeIngredientRole.INPUT, perTick);
        final List<PatternEncoderBlockEntity.DataCell> outputs = cells(recipeSlotsView, RecipeIngredientRole.OUTPUT, false);
        PacketDistributor.sendToServer(new SetProcessingPatternPayload(container.blockEntityPos(), inputs, outputs));

        // Flip the open screen to the PROCESSING tab so the transferred recipe is immediately visible.
        if (Minecraft.getInstance().screen instanceof PatternEncoderScreen screen) {
            screen.showProcessingTab();
        }
        return null;
    }

    /** One cell per slot with the given role, in display order; slots showing nothing are skipped. */
    private static List<PatternEncoderBlockEntity.DataCell> cells(final IRecipeSlotsView view,
                                                                  final RecipeIngredientRole role, final boolean perTick) {
        final List<PatternEncoderBlockEntity.DataCell> cells = new ArrayList<>();
        for (final IRecipeSlotView slot : view.getSlotViews(role)) {
            final PatternEncoderBlockEntity.DataCell cell = cellOf(slot, perTick);
            if (cell != null) {
                cells.add(cell);
            }
        }
        return cells;
    }

    /** The first displayed ingredient of a slot as a cell: an item, else a fluid, else a chemical a bridge knows. */
    @Nullable
    private static PatternEncoderBlockEntity.DataCell cellOf(final IRecipeSlotView slot, final boolean perTick) {
        final Optional<ItemStack> item = slot.getItemStacks().filter(s -> !s.isEmpty()).findFirst();
        if (item.isPresent()) {
            return new PatternEncoderBlockEntity.DataCell(StorageKey.of(item.get()), item.get().getCount(), false);
        }
        final Optional<FluidStack> fluid = slot.getIngredients(NeoForgeTypes.FLUID_STACK).filter(f -> !f.isEmpty()).findFirst();
        if (fluid.isPresent()) {
            return new PatternEncoderBlockEntity.DataCell(StorageKey.of(fluid.get()), fluid.get().getAmount(), false);
        }
        for (final ITypedIngredient<?> typed : slot.getAllIngredients().toList()) {
            final Optional<ChemicalBridge.ChemicalAmount> chemical = ChemicalBridges.chemicalIngredient(typed.getIngredient());
            if (chemical.isPresent()) {
                final long amount = perTick ? chemical.get().amount() * PER_TICK_BASE_TICKS : chemical.get().amount();
                return new PatternEncoderBlockEntity.DataCell(StorageKey.chemical(chemical.get().chemical()), amount, perTick);
            }
        }
        return null;
    }
}
