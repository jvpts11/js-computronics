/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.config;

import java.util.Objects;

/**
 * Inclusive numeric range for a {@link ConfigKey}.
 */

public record ConfigKeyRange<N extends Number & Comparable<N>>(N min, N max) {

    public ConfigKeyRange {
        Objects.requireNonNull(min, "min must not be null");
        Objects.requireNonNull(max, "max must not be null");
        if (min.compareTo(max) > 0) {
            throw new IllegalArgumentException(
                    "min (" + min + ") must be <= max (" + max + ")");
        }
    }

    public N clamp(final N value) {
        Objects.requireNonNull(value, "value must not be null");
        if (value.compareTo(min) < 0) {
            return min;
        }
        if (value.compareTo(max) > 0) {
            return max;
        }
        return value;
    }

    public boolean contains(final N value) {
        Objects.requireNonNull(value, "value must not be null");
        return value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
    }
}
