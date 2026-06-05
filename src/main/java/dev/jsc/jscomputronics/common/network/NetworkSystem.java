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

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Central facade for spatial connectivity, UUID lookup, and orchestration capacity queries on a J's Computronics network.
 */
public final class NetworkSystem {

    private final ConnectivityIndex connectivity = new ConnectivityIndex();

    private final java.util.Map<NetworkUuid, MainframeNode> mainframesByNetwork = new java.util.HashMap<>();

    private final java.util.Map<NetworkUuid, List<SubframeNode>> subframesByNetwork = new java.util.HashMap<>();

    private final java.util.Map<NetworkUuid, java.util.List<ServerNode>> serversByNetwork = new java.util.HashMap<>();
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
        serversByNetwork
                .computeIfAbsent(server.networkUuid(), k -> new java.util.ArrayList<>())
                .add(server);
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
        subframesByNetwork.clear();
        serversByNetwork.clear();
    }
}
