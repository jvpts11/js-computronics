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
    void removeUnregisteredCable_throws() {
        assertThrows(IllegalStateException.class,
                () -> index.onCableRemoved(pos(0, 0, 0)));
    }

    @Test
    void removeIsolatedCable_componentDisappears() {
        index.onCablePlaced(pos(0, 0, 0), Set.of());
        var result = index.onCableRemoved(pos(0, 0, 0));
        assertEquals(0, index.size());
        assertEquals(0, index.componentCount());
        assertFalse(index.contains(pos(0, 0, 0)));
        assertEquals(0, result.resultingComponents());
    }

    @Test
    void removeLeafCable_keepsRemainderConnected() {
        // Line A-B-C; removing the leaf C leaves A-B as one component.
        index.onCablePlaced(pos(0, 0, 0), Set.of());
        index.onCablePlaced(pos(1, 0, 0), Set.of(pos(0, 0, 0)));
        index.onCablePlaced(pos(2, 0, 0), Set.of(pos(1, 0, 0)));
        var result = index.onCableRemoved(pos(2, 0, 0));
        assertEquals(2, index.size());
        assertEquals(1, index.componentCount());
        assertTrue(index.inSameNetwork(pos(0, 0, 0), pos(1, 0, 0)));
        assertEquals(1, result.resultingComponents());
    }

    @Test
    void removeBridgeCable_splitsNetwork() {
        // Line A-B-C; removing the middle B splits into {A} and {C}.
        index.onCablePlaced(pos(0, 0, 0), Set.of());
        index.onCablePlaced(pos(1, 0, 0), Set.of(pos(0, 0, 0)));
        index.onCablePlaced(pos(2, 0, 0), Set.of(pos(1, 0, 0)));
        var result = index.onCableRemoved(pos(1, 0, 0));
        assertEquals(2, index.size());
        assertEquals(2, index.componentCount());
        assertFalse(index.inSameNetwork(pos(0, 0, 0), pos(2, 0, 0)));
        assertEquals(2, result.resultingComponents());
    }

    @Test
    void removeBridgeCable_severedFragmentsLoseUuid() {
        // Cutting bridge B from line A-B-C severs {A} from {C}. Neither fragment
        var uuid = NetworkUuid.random();
        index.onCablePlaced(pos(0, 0, 0), Set.of());
        index.onCablePlaced(pos(1, 0, 0), Set.of(pos(0, 0, 0)));
        index.onCablePlaced(pos(2, 0, 0), Set.of(pos(1, 0, 0)));
        index.assignUuid(pos(0, 0, 0), uuid);
        var result = index.onCableRemoved(pos(1, 0, 0));
        assertEquals(uuid, result.previousUuid().orElseThrow());
        assertFalse(index.networkOf(pos(0, 0, 0)).isPresent());
        assertFalse(index.networkOf(pos(2, 0, 0)).isPresent());
        assertFalse(index.inSameNetwork(pos(0, 0, 0), pos(2, 0, 0)));
    }

    @Test
    void removeLeafCable_survivingFragmentKeepsUuid() {
        // Removing a leaf does NOT sever the component, so the remainder keeps
        // its UUID — only a genuine split resets identity.
        var uuid = NetworkUuid.random();
        index.onCablePlaced(pos(0, 0, 0), Set.of());
        index.onCablePlaced(pos(1, 0, 0), Set.of(pos(0, 0, 0)));
        index.onCablePlaced(pos(2, 0, 0), Set.of(pos(1, 0, 0)));
        index.assignUuid(pos(0, 0, 0), uuid);
        index.onCableRemoved(pos(2, 0, 0)); // leaf C
        assertEquals(uuid, index.networkOf(pos(0, 0, 0)).orElseThrow());
        assertEquals(uuid, index.networkOf(pos(1, 0, 0)).orElseThrow());
    }

    @Test
    void componentPositions_returnsWholeSegmentOnly() {
        index.onCablePlaced(pos(0, 0, 0), Set.of());
        index.onCablePlaced(pos(1, 0, 0), Set.of(pos(0, 0, 0)));
        index.onCablePlaced(pos(2, 0, 0), Set.of(pos(1, 0, 0)));
        index.onCablePlaced(pos(10, 0, 0), Set.of()); // a separate segment
        var segment = index.componentPositions(pos(0, 0, 0));
        assertEquals(3, segment.size());
        assertTrue(segment.contains(pos(0, 0, 0)));
        assertTrue(segment.contains(pos(2, 0, 0)));
        assertFalse(segment.contains(pos(10, 0, 0)));
    }

    @Test
    void removeCable_leavesOtherNetworksIntact() {
        var uuidA = NetworkUuid.random();
        var uuidB = NetworkUuid.random();
        // Network A: a line of two.
        index.onCablePlaced(pos(0, 0, 0), Set.of());
        index.onCablePlaced(pos(1, 0, 0), Set.of(pos(0, 0, 0)));
        index.assignUuid(pos(0, 0, 0), uuidA);
        // Network B: a separate line of two.
        index.onCablePlaced(pos(10, 0, 0), Set.of());
        index.onCablePlaced(pos(11, 0, 0), Set.of(pos(10, 0, 0)));
        index.assignUuid(pos(10, 0, 0), uuidB);
        // Remove a cable from A; B must be untouched.
        index.onCableRemoved(pos(1, 0, 0));
        assertEquals(uuidB, index.networkOf(pos(10, 0, 0)).orElseThrow());
        assertEquals(uuidB, index.networkOf(pos(11, 0, 0)).orElseThrow());
        assertTrue(index.inSameNetwork(pos(10, 0, 0), pos(11, 0, 0)));
    }

    @Test
    void removeBridge_thenReplace_reunitesNetwork() {
        // Remove the bridge, then place it back: the network reunites.
        index.onCablePlaced(pos(0, 0, 0), Set.of());
        index.onCablePlaced(pos(1, 0, 0), Set.of(pos(0, 0, 0)));
        index.onCablePlaced(pos(2, 0, 0), Set.of(pos(1, 0, 0)));
        index.onCableRemoved(pos(1, 0, 0));
        assertEquals(2, index.componentCount());
        index.onCablePlaced(pos(1, 0, 0), Set.of(pos(0, 0, 0), pos(2, 0, 0)));
        assertEquals(1, index.componentCount());
        assertTrue(index.inSameNetwork(pos(0, 0, 0), pos(2, 0, 0)));
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

    @Test
    void bridge_unionsSeparateRuns_intoOneComponent() {
        // Two runs separated by a device (no direct cable-to-cable contact) — two components.
        index.onCablePlaced(pos(0, 0, 0), Set.of());
        index.onCablePlaced(pos(10, 0, 0), Set.of());
        assertEquals(2, index.componentCount());
        assertFalse(index.inSameNetwork(pos(0, 0, 0), pos(10, 0, 0)));
        index.bridge(Set.of(pos(0, 0, 0), pos(10, 0, 0)));
        assertEquals(1, index.componentCount());
        assertTrue(index.inSameNetwork(pos(0, 0, 0), pos(10, 0, 0)),
                "a device bridges the two runs it touches into one network");
    }

    @Test
    void bridge_preservesTheNetworkUuid() {
        index.onCablePlaced(pos(0, 0, 0), Set.of());
        index.onCablePlaced(pos(10, 0, 0), Set.of());
        var uuid = NetworkUuid.random();
        index.assignUuid(pos(0, 0, 0), uuid); // one run carries the network; the other is UUID-less
        index.bridge(Set.of(pos(0, 0, 0), pos(10, 0, 0)));
        assertEquals(uuid, index.networkOf(pos(0, 0, 0)).orElseThrow());
        assertEquals(uuid, index.networkOf(pos(10, 0, 0)).orElseThrow(),
                "the bridged run inherits the merged network's UUID");
    }

    @Test
    void bridge_singlePosition_isNoOp() {
        index.onCablePlaced(pos(0, 0, 0), Set.of());
        index.bridge(Set.of(pos(0, 0, 0)));
        assertEquals(1, index.componentCount());
    }

    @Test
    void bridge_ignoresUnregisteredPositions() {
        index.onCablePlaced(pos(0, 0, 0), Set.of());
        index.bridge(Set.of(pos(0, 0, 0), pos(99, 0, 0))); // the second is not registered
        assertEquals(1, index.componentCount());
        assertTrue(index.contains(pos(0, 0, 0)));
    }

    @Test
    void reachableFrom_blockingRouter_isolatesOneBranch() {
        // A router R at the centre with two cable branches; blocking R separates the branches —
        // this is how a Server Router computes one datacenter section per output face.
        index.onCablePlaced(pos(0, 0, 0), Set.of());                // router R
        index.onCablePlaced(pos(1, 0, 0), Set.of(pos(0, 0, 0)));    // branch A: A1
        index.onCablePlaced(pos(2, 0, 0), Set.of(pos(1, 0, 0)));    // A2
        index.onCablePlaced(pos(-1, 0, 0), Set.of(pos(0, 0, 0)));   // branch B: B1
        index.onCablePlaced(pos(-2, 0, 0), Set.of(pos(-1, 0, 0)));  // B2

        var branchA = index.reachableFrom(pos(1, 0, 0), Set.of(pos(0, 0, 0)));
        assertEquals(2, branchA.size());
        assertTrue(branchA.contains(pos(1, 0, 0)));
        assertTrue(branchA.contains(pos(2, 0, 0)));
        assertFalse(branchA.contains(pos(0, 0, 0)), "the blocked router is never entered");
        assertFalse(branchA.contains(pos(-1, 0, 0)), "the other branch is unreachable across the router");
    }

    @Test
    void reachableFrom_withoutBlock_spansWholeComponent() {
        index.onCablePlaced(pos(0, 0, 0), Set.of());
        index.onCablePlaced(pos(1, 0, 0), Set.of(pos(0, 0, 0)));
        index.onCablePlaced(pos(-1, 0, 0), Set.of(pos(0, 0, 0)));
        assertEquals(3, index.reachableFrom(pos(1, 0, 0), Set.of()).size());
    }

    @Test
    void reachableFrom_blockedStart_isEmpty() {
        index.onCablePlaced(pos(0, 0, 0), Set.of());
        assertTrue(index.reachableFrom(pos(0, 0, 0), Set.of(pos(0, 0, 0))).isEmpty());
    }

    @Test
    void reachableFrom_unregisteredStart_isEmpty() {
        assertTrue(index.reachableFrom(pos(7, 7, 7), Set.of()).isEmpty());
    }
}
