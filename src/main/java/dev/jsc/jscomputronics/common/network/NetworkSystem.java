/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.network;

import dev.jsc.jscomputronics.common.registry.JscAttachments;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.common.uuid.NodeUuid;

import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Central facade for spatial connectivity, UUID lookup, and orchestration capacity queries on a J's Computronics network.
 */
public final class NetworkSystem {

    private final ConnectivityIndex connectivity = new ConnectivityIndex();

    private final java.util.Map<NetworkUuid, MainframeNode> mainframesByNetwork = new java.util.HashMap<>();

    private final java.util.Map<NetworkUuid, Long> mainframePosByNetwork = new java.util.HashMap<>();

    private final java.util.Map<NetworkUuid, List<SubframeNode>> subframesByNetwork = new java.util.HashMap<>();

    private final java.util.Map<NetworkUuid, java.util.List<ServerNode>> serversByNetwork = new java.util.HashMap<>();

    private final java.util.Map<NodeUuid, ServerLocation> serverLocations = new java.util.HashMap<>();

    private final java.util.Map<NetworkUuid, java.util.List<PersonalComputerNode>> pcsByNetwork =
            new java.util.HashMap<>();

    private final java.util.Map<NetworkUuid, java.util.List<ServerRouterElement>> routersByNetwork =
            new java.util.HashMap<>();

    /**
     * A Personal Computer attached to a network: a Category-C node that issues, but never orchestrates, Operations.
     */
    public record PersonalComputerNode(NodeUuid nodeUuid, NetworkUuid networkUuid, long capacity, long pos) {
    }

    /**
     * The Rack block and internal slot that house a Server, for storage resolution.
     */
    public record ServerLocation(long rackPos, int slot) {
    }

    // Per-level acquisition (Phase 1+)

    public static NetworkSystem get(final ServerLevel level) {
        return level.getData(JscAttachments.NETWORK_SYSTEM.get());
    }

    // ConnectivityIndex facade — works in Phase 0

    public ConnectivityIndex connectivity() {
        return connectivity;
    }

    public Optional<NetworkUuid> networkOf(long encodedPos) {
        return connectivity.networkOf(encodedPos);
    }

    public boolean inSameNetwork(long a, long b) {
        return connectivity.inSameNetwork(a, b);
    }

    // Mainframe / Subframe registry — works in Phase 0 with snapshots

    public void registerMainframe(MainframeNode mainframe) {
        java.util.Objects.requireNonNull(mainframe, "mainframe must not be null");
        mainframesByNetwork.put(mainframe.networkUuid(), mainframe);
    }

    public void recordMainframePosition(NetworkUuid network, long pos) {
        mainframePosByNetwork.put(network, pos);
    }

    public Optional<Long> mainframePositionOf(NetworkUuid network) {
        return Optional.ofNullable(mainframePosByNetwork.get(network));
    }

    public void unregisterMainframe(NetworkUuid network, NodeUuid node) {
        final MainframeNode current = mainframesByNetwork.get(network);
        if (current != null && current.nodeUuid().equals(node)) {
            mainframesByNetwork.remove(network);
            mainframePosByNetwork.remove(network);
        }
    }

    public void registerSubframe(SubframeNode subframe) {
        java.util.Objects.requireNonNull(subframe, "subframe must not be null");
        subframesByNetwork
                .computeIfAbsent(subframe.networkUuid(), k -> new java.util.ArrayList<>())
                .add(subframe);
    }

    public Optional<MainframeNode> mainframeOf(NetworkUuid networkUuid) {
        return Optional.ofNullable(mainframesByNetwork.get(networkUuid));
    }

    public List<SubframeNode> subframesOf(NetworkUuid networkUuid) {
        var list = subframesByNetwork.get(networkUuid);
        if (list == null) {
            return List.of();
        }
        return List.copyOf(list);
    }

    public void registerServer(ServerNode server) {
        java.util.Objects.requireNonNull(server, "server must not be null");
        final java.util.List<ServerNode> list =
                serversByNetwork.computeIfAbsent(server.networkUuid(), k -> new java.util.ArrayList<>());
        // Idempotent by node UUID: replace any existing snapshot of the same
        // Server so a Rack re-registering each tick never duplicates entries.
        list.removeIf(s -> s.nodeUuid().equals(server.nodeUuid()));
        list.add(server);
    }

    public void registerServer(ServerNode server, long rackPos, int slot) {
        registerServer(server);
        serverLocations.put(server.nodeUuid(), new ServerLocation(rackPos, slot));
    }

    public Optional<ServerLocation> locationOf(NodeUuid node) {
        return Optional.ofNullable(serverLocations.get(node));
    }

    public void registerPersonalComputer(PersonalComputerNode pc) {
        java.util.Objects.requireNonNull(pc, "pc must not be null");
        final java.util.List<PersonalComputerNode> list =
                pcsByNetwork.computeIfAbsent(pc.networkUuid(), k -> new java.util.ArrayList<>());
        list.removeIf(p -> p.nodeUuid().equals(pc.nodeUuid()));
        list.add(pc);
    }

    public void unregisterPersonalComputer(NetworkUuid network, NodeUuid node) {
        final java.util.List<PersonalComputerNode> list = pcsByNetwork.get(network);
        if (list != null) {
            list.removeIf(p -> p.nodeUuid().equals(node));
            if (list.isEmpty()) {
                pcsByNetwork.remove(network);
            }
        }
    }

    public java.util.List<PersonalComputerNode> personalComputersOf(NetworkUuid networkUuid) {
        final var list = pcsByNetwork.get(networkUuid);
        return list == null ? java.util.List.of() : java.util.List.copyOf(list);
    }

    public void unregisterServer(NetworkUuid network, NodeUuid node) {
        final java.util.List<ServerNode> list = serversByNetwork.get(network);
        if (list != null) {
            list.removeIf(s -> s.nodeUuid().equals(node));
            if (list.isEmpty()) {
                serversByNetwork.remove(network);
            }
        }
        serverLocations.remove(node);
    }

    public java.util.List<ServerNode> serversOf(NetworkUuid networkUuid) {
        var list = serversByNetwork.get(networkUuid);
        if (list == null) {
            return java.util.List.of();
        }
        return java.util.List.copyOf(list);
    }

    public long totalStorageOf(NetworkUuid networkUuid) {
        long total = 0L;
        for (var server : serversOf(networkUuid)) {
            total += server.storageMB();
        }
        return total;
    }

    public long totalOrchestrationCapacityOf(NetworkUuid networkUuid) {
        long total = mainframeOf(networkUuid)
                .map(MainframeNode::contributedCapacity)
                .orElse(0L);
        for (var subframe : subframesOf(networkUuid)) {
            total += subframe.contributedCapacity();
        }
        return total;
    }

    // Server Router (topology element) registry

    public void registerRouter(final ServerRouterElement router) {
        java.util.Objects.requireNonNull(router, "router must not be null");
        final java.util.List<ServerRouterElement> list =
                routersByNetwork.computeIfAbsent(router.networkUuid(), k -> new java.util.ArrayList<>());
        list.removeIf(r -> r.pos() == router.pos());
        list.add(router);
    }

    public void unregisterRouter(final NetworkUuid network, final long pos) {
        final java.util.List<ServerRouterElement> list = routersByNetwork.get(network);
        if (list != null) {
            list.removeIf(r -> r.pos() == pos);
            if (list.isEmpty()) {
                routersByNetwork.remove(network);
            }
        }
    }

    public java.util.List<ServerRouterElement> routersOf(final NetworkUuid networkUuid) {
        final var list = routersByNetwork.get(networkUuid);
        return list == null ? java.util.List.of() : java.util.List.copyOf(list);
    }

    // Phase 1+ stubs — depend on runtime topology / BlockEntities

    public Optional<NodeUuid> nodeByPosition(long encodedPos) {
        throw new UnsupportedOperationException(
                "nodeByPosition requires runtime BlockEntity lookup, "
                        + "deferred to Phase 1+.");
    }

    public Optional<MainframeNode> failoverPartnerOf(NetworkUuid networkUuid) {
        throw new UnsupportedOperationException(
                "failoverPartnerOf resolves a partner via cross-Mainframe lookup, "
                        + "deferred to Phase 1+.");
    }

    public void clear() {
        connectivity.clear();
        mainframesByNetwork.clear();
        mainframePosByNetwork.clear();
        subframesByNetwork.clear();
        serversByNetwork.clear();
        serverLocations.clear();
        pcsByNetwork.clear();
        routersByNetwork.clear();
    }
}
