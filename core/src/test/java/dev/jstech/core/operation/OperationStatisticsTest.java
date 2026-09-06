/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.operation;

import dev.jstech.core.operation.OperationStatistics.TypeSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationStatisticsTest {

    private static final int SELECT = 0;
    private static final int CRAFT = 8;
    private static final int MINUTE = OperationStatistics.TICKS_PER_MINUTE;
    private static final int HOUR = OperationStatistics.WINDOW_TICKS;

    private static TypeSummary only(final List<TypeSummary> summaries, final int type) {
        for (final TypeSummary summary : summaries) {
            if (summary.type() == type) {
                return summary;
            }
        }
        throw new AssertionError("no summary for type " + type + " in " + summaries);
    }

    @Test
    void record_averagesWaitAndRunPerType() {
        final OperationStatistics stats = new OperationStatistics();
        stats.record(100L, SELECT, false, 10, 30, 64L);
        stats.record(200L, SELECT, false, 20, 50, 32L);
        stats.record(300L, CRAFT, true, 0, 400, 1L);
        final List<TypeSummary> summaries = stats.summaries(300L);
        assertEquals(2, summaries.size());
        final TypeSummary select = only(summaries, SELECT);
        assertEquals(2, select.count());
        assertEquals(15, select.averageWait());
        assertEquals(40, select.averageRun());
        assertEquals(96L, select.moved());
        assertEquals(0, select.shortfalls());
        final TypeSummary craft = only(summaries, CRAFT);
        assertEquals(1, craft.count());
        assertEquals(1, craft.shortfalls());
        assertEquals(100, craft.shortfallPercent());
        assertEquals(3L, stats.settledTotal());
    }

    @Test
    void shortfallPercent_isRoundedWholePercent() {
        final OperationStatistics stats = new OperationStatistics();
        stats.record(1L, SELECT, true, 0, 1, 0L);
        stats.record(2L, SELECT, false, 0, 1, 0L);
        stats.record(3L, SELECT, false, 0, 1, 0L);
        assertEquals(33, only(stats.summaries(3L), SELECT).shortfallPercent());
    }

    @Test
    void summaries_dropRecordsOlderThanAnHour() {
        final OperationStatistics stats = new OperationStatistics();
        stats.record(0L, SELECT, false, 0, 10, 5L);
        assertEquals(1, only(stats.summaries(HOUR - 1), SELECT).count());
        // An hour later the minute bucket the record sat in is reached again and cleared.
        assertTrue(stats.summaries(HOUR + MINUTE).isEmpty());
        assertEquals(0L, stats.movedLastHour(HOUR + MINUTE));
        assertEquals(1L, stats.settledTotal(), "the lifetime tally keeps counting");
    }

    @Test
    void summaries_keepRecordsWithinTheHourWhileOlderMinutesClear() {
        final OperationStatistics stats = new OperationStatistics();
        stats.record(0L, SELECT, false, 0, 10, 5L);          // minute 0
        stats.record(30L * MINUTE, SELECT, false, 0, 10, 7L); // minute 30
        // Minute 61: minute 0's bucket cleared, minute 30's still inside the hour.
        final TypeSummary select = only(stats.summaries(61L * MINUTE), SELECT);
        assertEquals(1, select.count());
        assertEquals(7L, select.moved());
    }

    @Test
    void advance_afterALongSilenceClearsEverything() {
        final OperationStatistics stats = new OperationStatistics();
        stats.record(0L, SELECT, false, 0, 10, 5L);
        stats.record(5L * MINUTE, CRAFT, false, 0, 10, 5L);
        assertTrue(stats.summaries(10L * HOUR).isEmpty());
    }

    @Test
    void movedLastHour_sumsEveryType() {
        final OperationStatistics stats = new OperationStatistics();
        stats.record(0L, SELECT, false, 0, 1, 10L);
        stats.record(1L, CRAFT, false, 0, 1, 15L);
        assertEquals(25L, stats.movedLastHour(2L));
    }

    @Test
    void peakConcurrent_tracksTheDaysHighWaterMark() {
        final OperationStatistics stats = new OperationStatistics();
        stats.observeConcurrency(0L, 2);
        stats.observeConcurrency(10L, 5);
        stats.observeConcurrency(20L, 3);
        assertEquals(5, stats.peakConcurrentLastDay(20L));
        // Two hours later the peak still counts; a day later its hour slot has been cleared.
        assertEquals(5, stats.peakConcurrentLastDay(2L * HOUR));
        assertEquals(0, stats.peakConcurrentLastDay(25L * HOUR));
    }

    @Test
    void record_ignoresNegativeInputs() {
        final OperationStatistics stats = new OperationStatistics();
        stats.record(0L, SELECT, false, -5, -5, -5L);
        final TypeSummary select = only(stats.summaries(0L), SELECT);
        assertEquals(0, select.averageWait());
        assertEquals(0, select.averageRun());
        assertEquals(0L, select.moved());
    }
}
