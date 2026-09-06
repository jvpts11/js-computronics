/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.network;

import dev.jstech.core.uuid.NetworkUuid;
import dev.jstech.core.uuid.NodeUuid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkSystemTest {

    private NetworkSystem system;
    private NetworkUuid net;

    @BeforeEach
    void setUp() {
        system = new NetworkSystem();
        net = NetworkUuid.random();
    }

    @Test
    void clear_resetsEveryRegistryIncludingCraftingAndSupercomputers() {
        system.registerCraftingComputer(
                new NetworkSystem.CraftingComputerNode(NodeUuid.random(), net, 1L, 0L));
        system.registerSupercomputer(
                new NetworkSystem.SupercomputerNode(NodeUuid.random(), net, 4L, 1L));
        assertFalse(system.craftingComputersOf(net).isEmpty());
        assertFalse(system.supercomputersOf(net).isEmpty());

        system.clear();

        assertTrue(system.craftingComputersOf(net).isEmpty(),
                "clear() must reset the crafting-computer registry");
        assertTrue(system.supercomputersOf(net).isEmpty(),
                "clear() must reset the supercomputer registry");
    }

    private MainframeNode standaloneMainframe(long capacity) {
        return new MainframeNode(
                NodeUuid.random(),
                net,
                capacity,
                FailoverRole.NONE,
                Optional.empty(),
                0L
        );
    }

    private MainframeNode mainframe(FailoverRole role, NodeUuid partner, long capacity) {
        return new MainframeNode(
                NodeUuid.random(),
                net,
                capacity,
                role,
                Optional.ofNullable(partner),
                0L
        );
    }

    private SubframeNode subframe(long ownCapacity, NodeUuid mainframeUuid) {
        return new SubframeNode(
                NodeUuid.random(),
                net,
                ownCapacity,
                Optional.ofNullable(mainframeUuid)
        );
    }

    @Test
    void freshSystem_hasNoMainframeOrSubframes() {
        assertFalse(system.mainframeOf(net).isPresent());
        assertEquals(0, system.subframesOf(net).size());
        assertEquals(0L, system.totalOrchestrationCapacityOf(net));
    }

    @Test
    void registerMainframe_isRetrievable() {
        var mf = standaloneMainframe(38_400L);
        system.registerMainframe(mf);
        assertEquals(mf, system.mainframeOf(net).orElseThrow());
    }

    @Test
    void registerMainframe_replacesExisting() {
        var first = standaloneMainframe(38_400L);
        var second = standaloneMainframe(50_000L);
        system.registerMainframe(first);
        system.registerMainframe(second);
        assertEquals(second, system.mainframeOf(net).orElseThrow());
    }

    @Test
    void registerMainframe_rejectsNull() {
        assertThrows(NullPointerException.class,
                () -> system.registerMainframe(null));
    }

    @Test
    void registerSubframe_appendsToList() {
        var mfUuid = NodeUuid.random();
        system.registerSubframe(subframe(38_400L, mfUuid));
        system.registerSubframe(subframe(38_400L, mfUuid));
        system.registerSubframe(subframe(38_400L, mfUuid));
        assertEquals(3, system.subframesOf(net).size());
    }

    @Test
    void subframesOf_returnsImmutableCopy() {
        system.registerSubframe(subframe(1000L, NodeUuid.random()));
        var snapshot = system.subframesOf(net);
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.add(subframe(2000L, NodeUuid.random())));
    }

    @Test
    void totalCapacity_mainframeAlone() {
        system.registerMainframe(standaloneMainframe(38_400L));
        assertEquals(38_400L, system.totalOrchestrationCapacityOf(net));
    }

    @Test
    void totalCapacity_canonicalExample() {
        // Worked example: a Threadkiller mainframe (38,400) plus 3 identical
        // subframes. Expected: 38,400 + (3 × 38,400 × 0.6) = 38,400 + 69,120 = 107,520.
        var mf = standaloneMainframe(38_400L);
        system.registerMainframe(mf);
        var mfUuid = mf.nodeUuid();
        system.registerSubframe(subframe(38_400L, mfUuid));
        system.registerSubframe(subframe(38_400L, mfUuid));
        system.registerSubframe(subframe(38_400L, mfUuid));
        assertEquals(107_520L, system.totalOrchestrationCapacityOf(net));
    }

    @Test
    void totalCapacity_idleSubframesContributeZero() {
        // A subframe with no orchestrating Mainframe (empty Optional)
        system.registerMainframe(standaloneMainframe(38_400L));
        system.registerSubframe(subframe(38_400L, null)); // idle
        assertEquals(38_400L, system.totalOrchestrationCapacityOf(net));
    }

    @Test
    void totalCapacity_passiveMainframeContributesZero() {
        // A PASSIVE Mainframe is pure overhead (50% standby, but NOT
        // a contribution to usable capacity).
        var partnerUuid = NodeUuid.random();
        var passive = mainframe(FailoverRole.PASSIVE, partnerUuid, 38_400L);
        system.registerMainframe(passive);
        assertEquals(0L, system.totalOrchestrationCapacityOf(net));
    }

    @Test
    void nodeByPosition_throwsInPhase0() {
        assertThrows(UnsupportedOperationException.class,
                () -> system.nodeByPosition(0L));
    }

    @Test
    void failoverPartnerOf_throwsInPhase0() {
        assertThrows(UnsupportedOperationException.class,
                () -> system.failoverPartnerOf(net));
    }

    @Test
    void connectivityFacade_delegatesCorrectly() {
        // Smoke test: delegations work end-to-end.
        long pos1 = 100L;
        long pos2 = 200L;
        system.connectivity().onCablePlaced(pos1, java.util.Set.of());
        system.connectivity().onCablePlaced(pos2, java.util.Set.of(pos1));
        assertTrue(system.inSameNetwork(pos1, pos2));
        assertFalse(system.networkOf(pos1).isPresent()); // no UUID assigned
    }

    @Test
    void unregisterMainframe_removesOwnSnapshot() {
        var mf = standaloneMainframe(38_400L);
        system.registerMainframe(mf);
        system.unregisterMainframe(net, mf.nodeUuid());
        assertFalse(system.mainframeOf(net).isPresent());
    }

    @Test
    void unregisterMainframe_ignoresForeignNode() {
        // If another mainframe has taken the network over, a stale unregister
        // from the old node must not evict the current owner.
        var owner = standaloneMainframe(38_400L);
        system.registerMainframe(owner);
        system.unregisterMainframe(net, NodeUuid.random()); // some other node
        assertEquals(owner, system.mainframeOf(net).orElseThrow());
    }

    @Test
    void clear_resetsEverything() {
        system.registerMainframe(standaloneMainframe(38_400L));
        system.registerSubframe(subframe(1000L, NodeUuid.random()));
        system.connectivity().onCablePlaced(0L, java.util.Set.of());
        system.clear();
        assertFalse(system.mainframeOf(net).isPresent());
        assertEquals(0, system.subframesOf(net).size());
        assertEquals(0, system.connectivity().size());
    }
}
