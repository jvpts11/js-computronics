/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.operation.exec;

import dev.jstech.core.operation.OperationPriority;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides which ready Operations get a queue slot this tick. Slots go to the highest effective priority
 * first; ties keep submission order, so an Operation already streaming is never displaced by a newer one
 * of the same level. Pure logic with no world types, so the scheduling rule is unit-tested on its own.
 *
 * <p>Effective priority ages: every {@code agingTicks} an Operation spends ready but without a slot lifts
 * it one level, up to {@link OperationPriority#HIGH}. A steady stream of HIGH requests therefore cannot
 * starve a LOW one forever; it merely delays it by a bounded number of levels' worth of ticks.
 */
public final class QueueArbiter {

    private QueueArbiter() {
    }

    /**
     * One ready Operation as the arbiter sees it.
     *
     * @param operation     the Operation itself, handed back in the grant list
     * @param priority      its requested priority
     * @param deferredTicks how many ticks it has been ready without holding a slot
     */
    public record Candidate<T>(T operation, OperationPriority priority, int deferredTicks) {
    }

    /**
     * The level an Operation competes at: its own priority lifted one step per {@code agingTicks} of
     * deferral, capped at HIGH. A non-positive {@code agingTicks} disables aging.
     */
    public static OperationPriority effective(final OperationPriority priority, final int deferredTicks,
                                              final int agingTicks) {
        final OperationPriority[] levels = OperationPriority.values();
        if (agingTicks <= 0 || deferredTicks <= 0) {
            return priority;
        }
        final int lifted = Math.min(levels.length - 1, priority.ordinal() + deferredTicks / agingTicks);
        return levels[lifted];
    }

    /**
     * Picks up to {@code slots} candidates. The result keeps the winners in the order they were given, so
     * a caller ticking them in list order preserves submission order among equals.
     */
    public static <T> List<T> grant(final List<Candidate<T>> ready, final int slots, final int agingTicks) {
        if (slots <= 0 || ready.isEmpty()) {
            return List.of();
        }
        // Rank by effective level, descending; a stable sort keeps submission order inside one level.
        final List<Integer> order = new ArrayList<>(ready.size());
        for (int i = 0; i < ready.size(); i++) {
            order.add(i);
        }
        order.sort((a, b) -> Integer.compare(rank(ready.get(b), agingTicks), rank(ready.get(a), agingTicks)));
        final boolean[] granted = new boolean[ready.size()];
        for (int i = 0; i < Math.min(slots, order.size()); i++) {
            granted[order.get(i)] = true;
        }
        final List<T> out = new ArrayList<>(Math.min(slots, ready.size()));
        for (int i = 0; i < ready.size(); i++) {
            if (granted[i]) {
                out.add(ready.get(i).operation());
            }
        }
        return out;
    }

    private static int rank(final Candidate<?> candidate, final int agingTicks) {
        return effective(candidate.priority(), candidate.deferredTicks(), agingTicks).ordinal();
    }
}
