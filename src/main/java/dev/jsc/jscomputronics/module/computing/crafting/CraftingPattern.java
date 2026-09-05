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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A crafting pattern: a snapshot of a 3x3 crafting-grid recipe as digital data.
 *
 * <p>Beyond the grid and its result, a pattern carries what its author gave it: a name and a note (both
 * optional; an unnamed pattern goes by its result), and per cell an optional item tag the cell accepts
 * instead of the exact item in the grid. A cell with a tag is "any": the network may use whatever it has of
 * that tag, resolved against stock right before a craft is planned. The default is exact, so a pattern
 * written before tags existed behaves as it always did.
 *
 * @param grid    the nine cells, empty stacks where the recipe leaves a cell empty
 * @param result  what one run produces
 * @param anyTags per cell, the id of the item tag the cell accepts ({@code "minecraft:planks"}), or
 *                {@code ""} for the exact item only; always nine entries
 * @param name    the author's name for the pattern, or {@code ""}
 * @param note    the author's note, or {@code ""}
 */
public record CraftingPattern(List<ItemStack> grid, ItemStack result, List<String> anyTags, String name,
                              String note) {

    public static final int GRID_SIZE = 9;
    public static final int MAX_NAME = 64;
    public static final int MAX_NOTE = 256;

    public CraftingPattern {
        if (grid.size() != GRID_SIZE) {
            throw new IllegalArgumentException("pattern grid must have " + GRID_SIZE + " cells, got " + grid.size());
        }
        grid = List.copyOf(grid);
        anyTags = normalizeTags(anyTags);
        name = clamp(name, MAX_NAME);
        note = clamp(note, MAX_NOTE);
    }

    /** A pattern with exact cells and no name: what every author wrote before names and tags existed. */
    public CraftingPattern(final List<ItemStack> grid, final ItemStack result) {
        this(grid, result, List.of(), "", "");
    }

    public static final Codec<CraftingPattern> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.OPTIONAL_CODEC.listOf().fieldOf("grid").forGetter(CraftingPattern::grid),
            ItemStack.CODEC.fieldOf("result").forGetter(CraftingPattern::result),
            Codec.STRING.listOf().optionalFieldOf("any", List.of()).forGetter(CraftingPattern::tagsForCodec),
            Codec.STRING.optionalFieldOf("name", "").forGetter(CraftingPattern::name),
            Codec.STRING.optionalFieldOf("note", "").forGetter(CraftingPattern::note)
    ).apply(instance, CraftingPattern::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftingPattern> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(16)), CraftingPattern::grid,
                    ItemStack.STREAM_CODEC, CraftingPattern::result,
                    ByteBufCodecs.stringUtf8(128).apply(ByteBufCodecs.list(16)), CraftingPattern::anyTags,
                    ByteBufCodecs.stringUtf8(MAX_NAME), CraftingPattern::name,
                    ByteBufCodecs.stringUtf8(MAX_NOTE), CraftingPattern::note,
                    CraftingPattern::new);

    /** The tags as written to a file: nothing at all when every cell is exact, so old readers see old files. */
    private List<String> tagsForCodec() {
        return hasAnyTags() ? anyTags : List.of();
    }

    private static List<String> normalizeTags(final List<String> tags) {
        final List<String> out = new ArrayList<>(GRID_SIZE);
        for (int i = 0; i < GRID_SIZE; i++) {
            final String tag = tags != null && i < tags.size() ? tags.get(i) : null;
            out.add(tag == null ? "" : tag.trim());
        }
        return List.copyOf(out);
    }

    private static String clamp(final String s, final int max) {
        final String value = s == null ? "" : s.trim();
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** The tag cell {@code cell} accepts, or {@code ""} when it takes the exact item only. */
    public String anyTagAt(final int cell) {
        return anyTags.get(cell);
    }

    /** Whether any cell accepts a tag rather than its exact item. */
    public boolean hasAnyTags() {
        for (final String tag : anyTags) {
            if (!tag.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** What the pattern is called where it is listed: its name, or its result's name when it has none. */
    public String displayName() {
        return name.isEmpty() ? result.getHoverName().getString() : name;
    }

    /** The same recipe under a new name and note. */
    public CraftingPattern withName(final String newName, final String newNote) {
        return new CraftingPattern(grid, result, anyTags, newName, newNote);
    }

    /** The same recipe with cell {@code cell} accepting {@code tagId} ({@code ""} makes it exact again). */
    public CraftingPattern withAnyTag(final int cell, final String tagId) {
        final List<String> tags = new ArrayList<>(anyTags);
        tags.set(cell, tagId == null ? "" : tagId);
        return new CraftingPattern(grid, result, tags, name, note);
    }

    /**
     * This pattern with every tagged cell replaced by what {@code chooser} picks for its tag (a stack of one),
     * or left as written when it picks nothing. The tags, name and note stay, so the pattern still says what
     * it accepts; only the grid names what this particular craft will use.
     */
    public CraftingPattern resolved(final Function<String, ItemStack> chooser) {
        if (!hasAnyTags()) {
            return this;
        }
        final List<ItemStack> cells = new ArrayList<>(grid);
        for (int i = 0; i < GRID_SIZE; i++) {
            final String tag = anyTags.get(i);
            if (tag.isEmpty() || cells.get(i).isEmpty()) {
                continue;
            }
            final ItemStack chosen = chooser.apply(tag);
            if (chosen != null && !chosen.isEmpty()) {
                cells.set(i, chosen.copyWithCount(1));
            }
        }
        return new CraftingPattern(cells, result, anyTags, name, note);
    }

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

    /**
     * Whether two patterns describe the same recipe: the same cells, result and tags; the name is not the
     * recipe. A cell that accepts a tag is the same cell whichever item of that tag either side holds, so a
     * pattern resolved against stock still matches the pattern it was resolved from in a Recipe ROM.
     */
    public boolean sameRecipe(final CraftingPattern other) {
        if (!ItemStack.isSameItem(result, other.result) || result.getCount() != other.result.getCount()
                || !anyTags.equals(other.anyTags)) {
            return false;
        }
        for (int i = 0; i < GRID_SIZE; i++) {
            final ItemStack a = grid.get(i);
            final ItemStack b = other.grid.get(i);
            if (a.isEmpty() != b.isEmpty()) {
                return false;
            }
            if (a.isEmpty() || ItemStack.isSameItem(a, b)) {
                continue;
            }
            final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag = AnyTagResolver.tagOf(anyTags.get(i));
            if (tag == null || !a.is(tag) || !b.is(tag)) {
                return false;
            }
        }
        return true;
    }

    // ItemStack has no value-based equals/hashCode in 1.21.1, so the record-generated ones compared by
    // identity — making two patterns that hold the same recipe unequal and breaking this type's use as a
    // data-component value (dedupe, stack comparison). Compare and hash the stacks by value instead. The
    // name and note are the author's label, not the recipe, and stay out of it.

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CraftingPattern other) || !ItemStack.matches(result, other.result)
                || grid.size() != other.grid.size() || !anyTags.equals(other.anyTags)) {
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
        return 31 * h + anyTags.hashCode();
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
