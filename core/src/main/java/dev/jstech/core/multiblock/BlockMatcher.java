/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.multiblock;

import java.util.Objects;
import java.util.Set;

/**
 * Predicate for whether a given block satisfies a slot in a {@link MultiblockPattern}.
 */
@FunctionalInterface
public interface BlockMatcher {

    boolean matches(String blockId);

    static BlockMatcher exact(String requiredId) {
        Objects.requireNonNull(requiredId, "requiredId must not be null");
        return blockId -> requiredId.equals(blockId);
    }

    static BlockMatcher anyOf(Set<String> allowedIds) {
        Objects.requireNonNull(allowedIds, "allowedIds must not be null");
        if (allowedIds.isEmpty()) {
            throw new IllegalArgumentException("allowedIds must not be empty");
        }
        // Defensive copy so the matcher is not affected by later mutations.
        var snapshot = Set.copyOf(allowedIds);
        return snapshot::contains;
    }

    static BlockMatcher air() {
        return exact("minecraft:air");
    }

    static BlockMatcher any() {
        return blockId -> true;
    }
}
