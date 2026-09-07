/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.peripheral;

/**
 * Outcome of a {@link PeripheralLinkValidator} attempt to establish or validate a link.
 */
public sealed interface ILinkResult
        permits ILinkResult.Established,
        ILinkResult.AlreadyLinked,
        ILinkResult.OwnerAtCapacity,
        ILinkResult.NoPathFound,
        ILinkResult.ExceedsMaxLength,
        ILinkResult.CableTypeMismatch {

    /**
     * Link successfully established between owner and endpoint.
     */
    record Established(long ownerPos, long endpointPos, int pathLength)
            implements ILinkResult {
    }

    /**
     * The endpoint is already linked to another owner — endpoints have cardinality 1.
     */
    record AlreadyLinked(long endpointPos, long existingOwnerPos)
            implements ILinkResult {
    }

    /**
     * The owner has reached its hardware-bounded maximum number of linked endpoints (motherboard ports, PCIe slots, machine faces).
     */
    record OwnerAtCapacity(long ownerPos, int currentCount, int maxAllowed)
            implements ILinkResult {
    }

    /**
     * BFS could not reach the endpoint from the owner via cables of the matching type — no continuous path exists.
     */
    record NoPathFound(long ownerPos, long endpointPos) implements ILinkResult {
    }

    /**
     * A path exists but is longer than {@link PeripheralCableType#maxLength()}.
     */
    record ExceedsMaxLength(long ownerPos, long endpointPos,
                            int pathLength, int maxAllowed)
            implements ILinkResult {
    }

    /**
     * The cable type of the path does not match either the owner's or the endpoint's accepted type — happens when the BFS picks up a cable of the wrong system mid-path.
     */
    record CableTypeMismatch(PeripheralCableType expected,
                             PeripheralCableType actual)
            implements ILinkResult {
    }
}
