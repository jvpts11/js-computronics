/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.config;

/**
 * Outcome of validating a config value against its declared {@link ConfigKey}.
 */
public sealed interface ConfigValidationResult<T>
        permits ConfigValidationResult.Valid,
        ConfigValidationResult.Clamped,
        ConfigValidationResult.Rejected {

    T value();

    /**
     * Value passed validation as-is.
     */
    record Valid<T>(T value) implements ConfigValidationResult<T> {
    }

    /**
     * Numeric value was outside its range and was adjusted to the nearest bound.
     */
    record Clamped<T>(T value, T original) implements ConfigValidationResult<T> {
    }

    /**
     * Value was rejected (wrong type, null, or other structural issue) and the default was substituted.
     */
    record Rejected<T>(T value, String reason) implements ConfigValidationResult<T> {
    }
}
