/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.network;

import dev.jstech.core.uuid.NetworkUuid;
import dev.jstech.core.uuid.NodeUuid;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubframeNodeTest {

    @Test
    void active_appliesContributionFactor() {
        // 38.400 × 0.6 = 23.040
        var sub = new SubframeNode(
                NodeUuid.random(),
                NetworkUuid.random(),
                38_400L,
                Optional.of(NodeUuid.random())
        );
        assertEquals(23_040L, sub.contributedCapacity());
    }

    @Test
    void idle_contributesZero() {
        var sub = new SubframeNode(
                NodeUuid.random(),
                NetworkUuid.random(),
                38_400L,
                Optional.empty()
        );
        assertEquals(0L, sub.contributedCapacity());
    }

    @Test
    void contributionFactor_isCanonical() {
        // The contribution factor is a fixed, canonical 0.6.
        assertEquals(0.6, SubframeNode.CONTRIBUTION_FACTOR);
    }

    @Test
    void contributedQueues_countTheGpusOnlyWhileOrchestrated() {
        final var active = new SubframeNode(NodeUuid.random(), NetworkUuid.random(), 1000L,
                Optional.of(NodeUuid.random()), 2);
        final var idle = new SubframeNode(NodeUuid.random(), NetworkUuid.random(), 1000L,
                Optional.empty(), 2);
        final var noGpu = new SubframeNode(NodeUuid.random(), NetworkUuid.random(), 1000L,
                Optional.of(NodeUuid.random()));
        assertEquals(2, active.contributedQueues());
        assertEquals(0, idle.contributedQueues());
        assertEquals(0, noGpu.contributedQueues());
        assertThrows(IllegalArgumentException.class, () -> new SubframeNode(NodeUuid.random(),
                NetworkUuid.random(), 1000L, Optional.empty(), -1));
    }

    @Test
    void contributedCapacity_followsTheBalanceFactor() {
        final var sub = new SubframeNode(NodeUuid.random(), NetworkUuid.random(), 1000L,
                Optional.of(NodeUuid.random()));
        try {
            dev.jstech.core.operation.OperationBalance.setSubframeEfficiencyFactor(0.25);
            assertEquals(250L, sub.contributedCapacity());
        } finally {
            dev.jstech.core.operation.OperationBalance.reset();
        }
        assertEquals(600L, sub.contributedCapacity());
    }

    @Test
    void rounding_handlesNonInteger() {
        // 100 × 0.6 = 60 (exact)
        var s100 = new SubframeNode(NodeUuid.random(), NetworkUuid.random(), 100L,
                Optional.of(NodeUuid.random()));
        var s101 = new SubframeNode(NodeUuid.random(), NetworkUuid.random(), 101L,
                Optional.of(NodeUuid.random()));
        var s103 = new SubframeNode(NodeUuid.random(), NetworkUuid.random(), 103L,
                Optional.of(NodeUuid.random()));
        assertEquals(60L, s100.contributedCapacity());
        assertEquals(61L, s101.contributedCapacity());
        assertEquals(62L, s103.contributedCapacity());
    }

    @Test
    void negativeCapacity_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SubframeNode(
                NodeUuid.random(),
                NetworkUuid.random(),
                -1L,
                Optional.empty()
        ));
    }

    @Test
    void category_isC() {
        var sub = new SubframeNode(
                NodeUuid.random(),
                NetworkUuid.random(),
                38_400L,
                Optional.of(NodeUuid.random())
        );
        assertEquals(NetworkCategory.C, sub.category());
    }
}
