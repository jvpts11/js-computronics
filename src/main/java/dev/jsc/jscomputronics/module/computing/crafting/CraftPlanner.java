/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.crafting;

import dev.jsc.jscomputronics.module.computing.storage.StorageKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The recursive CRAFT planner: given the network's pattern set and its current stock, expands a request ("512 pistons") bottom-up into ordered pattern executions.
 */
public final class CraftPlanner {

    private static final int MAX_DEPTH = 16;

    private CraftPlanner() {
    }

    /**
     * One pattern executed {@code runs} times, inputs guaranteed by the steps before it.
     */
    public record Step(CraftingPattern pattern, long runs) {

        public long produced() {
            return runs * pattern.result().getCount();
        }
    }

    /**
     * The bill of materials for a request.
     */
    public record Plan(List<Step> steps, Map<StorageKey, Long> rawConsumption,
                       Map<StorageKey, Long> missing, long produced) {

        public boolean feasible() {
            return missing.isEmpty();
        }
    }

    public static Plan plan(final StorageKey resultKey, final long quantity,
                            final List<CraftingPattern> patterns, final Map<StorageKey, Long> stock) {
        final State state = new State(patterns, stock);
        final long covered = state.produce(resultKey, quantity, 0, new HashSet<>(), true);
        return new Plan(List.copyOf(state.steps), Map.copyOf(state.rawConsumption),
                Map.copyOf(state.missing), covered);
    }

    public static long maxFeasible(final StorageKey resultKey, final long quantity,
                                   final List<CraftingPattern> patterns, final Map<StorageKey, Long> stock) {
        long low = 0;
        // Cap the search ceiling so the midpoint arithmetic below cannot overflow when quantity is near
        // Long.MAX_VALUE (e.g. an IQL CRAFT with no count cap); a craft beyond this bound is unrealistic.
        long high = Math.min(quantity, 2_000_000_000L);
        while (low < high) {
            final long mid = low + (high - low + 1) / 2;
            if (plan(resultKey, mid, patterns, stock).feasible()) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    /**
     * Mutable planning pass: virtual stock + intermediates, consumed as the tree expands.
     */
    private static final class State {
        private final List<CraftingPattern> patterns;
        private final Map<StorageKey, Long> remainingStock;
        private final Map<StorageKey, Long> intermediates = new HashMap<>();
        private final List<Step> steps = new ArrayList<>();
        private final Map<StorageKey, Long> rawConsumption = new LinkedHashMap<>();
        private final Map<StorageKey, Long> missing = new LinkedHashMap<>();

        private State(final List<CraftingPattern> patterns, final Map<StorageKey, Long> stock) {
            this.patterns = patterns;
            this.remainingStock = new HashMap<>(stock);
        }

        private long produce(final StorageKey key, final long quantity, final int depth,
                             final Set<StorageKey> chain, final boolean isRoot) {
            long deficit = quantity;

            if (!isRoot) {
                deficit -= takeFrom(intermediates, key, deficit);
                final long fromStock = takeFrom(remainingStock, key, deficit);
                if (fromStock > 0) {
                    rawConsumption.merge(key, fromStock, Long::sum);
                    deficit -= fromStock;
                }
            }
            if (deficit <= 0) {
                return quantity;
            }

            final CraftingPattern pattern = patternFor(key);
            if (pattern == null || depth >= MAX_DEPTH || chain.contains(key)) {
                missing.merge(key, deficit, Long::sum);
                return quantity - deficit;
            }

            final long perRun = pattern.result().getCount();
            final long runs = (deficit + perRun - 1) / perRun;

            // Secure every ingredient before this step executes (dependency order).
            chain.add(key);
            long feasibleRuns = runs;
            for (final Map.Entry<StorageKey, Long> ingredient : pattern.ingredientTotals().entrySet()) {
                final long need = ingredient.getValue() * runs;
                final long got = produce(ingredient.getKey(), need, depth + 1, chain, false);
                if (got < need) {
                    // Short on this ingredient: only the runs it fully covers can execute.
                    feasibleRuns = Math.min(feasibleRuns, got / ingredient.getValue());
                }
            }
            chain.remove(key);

            if (feasibleRuns < runs) {
                // The uncovered remainder of the request is missing; surplus ingredients secured
                // above stay in the virtual pools (the real operation only locks what it uses).
                missing.merge(key, deficit - feasibleRuns * perRun, Long::sum);
            }
            if (feasibleRuns > 0) {
                steps.add(new Step(pattern, feasibleRuns));
                final long produced = feasibleRuns * perRun;
                final long surplus = produced - Math.min(deficit, produced);
                if (surplus > 0) {
                    intermediates.merge(key, surplus, Long::sum); // rounding overflow — never wasted
                }
                deficit -= Math.min(deficit, produced);
            }
            return quantity - deficit;
        }

        private CraftingPattern patternFor(final StorageKey key) {
            for (final CraftingPattern pattern : patterns) {
                if (StorageKey.of(pattern.result()).equals(key)) {
                    return pattern;
                }
            }
            return null;
        }

        private static long takeFrom(final Map<StorageKey, Long> pool, final StorageKey key, final long want) {
            if (want <= 0) {
                return 0;
            }
            final long have = pool.getOrDefault(key, 0L);
            final long taken = Math.min(have, want);
            if (taken > 0) {
                pool.put(key, have - taken);
            }
            return taken;
        }
    }
}
