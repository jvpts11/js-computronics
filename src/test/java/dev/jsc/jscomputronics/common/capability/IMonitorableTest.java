/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.capability;

import dev.jsc.jscomputronics.common.network.NetworkCategory;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.common.uuid.NodeUuid;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IMonitorableTest {

    /**
     * Fictional monitorable.
     */
    private static final class FakeMonitorable implements IMonitorable {
        private final NodeUuid nodeUuid = NodeUuid.random();

        @Override public NodeUuid getNodeUuid() { return nodeUuid; }
        @Override public Optional<NetworkUuid> getNetworkUuid() { return Optional.empty(); }
        @Override public NetworkCategory getCategory() { return NetworkCategory.B; }
    }

    @Test
    void implementing_isSubtypeOfINetworkNode() {
        var m = new FakeMonitorable();
        assertTrue(m instanceof INetworkNode);
    }

    @Test
    void category_isAlwaysB() {
        // Documented invariant: monitorables are Category B (read-only).
        var m = new FakeMonitorable();
        assertSame(NetworkCategory.B, m.getCategory());
    }
}
