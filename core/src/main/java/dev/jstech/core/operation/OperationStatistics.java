/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.operation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Rolling statistics of an orchestrator's Operations over the last hour, kept per Operation type: how many
 * settled, how long they waited for a queue and how long they ran, how many fell short, and how much they
 * moved. The hour is sixty one-minute buckets in a ring; a bucket is cleared the first time the clock
 * reaches it again, so a quiet orchestrator costs nothing to keep current. Pure logic with no world types,
 * driven by the tick clock the caller passes in.
 */
public final class OperationStatistics {

    public static final int TICKS_PER_MINUTE = 1200;
    public static final int MINUTES_PER_HOUR = 60;
    public static final int HOURS_PER_DAY = 24;
    public static final int WINDOW_TICKS = TICKS_PER_MINUTE * MINUTES_PER_HOUR;

    /**
     * One type's summary over the last hour.
     *
     * @param type           the Operation type id (the orchestrator's own numbering)
     * @param count          Operations of the type that settled in the hour
     * @param shortfalls     of those, how many did not deliver everything (failed, partial, discarded, timed out)
     * @param averageWait    mean ticks spent queued or waiting before running
     * @param averageRun     mean ticks spent running
     * @param moved          items the type moved in the hour
     */
    public record TypeSummary(int type, int count, int shortfalls, int averageWait, int averageRun, long moved) {

        /** The share of settled Operations that fell short, in whole percent. */
        public int shortfallPercent() {
            return count == 0 ? 0 : (int) Math.round(100.0 * shortfalls / count);
        }
    }

    /** One type's ring of minute buckets. */
    private static final class TypeRing {
        final int[] count = new int[MINUTES_PER_HOUR];
        final int[] shortfalls = new int[MINUTES_PER_HOUR];
        final long[] waited = new long[MINUTES_PER_HOUR];
        final long[] ran = new long[MINUTES_PER_HOUR];
        final long[] moved = new long[MINUTES_PER_HOUR];

        void clear(final int bucket) {
            count[bucket] = 0;
            shortfalls[bucket] = 0;
            waited[bucket] = 0L;
            ran[bucket] = 0L;
            moved[bucket] = 0L;
        }
    }

    private final Map<Integer, TypeRing> rings = new TreeMap<>();
    private final int[] peakByHour = new int[HOURS_PER_DAY];
    private long lastMinute = Long.MIN_VALUE;
    private long lastHour = Long.MIN_VALUE;
    private long settledTotal;

    /**
     * Records one settled Operation.
     *
     * @param nowTick     the tick it settled on
     * @param type        the Operation type id
     * @param shortfall   whether it delivered less than asked (failed, partial, discarded, timed out)
     * @param waitedTicks ticks it spent queued or waiting before running
     * @param ranTicks    ticks it spent running
     * @param moved       what it moved
     */
    public void record(final long nowTick, final int type, final boolean shortfall, final int waitedTicks,
                       final int ranTicks, final long moved) {
        advance(nowTick);
        final int bucket = bucketOf(nowTick);
        final TypeRing ring = rings.computeIfAbsent(type, t -> new TypeRing());
        ring.count[bucket]++;
        if (shortfall) {
            ring.shortfalls[bucket]++;
        }
        ring.waited[bucket] += Math.max(0, waitedTicks);
        ring.ran[bucket] += Math.max(0, ranTicks);
        ring.moved[bucket] += Math.max(0L, moved);
        settledTotal++;
    }

    /** Notes how many Operations are in flight this tick, for the day's peak. */
    public void observeConcurrency(final long nowTick, final int inFlight) {
        advance(nowTick);
        final int hour = hourOf(nowTick);
        if (inFlight > peakByHour[hour]) {
            peakByHour[hour] = inFlight;
        }
    }

    /** The per-type summaries of the last hour, in type order; types with nothing in the hour are left out. */
    public List<TypeSummary> summaries(final long nowTick) {
        advance(nowTick);
        final List<TypeSummary> out = new ArrayList<>();
        for (final Map.Entry<Integer, TypeRing> entry : rings.entrySet()) {
            final TypeRing ring = entry.getValue();
            int count = 0;
            int shortfalls = 0;
            long waited = 0L;
            long ran = 0L;
            long moved = 0L;
            for (int i = 0; i < MINUTES_PER_HOUR; i++) {
                count += ring.count[i];
                shortfalls += ring.shortfalls[i];
                waited += ring.waited[i];
                ran += ring.ran[i];
                moved += ring.moved[i];
            }
            if (count > 0) {
                out.add(new TypeSummary(entry.getKey(), count, shortfalls,
                        (int) (waited / count), (int) (ran / count), moved));
            }
        }
        return out;
    }

    /** Items every type together moved in the last hour. */
    public long movedLastHour(final long nowTick) {
        long total = 0L;
        for (final TypeSummary summary : summaries(nowTick)) {
            total += summary.moved();
        }
        return total;
    }

    /** The most Operations seen in flight at once during the last day. */
    public int peakConcurrentLastDay(final long nowTick) {
        advance(nowTick);
        int peak = 0;
        for (final int value : peakByHour) {
            peak = Math.max(peak, value);
        }
        return peak;
    }

    /** Operations settled since this tally started. */
    public long settledTotal() {
        return settledTotal;
    }

    private static int bucketOf(final long tick) {
        return (int) Math.floorMod(tick / TICKS_PER_MINUTE, MINUTES_PER_HOUR);
    }

    private static int hourOf(final long tick) {
        return (int) Math.floorMod(tick / WINDOW_TICKS, HOURS_PER_DAY);
    }

    /**
     * Moves the clock forward, clearing every minute bucket the clock passed since the last call (all of
     * them when more than an hour went by) and every hour slot passed for the day's peaks.
     */
    private void advance(final long nowTick) {
        final long minute = Math.floorDiv(nowTick, TICKS_PER_MINUTE);
        if (lastMinute == Long.MIN_VALUE) {
            lastMinute = minute;
        } else if (minute > lastMinute) {
            final long passed = Math.min(minute - lastMinute, MINUTES_PER_HOUR);
            for (long m = lastMinute + 1; m <= lastMinute + passed; m++) {
                final int bucket = (int) Math.floorMod(m, MINUTES_PER_HOUR);
                for (final TypeRing ring : rings.values()) {
                    ring.clear(bucket);
                }
            }
            lastMinute = minute;
        }
        final long hour = Math.floorDiv(nowTick, WINDOW_TICKS);
        if (lastHour == Long.MIN_VALUE) {
            lastHour = hour;
        } else if (hour > lastHour) {
            final long passed = Math.min(hour - lastHour, HOURS_PER_DAY);
            for (long h = lastHour + 1; h <= lastHour + passed; h++) {
                peakByHour[(int) Math.floorMod(h, HOURS_PER_DAY)] = 0;
            }
            lastHour = hour;
        }
    }
}
