/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.network;

import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.common.uuid.NodeUuid;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MainframeNodeTest {

    @Test
    void standalone_hasFullCapacity() {
        var mf = new MainframeNode(
                NodeUuid.random(),
                NetworkUuid.random(),
                38_400L,
                FailoverRole.NONE,
                Optional.empty(),
                0L
        );
        assertEquals(38_400L, mf.contributedCapacity());
    }

    @Test
    void active_hasFullCapacity() {
        var mf = new MainframeNode(
                NodeUuid.random(),
                NetworkUuid.random(),
                38_400L,
                FailoverRole.ACTIVE,
                Optional.of(NodeUuid.random()),
                0L
        );
        assertEquals(38_400L, mf.contributedCapacity());
    }

    @Test
    void passive_contributesZero() {
        var mf = new MainframeNode(
                NodeUuid.random(),
                NetworkUuid.random(),
                38_400L,
                FailoverRole.PASSIVE,
                Optional.of(NodeUuid.random()),
                0L
        );
        assertEquals(0L, mf.contributedCapacity());
    }

    @Test
    void pairedRole_requiresPartner() {
        assertThrows(IllegalArgumentException.class, () -> new MainframeNode(
                NodeUuid.random(),
                NetworkUuid.random(),
                38_400L,
                FailoverRole.ACTIVE,
                Optional.empty(), // missing partner
                0L
        ));
    }

    @Test
    void unpairedRole_rejectsPartner() {
        assertThrows(IllegalArgumentException.class, () -> new MainframeNode(
                NodeUuid.random(),
                NetworkUuid.random(),
                38_400L,
                FailoverRole.NONE,
                Optional.of(NodeUuid.random()), // unexpected partner
                0L
        ));
    }

    @Test
    void negativeCapacity_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MainframeNode(
                NodeUuid.random(),
                NetworkUuid.random(),
                -1L,
                FailoverRole.NONE,
                Optional.empty(),
                0L
        ));
    }
}
