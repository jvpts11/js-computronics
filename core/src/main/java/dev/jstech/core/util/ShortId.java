/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.util;

/**
 * Renders a short, human-readable prefix of a UUID string for diagnostics and GUI labels (e.g. {@code "SRV-3f9a2b"}). Keeps a single canonical form so node/network identifiers read the same everywhere.
 */
public final class ShortId {

    private static final int PREFIX_LENGTH = 6;

    private ShortId() {
    }

    /**
     * Returns the first few hex characters of a UUID string, or the whole string if it is shorter than the prefix length.
     */
    public static String of(final String uuid) {
        return uuid.length() >= PREFIX_LENGTH ? uuid.substring(0, PREFIX_LENGTH) : uuid;
    }
}
