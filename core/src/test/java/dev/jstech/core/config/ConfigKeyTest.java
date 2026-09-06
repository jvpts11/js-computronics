/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigKeyTest {

    @Test
    void constructor_rejectsNonNumericKeyWithNumericRange() {
        assertThrows(IllegalArgumentException.class, () -> new ConfigKey<>(
                List.of("a"), Boolean.class, Boolean.TRUE,
                Optional.<ConfigKeyRange<?>>of(new ConfigKeyRange<>(0, 10))));
    }

    @Test
    void constructor_rejectsRangeBoundsOfAnotherNumericType() {
        assertThrows(IllegalArgumentException.class, () -> new ConfigKey<>(
                List.of("a"), Integer.class, 5,
                Optional.<ConfigKeyRange<?>>of(new ConfigKeyRange<>(0L, 10L))));
    }

    @Test
    void ranged_acceptsCoherentBounds() {
        final ConfigKey<Integer> key = ConfigKey.ranged(
                List.of("a"), Integer.class, 5, new ConfigKeyRange<>(0, 10));
        assertEquals(5, key.defaultValue());
        assertEquals(Optional.of(new ConfigKeyRange<>(0, 10)), key.range());
    }
}
