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

import java.util.List;
import java.util.Map;

/**
 * Turns a craft request into the plan the craft runs: the full quantity when the network can make it, else
 * (when the requester allows a partial) as much as it can. Pure CPU over immutable inputs — the patterns,
 * the machines and a stock snapshot — so it runs the same on the main thread or on a virtual thread.
 */
public final class CraftPlanning {

    private CraftPlanning() {
    }

    /** A feasible plan and the quantity it makes ({@code target} is the request, or less for a partial). */
    public record Planned(CraftPlanner.Plan plan, long target) {
    }

    /**
     * Plans {@code demand} of {@code key}. Returns null when nothing on the network makes it, or when the
     * full quantity cannot be made and {@code partial} is off (or nothing at all can be made).
     */
    @Nullable
    public static Planned plan(final StorageKey key, final long demand, final boolean partial,
                               final List<CraftingPattern> patterns, final List<ProcessingPattern> machines,
                               final Map<StorageKey, Long> stock) {
        long target = demand;
        CraftPlanner.Plan plan = CraftPlanner.plan(key, target, patterns, machines, stock);
        if (plan.steps().isEmpty()) {
            return null; // no pattern on the network produces this item
        }
        if (!plan.feasible()) {
            if (!partial) {
                return null;
            }
            target = CraftPlanner.maxFeasible(key, demand, patterns, machines, stock);
            if (target <= 0) {
                return null;
            }
            plan = CraftPlanner.plan(key, target, patterns, machines, stock);
        }
        return new Planned(plan, target);
    }
}
