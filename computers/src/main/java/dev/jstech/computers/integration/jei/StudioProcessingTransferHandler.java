/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.integration.jei;

import dev.jstech.computers.client.os.PatternStudioApp;
import dev.jstech.computers.crafting.PatternWorkbench;
import dev.jstech.computers.integration.jei.payload.SetProcessingPatternPayload;
import dev.jstech.computers.menu.DesktopMenu;
import dev.jstech.computers.storage.IChemicalBridge;
import dev.jstech.computers.storage.ChemicalBridges;
import dev.jstech.computers.storage.StorageKey;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import net.minecraft.network.chat.Component;
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
 * Sends any recipe shown in the viewer to the Pattern Studio's machine draft: every input and output slot
 * becomes a data cell, an item with its count, a fluid or a chemical with its millibuckets. A recipe that
 * meters its chemical per tick shows the per-tick figure, so the transfer multiplies it by the machine's base
 * duration and marks the cell as an estimate the author can confirm or edit.
 */
public final class StudioProcessingTransferHandler implements IUniversalRecipeTransferHandler<DesktopMenu> {

    /** Ticks a per-tick recipe runs without speed upgrades; the same across the machines that meter that way. */
    public static final int PER_TICK_BASE_TICKS = 200;

    private final IRecipeTransferHandlerHelper helper;

    StudioProcessingTransferHandler(final IRecipeTransferHandlerHelper helper) {
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
    public @Nullable IRecipeTransferError transferRecipe(final DesktopMenu container, final Object recipe,
                                                         final IRecipeSlotsView recipeSlotsView, final Player player,
                                                         final boolean maxTransfer, final boolean doTransfer) {
        final PatternStudioApp studio = PatternStudioApp.active();
        if (studio == null || !JscJeiPlugin.studioInFront(container)) {
            return helper.createUserErrorWithTooltip(Component.literal("Open the Pattern Studio to transfer recipes"));
        }
        if (!doTransfer) {
            return null;
        }
        final boolean perTick = ChemicalBridges.perTickUsage(recipe);
        final List<PatternWorkbench.DataCell> inputs = cells(recipeSlotsView, RecipeIngredientRole.INPUT, perTick);
        final List<PatternWorkbench.DataCell> outputs = cells(recipeSlotsView, RecipeIngredientRole.OUTPUT, false);
        PacketDistributor.sendToServer(new SetProcessingPatternPayload(studio.host(), studio.monitorPos(), inputs, outputs,
                recipeTypeOf(recipe)));
        studio.showTab(PatternStudioApp.TAB_MACHINE);
        return null;
    }

    /** The recipe type id the server maps to a machine, for the recipes the game registers; empty otherwise. */
    private static String recipeTypeOf(final Object recipe) {
        if (recipe instanceof net.minecraft.world.item.crafting.RecipeHolder<?> holder) {
            final net.minecraft.resources.ResourceLocation key =
                    net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType());
            if (key != null) {
                final String id = key.toString();
                return id.length() <= SetProcessingPatternPayload.MAX_TYPE ? id : "";
            }
        }
        return "";
    }

    /** One cell per slot with the given role, in display order; slots showing nothing are skipped. */
    private static List<PatternWorkbench.DataCell> cells(final IRecipeSlotsView view, final RecipeIngredientRole role,
                                                         final boolean perTick) {
        final List<PatternWorkbench.DataCell> cells = new ArrayList<>();
        for (final IRecipeSlotView slot : view.getSlotViews(role)) {
            final PatternWorkbench.DataCell cell = cellOf(slot, perTick);
            if (cell != null) {
                cells.add(cell);
            }
        }
        return cells;
    }

    /** The first displayed ingredient of a slot as a cell: an item, else a fluid, else a chemical a bridge knows. */
    @Nullable
    private static PatternWorkbench.DataCell cellOf(final IRecipeSlotView slot, final boolean perTick) {
        final Optional<ItemStack> item = slot.getItemStacks().filter(s -> !s.isEmpty()).findFirst();
        if (item.isPresent()) {
            return new PatternWorkbench.DataCell(StorageKey.of(item.get()), item.get().getCount(), false);
        }
        final Optional<FluidStack> fluid = slot.getIngredients(NeoForgeTypes.FLUID_STACK).filter(f -> !f.isEmpty()).findFirst();
        if (fluid.isPresent()) {
            return new PatternWorkbench.DataCell(StorageKey.of(fluid.get()), fluid.get().getAmount(), false);
        }
        for (final ITypedIngredient<?> typed : slot.getAllIngredients().toList()) {
            final Optional<IChemicalBridge.ChemicalAmount> chemical = ChemicalBridges.chemicalIngredient(typed.getIngredient());
            if (chemical.isPresent()) {
                final long amount = perTick ? chemical.get().amount() * PER_TICK_BASE_TICKS : chemical.get().amount();
                return new PatternWorkbench.DataCell(StorageKey.chemical(chemical.get().chemical()), amount, perTick);
            }
        }
        return null;
    }
}
