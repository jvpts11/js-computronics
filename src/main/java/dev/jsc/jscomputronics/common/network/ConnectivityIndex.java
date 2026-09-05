/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.network;

import dev.jsc.jscomputronics.common.uuid.NetworkUuid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
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

    private final Map<Long, Set<Long>> adjacency = new HashMap<>();

    // Every component's positions, by root, kept until the topology changes: a Mainframe asks for its
    // segment every tick, and walking every cable of a big base to answer was a fixed cost on the idle tick.
    private final Map<Integer, Set<Long>> componentCache = new HashMap<>();

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

    public int componentSize(long encodedPos) {
        return componentPositions(encodedPos).size();
    }

    public Set<Long> componentPositions(long encodedPos) {
        final Integer id = posToId.get(encodedPos);
        if (id == null) {
            return Set.of();
        }
        final int root = dsu.find(id);
        Set<Long> cached = componentCache.get(root);
        if (cached == null) {
            final Set<Long> result = new LinkedHashSet<>();
            for (final Map.Entry<Long, Integer> entry : posToId.entrySet()) {
                if (dsu.find(entry.getValue()) == root) {
                    result.add(entry.getKey());
                }
            }
            cached = java.util.Collections.unmodifiableSet(result);
            componentCache.put(root, cached);
        }
        return cached;
    }

    /**
     * Every cable position belonging to the given network. A read-only scan (the same shape as
     * {@link #componentPositions}) used to locate a named device, such as a bus, mounted anywhere on the
     * network's cabling without needing a starting position.
     */
    public Set<Long> positionsOf(final NetworkUuid network) {
        if (network == null) {
            return Set.of();
        }
        final Set<Long> result = new LinkedHashSet<>();
        for (final Map.Entry<Long, Integer> entry : posToId.entrySet()) {
            if (network.equals(rootToUuid.get(dsu.find(entry.getValue())))) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    // Mutations

    public PlacementResult onCablePlaced(long encodedPos, Set<Long> neighbors) {
        if (posToId.containsKey(encodedPos)) {
            throw new IllegalStateException(
                    "Position already registered: " + encodedPos);
        }

        // 1. Allocate a fresh DSU element for the new cable.
        componentCache.clear();
        int newId = dsu.makeSet();
        posToId.put(encodedPos, newId);
        idToPos.put(newId, encodedPos);
        adjacency.put(encodedPos, new LinkedHashSet<>());

        // 2. Find which neighbors are actually in the index, and what UUIDs
        NetworkUuid firstSeenUuid = null;
        NetworkUuid conflictingUuid = null;
        boolean unionedWithAny = false;

        for (Long neighborPos : neighbors) {
            Integer neighborId = posToId.get(neighborPos);
            if (neighborId == null) {
                continue; // Unknown neighbor — ignore.
            }
            // Record the edge in both directions for rediscovery.
            adjacency.get(encodedPos).add(neighborPos);
            adjacency.get(neighborPos).add(encodedPos);
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

    public void bridge(Collection<Long> positions) {
        final List<Integer> ids = new ArrayList<>();
        NetworkUuid surviving = null;
        for (final long pos : positions) {
            final Integer id = posToId.get(pos);
            if (id == null) {
                continue;
            }
            ids.add(id);
            // Deterministic survivor: the lexicographically smallest UUID among the merged components, so
            // which network identity wins a merge does not depend on iteration order (unpredictable to the
            // player and unstable across reloads).
            final NetworkUuid uuid = rootToUuid.get(dsu.find(id));
            if (uuid != null && (surviving == null || uuid.asString().compareTo(surviving.asString()) < 0)) {
                surviving = uuid;
            }
        }
        if (ids.size() < 2) {
            return; // nothing to bridge — a device touching one run (or none) changes nothing
        }
        int root = dsu.find(ids.get(0));
        boolean merged = false;
        for (int k = 1; k < ids.size(); k++) {
            if (dsu.union(root, ids.get(k))) {
                merged = true;
            }
            root = dsu.find(root);
        }
        if (merged) {
            componentCache.clear();
            cleanupOrphanedUuids(root);
            if (surviving != null) {
                rootToUuid.put(root, surviving);
            }
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

    public void clearNetwork(NetworkUuid uuid) {
        rootToUuid.values().removeIf(uuid::equals);
    }

    public RemovalResult onCableRemoved(long encodedPos) {
        if (!posToId.containsKey(encodedPos)) {
            throw new IllegalStateException(
                    "Position not registered: " + encodedPos);
        }

        final Optional<NetworkUuid> previousUuid = networkOf(encodedPos);
        final Set<Long> affectedComponent = collectComponent(encodedPos);

        // Snapshot every cable's current UUID before tearing the index down.
        final Map<Long, NetworkUuid> uuidByPos = new HashMap<>();
        for (final Long pos : posToId.keySet()) {
            networkOf(pos).ifPresent(uuid -> uuidByPos.put(pos, uuid));
        }

        // Detach the removed cable from the adjacency graph.
        for (final Long neighbor : adjacency.getOrDefault(encodedPos, Set.of())) {
            final Set<Long> neighborEdges = adjacency.get(neighbor);
            if (neighborEdges != null) {
                neighborEdges.remove(encodedPos);
            }
        }
        adjacency.remove(encodedPos);

        // Rebuild the DSU from scratch over the surviving cables.
        final Set<Long> survivors = new LinkedHashSet<>(posToId.keySet());
        survivors.remove(encodedPos);
        dsu.clear();
        componentCache.clear();
        posToId.clear();
        idToPos.clear();
        rootToUuid.clear();
        for (final Long pos : survivors) {
            final int newId = dsu.makeSet();
            posToId.put(pos, newId);
            idToPos.put(newId, pos);
        }
        for (final Long pos : survivors) {
            for (final Long neighbor : adjacency.getOrDefault(pos, Set.of())) {
                final Integer neighborId = posToId.get(neighbor);
                if (neighborId != null) {
                    dsu.union(posToId.get(pos), neighborId);
                }
            }
        }

        // Count the distinct fragments the affected component split into.
        final Set<Integer> fragmentRoots = new HashSet<>();
        for (final Long pos : affectedComponent) {
            final Integer id = posToId.get(pos);
            if (id != null) {
                fragmentRoots.add(dsu.find(id));
            }
        }
        final boolean severed = fragmentRoots.size() >= 2;

        // Restore UUIDs. Networks untouched by this removal keep theirs. But
        for (final Map.Entry<Long, NetworkUuid> entry : uuidByPos.entrySet()) {
            if (severed && affectedComponent.contains(entry.getKey())) {
                continue;
            }
            final Integer id = posToId.get(entry.getKey());
            if (id != null) {
                rootToUuid.put(dsu.find(id), entry.getValue());
            }
        }
        return new RemovalResult(previousUuid, fragmentRoots.size());
    }

    public Set<Long> reachableFrom(final long start, final Set<Long> blocked) {
        final Set<Long> visited = new LinkedHashSet<>();
        if (blocked.contains(start) || !posToId.containsKey(start)) {
            return visited;
        }
        final Deque<Long> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            final Long current = queue.poll();
            for (final Long neighbor : adjacency.getOrDefault(current, Set.of())) {
                if (!blocked.contains(neighbor) && visited.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        return visited;
    }

    private Set<Long> collectComponent(long start) {
        final Set<Long> visited = new LinkedHashSet<>();
        final Deque<Long> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            final Long current = queue.poll();
            for (final Long neighbor : adjacency.getOrDefault(current, Set.of())) {
                if (visited.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        return visited;
    }

    public void clear() {
        posToId.clear();
        idToPos.clear();
        rootToUuid.clear();
        adjacency.clear();
        dsu.clear();
        componentCache.clear();
    }

    /**
     * Outcome of an {@link #onCableRemoved} invocation.
     */
    public record RemovalResult(Optional<NetworkUuid> previousUuid, int resultingComponents) {
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
