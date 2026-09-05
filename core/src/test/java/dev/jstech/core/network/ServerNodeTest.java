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
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerNodeTest {

    private static NodeUuid nodeUuid() {
        return new NodeUuid(UUID.randomUUID());
    }

    private static NetworkUuid networkUuid() {
        return new NetworkUuid(UUID.randomUUID());
    }

    @Test
    void server_isCategoryC() {
        ServerNode server = new ServerNode(nodeUuid(), networkUuid(), 1024L);
        assertEquals(NetworkCategory.C, server.category());
    }

    @Test
    void server_isServiceNode() {
        ServerNode server = new ServerNode(nodeUuid(), networkUuid(), 1024L);
        assertInstanceOf(ServiceNode.class, server);
        assertInstanceOf(INetworkNode.class, server);
    }

    @Test
    void zeroStorage_isAllowed() {
        // A Server with no disks installed is valid (just won't store anything).
        ServerNode server = new ServerNode(nodeUuid(), networkUuid(), 0L);
        assertEquals(0L, server.storageItems());
    }

    @Test
    void negativeStorage_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () ->
                new ServerNode(nodeUuid(), networkUuid(), -1L));
    }

    @Test
    void nullNodeUuid_throwsNullPointer() {
        assertThrows(NullPointerException.class, () ->
                new ServerNode(null, networkUuid(), 1024L));
    }

    @Test
    void nullNetworkUuid_throwsNullPointer() {
        assertThrows(NullPointerException.class, () ->
                new ServerNode(nodeUuid(), null, 1024L));
    }

    @Test
    void records_haveStructuralEquality() {
        NodeUuid n = nodeUuid();
        NetworkUuid net = networkUuid();
        ServerNode a = new ServerNode(n, net, 1024L);
        ServerNode b = new ServerNode(n, net, 1024L);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
