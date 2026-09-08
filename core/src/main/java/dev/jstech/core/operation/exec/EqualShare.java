/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.operation.exec;

/**
 * Splits a total into integer shares that sum to exactly the total, as evenly as possible.
 */
public final class EqualShare {

    private EqualShare() {
    }

    public static long[] split(final long total, final int parts) {
        if (parts <= 0) {
            return new long[0];
        }
        final long amount = Math.max(0L, total);
        final long base = amount / parts;
        final long remainder = amount % parts;
        final long[] shares = new long[parts];
        for (int i = 0; i < parts; i++) {
            shares[i] = base + (i < remainder ? 1L : 0L);
        }
        return shares;
    }
}
