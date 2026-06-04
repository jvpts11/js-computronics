/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.config;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Typed declaration of a single config entry: its TOML path, its type, its default value, and (for numeric types) its allowed range.
 */
public record ConfigKey<T>(
        List<String> path,
        Class<T> valueClass,
        T defaultValue,
        Optional<ConfigKeyRange<?>> range) {

    public ConfigKey {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(valueClass, "valueClass must not be null");
        Objects.requireNonNull(defaultValue, "defaultValue must not be null");
        Objects.requireNonNull(range, "range must not be null (use Optional.empty)");
        if (path.isEmpty()) {
            throw new IllegalArgumentException("path must not be empty");
        }
        for (final String segment : path) {
            if (segment == null || segment.isBlank()) {
                throw new IllegalArgumentException(
                        "path segments must be non-blank, got: " + path);
            }
        }
        path = List.copyOf(path);
        if (!valueClass.isInstance(defaultValue)) {
            throw new IllegalArgumentException(
                    "defaultValue type mismatch: expected " + valueClass.getSimpleName()
                            + ", got " + defaultValue.getClass().getSimpleName());
        }
    }

    public static <T> ConfigKey<T> of(
            final List<String> path,
            final Class<T> valueClass,
            final T defaultValue) {
        return new ConfigKey<>(path, valueClass, defaultValue, Optional.empty());
    }

    public static <N extends Number & Comparable<N>> ConfigKey<N> ranged(
            final List<String> path,
            final Class<N> valueClass,
            final N defaultValue,
            final ConfigKeyRange<N> range) {
        if (!range.contains(defaultValue)) {
            throw new IllegalArgumentException(
                    "defaultValue " + defaultValue + " is outside declared range "
                            + "[" + range.min() + ", " + range.max() + "]");
        }
        return new ConfigKey<>(path, valueClass, defaultValue, Optional.of(range));
    }

    public String dottedPath() {
        return String.join(".", path);
    }
}
