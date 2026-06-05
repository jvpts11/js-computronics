/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.network;

import dev.jsc.jscomputronics.common.network.ConnectivityIndex.PlacementResult;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectivityIndexTest {

    private ConnectivityIndex index;

    private static long pos(int x, int y, int z) {
        return ((long) x & 0xFFFFFFFL) << 38
                | ((long) y & 0xFFFL)
                | ((long) z & 0xFFFFFFFL) << 12;
    }

    @BeforeEach
    void setUp() {
        index = new ConnectivityIndex();
    }

    @Test
    void freshIndex_isEmpty() {
        assertEquals(0, index.size());
        assertEquals(0, index.componentCount());
        assertFalse(index.contains(pos(0, 0, 0)));
        assertFalse(index.networkOf(pos(0, 0, 0)).isPresent());
    }

    @Test
    void placeIsolatedCable_returnsIsolated() {
        var result = index.onCablePlaced(pos(0, 0, 0), Set.of());
        assertInstanceOf(PlacementResult.Isolated.class, result);
        assertEquals(1, index.size());
        assertEquals(1, index.componentCount());
    }

    @Test
    void placeIsolated_thenQuery_hasNoUuid() {
        index.onCablePlaced(pos(0, 0, 0), Set.of());
        assertTrue(index.contains(pos(0, 0, 0)));
        assertFalse(index.networkOf(pos(0, 0, 0)).isPresent());
    }

    @Test
    void placeAlreadyRegisteredPosition_throws() {
        index.onCablePlaced(pos(0, 0, 0), Set.of());
        assertThrows(IllegalStateException.class,
                () -> index.onCablePlaced(pos(0, 0, 0), Set.of()));
    }

    @Test
    void placeWithUnknownNeighbors_treatsAsIsolated() {
        // Neighbor positions are listed but none of them are in the index.
        var result = index.onCablePlaced(
                pos(0, 0, 0),
                Set.of(pos(1, 0, 0), pos(-1, 0, 0)));
        assertInstanceOf(PlacementResult.Isolated.class, result);
        assertEquals(1, index.size());
        assertEquals(1, index.componentCount());
    }

    @Test
    void placeAdjacentToExisting_mergesWithoutUuid() {
        // Place A first (isolated), then B adjacent to A — neither has UUID.
        index.onCablePlaced(pos(0, 0, 0), Set.of());
        var result = index.onCablePlaced(pos(1, 0, 0), Set.of(pos(0, 0, 0)));
        assertInstanceOf(PlacementResult.MergedWithoutUuid.class, result);
        assertEquals(2, index.size());
        assertEquals(1, index.componentCount());
        assertTrue(index.inSameNetwork(pos(0, 0, 0), pos(1, 0, 0)));
        assertFalse(index.networkOf(pos(0, 0, 0)).isPresent());
    }

    @Test
    void placeAdjacentToUuidComponent_inheritsUuid() {
        var uuid = NetworkUuid.random();
        // Place A and assign UUID to its component.
        index.onCablePlaced(pos(0, 0, 0), Set.of());
        index.assignUuid(pos(0, 0, 0), uuid);
        // Place B adjacent to A — B inherits A's UUID.
        var result = index.onCablePlaced(pos(1, 0, 0), Set.of(pos(0, 0, 0)));
        var inherited = assertInstanceOf(PlacementResult.Inherited.class, result);
        assertEquals(uuid, inherited.uuid());
        // Both positions report the inherited UUID.
        assertEquals(uuid, index.networkOf(pos(0, 0, 0)).orElseThrow());
        assertEquals(uuid, index.networkOf(pos(1, 0, 0)).orElseThrow());
    }

    @Test
    void placeBetweenSameUuidComponents_isInheritedNotConflict() {
        // Two separate components with the SAME UUID (rare but possible —
        var uuid = NetworkUuid.random();
        index.onCablePlaced(pos(0, 0, 0), Set.of());
        index.assignUuid(pos(0, 0, 0), uuid);
        index.onCablePlaced(pos(10, 0, 0), Set.of());
        index.assignUuid(pos(10, 0, 0), uuid);
        // Bridge cable touching both.
        var result = index.onCablePlaced(
                pos(5, 0, 0),
                Set.of(pos(0, 0, 0), pos(10, 0, 0)));
        var inherited = assertInstanceOf(PlacementResult.Inherited.class, result);
        assertEquals(uuid, inherited.uuid());
        assertEquals(1, index.componentCount());
    }

    @Test
    void placeBetweenDifferentUuidComponents_triggersConflict() {
        var uuidA = NetworkUuid.random();
        var uuidB = NetworkUuid.random();
        // Sanity.
        assertNotEquals(uuidA, uuidB);
        // Network A.
        index.onCablePlaced(pos(0, 0, 0), Set.of());
        index.assignUuid(pos(0, 0, 0), uuidA);
        // Network B.
        index.onCablePlaced(pos(10, 0, 0), Set.of());
        index.assignUuid(pos(10, 0, 0), uuidB);
        // Bridge cable connecting both.
        var result = index.onCablePlaced(
                pos(5, 0, 0),
                Set.of(pos(0, 0, 0), pos(10, 0, 0)));
        var conflict = assertInstanceOf(PlacementResult.Conflict.class, result);
        // The exact "first" UUID depends on iteration order of Set; what
        // matters is that BOTH UUIDs appear in the conflict report.
        Set<NetworkUuid> reported = Set.of(conflict.first(), conflict.second());
        assertTrue(reported.contains(uuidA));
        assertTrue(reported.contains(uuidB));
    }

    @Test
    void afterConflict_componentsAreUnifiedWithSurvivingUuid() {
        // After NETWORK_CONFLICT, the merged component still has ONE
        var uuidA = NetworkUuid.random();
        var uuidB = NetworkUuid.random();
        index.onCablePlaced(pos(0, 0, 0), Set.of());
        index.assignUuid(pos(0, 0, 0), uuidA);
        index.onCablePlaced(pos(10, 0, 0), Set.of());
        index.assignUuid(pos(10, 0, 0), uuidB);
        index.onCablePlaced(pos(5, 0, 0), Set.of(pos(0, 0, 0), pos(10, 0, 0)));
        // After bridging: 1 component, 1 surviving UUID.
        assertEquals(1, index.componentCount());
        var survivor = index.networkOf(pos(5, 0, 0)).orElseThrow();
        assertTrue(survivor.equals(uuidA) || survivor.equals(uuidB));
        // All three positions report the same UUID.
        assertEquals(survivor, index.networkOf(pos(0, 0, 0)).orElseThrow());
        assertEquals(survivor, index.networkOf(pos(10, 0, 0)).orElseThrow());
    }

    @Test
    void scenario_buildSmallNetworkOneCableAtATime() {
        // Place a cable line (5 cables in a row). Each new cable connects
        // to the previous one. After all 5: one component, no UUID yet.
        index.onCablePlaced(pos(0, 0, 0), Set.of());
        index.onCablePlaced(pos(1, 0, 0), Set.of(pos(0, 0, 0)));
        index.onCablePlaced(pos(2, 0, 0), Set.of(pos(1, 0, 0)));
        index.onCablePlaced(pos(3, 0, 0), Set.of(pos(2, 0, 0)));
        index.onCablePlaced(pos(4, 0, 0), Set.of(pos(3, 0, 0)));
        assertEquals(5, index.size());
        assertEquals(1, index.componentCount());

        // Now a Mainframe attaches to the line.
        var uuid = NetworkUuid.random();
        index.assignUuid(pos(0, 0, 0), uuid);
        // Every cable in the line reports the assigned UUID.
        for (int x = 0; x < 5; x++) {
            assertEquals(uuid, index.networkOf(pos(x, 0, 0)).orElseThrow(),
                    "Cable at x=" + x + " should report the network's UUID");
        }
    }

    @Test
    void onCableRemoved_throwsInPhase0() {
        index.onCablePlaced(pos(0, 0, 0), Set.of());
        assertThrows(UnsupportedOperationException.class,
                () -> index.onCableRemoved(pos(0, 0, 0)));
    }

    @Test
    void assignUuid_onUnknownPosition_throws() {
        assertThrows(IllegalStateException.class,
                () -> index.assignUuid(pos(0, 0, 0), NetworkUuid.random()));
    }

    @Test
    void assignUuid_replacesExistingUuid() {
        // Already-assigned UUID can be replaced (used by Mainframe takeover).
        var first = NetworkUuid.random();
        var second = NetworkUuid.random();
        index.onCablePlaced(pos(0, 0, 0), Set.of());
        index.assignUuid(pos(0, 0, 0), first);
        assertEquals(first, index.networkOf(pos(0, 0, 0)).orElseThrow());
        index.assignUuid(pos(0, 0, 0), second);
        assertEquals(second, index.networkOf(pos(0, 0, 0)).orElseThrow());
    }

    @Test
    void clear_resetsAllState() {
        index.onCablePlaced(pos(0, 0, 0), Set.of());
        index.onCablePlaced(pos(1, 0, 0), Set.of(pos(0, 0, 0)));
        index.assignUuid(pos(0, 0, 0), NetworkUuid.random());
        index.clear();
        assertEquals(0, index.size());
        assertEquals(0, index.componentCount());
        assertFalse(index.contains(pos(0, 0, 0)));
        assertFalse(index.networkOf(pos(0, 0, 0)).isPresent());
    }

    @Test
    void clear_thenReuse_reportsConsistentCounts() {
        // After clear, the index must behave like a fresh instance: the
        // backing DSU component count must also reset, not carry stale data.
        index.onCablePlaced(pos(0, 0, 0), Set.of());
        index.onCablePlaced(pos(1, 0, 0), Set.of(pos(0, 0, 0)));
        index.clear();
        index.onCablePlaced(pos(5, 0, 0), Set.of());
        index.onCablePlaced(pos(6, 0, 0), Set.of(pos(5, 0, 0)));
        assertEquals(2, index.size());
        assertEquals(1, index.componentCount());
    }
}
