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
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

/**
 * The Pattern Reader: takes pattern media and copies selected patterns into the Recipe ROM of the Crafting Computer it touches.
 */
public class PatternReaderBlockEntity extends BlockEntity {

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

    public PatternReaderBlockEntity(final BlockPos pos, final BlockState state) {
        super(ComputingModule.PATTERN_READER_BE.get(), pos, state);
    }

    public ItemStackHandler media() {
        return media;
    }

    public CraftingComputerBlockEntity adjacentComputer() {
        final Level level = getLevel();
        if (level == null) {
            return null;
        }
        for (final Direction side : Direction.values()) {
            if (level.getBlockEntity(getBlockPos().relative(side)) instanceof CraftingComputerBlockEntity cc) {
                return cc;
            }
        }
        return null;
    }

    public int loadSelected(final List<Integer> indices) {
        final CraftingComputerBlockEntity cc = adjacentComputer();
        final ItemStack disc = media.getStackInSlot(0);
        if (cc == null || disc.isEmpty()) {
            return 0;
        }
        final List<CraftingPattern> patterns = PatternDiscItem.patterns(disc);
        int loaded = 0;
        for (final int index : indices) {
            if (index >= 0 && index < patterns.size() && cc.loadPattern(patterns.get(index))) {
                loaded++;
            }
        }
        return loaded;
    }

    public int loadAll() {
        final ItemStack disc = media.getStackInSlot(0);
        final int count = PatternDiscItem.patterns(disc).size();
        final java.util.List<Integer> all = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            all.add(i);
        }
        return loadSelected(all);
    }

    /**
     * The Recipe ROM of the adjacent Crafting Computer, or an empty list when none is touching.
     */
    public List<CraftingPattern> romPatterns() {
        final CraftingComputerBlockEntity cc = adjacentComputer();
        return cc == null ? List.of() : cc.romPatterns();
    }

    public int romUsed() {
        final CraftingComputerBlockEntity cc = adjacentComputer();
        return cc == null ? 0 : cc.romUsed();
    }

    public int romLimit() {
        return CraftingComputerBlockEntity.RECIPE_ROM_LIMIT;
    }

    /**
     * Copies the chosen Recipe ROM patterns of the adjacent Crafting Computer onto the rewritable
     * disc in the media slot. The ROM keeps its patterns (an export is a copy, never a move) and the
     * whole burn spends one rewrite cycle. Returns how many patterns were written to the disc.
     */
    public int exportToDisc(final List<Integer> romIndices) {
        final CraftingComputerBlockEntity cc = adjacentComputer();
        final ItemStack disc = media.getStackInSlot(0);
        if (cc == null || disc.isEmpty() || !(disc.getItem() instanceof PatternDiscItem item)) {
            return 0;
        }
        final List<CraftingPattern> rom = cc.romPatterns();
        final List<CraftingPattern> picked = new java.util.ArrayList<>();
        for (final int index : romIndices) {
            if (index >= 0 && index < rom.size()) {
                picked.add(rom.get(index));
            }
        }
        final int written = item.writePatterns(disc, picked);
        if (written > 0) {
            setChanged();
        }
        return written;
    }

    public int exportAll() {
        final CraftingComputerBlockEntity cc = adjacentComputer();
        if (cc == null) {
            return 0;
        }
        final int count = cc.romUsed();
        final List<Integer> all = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            all.add(i);
        }
        return exportToDisc(all);
    }

    /**
     * Deletes the pattern at the given Recipe ROM index from the adjacent Crafting Computer, freeing
     * a ROM slot. The disc is not touched.
     */
    public void removeFromRom(final int index) {
        final CraftingComputerBlockEntity cc = adjacentComputer();
        if (cc != null) {
            cc.removePattern(index);
        }
    }

    public void dropContents(final Level level, final BlockPos pos) {
        final ItemStack disc = media.getStackInSlot(0);
        if (!disc.isEmpty()) {
            net.minecraft.world.Containers.dropItemStack(
                    level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, disc);
            media.setStackInSlot(0, ItemStack.EMPTY);
        }
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Media")) {
            media.deserializeNBT(registries, tag.getCompound("Media"));
        }
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Media", media.serializeNBT(registries));
    }
}
