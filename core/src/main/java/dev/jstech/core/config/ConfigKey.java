/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.config;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Typed declaration of a single config entry: its TOML path, its type, its default value, and (depending on the type)
 * either an allowed numeric range (clamped) or a string whitelist (substituted with the default when violated).
 *
 * <p>A key has at most one constraint: a numeric {@code range} or a string {@code whitelist}, never both.
 */
public record ConfigKey<T>(
        List<String> path,
        Class<T> valueClass,
        T defaultValue,
        Optional<ConfigKeyRange<?>> range,
        Optional<List<String>> whitelist) {

    public ConfigKey {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(valueClass, "valueClass must not be null");
        Objects.requireNonNull(defaultValue, "defaultValue must not be null");
        Objects.requireNonNull(range, "range must not be null (use Optional.empty)");
        Objects.requireNonNull(whitelist, "whitelist must not be null (use Optional.empty)");
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
        if (range.isPresent() && whitelist.isPresent()) {
            throw new IllegalArgumentException(
                    "a key cannot have both a numeric range and a string whitelist: " + path);
        }
        if (range.isPresent()) {
            /*
             * A range's bounds must be the key's own value type, or the validator would later cast the
             * value to the bound type and throw instead of clamping (a Boolean key with a numeric range,
             * or an Integer value with a Long range, must be rejected here, not crash at use).
             */
            final Object min = range.get().min();
            final Object max = range.get().max();
            if (!valueClass.isInstance(min) || !valueClass.isInstance(max)) {
                throw new IllegalArgumentException(
                        "range bounds must match the key's value type " + valueClass.getSimpleName()
                                + ", got " + min.getClass().getSimpleName());
            }
        }
        if (whitelist.isPresent()) {
            /*
             * A whitelist only makes sense for string-valued keys, and the default must itself be allowed,
             * otherwise a rejected value would be substituted with a value that is also not in the list.
             */
            if (!String.class.equals(valueClass)) {
                throw new IllegalArgumentException(
                        "whitelist is only supported for String keys, got " + valueClass.getSimpleName());
            }
            final List<String> allowed = whitelist.get();
            if (allowed.isEmpty()) {
                throw new IllegalArgumentException("whitelist must not be empty: " + path);
            }
            if (!allowed.contains(defaultValue)) {
                throw new IllegalArgumentException(
                        "default value " + defaultValue + " is not in the whitelist " + allowed);
            }
            whitelist = Optional.of(List.copyOf(allowed));
        }
    }

    /**
     * Backwards-compatible constructor for keys with no string whitelist (only a numeric range or no constraint at all).
     */
    public ConfigKey(
            final List<String> path,
            final Class<T> valueClass,
            final T defaultValue,
            final Optional<ConfigKeyRange<?>> range) {
        this(path, valueClass, defaultValue, range, Optional.empty());
    }

    public static <T> ConfigKey<T> of(
            final List<String> path,
            final Class<T> valueClass,
            final T defaultValue) {
        return new ConfigKey<>(path, valueClass, defaultValue, Optional.empty(), Optional.empty());
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
        return new ConfigKey<>(path, valueClass, defaultValue, Optional.of(range), Optional.empty());
    }

    /**
     * Declares a string key whose value is restricted to a fixed set. A value outside the set is rejected by the
     * {@link ConfigValidator} and replaced with the default, so the loaded value is always one of the allowed strings.
     */
    public static ConfigKey<String> whitelisted(
            final List<String> path,
            final String defaultValue,
            final List<String> allowed) {
        return new ConfigKey<>(path, String.class, defaultValue, Optional.empty(), Optional.of(allowed));
    }

    public String dottedPath() {
        return String.join(".", path);
    }
}
