/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.peripheral;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeripheralLinkValidatorTest {

    // Arbitrary deterministic positions.
    private static final long OWNER_POS = 1_000_000L;
    private static final long ENDPOINT_POS = 2_000_000L;
    private static final long C1 = 10L;
    private static final long C2 = 20L;
    private static final long C3 = 30L;

    @Test
    void ownerAdjacentToEndpoint_zeroCablePathSucceeds() {
        TestOwner owner = new TestOwner(PeripheralCableType.COMPUTING, 8);
        TestEndpoint endpoint = new TestEndpoint(PeripheralCableType.COMPUTING);
        Map<Long, List<Long>> adj = new HashMap<>();
        adj.put(OWNER_POS, List.of(ENDPOINT_POS));
        adj.put(ENDPOINT_POS, List.of(OWNER_POS));

        PeripheralLinkValidator validator = build(adj, owner, endpoint, Map.of());
        LinkResult result = validator.tryEstablishLink(OWNER_POS, ENDPOINT_POS);

        LinkResult.Established e = assertInstanceOf(LinkResult.Established.class, result);
        assertEquals(0, e.pathLength());
        assertTrue(owner.linkedEndpoints().contains(ENDPOINT_POS));
        assertEquals(Optional.of(OWNER_POS), endpoint.linkedOwner());
    }

    @Test
    void singleCablePath_succeedsWithLength1() {
        TestOwner owner = new TestOwner(PeripheralCableType.COMPUTING, 8);
        TestEndpoint endpoint = new TestEndpoint(PeripheralCableType.COMPUTING);
        Map<Long, List<Long>> adj = new HashMap<>();
        adj.put(OWNER_POS, List.of(C1));
        adj.put(C1, List.of(OWNER_POS, ENDPOINT_POS));
        adj.put(ENDPOINT_POS, List.of(C1));

        Map<Long, PeripheralCableType> cables = Map.of(
                C1, PeripheralCableType.COMPUTING);

        PeripheralLinkValidator validator = build(adj, owner, endpoint, cables);
        LinkResult result = validator.tryEstablishLink(OWNER_POS, ENDPOINT_POS);

        LinkResult.Established e = assertInstanceOf(LinkResult.Established.class, result);
        assertEquals(1, e.pathLength());
    }

    @Test
    void pathWithinMaxLength_succeeds() {
        // 3-cable path for COMPUTING (max 16) — well within budget.
        TestOwner owner = new TestOwner(PeripheralCableType.COMPUTING, 8);
        TestEndpoint endpoint = new TestEndpoint(PeripheralCableType.COMPUTING);

        Map<Long, List<Long>> adj = new HashMap<>();
        adj.put(OWNER_POS, List.of(C1));
        adj.put(C1, List.of(OWNER_POS, C2));
        adj.put(C2, List.of(C1, C3));
        adj.put(C3, List.of(C2, ENDPOINT_POS));
        adj.put(ENDPOINT_POS, List.of(C3));

        Map<Long, PeripheralCableType> cables = Map.of(
                C1, PeripheralCableType.COMPUTING,
                C2, PeripheralCableType.COMPUTING,
                C3, PeripheralCableType.COMPUTING);

        PeripheralLinkValidator validator = build(adj, owner, endpoint, cables);
        LinkResult result = validator.tryEstablishLink(OWNER_POS, ENDPOINT_POS);

        LinkResult.Established e = assertInstanceOf(LinkResult.Established.class, result);
        assertEquals(3, e.pathLength());
    }

    @Test
    void pathExceedsMaxLength_returnsTooLong() {
        // INDUSTRIAL_CONTROL has max=8. We build a 10-hop path.
        TestOwner owner = new TestOwner(PeripheralCableType.INDUSTRIAL_CONTROL, 5);
        TestEndpoint endpoint = new TestEndpoint(PeripheralCableType.INDUSTRIAL_CONTROL);

        Map<Long, List<Long>> adj = new HashMap<>();
        Map<Long, PeripheralCableType> cables = new HashMap<>();
        long prev = OWNER_POS;
        adj.put(OWNER_POS, new ArrayList<>());
        for (int i = 1; i <= 10; i++) {
            long cable = 1000L + i;
            adj.computeIfAbsent(prev, k -> new ArrayList<>()).add(cable);
            adj.computeIfAbsent(cable, k -> new ArrayList<>()).add(prev);
            cables.put(cable, PeripheralCableType.INDUSTRIAL_CONTROL);
            prev = cable;
        }
        adj.computeIfAbsent(prev, k -> new ArrayList<>()).add(ENDPOINT_POS);
        adj.put(ENDPOINT_POS, List.of(prev));

        PeripheralLinkValidator validator = build(adj, owner, endpoint, cables);
        LinkResult result = validator.tryEstablishLink(OWNER_POS, ENDPOINT_POS);

        LinkResult.ExceedsMaxLength too = assertInstanceOf(
                LinkResult.ExceedsMaxLength.class, result);
        assertEquals(10, too.pathLength());
        assertEquals(8, too.maxAllowed());
    }

    @Test
    void mismatchedCableTypes_returnsCableTypeMismatch() {
        // Owner declares COMPUTING, endpoint declares TELEMETRY.
        TestOwner owner = new TestOwner(PeripheralCableType.COMPUTING, 8);
        TestEndpoint endpoint = new TestEndpoint(PeripheralCableType.TELEMETRY);

        Map<Long, List<Long>> adj = Map.of(
                OWNER_POS, List.of(ENDPOINT_POS),
                ENDPOINT_POS, List.of(OWNER_POS));

        PeripheralLinkValidator validator = build(adj, owner, endpoint, Map.of());
        LinkResult result = validator.tryEstablishLink(OWNER_POS, ENDPOINT_POS);

        LinkResult.CableTypeMismatch m = assertInstanceOf(
                LinkResult.CableTypeMismatch.class, result);
        assertEquals(PeripheralCableType.COMPUTING, m.expected());
        assertEquals(PeripheralCableType.TELEMETRY, m.actual());
    }

    @Test
    void bfs_ignoresCablesOfWrongType() {
        // Path is OWNER -- C1(TELEMETRY) -- ENDPOINT, but owner needs COMPUTING.
        // Path should fail because BFS won't traverse the TELEMETRY cable.
        TestOwner owner = new TestOwner(PeripheralCableType.COMPUTING, 8);
        TestEndpoint endpoint = new TestEndpoint(PeripheralCableType.COMPUTING);

        Map<Long, List<Long>> adj = Map.of(
                OWNER_POS, List.of(C1),
                C1, List.of(OWNER_POS, ENDPOINT_POS),
                ENDPOINT_POS, List.of(C1));
        Map<Long, PeripheralCableType> cables = Map.of(
                C1, PeripheralCableType.TELEMETRY);

        PeripheralLinkValidator validator = build(adj, owner, endpoint, cables);
        LinkResult result = validator.tryEstablishLink(OWNER_POS, ENDPOINT_POS);

        assertInstanceOf(LinkResult.NoPathFound.class, result);
    }

    @Test
    void endpointAlreadyLinkedToDifferentOwner_returnsAlreadyLinked() {
        TestOwner owner = new TestOwner(PeripheralCableType.COMPUTING, 8);
        TestEndpoint endpoint = new TestEndpoint(PeripheralCableType.COMPUTING);
        endpoint.onOwnerLinked(99_999_999L); // pre-linked to a fake owner

        Map<Long, List<Long>> adj = Map.of(
                OWNER_POS, List.of(ENDPOINT_POS),
                ENDPOINT_POS, List.of(OWNER_POS));

        PeripheralLinkValidator validator = build(adj, owner, endpoint, Map.of());
        LinkResult result = validator.tryEstablishLink(OWNER_POS, ENDPOINT_POS);

        LinkResult.AlreadyLinked al = assertInstanceOf(
                LinkResult.AlreadyLinked.class, result);
        assertEquals(99_999_999L, al.existingOwnerPos());
    }

    @Test
    void ownerAtMaxEndpoints_returnsOwnerAtCapacity() {
        TestOwner owner = new TestOwner(PeripheralCableType.COMPUTING, 2);
        owner.onEndpointLinked(50L);
        owner.onEndpointLinked(60L);
        TestEndpoint endpoint = new TestEndpoint(PeripheralCableType.COMPUTING);

        Map<Long, List<Long>> adj = Map.of(
                OWNER_POS, List.of(ENDPOINT_POS),
                ENDPOINT_POS, List.of(OWNER_POS));

        PeripheralLinkValidator validator = build(adj, owner, endpoint, Map.of());
        LinkResult result = validator.tryEstablishLink(OWNER_POS, ENDPOINT_POS);

        LinkResult.OwnerAtCapacity cap = assertInstanceOf(
                LinkResult.OwnerAtCapacity.class, result);
        assertEquals(2, cap.currentCount());
        assertEquals(2, cap.maxAllowed());
    }

    @Test
    void reLinkSameEndpoint_atCapacity_succeeds() {
        // If endpoint is already linked to this owner, link is idempotent
        // and should succeed even when capacity is full.
        TestOwner owner = new TestOwner(PeripheralCableType.COMPUTING, 2);
        owner.onEndpointLinked(ENDPOINT_POS); // pre-linked
        owner.onEndpointLinked(60L);          // at capacity now
        TestEndpoint endpoint = new TestEndpoint(PeripheralCableType.COMPUTING);
        endpoint.onOwnerLinked(OWNER_POS);    // matching the existing link

        Map<Long, List<Long>> adj = Map.of(
                OWNER_POS, List.of(ENDPOINT_POS),
                ENDPOINT_POS, List.of(OWNER_POS));

        PeripheralLinkValidator validator = build(adj, owner, endpoint, Map.of());
        LinkResult result = validator.tryEstablishLink(OWNER_POS, ENDPOINT_POS);

        assertInstanceOf(LinkResult.Established.class, result);
    }

    @Test
    void differentCableTypes_coexistInSameWorld() {
        // Two parallel paths exist from OWNER to ENDPOINT:
        TestOwner owner = new TestOwner(PeripheralCableType.COMPUTING, 8);
        TestEndpoint endpoint = new TestEndpoint(PeripheralCableType.COMPUTING);

        Map<Long, List<Long>> adj = Map.of(
                OWNER_POS, List.of(C1, C2),
                C1, List.of(OWNER_POS, ENDPOINT_POS),
                C2, List.of(OWNER_POS, ENDPOINT_POS),
                ENDPOINT_POS, List.of(C1, C2));
        Map<Long, PeripheralCableType> cables = Map.of(
                C1, PeripheralCableType.COMPUTING,
                C2, PeripheralCableType.TELEMETRY);

        PeripheralLinkValidator validator = build(adj, owner, endpoint, cables);
        LinkResult result = validator.tryEstablishLink(OWNER_POS, ENDPOINT_POS);

        LinkResult.Established e = assertInstanceOf(LinkResult.Established.class, result);
        assertEquals(1, e.pathLength());
    }

    @Test
    void isLinkStillValid_returnsTrueWhenPathExists() {
        Map<Long, List<Long>> adj = Map.of(
                OWNER_POS, List.of(C1),
                C1, List.of(OWNER_POS, ENDPOINT_POS),
                ENDPOINT_POS, List.of(C1));
        Map<Long, PeripheralCableType> cables = Map.of(
                C1, PeripheralCableType.COMPUTING);

        PeripheralLinkValidator validator = build(
                adj,
                new TestOwner(PeripheralCableType.COMPUTING, 8),
                new TestEndpoint(PeripheralCableType.COMPUTING),
                cables);

        assertTrue(validator.isLinkStillValid(
                OWNER_POS, ENDPOINT_POS, PeripheralCableType.COMPUTING));
    }

    @Test
    void isLinkStillValid_returnsFalseWhenPathBroken() {
        // Adjacency exists but no cable connects them.
        Map<Long, List<Long>> adj = Map.of(
                OWNER_POS, List.of(C1),
                C1, List.of(OWNER_POS),
                ENDPOINT_POS, List.of());

        PeripheralLinkValidator validator = build(
                adj,
                new TestOwner(PeripheralCableType.COMPUTING, 8),
                new TestEndpoint(PeripheralCableType.COMPUTING),
                Map.of(C1, PeripheralCableType.COMPUTING));

        assertFalse(validator.isLinkStillValid(
                OWNER_POS, ENDPOINT_POS, PeripheralCableType.COMPUTING));
    }

    @Test
    void telemetryCable_supportsLongPaths() {
        // Build a 50-hop path of TELEMETRY cables — well under max 256.
        TestOwner owner = new TestOwner(PeripheralCableType.TELEMETRY, 6);
        TestEndpoint endpoint = new TestEndpoint(PeripheralCableType.TELEMETRY);

        Map<Long, List<Long>> adj = new HashMap<>();
        Map<Long, PeripheralCableType> cables = new HashMap<>();
        adj.put(OWNER_POS, new ArrayList<>());
        long prev = OWNER_POS;
        for (int i = 1; i <= 50; i++) {
            long cable = 1000L + i;
            adj.computeIfAbsent(prev, k -> new ArrayList<>()).add(cable);
            adj.computeIfAbsent(cable, k -> new ArrayList<>()).add(prev);
            cables.put(cable, PeripheralCableType.TELEMETRY);
            prev = cable;
        }
        adj.computeIfAbsent(prev, k -> new ArrayList<>()).add(ENDPOINT_POS);
        adj.put(ENDPOINT_POS, List.of(prev));

        PeripheralLinkValidator validator = build(adj, owner, endpoint, cables);
        LinkResult result = validator.tryEstablishLink(OWNER_POS, ENDPOINT_POS);

        LinkResult.Established e = assertInstanceOf(LinkResult.Established.class, result);
        assertEquals(50, e.pathLength());
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private static PeripheralLinkValidator build(
            Map<Long, List<Long>> adjacency,
            PeripheralOwner owner,
            PeripheralEndpoint endpoint,
            Map<Long, PeripheralCableType> cables) {
        return new PeripheralLinkValidator(
                pos -> Optional.ofNullable(cables.get(pos)),
                pos -> pos == OWNER_POS ? Optional.of(owner) : Optional.empty(),
                pos -> pos == ENDPOINT_POS ? Optional.of(endpoint) : Optional.empty(),
                PeripheralLinkValidator.adjacencyFrom(adjacency));
    }

    /**
     * Mutable test-only PeripheralOwner.
     */
    private static final class TestOwner implements PeripheralOwner {
        private final PeripheralCableType cableType;
        private final int maxEndpoints;
        private final List<Long> linked = new ArrayList<>();

        TestOwner(PeripheralCableType cableType, int maxEndpoints) {
            this.cableType = cableType;
            this.maxEndpoints = maxEndpoints;
        }

        @Override public PeripheralCableType cableType() { return cableType; }
        @Override public List<Long> linkedEndpoints() { return List.copyOf(linked); }
        @Override public int maxEndpoints() { return maxEndpoints; }
        @Override public void onEndpointLinked(long endpointPos) {
            if (!linked.contains(endpointPos)) {
                linked.add(endpointPos);
            }
        }
        @Override public void onEndpointUnlinked(long endpointPos) {
            linked.removeIf(p -> p == endpointPos);
        }
    }

    /**
     * Mutable test-only PeripheralEndpoint.
     */
    private static final class TestEndpoint implements PeripheralEndpoint {
        private final PeripheralCableType cableType;
        private Optional<Long> ownerPos = Optional.empty();

        TestEndpoint(PeripheralCableType cableType) {
            this.cableType = cableType;
        }

        @Override public PeripheralCableType cableType() { return cableType; }
        @Override public Optional<Long> linkedOwner() { return ownerPos; }
        @Override public void onOwnerLinked(long pos) { this.ownerPos = Optional.of(pos); }
        @Override public void onOwnerUnlinked() { this.ownerPos = Optional.empty(); }
    }
}
