/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.peripheral;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Validates and establishes peripheral links via bounded BFS.
 */
public final class PeripheralLinkValidator {

    /**
     * Looks up the cable type at a position, or empty if no cable of any peripheral type is present.
     */
    @FunctionalInterface
    public interface CableLookup {
        Optional<PeripheralCableType> cableTypeAt(long pos);
    }

    /**
     * Looks up an owner BlockEntity at a position, or empty if no owner is present.
     */
    @FunctionalInterface
    public interface OwnerLookup {
        Optional<PeripheralOwner> ownerAt(long pos);
    }

    /**
     * Looks up an endpoint BlockEntity at a position, or empty if no endpoint is present.
     */
    @FunctionalInterface
    public interface EndpointLookup {
        Optional<PeripheralEndpoint> endpointAt(long pos);
    }

    /**
     * Provides the 6 face-adjacent neighbors of a given position.
     */
    @FunctionalInterface
    public interface NeighborLookup {
        List<Long> neighborsOf(long pos);
    }

    private final CableLookup cableLookup;
    private final OwnerLookup ownerLookup;
    private final EndpointLookup endpointLookup;
    private final NeighborLookup neighborLookup;

    public PeripheralLinkValidator(
            final CableLookup cableLookup,
            final OwnerLookup ownerLookup,
            final EndpointLookup endpointLookup,
            final NeighborLookup neighborLookup) {
        this.cableLookup = cableLookup;
        this.ownerLookup = ownerLookup;
        this.endpointLookup = endpointLookup;
        this.neighborLookup = neighborLookup;
    }

    public LinkResult tryEstablishLink(
            final long ownerPos,
            final long endpointPos) {

        final Optional<PeripheralOwner> ownerOpt = ownerLookup.ownerAt(ownerPos);
        final Optional<PeripheralEndpoint> endpointOpt = endpointLookup.endpointAt(endpointPos);

        if (ownerOpt.isEmpty() || endpointOpt.isEmpty()) {
            return new LinkResult.NoPathFound(ownerPos, endpointPos);
        }

        final PeripheralOwner owner = ownerOpt.get();
        final PeripheralEndpoint endpoint = endpointOpt.get();

        if (owner.cableType() != endpoint.cableType()) {
            return new LinkResult.CableTypeMismatch(
                    owner.cableType(), endpoint.cableType());
        }

        // Endpoint cardinality check: at most one owner.
        final Optional<Long> existingOwner = endpoint.linkedOwner();
        if (existingOwner.isPresent() && existingOwner.get() != ownerPos) {
            return new LinkResult.AlreadyLinked(endpointPos, existingOwner.get());
        }

        // Owner capacity check — but allow re-linking the same endpoint
        // (idempotent re-establish after periodic validation).
        final List<Long> currentLinks = owner.linkedEndpoints();
        if (currentLinks.size() >= owner.maxEndpoints()
                && !currentLinks.contains(endpointPos)) {
            return new LinkResult.OwnerAtCapacity(
                    ownerPos, currentLinks.size(), owner.maxEndpoints());
        }

        final PeripheralCableType requiredType = owner.cableType();
        final PathSearchResult pathResult = findPath(
                ownerPos, endpointPos, requiredType);

        return switch (pathResult) {
            case PathSearchResult.Found(int length) -> {
                owner.onEndpointLinked(endpointPos);
                endpoint.onOwnerLinked(ownerPos);
                yield new LinkResult.Established(ownerPos, endpointPos, length);
            }
            case PathSearchResult.NotFound notFound ->
                    new LinkResult.NoPathFound(ownerPos, endpointPos);
            case PathSearchResult.TooLong(int length, int max) ->
                    new LinkResult.ExceedsMaxLength(
                            ownerPos, endpointPos, length, max);
        };
    }

    public boolean isLinkStillValid(
            final long ownerPos,
            final long endpointPos,
            final PeripheralCableType cableType) {
        return findPath(ownerPos, endpointPos, cableType)
                instanceof PathSearchResult.Found;
    }

    // ─── BFS internals ──────────────────────────────────────────────────────

    /**
     * Internal sealed result of the path-finding step.
     */
    private sealed interface PathSearchResult
            permits PathSearchResult.Found,
            PathSearchResult.NotFound,
            PathSearchResult.TooLong {

        record Found(int length) implements PathSearchResult {}

        record NotFound() implements PathSearchResult {}

        record TooLong(int length, int max) implements PathSearchResult {}
    }

    private PathSearchResult findPath(
            final long source,
            final long target,
            final PeripheralCableType requiredType) {

        final int maxLength = requiredType.maxLength();
        final Set<Long> visited = new HashSet<>();
        final Deque<long[]> queue = new ArrayDeque<>();

        // The source may be a multiblock owner: seed BFS from every face of every
        final Set<Long> sources = ownerLookup.ownerAt(source)
                .map(owner -> owner.occupiedPositions(source))
                .filter(positions -> !positions.isEmpty())
                .orElseGet(() -> Set.of(source));
        visited.addAll(sources);
        for (final long src : sources) {
            for (final long neighbor : neighborLookup.neighborsOf(src)) {
                if (neighbor == target) {
                    // Owner adjacent to endpoint — zero cables between them.
                    return new PathSearchResult.Found(0);
                }
                if (visited.add(neighbor)
                        && cableLookup.cableTypeAt(neighbor)
                        .filter(t -> t == requiredType).isPresent()) {
                    queue.addLast(new long[]{neighbor, 1L});
                }
            }
        }

        while (!queue.isEmpty()) {
            final long[] current = queue.pollFirst();
            final long pos = current[0];
            final int distance = (int) current[1];

            for (final long neighbor : neighborLookup.neighborsOf(pos)) {
                if (neighbor == target) {
                    // First reach is shortest path (BFS invariant).
                    if (distance <= maxLength) {
                        return new PathSearchResult.Found(distance);
                    }
                    return new PathSearchResult.TooLong(distance, maxLength);
                }
                if (!visited.add(neighbor)) {
                    continue;
                }
                if (cableLookup.cableTypeAt(neighbor)
                        .filter(t -> t == requiredType).isPresent()) {
                    queue.addLast(new long[]{neighbor, distance + 1});
                }
            }
        }

        return new PathSearchResult.NotFound();
    }

    // ─── Adjacency helper for tests ─────────────────────────────────────────

    public static NeighborLookup adjacencyFrom(
            final Map<Long, List<Long>> adjacency) {
        return pos -> adjacency.getOrDefault(pos, List.of());
    }
}
