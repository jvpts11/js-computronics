/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.network;

import dev.jstech.core.uuid.NetworkUuid;
import dev.jstech.core.uuid.NodeUuid;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkSystemServerRegistryTest {

    private static NetworkUuid net() {
        return new NetworkUuid(UUID.randomUUID());
    }

    private static NodeUuid node() {
        return new NodeUuid(UUID.randomUUID());
    }

    @Test
    void emptyRegistry_returnsEmptyServersList() {
        NetworkSystem ns = new NetworkSystem();
        assertTrue(ns.serversOf(net()).isEmpty());
    }

    @Test
    void emptyRegistry_returnsZeroTotalStorage() {
        NetworkSystem ns = new NetworkSystem();
        assertEquals(0L, ns.totalStorageItemsOf(net()));
    }

    @Test
    void registerServer_appearsInServersOf() {
        NetworkSystem ns = new NetworkSystem();
        NetworkUuid networkUuid = net();
        ServerNode server = new ServerNode(node(), networkUuid, 1024L);

        ns.registerServer(server);

        assertEquals(1, ns.serversOf(networkUuid).size());
        assertEquals(server, ns.serversOf(networkUuid).get(0));
    }

    @Test
    void multipleServers_onSameNetwork_allRegistered() {
        NetworkSystem ns = new NetworkSystem();
        NetworkUuid networkUuid = net();
        ServerNode s1 = new ServerNode(node(), networkUuid, 1024L);
        ServerNode s2 = new ServerNode(node(), networkUuid, 2048L);
        ServerNode s3 = new ServerNode(node(), networkUuid, 4096L);

        ns.registerServer(s1);
        ns.registerServer(s2);
        ns.registerServer(s3);

        assertEquals(3, ns.serversOf(networkUuid).size());
    }

    @Test
    void serversOnDifferentNetworks_areIsolated() {
        NetworkSystem ns = new NetworkSystem();
        NetworkUuid netA = net();
        NetworkUuid netB = net();

        ns.registerServer(new ServerNode(node(), netA, 1024L));
        ns.registerServer(new ServerNode(node(), netB, 2048L));

        assertEquals(1, ns.serversOf(netA).size());
        assertEquals(1, ns.serversOf(netB).size());
        assertEquals(1024L, ns.serversOf(netA).get(0).storageItems());
        assertEquals(2048L, ns.serversOf(netB).get(0).storageItems());
    }

    @Test
    void totalStorage_sumsAcrossAllServersOfNetwork() {
        NetworkSystem ns = new NetworkSystem();
        NetworkUuid networkUuid = net();
        ns.registerServer(new ServerNode(node(), networkUuid, 1024L));
        ns.registerServer(new ServerNode(node(), networkUuid, 2048L));
        ns.registerServer(new ServerNode(node(), networkUuid, 4096L));

        assertEquals(7168L, ns.totalStorageItemsOf(networkUuid));
    }

    @Test
    void totalStorage_isolatedPerNetwork() {
        NetworkSystem ns = new NetworkSystem();
        NetworkUuid netA = net();
        NetworkUuid netB = net();
        ns.registerServer(new ServerNode(node(), netA, 1024L));
        ns.registerServer(new ServerNode(node(), netB, 2048L));

        assertEquals(1024L, ns.totalStorageItemsOf(netA));
        assertEquals(2048L, ns.totalStorageItemsOf(netB));
    }

    @Test
    void serversOf_returnedListIsImmutable() {
        NetworkSystem ns = new NetworkSystem();
        NetworkUuid networkUuid = net();
        ns.registerServer(new ServerNode(node(), networkUuid, 1024L));

        var list = ns.serversOf(networkUuid);
        assertThrows(UnsupportedOperationException.class, () ->
                list.add(new ServerNode(node(), networkUuid, 2048L)));
    }

    @Test
    void registerServer_nullThrows() {
        NetworkSystem ns = new NetworkSystem();
        assertThrows(NullPointerException.class, () -> ns.registerServer(null));
    }

    @Test
    void clear_removesAllServers() {
        NetworkSystem ns = new NetworkSystem();
        NetworkUuid networkUuid = net();
        ns.registerServer(new ServerNode(node(), networkUuid, 1024L));

        ns.clear();

        assertTrue(ns.serversOf(networkUuid).isEmpty());
        assertEquals(0L, ns.totalStorageItemsOf(networkUuid));
    }
}
