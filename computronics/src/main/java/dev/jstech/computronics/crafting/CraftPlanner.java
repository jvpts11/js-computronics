/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.crafting;

import dev.jstech.computronics.storage.StorageKey;

import org.jetbrains.annotations.Nullable;

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
     * One recipe executed {@code runs} times, inputs guaranteed by the steps before it: either a bench pattern
     * a Crafting Computer runs, or a machine pattern the network feeds through a Crafting Switch.
     */
    public record Step(@Nullable CraftingPattern pattern, @Nullable ProcessingPattern machine, long runs) {

        public static Step bench(final CraftingPattern pattern, final long runs) {
            return new Step(pattern, null, runs);
        }

        public static Step machine(final ProcessingPattern machine, final long runs) {
            return new Step(null, machine, runs);
        }

        public boolean isMachine() {
            return machine != null;
        }

        /** Units of the result one run yields. */
        public long perRun() {
            if (machine != null) {
                final ProcessingPattern.ProcessingOutput primary = machine.primaryOutput();
                return primary == null ? 1 : Math.max(1, primary.amount());
            }
            return Math.max(1, pattern.result().getCount());
        }

        public StorageKey resultKey() {
            if (machine != null) {
                final ProcessingPattern.ProcessingOutput primary = machine.primaryOutput();
                return primary == null ? null : primary.key();
            }
            return StorageKey.of(pattern.result());
        }

        public String resultName() {
            final StorageKey key = resultKey();
            return key == null ? "?" : key.displayName().getString();
        }

        /** Work units one run costs a computer: the ingredients it handles. */
        public long unitsPerRun() {
            return machine != null ? Math.max(1, machine.inputs().size()) : Math.max(1, pattern.filledCells());
        }

        public long produced() {
            return runs * perRun();
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
        return plan(resultKey, quantity, patterns, List.of(), stock);
    }

    /**
     * Plans with bench patterns AND machine patterns: an ingredient no bench pattern makes may come out of a
     * machine, whose own inputs are planned the same way, so a request expands through both kinds of recipe.
     */
    public static Plan plan(final StorageKey resultKey, final long quantity,
                            final List<CraftingPattern> patterns, final List<ProcessingPattern> machines,
                            final Map<StorageKey, Long> stock) {
        final State state = new State(patterns, machines, stock);
        final long covered = state.produce(resultKey, quantity, 0, new HashSet<>(), true);
        return new Plan(mergeMachineSteps(state.steps), Map.copyOf(state.rawConsumption),
                Map.copyOf(state.missing), covered);
    }

    /**
     * Folds later machine steps of the same pattern into the first one, so a machine is fed once for all the
     * runs a request needs instead of once per ingredient that needs it. A later step only moves up when every
     * input of that machine is raw stock or is made by a step that already precedes the first occurrence, so
     * the dependency order still holds; making more of an intermediate earlier never harms a later consumer.
     */
    private static List<Step> mergeMachineSteps(final List<Step> steps) {
        final List<Step> merged = new ArrayList<>();
        for (final Step step : steps) {
            boolean folded = false;
            if (step.isMachine()) {
                for (int i = 0; i < merged.size(); i++) {
                    final Step earlier = merged.get(i);
                    if (earlier.isMachine() && earlier.machine().sameRecipe(step.machine())
                            && inputsAvailableBefore(step.machine(), merged, i)) {
                        merged.set(i, Step.machine(earlier.machine(), earlier.runs() + step.runs()));
                        folded = true;
                        break;
                    }
                }
            }
            if (!folded) {
                merged.add(step);
            }
        }
        return List.copyOf(merged);
    }

    /** Whether every input of {@code machine} is raw stock or produced by a step before index {@code at}. */
    private static boolean inputsAvailableBefore(final ProcessingPattern machine, final List<Step> steps, final int at) {
        final Set<StorageKey> madeAnywhere = new HashSet<>();
        for (final Step step : steps) {
            final StorageKey made = step.resultKey();
            if (made != null) {
                madeAnywhere.add(made);
            }
        }
        final Set<StorageKey> madeEarlier = new HashSet<>();
        for (int i = 0; i < at; i++) {
            final StorageKey made = steps.get(i).resultKey();
            if (made != null) {
                madeEarlier.add(made);
            }
        }
        for (final ProcessingPattern.ProcessingInput in : machine.inputs()) {
            // An input no step makes is raw stock (locked from the start); an intermediate must already exist.
            if (madeAnywhere.contains(in.key()) && !madeEarlier.contains(in.key())) {
                return false;
            }
        }
        return true;
    }

    public static long maxFeasible(final StorageKey resultKey, final long quantity,
                                   final List<CraftingPattern> patterns, final Map<StorageKey, Long> stock) {
        return maxFeasible(resultKey, quantity, patterns, List.of(), stock);
    }

    public static long maxFeasible(final StorageKey resultKey, final long quantity,
                                   final List<CraftingPattern> patterns, final List<ProcessingPattern> machines,
                                   final Map<StorageKey, Long> stock) {
        long low = 0;
        // Cap the search ceiling so the midpoint arithmetic below cannot overflow when quantity is near
        // Long.MAX_VALUE (e.g. an IQL CRAFT with no count cap); a craft beyond this bound is unrealistic.
        long high = Math.min(quantity, 2_000_000_000L);
        while (low < high) {
            final long mid = low + (high - low + 1) / 2;
            if (plan(resultKey, mid, patterns, machines, stock).feasible()) {
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
        private final List<ProcessingPattern> machines;
        private final Map<StorageKey, Long> remainingStock;
        private final Map<StorageKey, Long> intermediates = new HashMap<>();
        private final List<Step> steps = new ArrayList<>();
        private final Map<StorageKey, Long> rawConsumption = new LinkedHashMap<>();
        private final Map<StorageKey, Long> missing = new LinkedHashMap<>();

        private State(final List<CraftingPattern> patterns, final List<ProcessingPattern> machines,
                      final Map<StorageKey, Long> stock) {
            this.patterns = patterns;
            this.machines = machines;
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

            // A bench pattern wins; otherwise a machine pattern whose primary output is the key.
            final CraftingPattern pattern = patternFor(key);
            final ProcessingPattern machine = pattern == null ? machineFor(key) : null;
            if ((pattern == null && machine == null) || depth >= MAX_DEPTH || chain.contains(key)) {
                missing.merge(key, deficit, Long::sum);
                return quantity - deficit;
            }

            final long perRun = pattern != null ? Math.max(1, pattern.result().getCount())
                    : Math.max(1, machine.primaryOutput().amount());
            final long runs = (deficit + perRun - 1) / perRun;
            final Map<StorageKey, Long> ingredients = pattern != null
                    ? pattern.ingredientTotals() : machineInputs(machine);

            // Secure every ingredient before this step executes (dependency order).
            chain.add(key);
            long feasibleRuns = runs;
            for (final Map.Entry<StorageKey, Long> ingredient : ingredients.entrySet()) {
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
                steps.add(pattern != null ? Step.bench(pattern, feasibleRuns) : Step.machine(machine, feasibleRuns));
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

        @Nullable
        private ProcessingPattern machineFor(final StorageKey key) {
            for (final ProcessingPattern machine : machines) {
                final ProcessingPattern.ProcessingOutput primary = machine.primaryOutput();
                if (primary != null && primary.key().equals(key) && !machine.inputs().isEmpty()) {
                    return machine;
                }
            }
            return null;
        }

        /** A machine pattern's inputs per run, duplicates merged, in declaration order. */
        private static Map<StorageKey, Long> machineInputs(final ProcessingPattern machine) {
            final Map<StorageKey, Long> totals = new LinkedHashMap<>();
            for (final ProcessingPattern.ProcessingInput in : machine.inputs()) {
                totals.merge(in.key(), in.amount(), Long::sum);
            }
            return totals;
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
