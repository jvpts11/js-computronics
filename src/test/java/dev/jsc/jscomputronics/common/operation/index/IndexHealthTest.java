/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.operation.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IndexHealthTest {

    private IndexHealth health;

    @BeforeEach
    void setUp() {
        health = new IndexHealth();
    }

    @Test
    void state_startsClean() {
        assertEquals(IndexHealth.State.OK, health.state());
        assertTrue(health.isClean());
        assertEquals("", health.recommendedAction());
    }

    @Test
    void markStale_asksForAReindexAndNamesTheTypes() {
        health.markStale(List.of("minecraft:cobblestone", "minecraft:iron_ingot"), "srv-01");

        assertEquals(IndexHealth.State.STALE, health.state());
        assertEquals("REINDEX", health.recommendedAction());
        assertTrue(health.affectedTypes().contains("minecraft:cobblestone"));
        assertEquals(2, health.entries().size());
        assertEquals("srv-01", health.entries().get(0).source());
    }

    @Test
    void markGhosts_outranksStaleAndAsksForAVacuum() {
        health.markStale(List.of("minecraft:cobblestone"), "srv-01");
        health.markGhosts(List.of("minecraft:redstone"), "srv-02");

        assertEquals(IndexHealth.State.FRAGMENTED, health.state());
        assertEquals("VACUUM", health.recommendedAction());
        // Ghosts lead the list: they are the heavier problem.
        assertEquals("minecraft:redstone", health.entries().get(0).type());
    }

    @Test
    void entries_doNotRepeatATypeFlaggedBothWays() {
        health.markStale(List.of("minecraft:redstone"), "srv-01");
        health.markGhosts(List.of("minecraft:redstone"), "srv-02");

        assertEquals(1, health.entries().size());
        assertEquals(IndexHealth.State.FRAGMENTED, health.entries().get(0).severity());
    }

    @Test
    void onReindex_clearsUnconfirmedEntriesButNotGhosts() {
        health.markStale(List.of("minecraft:cobblestone"), "srv-01");
        health.markGhosts(List.of("minecraft:redstone"), "srv-02");

        health.onReindex();

        assertEquals(IndexHealth.State.FRAGMENTED, health.state());
        assertFalse(health.affectedTypes().contains("minecraft:cobblestone"));
    }

    @Test
    void onVacuum_clearsGhostsAndTheDoubtTheyCarried() {
        health.markStale(List.of("minecraft:redstone"), "srv-01");
        health.markGhosts(List.of("minecraft:redstone"), "srv-02");

        health.onVacuum();

        assertTrue(health.isClean());
        assertEquals(IndexHealth.State.OK, health.state());
    }

    @Test
    void onVacuum_leavesUnrelatedStaleEntriesAlone() {
        health.markStale(List.of("minecraft:cobblestone"), "srv-01");
        health.markGhosts(List.of("minecraft:redstone"), "srv-02");

        health.onVacuum();

        assertEquals(IndexHealth.State.STALE, health.state());
        assertTrue(health.affectedTypes().contains("minecraft:cobblestone"));
    }

    @Test
    void onFullRebuild_settlesEverything() {
        health.markStale(List.of("minecraft:cobblestone"), "srv-01");
        health.markGhosts(List.of("minecraft:redstone"), "srv-02");

        health.onFullRebuild();

        assertTrue(health.isClean());
    }
}
