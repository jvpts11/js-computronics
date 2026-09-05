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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class INetworkNodeCapabilityTest {

    /**
     * Fictional implementation used only to exercise the contract.
     */
    private static final class FakeNode implements INetworkNodeCapability {
        private final NodeUuid nodeUuid = NodeUuid.random();
        private final Optional<NetworkUuid> networkUuid;
        private final NetworkCategory category;

        FakeNode(Optional<NetworkUuid> networkUuid, NetworkCategory category) {
            this.networkUuid = networkUuid;
            this.category = category;
        }

        @Override public NodeUuid getNodeUuid() { return nodeUuid; }
        @Override public Optional<NetworkUuid> getNetworkUuid() { return networkUuid; }
        @Override public NetworkCategory getCategory() { return category; }
    }

    @Test
    void freshNode_hasNonNullNodeUuid() {
        var node = new FakeNode(Optional.empty(), NetworkCategory.C);
        assertNotNull(node.getNodeUuid());
        assertNotNull(node.getNodeUuid().value());
    }

    @Test
    void unattachedNode_returnsEmptyNetworkUuid() {
        var node = new FakeNode(Optional.empty(), NetworkCategory.B);
        assertFalse(node.getNetworkUuid().isPresent());
    }

    @Test
    void attachedNode_returnsItsNetworkUuid() {
        var net = NetworkUuid.random();
        var node = new FakeNode(Optional.of(net), NetworkCategory.C);
        assertTrue(node.getNetworkUuid().isPresent());
        assertEquals(net, node.getNetworkUuid().get());
    }

    @Test
    void categoryB_isPermitted() {
        var node = new FakeNode(Optional.empty(), NetworkCategory.B);
        assertSame(NetworkCategory.B, node.getCategory());
    }

    @Test
    void categoryC_isPermitted() {
        var node = new FakeNode(Optional.empty(), NetworkCategory.C);
        assertSame(NetworkCategory.C, node.getCategory());
    }

    @Test
    void twoNodes_haveDistinctNodeUuids() {
        // Sanity check on the implementation's UUID generation.
        var a = new FakeNode(Optional.empty(), NetworkCategory.C);
        var b = new FakeNode(Optional.empty(), NetworkCategory.C);
        assertFalse(a.getNodeUuid().equals(b.getNodeUuid()));
    }
}
