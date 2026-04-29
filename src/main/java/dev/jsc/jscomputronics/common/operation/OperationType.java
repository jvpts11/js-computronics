/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 *
 * J's Computronics is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License version 3
 * as published by the Free Software Foundation.
 *
 * J's Computronics is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 */
package dev.jsc.jscomputronics.common.operation;

import dev.jsc.jscomputronics.common.network.NetworkCategory;
import dev.jsc.jscomputronics.common.tier.IndustrialTier;

import java.util.EnumSet;
import java.util.Objects;

/**
 * Definition of an operation type registered with the {@link OperationTypeRegistry}.
 */
public record OperationType<T extends OperationArgs>(
        String id,
        Class<T> argsClass,
        OperationCategory category,
        IndustrialTier minTier,
        EnumSet<NetworkCategory> requiredCategories,
        OperationHandler<T> handler
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
