/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.crafting;

import dev.jstech.computronics.storage.StorageKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a pattern's "any" cells into the items this craft will actually use, against what the network holds
 * right now. A cell that accepts a tag takes the stocked item of that tag the network has the most of; a
 * tag nothing in stock matches keeps the item the author drew, so the planner may still craft it. The rest
 * of the engine then works with exact keys, as it always has.
 */
public final class AnyTagResolver {

    private AnyTagResolver() {
    }

    /** Every pattern of {@code patterns} with its tagged cells resolved against {@code stock}. */
    public static List<CraftingPattern> resolveAll(final List<CraftingPattern> patterns,
                                                   final Map<StorageKey, Long> stock) {
        final Map<String, ItemStack> chosen = new HashMap<>();
        final List<CraftingPattern> out = new ArrayList<>(patterns.size());
        for (final CraftingPattern pattern : patterns) {
            out.add(pattern.hasAnyTags()
                    ? pattern.resolved(tag -> chosen.computeIfAbsent(tag, t -> mostStocked(t, stock)))
                    : pattern);
        }
        return out;
    }

    /** {@code pattern} with its tagged cells resolved against {@code stock}. */
    public static CraftingPattern resolve(final CraftingPattern pattern, final Map<StorageKey, Long> stock) {
        return pattern.hasAnyTags() ? pattern.resolved(tag -> mostStocked(tag, stock)) : pattern;
    }

    /** {@code pipeline} with every bench stage's tagged cells resolved against {@code stock}. */
    public static MultiStagePattern resolve(final MultiStagePattern pipeline, final Map<StorageKey, Long> stock) {
        final Map<String, ItemStack> chosen = new HashMap<>();
        return pipeline.resolved(tag -> chosen.computeIfAbsent(tag, t -> mostStocked(t, stock)));
    }

    /**
     * The stocked item of {@code tagId} the network has the most of, as a stack of one, or an empty stack when
     * nothing in stock carries the tag (or the id is not a tag id at all).
     */
    @Nullable
    public static ItemStack mostStocked(final String tagId, final Map<StorageKey, Long> stock) {
        final TagKey<Item> tag = tagOf(tagId);
        if (tag == null) {
            return ItemStack.EMPTY;
        }
        ItemStack best = ItemStack.EMPTY;
        long bestCount = 0L;
        for (final Map.Entry<StorageKey, Long> entry : stock.entrySet()) {
            final StorageKey key = entry.getKey();
            if (!key.isItem() || entry.getValue() <= bestCount) {
                continue;
            }
            final ItemStack candidate = key.stack(1);
            if (candidate.is(tag)) {
                best = candidate;
                bestCount = entry.getValue();
            }
        }
        return best;
    }

    /** The item tag {@code tagId} names, or null when it is not a valid id. */
    @Nullable
    public static TagKey<Item> tagOf(final String tagId) {
        if (tagId == null || tagId.isBlank()) {
            return null;
        }
        final ResourceLocation id = ResourceLocation.tryParse(tagId.startsWith("#") ? tagId.substring(1) : tagId);
        return id == null ? null : TagKey.create(Registries.ITEM, id);
    }
}
