/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A crafting pattern: a snapshot of a 3x3 crafting-grid recipe as digital data.
 */
public record CraftingPattern(List<ItemStack> grid, ItemStack result) {

    public static final int GRID_SIZE = 9;

    public CraftingPattern {
        if (grid.size() != GRID_SIZE) {
            throw new IllegalArgumentException("pattern grid must have " + GRID_SIZE + " cells, got " + grid.size());
        }
        grid = List.copyOf(grid);
    }

    public static final Codec<CraftingPattern> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.OPTIONAL_CODEC.listOf().fieldOf("grid").forGetter(CraftingPattern::grid),
            ItemStack.CODEC.fieldOf("result").forGetter(CraftingPattern::result)
    ).apply(instance, CraftingPattern::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftingPattern> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()), CraftingPattern::grid,
                    ItemStack.STREAM_CODEC, CraftingPattern::result,
                    CraftingPattern::new);

    public Map<StorageKey, Long> ingredientTotals() {
        final Map<StorageKey, Long> totals = new LinkedHashMap<>();
        for (final ItemStack stack : grid) {
            if (!stack.isEmpty()) {
                totals.merge(StorageKey.of(stack), 1L, Long::sum);
            }
        }
        return totals;
    }

    public int filledCells() {
        int n = 0;
        for (final ItemStack stack : grid) {
            if (!stack.isEmpty()) {
                n++;
            }
        }
        return n;
    }

    public boolean sameRecipe(final CraftingPattern other) {
        if (!ItemStack.isSameItem(result, other.result) || result.getCount() != other.result.getCount()) {
            return false;
        }
        for (int i = 0; i < GRID_SIZE; i++) {
            final ItemStack a = grid.get(i);
            final ItemStack b = other.grid.get(i);
            if (a.isEmpty() != b.isEmpty() || (!a.isEmpty() && !ItemStack.isSameItem(a, b))) {
                return false;
            }
        }
        return true;
    }

    // ItemStack has no value-based equals/hashCode in 1.21.1, so the record-generated ones compared by
    // identity — making two patterns that hold the same recipe unequal and breaking this type's use as a
    // data-component value (dedupe, stack comparison). Compare and hash the stacks by value instead.

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CraftingPattern other) || !ItemStack.matches(result, other.result)
                || grid.size() != other.grid.size()) {
            return false;
        }
        for (int i = 0; i < grid.size(); i++) {
            if (!ItemStack.matches(grid.get(i), other.grid.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int h = hashStack(result);
        for (final ItemStack stack : grid) {
            h = 31 * h + hashStack(stack);
        }
        return h;
    }

    private static int hashStack(final ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        int h = stack.getItem().hashCode();
        h = 31 * h + stack.getComponents().hashCode();
        return 31 * h + stack.getCount();
    }
}
