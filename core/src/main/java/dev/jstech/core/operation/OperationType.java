/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.operation;

import dev.jstech.core.network.NetworkCategory;
import dev.jstech.core.tier.IndustrialTier;

import java.util.EnumSet;
import java.util.Objects;

/**
 * Definition of an operation type registered with the {@link OperationTypeRegistry}.
 */
public record OperationType<T extends IOperationArgs>(
        String id,
        Class<T> argsClass,
        OperationCategory category,
        IndustrialTier minTier,
        EnumSet<NetworkCategory> requiredCategories,
        IOperationHandler<T> handler
) {

    public OperationType {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(argsClass, "argsClass must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(minTier, "minTier must not be null");
        Objects.requireNonNull(requiredCategories, "requiredCategories must not be null");
        Objects.requireNonNull(handler, "handler must not be null");
        if (requiredCategories.isEmpty()) {
            throw new IllegalArgumentException(
                    "requiredCategories must contain at least one NetworkCategory; got empty set for " + id);
        }
        if (!id.matches("[a-z0-9_]+:[a-z0-9_/]+")) {
            throw new IllegalArgumentException(
                    "id must be in 'namespace:path' form using [a-z0-9_/]; got: " + id);
        }
    }
}
