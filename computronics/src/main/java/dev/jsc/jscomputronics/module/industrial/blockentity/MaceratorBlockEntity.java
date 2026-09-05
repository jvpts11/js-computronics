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
import dev.jsc.jscomputronics.module.industrial.recipe.MaceratingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * The Macerator's processing logic: grinds the input item into the recipe result, spending {@value #FE_PER_TICK} FE per tick over the recipe's processing time (Tier-1: 200 ticks, ore → 2 dust).
 */
public class MaceratorBlockEntity extends AbstractMachineBlockEntity {

    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    public static final int FE_PER_TICK = 40;
    private static final int ENERGY_CAPACITY = 16_000;
    private static final int ENERGY_MAX_RECEIVE = 1_000;

    private int progress;
    private int maxProgress;

    public MaceratorBlockEntity(final BlockPos pos, final BlockState state) {
        super(IndustrialModule.MACERATOR_BE.get(), pos, state,
                2, ENERGY_CAPACITY, ENERGY_MAX_RECEIVE, 0);
    }

    public static void serverTick(final Level level, final BlockPos pos,
                                  final BlockState state, final MaceratorBlockEntity be) {
        be.tick(level);
    }

    private void tick(final Level level) {
        final Optional<RecipeHolder<MaceratingRecipe>> recipe = currentRecipe(level);
        if (recipe.isEmpty() || !hasOutputSpace(recipe.get().value())) {
            if (progress != 0) {
                progress = 0;
                setChanged();
            }
            return;
        }
        maxProgress = recipe.get().value().processingTime();
        if (energy.consume(FE_PER_TICK)) {
            progress++;
            if (progress >= maxProgress) {
                craft(recipe.get().value());
                progress = 0;
            }
            setChanged();
        }
    }

    private Optional<RecipeHolder<MaceratingRecipe>> currentRecipe(final Level level) {
        final ItemStack input = inventory.getStackInSlot(INPUT_SLOT);
        if (input.isEmpty()) {
            return Optional.empty();
        }
        return level.getRecipeManager().getRecipeFor(
                IndustrialModule.MACERATING_TYPE.get(), new SingleRecipeInput(input), level);
    }

    private boolean hasOutputSpace(final MaceratingRecipe recipe) {
        final ItemStack output = inventory.getStackInSlot(OUTPUT_SLOT);
        if (output.isEmpty()) {
            return true;
        }
        final ItemStack result = recipe.result();
        return ItemStack.isSameItemSameComponents(output, result)
                && output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    private void craft(final MaceratingRecipe recipe) {
        inventory.extractItem(INPUT_SLOT, 1, false);
        inventory.insertItem(OUTPUT_SLOT, recipe.result().copy(), false);
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

    public int getMaxProgress() {
        return maxProgress;
    }

    private final ContainerData dataAccess = new FieldContainerData(
            new IntSupplier[] {
                () -> progress,
                () -> maxProgress,
                () -> energy.getEnergyStored(),
            },
            new IntConsumer[] {
                value -> progress = value,
                value -> maxProgress = value,
                value -> energy.setEnergyStored(value),
            });

    public ContainerData getDataAccess() {
        return dataAccess;
    }
}
