/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.uuid;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly-typed wrapper for the UUID of a network node.
 */
public record NodeUuid(UUID value) {

    public NodeUuid {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static NodeUuid random() {
        return new NodeUuid(UUID.randomUUID());
    }

    public static NodeUuid fromString(String s) {
        Objects.requireNonNull(s, "s must not be null");
        return new NodeUuid(UUID.fromString(s));
    }

    public String asString() {
        return value.toString();
    }
}
