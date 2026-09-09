/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SetupJobTest {

    private static SetupJob job(final int ticks, final boolean removing) {
        return new SetupJob("jsc:virtual_studio", "Virtual Studio", "Midsoft", 512, "DVD", removing, ticks);
    }

    @Test
    void tick_countsDownAndSaysWhenTheLastOneWent() {
        final SetupJob job = job(3, false);
        assertFalse(job.tick());
        assertFalse(job.tick());
        assertTrue(job.tick());
        assertTrue(job.finished());
        assertTrue(job.tick(), "a finished job stays finished");
    }

    @Test
    void permille_goesFromNothingToEverything() {
        final SetupJob job = job(4, false);
        assertEquals(0, job.permille());
        job.tick();
        assertEquals(250, job.permille());
        job.tick();
        job.tick();
        job.tick();
        assertEquals(1000, job.permille());
    }

    @Test
    void secondsLeft_roundsUpSoItNeverSaysZeroWhileGoing() {
        final SetupJob job = job(SetupTiming.TICKS_PER_SECOND + 1, false);
        assertEquals(2, job.secondsLeft());
        job.tick();
        assertEquals(1, job.secondsLeft());
    }

    @Test
    void phase_changesAsTheBarMoves() {
        final SetupJob job = job(100, false);
        assertEquals("Preparing to install", job.phase());
        for (int i = 0; i < 50; i++) {
            job.tick();
        }
        assertEquals("Copying files", job.phase());
        for (int i = 0; i < 40; i++) {
            job.tick();
        }
        assertEquals("Registering Virtual Studio", job.phase());
        for (int i = 0; i < 10; i++) {
            job.tick();
        }
        assertEquals("Finishing", job.phase());
    }

    @Test
    void phase_saysRemovingWhenRemoving() {
        final SetupJob job = job(10, true);
        assertEquals("Removing files", job.phase());
        for (int i = 0; i < 6; i++) {
            job.tick();
        }
        assertEquals("Cleaning up", job.phase());
    }

    @Test
    void constructor_keepsTheCountWithinTheTotal() {
        final SetupJob job = new SetupJob("jsc:x", "X", "Nobody", 1, "CD", false, 10, 50);
        assertEquals(10, job.ticksLeft());
        final SetupJob none = new SetupJob("jsc:x", "X", "Nobody", 1, "CD", false, 0);
        assertTrue(none.ticksTotal() >= 1, "a job of no ticks still has a whole to be a share of");
    }
}
