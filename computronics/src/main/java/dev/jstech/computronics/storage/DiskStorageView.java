/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.storage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * Splits one disk's contents into a public view (what the network may read) and a private view (owner-only), using a capacity-fraction budget.
 *
 * <p>The public budget is a weight ceiling: {@code floor(capacityWeight * permille / 1000)}. The disk's contents are walked in their insertion order, accumulating weight; everything up to the budget is public and the remainder is private. Each type is assigned wholly to one side except the single type that straddles the budget boundary, which is split so the public weight matches the budget as closely as the unit weight allows. This makes the public/private split a pure function of (contents, capacityWeight, permille) with no movement of items between disks.</p>
 *
 * <p>The core algorithm is generic over the key type and a unit-weight function, so it can be unit-tested without Minecraft types; the {@link StorageKey} overloads simply supply {@code key -> key.weight(1L)}.</p>
 */
public final class DiskStorageView {

    private DiskStorageView() {
    }

    /** The weight budget a disk exposes to the network at the given fill permille. */
    public static long publicBudget(final long capacityWeight, final int permille) {
        return new DiskPrivacy(permille).publicShareOf(Math.max(0L, capacityWeight));
    }

    // Generic core (no Minecraft types) — walks the insertion-ordered contents up to the public budget.

    /**
     * The public portion of {@code contents}, walked in insertion order up to the weight budget. {@code unitWeight} returns the weight of one of a key (1000 for an item, 1 per mB of fluid).
     */
    public static <K> Map<K, Long> publicView(final Map<K, Long> contents, final long capacityWeight,
                                              final int permille, final ToLongFunction<K> unitWeight) {
        final Map<K, Long> out = new LinkedHashMap<>();
        long budget = publicBudget(capacityWeight, permille);
        if (budget <= 0L) {
            return out;
        }
        for (final Map.Entry<K, Long> entry : contents.entrySet()) {
            if (budget <= 0L) {
                break;
            }
            final K key = entry.getKey();
            final long have = entry.getValue() == null ? 0L : entry.getValue();
            if (have <= 0L) {
                continue;
            }
            final long unit = unitWeight.applyAsLong(key);
            final long affordable = Math.min(have, budget / unit);
            if (affordable <= 0L) {
                continue;
            }
            out.put(key, affordable);
            budget -= affordable * unit;
        }
        return out;
    }

    /** The private portion of {@code contents}: the complement of {@link #publicView}. */
    public static <K> Map<K, Long> privateView(final Map<K, Long> contents, final long capacityWeight,
                                               final int permille, final ToLongFunction<K> unitWeight) {
        final Map<K, Long> pub = publicView(contents, capacityWeight, permille, unitWeight);
        final Map<K, Long> out = new LinkedHashMap<>();
        for (final Map.Entry<K, Long> entry : contents.entrySet()) {
            final long have = entry.getValue() == null ? 0L : entry.getValue();
            if (have <= 0L) {
                continue;
            }
            final long left = have - pub.getOrDefault(entry.getKey(), 0L);
            if (left > 0L) {
                out.put(entry.getKey(), left);
            }
        }
        return out;
    }

    /** The total weight of the public view. */
    public static <K> long publicWeight(final Map<K, Long> contents, final long capacityWeight,
                                        final int permille, final ToLongFunction<K> unitWeight) {
        return weightOf(publicView(contents, capacityWeight, permille, unitWeight), unitWeight);
    }

    /** The total weight of the private view. */
    public static <K> long privateWeight(final Map<K, Long> contents, final long capacityWeight,
                                         final int permille, final ToLongFunction<K> unitWeight) {
        return weightOf(privateView(contents, capacityWeight, permille, unitWeight), unitWeight);
    }

    private static <K> long weightOf(final Map<K, Long> view, final ToLongFunction<K> unitWeight) {
        long sum = 0L;
        for (final Map.Entry<K, Long> entry : view.entrySet()) {
            sum += unitWeight.applyAsLong(entry.getKey()) * entry.getValue();
        }
        return sum;
    }

    // StorageKey overloads — the production callers; they reuse the generic core with the key's weight.

    private static final ToLongFunction<StorageKey> STORAGE_KEY_WEIGHT = key -> key.weight(1L);

    public static Map<StorageKey, Long> publicView(final Map<StorageKey, Long> contents,
                                                   final long capacityWeight, final int permille) {
        return publicView(contents, capacityWeight, permille, STORAGE_KEY_WEIGHT);
    }

    public static Map<StorageKey, Long> privateView(final Map<StorageKey, Long> contents,
                                                    final long capacityWeight, final int permille) {
        return privateView(contents, capacityWeight, permille, STORAGE_KEY_WEIGHT);
    }

    public static long publicWeight(final Map<StorageKey, Long> contents,
                                    final long capacityWeight, final int permille) {
        return publicWeight(contents, capacityWeight, permille, STORAGE_KEY_WEIGHT);
    }

    public static long privateWeight(final Map<StorageKey, Long> contents,
                                     final long capacityWeight, final int permille) {
        return privateWeight(contents, capacityWeight, permille, STORAGE_KEY_WEIGHT);
    }
}
