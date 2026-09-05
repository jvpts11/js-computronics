/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.crafting;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.ArrayList;
import java.util.List;

/**
 * How a game recipe lays out on the Pattern Studio's bench: its ingredients on the 3x3 grid, and per cell the
 * tag the ingredient stands for, so a recipe transferred from the recipe viewer comes in with its "any" cells
 * marked. Reads only items, which every recipe exposes.
 */
public final class RecipeBook {

    private RecipeBook() {
    }

    /** A bench recipe's ingredients laid on a 3x3 grid: shaped by its width, shapeless in reading order. */
    public static List<ItemStack> benchGrid(final Recipe<?> recipe) {
        final List<ItemStack> grid = new ArrayList<>(CraftingPattern.GRID_SIZE);
        for (int i = 0; i < CraftingPattern.GRID_SIZE; i++) {
            grid.add(ItemStack.EMPTY);
        }
        final List<Ingredient> ingredients = recipe.getIngredients();
        if (recipe instanceof ShapedRecipe shaped) {
            final int w = shaped.getWidth();
            final int h = shaped.getHeight();
            for (int row = 0; row < h && row < 3; row++) {
                for (int col = 0; col < w && col < 3; col++) {
                    final int i = row * w + col;
                    if (i < ingredients.size()) {
                        grid.set(row * 3 + col, first(ingredients.get(i)));
                    }
                }
            }
        } else {
            int cell = 0;
            for (final Ingredient ingredient : ingredients) {
                if (cell >= CraftingPattern.GRID_SIZE) {
                    break;
                }
                final ItemStack first = first(ingredient);
                if (!first.isEmpty()) {
                    grid.set(cell++, first);
                }
            }
        }
        return grid;
    }

    /**
     * Per grid cell, the tag the recipe's ingredient stands for when it accepts more than one item (the smallest
     * item tag holding all of them), else {@code ""}. Lets an "any planks" recipe come in as any planks.
     */
    public static List<String> benchTags(final Recipe<?> recipe) {
        final List<String> tags = new ArrayList<>(CraftingPattern.GRID_SIZE);
        for (int i = 0; i < CraftingPattern.GRID_SIZE; i++) {
            tags.add("");
        }
        final List<Ingredient> ingredients = recipe.getIngredients();
        if (recipe instanceof ShapedRecipe shaped) {
            final int w = shaped.getWidth();
            final int h = shaped.getHeight();
            for (int row = 0; row < h && row < 3; row++) {
                for (int col = 0; col < w && col < 3; col++) {
                    final int i = row * w + col;
                    if (i < ingredients.size()) {
                        tags.set(row * 3 + col, tagOf(ingredients.get(i)));
                    }
                }
            }
        } else {
            int cell = 0;
            for (final Ingredient ingredient : ingredients) {
                if (cell >= CraftingPattern.GRID_SIZE) {
                    break;
                }
                if (!first(ingredient).isEmpty()) {
                    tags.set(cell++, tagOf(ingredient));
                }
            }
        }
        return tags;
    }

    private static ItemStack first(final Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return ItemStack.EMPTY;
        }
        final ItemStack[] items = ingredient.getItems();
        return items.length == 0 || items[0].isEmpty() ? ItemStack.EMPTY : items[0].copyWithCount(1);
    }

    /**
     * The id of the smallest item tag that holds every item the ingredient accepts, or {@code ""} when the
     * ingredient is one item (or no tag covers all of them).
     */
    public static String tagOf(final Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return "";
        }
        final ItemStack[] items = ingredient.getItems();
        if (items.length < 2) {
            return "";
        }
        final List<Holder<Item>> holders = new ArrayList<>();
        for (final ItemStack s : items) {
            if (!s.isEmpty()) {
                holders.add(s.getItemHolder());
            }
        }
        TagKey<Item> best = null;
        int bestSize = Integer.MAX_VALUE;
        for (final var pair : BuiltInRegistries.ITEM.getTags().toList()) {
            final HolderSet.Named<Item> set = pair.getSecond();
            final int size = set.size();
            if (size < holders.size() || size >= bestSize) {
                continue;
            }
            boolean all = true;
            for (final Holder<Item> h : holders) {
                if (!set.contains(h)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                best = pair.getFirst();
                bestSize = size;
            }
        }
        return best == null ? "" : best.location().toString();
    }
}
