/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.config;

import java.util.Objects;

/**
 * Validates a raw value against a {@link ConfigKey}, returning a {@link ConfigValidationResult}.
 */
public final class ConfigValidator {

    private final ConfigLogger logger;

    public ConfigValidator(final ConfigLogger logger) {
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
    }

    @SuppressWarnings("unchecked")
    public <T> ConfigValidationResult<T> validate(
            final ConfigKey<T> key,
            final Object rawValue) {
        Objects.requireNonNull(key, "key must not be null");

        // Rule 1: null -> Rejected.
        if (rawValue == null) {
            final String reason = "value for key '" + key.dottedPath()
                    + "' is null; using default";
            logger.warn(reason);
            return new ConfigValidationResult.Rejected<>(
                    key.defaultValue(), reason);
        }

        // Rule 2: type mismatch -> Rejected.
        if (!key.valueClass().isInstance(rawValue)) {
            final String reason = "value for key '" + key.dottedPath()
                    + "' has wrong type: expected "
                    + key.valueClass().getSimpleName()
                    + ", got " + rawValue.getClass().getSimpleName()
                    + "; using default";
            logger.warn(reason);
            return new ConfigValidationResult.Rejected<>(
                    key.defaultValue(), reason);
        }

        final T typedValue = (T) rawValue;

        // Rule 3: value not in the string whitelist -> Rejected (default substituted).
        // There is no "nearest valid" string to clamp to, so the safe house rule is to fall back to the
        // default, which the key guarantees is itself whitelisted.
        if (key.whitelist().isPresent() && !key.whitelist().get().contains(typedValue)) {
            final String reason = "value '" + typedValue + "' for key '"
                    + key.dottedPath() + "' is not one of " + key.whitelist().get()
                    + "; using default '" + key.defaultValue() + "'";
            logger.warn(reason);
            return new ConfigValidationResult.Rejected<>(key.defaultValue(), reason);
        }

        // Rule 4: numeric out of range -> Clamped.
        if (key.range().isPresent()) {
            @SuppressWarnings("rawtypes")
            final ConfigKeyRange range = key.range().get();
            @SuppressWarnings("unchecked")
            final boolean inRange = range.contains((Number & Comparable) typedValue);
            if (!inRange) {
                @SuppressWarnings("unchecked")
                final T clamped = (T) range.clamp((Number & Comparable) typedValue);
                final String reason = "value " + typedValue + " for key '"
                        + key.dottedPath() + "' is outside range ["
                        + range.min() + ", " + range.max()
                        + "]; clamped to " + clamped;
                logger.warn(reason);
                return new ConfigValidationResult.Clamped<>(clamped, typedValue);
            }
        }

        return new ConfigValidationResult.Valid<>(typedValue);
    }
}
