/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.network.payload;

/**
 * Pure-data carrier for {@link PingPayload}, free of any Minecraft dependency so its invariants can be unit-tested in the test source set (which does not have the MC client on its compile classpath).
 */
public record PingData(long timestamp) {

    public PingData {
        if (timestamp < 0) {
            throw new IllegalArgumentException(
                    "timestamp must be >= 0; got " + timestamp);
        }
    }
}