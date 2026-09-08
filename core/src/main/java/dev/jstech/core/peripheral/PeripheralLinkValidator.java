/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
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
    public interface ICableLookup {
        Optional<PeripheralCableType> cableTypeAt(long pos);
    }

    /**
     * Looks up an owner BlockEntity at a position, or empty if no owner is present.
     */
    @FunctionalInterface
    public interface IOwnerLookup {
        Optional<IPeripheralOwner> ownerAt(long pos);
    }

    /**
     * Looks up an endpoint BlockEntity at a position, or empty if no endpoint is present.
     */
    @FunctionalInterface
    public interface IEndpointLookup {
        Optional<IPeripheralEndpoint> endpointAt(long pos);
    }

    /**
     * Provides the 6 face-adjacent neighbors of a given position.
     */
    @FunctionalInterface
    public interface INeighborLookup {
        List<Long> neighborsOf(long pos);
    }

    private final ICableLookup cableLookup;
    private final IOwnerLookup ownerLookup;
    private final IEndpointLookup endpointLookup;
    private final INeighborLookup neighborLookup;

    public PeripheralLinkValidator(
            final ICableLookup cableLookup,
            final IOwnerLookup ownerLookup,
            final IEndpointLookup endpointLookup,
            final INeighborLookup neighborLookup) {
        this.cableLookup = cableLookup;
        this.ownerLookup = ownerLookup;
        this.endpointLookup = endpointLookup;
        this.neighborLookup = neighborLookup;
    }

    public ILinkResult tryEstablishLink(
            final long ownerPos,
            final long endpointPos) {

        final Optional<IPeripheralOwner> ownerOpt = ownerLookup.ownerAt(ownerPos);
        final Optional<IPeripheralEndpoint> endpointOpt = endpointLookup.endpointAt(endpointPos);

        if (ownerOpt.isEmpty() || endpointOpt.isEmpty()) {
            return new ILinkResult.NoPathFound(ownerPos, endpointPos);
        }

        final IPeripheralOwner owner = ownerOpt.get();
        final IPeripheralEndpoint endpoint = endpointOpt.get();

        if (owner.cableType() != endpoint.cableType()) {
            return new ILinkResult.CableTypeMismatch(
                    owner.cableType(), endpoint.cableType());
        }

        // Endpoint cardinality check: at most one owner.
        final Optional<Long> existingOwner = endpoint.linkedOwner();
        if (existingOwner.isPresent() && existingOwner.get() != ownerPos) {
            return new ILinkResult.AlreadyLinked(endpointPos, existingOwner.get());
        }

        /*
         * Owner capacity check, but allow re-linking the same endpoint
         * (idempotent re-establish after periodic validation).
         */
        final List<Long> currentLinks = owner.linkedEndpoints();
        if (currentLinks.size() >= owner.maxEndpoints()
                && !currentLinks.contains(endpointPos)) {
            return new ILinkResult.OwnerAtCapacity(
                    ownerPos, currentLinks.size(), owner.maxEndpoints());
        }

        final PeripheralCableType requiredType = owner.cableType();
        final IPathSearchResult pathResult = findPath(
                ownerPos, endpointPos, requiredType);

        return switch (pathResult) {
            case IPathSearchResult.Found(int length) -> {
                owner.onEndpointLinked(endpointPos);
                endpoint.onOwnerLinked(ownerPos);
                yield new ILinkResult.Established(ownerPos, endpointPos, length);
            }
            case IPathSearchResult.NotFound notFound ->
                    new ILinkResult.NoPathFound(ownerPos, endpointPos);
            case IPathSearchResult.TooLong(int length, int max) ->
                    new ILinkResult.ExceedsMaxLength(
                            ownerPos, endpointPos, length, max);
        };
    }

    public boolean isLinkStillValid(
            final long ownerPos,
            final long endpointPos,
            final PeripheralCableType cableType) {
        return findPath(ownerPos, endpointPos, cableType)
                instanceof IPathSearchResult.Found;
    }

    // ─── BFS internals ──────────────────────────────────────────────────────

    /**
     * Internal sealed result of the path-finding step.
     */
    private sealed interface IPathSearchResult
            permits IPathSearchResult.Found,
            IPathSearchResult.NotFound,
            IPathSearchResult.TooLong {

        record Found(int length) implements IPathSearchResult {}

        record NotFound() implements IPathSearchResult {}

        record TooLong(int length, int max) implements IPathSearchResult {}
    }

    private IPathSearchResult findPath(
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
                    // Owner adjacent to endpoint, zero cables between them.
                    return new IPathSearchResult.Found(0);
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
                        return new IPathSearchResult.Found(distance);
                    }
                    return new IPathSearchResult.TooLong(distance, maxLength);
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

        return new IPathSearchResult.NotFound();
    }

    // ─── Adjacency helper for tests ─────────────────────────────────────────

    public static INeighborLookup adjacencyFrom(
            final Map<Long, List<Long>> adjacency) {
        return pos -> adjacency.getOrDefault(pos, List.of());
    }
}
