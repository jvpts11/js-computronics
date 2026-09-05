/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.industrial.blockentity;

import dev.jstech.core.util.FieldContainerData;
import dev.jsc.jscomputronics.module.industrial.IndustrialModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * The Electric Furnace's processing logic: smelts items using vanilla smelting recipes, spending
 * {@value #FE_PER_TICK} FE per tick over {@value #PROCESS_TIME} ticks (Tier-1).
 */
public class ElectricFurnaceBlockEntity extends AbstractMachineBlockEntity {

    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    public static final int FE_PER_TICK = 30;
    public static final int PROCESS_TIME = 160;
    private static final int ENERGY_CAPACITY = 12_000;
    private static final int ENERGY_MAX_RECEIVE = 600;

    private int progress;

    public ElectricFurnaceBlockEntity(final BlockPos pos, final BlockState state) {
        super(IndustrialModule.ELECTRIC_FURNACE_BE.get(), pos, state,
                2, ENERGY_CAPACITY, ENERGY_MAX_RECEIVE, 0);
    }

    public static void serverTick(final Level level, final BlockPos pos,
                                  final BlockState state, final ElectricFurnaceBlockEntity be) {
        be.tick(level);
    }

    private void tick(final Level level) {
        final Optional<RecipeHolder<SmeltingRecipe>> recipe = currentRecipe(level);
        if (recipe.isEmpty() || !hasOutputSpace(level, recipe.get())) {
            if (progress != 0) {
                progress = 0;
                setChanged();
            }
            return;
        }
        if (energy.consume(FE_PER_TICK)) {
            progress++;
            if (progress >= PROCESS_TIME) {
                craft(level, recipe.get());
                progress = 0;
            }
            setChanged();
        }
    }

    private Optional<RecipeHolder<SmeltingRecipe>> currentRecipe(final Level level) {
        final ItemStack input = inventory.getStackInSlot(INPUT_SLOT);
        if (input.isEmpty()) {
            return Optional.empty();
        }
        return level.getRecipeManager().getRecipeFor(
                RecipeType.SMELTING, new SingleRecipeInput(input), level);
    }

    private boolean hasOutputSpace(final Level level, final RecipeHolder<SmeltingRecipe> recipeHolder) {
        final ItemStack output = inventory.getStackInSlot(OUTPUT_SLOT);
        if (output.isEmpty()) {
            return true;
        }
        final ItemStack result = recipeHolder.value().getResultItem(level.registryAccess());
        return ItemStack.isSameItemSameComponents(output, result)
                && output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    private void craft(final Level level, final RecipeHolder<SmeltingRecipe> recipeHolder) {
        final ItemStack result = recipeHolder.value().getResultItem(level.registryAccess()).copy();
        inventory.extractItem(INPUT_SLOT, 1, false);
        inventory.insertItem(OUTPUT_SLOT, result, false);
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        progress = tag.getInt("Progress");
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Progress", progress);
    }

    public int getProgress() {
        return progress;
    }

    private final ContainerData dataAccess = new FieldContainerData(
            new IntSupplier[] {
                () -> progress,
                () -> PROCESS_TIME,
                () -> energy.getEnergyStored(),
            },
            new IntConsumer[] {
                value -> progress = value,
                value -> { /* PROCESS_TIME is constant */ },
                value -> energy.setEnergyStored(value),
            });

    public ContainerData getDataAccess() {
        return dataAccess;
    }
}
