/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jstech.computers.os.media.MediaFormat;
import org.junit.jupiter.api.Test;

class SetupTimingTest {

    private static int seconds(final int ticks) {
        return ticks / SetupTiming.TICKS_PER_SECOND;
    }

    @Test
    void ticks_readsTheProgramAtTheMediumsSpeed() {
        // 128 MB at a CD's 4 MB/s is 32 seconds; the same program on a DVD, 16 MB/s, is 8.
        assertEquals(32, seconds(SetupTiming.ticks(128, MediaFormat.CD, false)));
        assertEquals(8, seconds(SetupTiming.ticks(128, MediaFormat.DVD, false)));
        assertEquals(16, seconds(SetupTiming.ticks(16, MediaFormat.FLOPPY, false)));
    }

    @Test
    void ticks_neverGoesUnderTheFloorSoTheWindowIsSeen() {
        assertEquals(SetupTiming.MIN_SECONDS, seconds(SetupTiming.ticks(16, MediaFormat.USB, false)));
        assertEquals(SetupTiming.MIN_SECONDS, seconds(SetupTiming.ticks(0, MediaFormat.DVD, false)));
    }

    @Test
    void ticks_neverGoesOverTheCeilingSoNobodyWaitsForAGame() {
        assertEquals(SetupTiming.MAX_SECONDS, seconds(SetupTiming.ticks(512, MediaFormat.FLOPPY, false)));
        assertEquals(SetupTiming.MAX_SECONDS, seconds(SetupTiming.ticks(100_000, MediaFormat.USB, false)));
    }

    @Test
    void ticks_removingTakesAFifthOfInstalling() {
        final int install = SetupTiming.ticks(512, MediaFormat.DVD, false);
        final int remove = SetupTiming.ticks(512, MediaFormat.DVD, true);
        assertEquals(install / SetupTiming.REMOVE_DIVISOR, remove);
        // And never nothing: even a tiny program is seen going.
        assertTrue(SetupTiming.ticks(1, MediaFormat.USB, true) >= SetupTiming.TICKS_PER_SECOND);
    }

    @Test
    void ticks_treatsARateOfNothingAsTheSlowestMedium() {
        assertEquals(SetupTiming.ticks(16, MediaFormat.FLOPPY, false), SetupTiming.ticks(16, 0.0, false));
        assertEquals(SetupTiming.ticks(16, MediaFormat.FLOPPY, false), SetupTiming.ticks(16, -5.0, false));
    }

    @Test
    void networkTicks_readsAtTheNetworksRate() {
        assertEquals(16, seconds(SetupTiming.networkTicks(128, false)));
    }

    @Test
    void rateOf_ordersTheMediaByAge() {
        assertTrue(SetupTiming.rateOf(MediaFormat.FLOPPY) < SetupTiming.rateOf(MediaFormat.CD));
        assertTrue(SetupTiming.rateOf(MediaFormat.CD) < SetupTiming.rateOf(MediaFormat.DVD));
        assertTrue(SetupTiming.rateOf(MediaFormat.DVD) < SetupTiming.rateOf(MediaFormat.USB));
    }
}
