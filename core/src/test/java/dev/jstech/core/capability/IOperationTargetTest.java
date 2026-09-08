/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.capability;

import dev.jstech.core.network.NetworkCategory;
import dev.jstech.core.uuid.NetworkUuid;
import dev.jstech.core.uuid.NodeUuid;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IOperationTargetTest {

    /**
     * Fictional Operation target.
     */
    private static final class FakeTarget implements IOperationTarget {
        private final NodeUuid nodeUuid = NodeUuid.random();

        @Override public NodeUuid getNodeUuid() { return nodeUuid; }
        @Override public Optional<NetworkUuid> getNetworkUuid() { return Optional.empty(); }
        @Override public NetworkCategory getCategory() { return NetworkCategory.C; }
    }

    @Test
    void implementing_isSubtypeOfINetworkNodeCapability() {
        var target = new FakeTarget();
        assertTrue(target instanceof INetworkNodeCapability);
    }

    @Test
    void category_isAlwaysC() {
        // Documented invariant: targets are by definition Category C.
        var target = new FakeTarget();
        assertSame(NetworkCategory.C, target.getCategory());
    }
}
