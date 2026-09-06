/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.uuid;

import java.util.UUID;
import java.util.Objects;

/**
 * Strongly-typed wrapper for the UUID of an entire network.
 */
public record NetworkUuid(UUID value) {

    public NetworkUuid {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static NetworkUuid random() {
        return new NetworkUuid(UUID.randomUUID());
    }

    public static NetworkUuid fromString(String s) {
        Objects.requireNonNull(s, "s must not be null");
        return new NetworkUuid(UUID.fromString(s));
    }

    public String asString() {
        return value.toString();
    }
}
