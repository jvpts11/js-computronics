/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.blockentity;

import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.crafting.CraftingPattern;
import dev.jsc.jscomputronics.module.computing.item.PatternDiscItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * The Pattern Encoder: a workstation that turns a recipe laid out on a ghost 3x3 grid into a crafting pattern written onto pattern media.
 */
public class PatternEncoderBlockEntity extends BlockEntity {

    private final ItemStackHandler ghostGrid = new ItemStackHandler(CraftingPattern.GRID_SIZE) {
        @Override
        protected void onContentsChanged(final int slot) {
            setChanged();
            refreshPreview();
        }
    };

    private final ItemStackHandler media = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(final int slot, final ItemStack stack) {
            return stack.getItem() instanceof PatternDiscItem;
        }

        @Override
        public int getSlotLimit(final int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(final int slot) {
            setChanged();
        }
    };

    private ItemStack preview = ItemStack.EMPTY;

    public PatternEncoderBlockEntity(final BlockPos pos, final BlockState state) {
        super(ComputingModule.PATTERN_ENCODER_BE.get(), pos, state);
    }

    public ItemStackHandler ghostGrid() {
        return ghostGrid;
    }

    public ItemStackHandler media() {
        return media;
    }

    public ItemStack preview() {
        return preview;
    }

    public void setGhost(final int cell, final ItemStack stack) {
        if (cell < 0 || cell >= CraftingPattern.GRID_SIZE) {
            return;
        }
        ghostGrid.setStackInSlot(cell, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
    }

    public void refreshPreview() {
        final Level level = getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        final List<ItemStack> cells = gridCells();
        boolean empty = true;
        for (final ItemStack cell : cells) {
            if (!cell.isEmpty()) {
                empty = false;
                break;
            }
        }
        if (empty) {
            preview = ItemStack.EMPTY;
            return;
        }
        final CraftingInput input = CraftingInput.of(3, 3, cells);
        preview = level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(holder -> holder.value().assemble(input, level.registryAccess()))
                .orElse(ItemStack.EMPTY);
    }

    public boolean canWrite() {
        if (preview.isEmpty() || media.getStackInSlot(0).isEmpty()) {
            return false;
        }
        final ItemStack disc = media.getStackInSlot(0);
        // A spent rewritable disc is read-only; a write-once disc is always writable (append-only).
        return disc.getItem() instanceof PatternDiscItem item
                && (!item.isRewritable() || PatternDiscItem.cyclesLeft(disc) > 0);
    }

    public boolean writePattern() {
        refreshPreview();
        if (!canWrite()) {
            return false;
        }
        final ItemStack disc = media.getStackInSlot(0);
        if (!(disc.getItem() instanceof PatternDiscItem item)) {
            return false;
        }
        final boolean written = item.write(disc, new CraftingPattern(gridCells(), preview.copy()));
        if (written) {
            media.setStackInSlot(0, disc); // re-set so the slot syncs the new component to the client
            setChanged();
        }
        return written;
    }

    public boolean eraseMedia() {
        final ItemStack disc = media.getStackInSlot(0);
        if (disc.getItem() instanceof PatternDiscItem item && item.canErase(disc)) {
            item.erase(disc);
            media.setStackInSlot(0, disc);
            setChanged();
            return true;
        }
        return false;
    }

    public void dropContents(final Level level, final BlockPos pos) {
        final ItemStack disc = media.getStackInSlot(0);
        if (!disc.isEmpty()) {
            net.minecraft.world.Containers.dropItemStack(
                    level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, disc);
            media.setStackInSlot(0, ItemStack.EMPTY);
        }
    }

    private List<ItemStack> gridCells() {
        final List<ItemStack> cells = new ArrayList<>(CraftingPattern.GRID_SIZE);
        for (int i = 0; i < CraftingPattern.GRID_SIZE; i++) {
            cells.add(ghostGrid.getStackInSlot(i));
        }
        return cells;
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("GhostGrid")) {
            ghostGrid.deserializeNBT(registries, tag.getCompound("GhostGrid"));
        }
        if (tag.contains("Media")) {
            media.deserializeNBT(registries, tag.getCompound("Media"));
        }
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("GhostGrid", ghostGrid.serializeNBT(registries));
        tag.put("Media", media.serializeNBT(registries));
    }
}
