/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.network;

import dev.jsc.jscomputronics.common.uuid.NetworkUuid;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Spatial connectivity index for the J's Computronics computation network.
 */
public final class ConnectivityIndex {

    private final DisjointSetUnion dsu = new DisjointSetUnion();

    private final Map<Long, Integer> posToId = new HashMap<>();

    private final Map<Integer, Long> idToPos = new HashMap<>();

    private final Map<Integer, NetworkUuid> rootToUuid = new HashMap<>();

    // Queries

    public Optional<NetworkUuid> networkOf(long encodedPos) {
        Integer id = posToId.get(encodedPos);
        if (id == null) {
            return Optional.empty();
        }
        int root = dsu.find(id);
        return Optional.ofNullable(rootToUuid.get(root));
    }

    public boolean inSameNetwork(long encodedA, long encodedB) {
        Integer idA = posToId.get(encodedA);
        Integer idB = posToId.get(encodedB);
        if (idA == null || idB == null) {
            return false;
        }
        return dsu.connected(idA, idB);
    }

    public boolean contains(long encodedPos) {
        return posToId.containsKey(encodedPos);
    }

    public int size() {
        return posToId.size();
    }

    public int componentCount() {
        return dsu.componentCount();
    }

    // Mutations

    public PlacementResult onCablePlaced(long encodedPos, Set<Long> neighbors) {
        if (posToId.containsKey(encodedPos)) {
            throw new IllegalStateException(
                    "Position already registered: " + encodedPos);
        }

        // 1. Allocate a fresh DSU element for the new cable.
        int newId = dsu.makeSet();
        posToId.put(encodedPos, newId);
        idToPos.put(newId, encodedPos);

        // 2. Find which neighbors are actually in the index, and what UUIDs
        NetworkUuid firstSeenUuid = null;
        NetworkUuid conflictingUuid = null;
        boolean unionedWithAny = false;

        for (Long neighborPos : neighbors) {
            Integer neighborId = posToId.get(neighborPos);
            if (neighborId == null) {
                continue; // Unknown neighbor — ignore.
            }
            int neighborRoot = dsu.find(neighborId);
            NetworkUuid neighborUuid = rootToUuid.get(neighborRoot);

            if (neighborUuid != null) {
                if (firstSeenUuid == null) {
                    firstSeenUuid = neighborUuid;
                } else if (!firstSeenUuid.equals(neighborUuid) && conflictingUuid == null) {
                    conflictingUuid = neighborUuid;
                }
            }

            // Union regardless — even on conflict, we want the topology
            if (dsu.union(newId, neighborId)) {
                unionedWithAny = true;
            }
        }

        // 3. After all unions, the new cable's component has ONE root.
        //    Decide what UUID (if any) the merged component should have.
        int newRoot = dsu.find(newId);

        if (conflictingUuid != null) {
            // Multiple distinct UUIDs were merged. Keep the first one as
            cleanupOrphanedUuids(newRoot);
            rootToUuid.put(newRoot, firstSeenUuid);
            return new PlacementResult.Conflict(firstSeenUuid, conflictingUuid);
        }

        if (firstSeenUuid != null) {
            // All neighbors with UUIDs agreed on a single value. The
            // merged component now carries that UUID.
            cleanupOrphanedUuids(newRoot);
            rootToUuid.put(newRoot, firstSeenUuid);
            return new PlacementResult.Inherited(firstSeenUuid);
        }

        if (unionedWithAny) {
            // We merged with at least one neighbor, but none of them had
            // a UUID. The merged component is still UUID-less.
            return PlacementResult.MERGED_WITHOUT_UUID;
        }

        // No relevant neighbors at all — isolated cable.
        return PlacementResult.ISOLATED;
    }

    private void cleanupOrphanedUuids(int currentRoot) {
        // Collect ids to remove (avoid concurrent modification).
        var toRemove = new java.util.ArrayList<Integer>();
        for (Integer id : rootToUuid.keySet()) {
            if (id != currentRoot && dsu.find(id) == currentRoot) {
                toRemove.add(id);
            }
        }
        for (Integer id : toRemove) {
            rootToUuid.remove(id);
        }
    }

    public void assignUuid(long encodedPos, NetworkUuid uuid) {
        Integer id = posToId.get(encodedPos);
        if (id == null) {
            throw new IllegalStateException(
                    "Position not registered: " + encodedPos);
        }
        int root = dsu.find(id);
        rootToUuid.put(root, uuid);
    }

    public void onCableRemoved(long encodedPos) {
        throw new UnsupportedOperationException(
                "onCableRemoved is deferred to Phase 1+ — it requires runtime "
                        + "topology to perform lazy rediscovery, not available in Phase 0.");
    }

    public void clear() {
        posToId.clear();
        idToPos.clear();
        rootToUuid.clear();
        dsu.clear();
    }

    // PlacementResult

    /**
     * Outcome of a single {@link #onCablePlaced} invocation.
     */
    public sealed interface PlacementResult
            permits PlacementResult.Isolated,
            PlacementResult.MergedWithoutUuid,
            PlacementResult.Inherited,
            PlacementResult.Conflict {

        /**
         * No registered neighbors — cable is alone in its own new component.
         */
        record Isolated() implements PlacementResult {}

        /**
         * Unioned with neighbors, but none of them had a UUID.
         */
        record MergedWithoutUuid() implements PlacementResult {}

        /**
         * Unioned with neighbors that all agreed on a single UUID; component now carries it.
         */
        record Inherited(NetworkUuid uuid) implements PlacementResult {}

        /**
         * Two or more neighbors carried distinct UUIDs.
         */
        record Conflict(NetworkUuid first, NetworkUuid second) implements PlacementResult {}

        // Stateless singletons for the cases without payload.
        Isolated ISOLATED = new Isolated();
        MergedWithoutUuid MERGED_WITHOUT_UUID = new MergedWithoutUuid();
    }
}
