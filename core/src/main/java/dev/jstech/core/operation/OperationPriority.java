/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.operation;

import java.util.Locale;
import java.util.Optional;

/**
 * Scheduling priority of an Operation. The Mainframe hands its queue slots to the highest level first and
 * keeps submission order inside a level; a manual request and an automation job both start at
 * {@link #MEDIUM} unless the requester says otherwise.
 */
public enum OperationPriority {
    LOW("LOW"),
    MEDIUM_LOW("MED-"),
    MEDIUM("MED"),
    MEDIUM_HIGH("MED+"),
    HIGH("HIGH");

    /** The level every Operation starts at when the requester does not choose one. */
    public static final OperationPriority DEFAULT = MEDIUM;

    private final String label;

    OperationPriority(final String label) {
        this.label = label;
    }

    /** A four-character tag for dense views (task lists, dialogs). */
    public String label() {
        return label;
    }

    /** The next level up, saturating at {@link #HIGH}. */
    public OperationPriority raise() {
        return values()[Math.min(values().length - 1, ordinal() + 1)];
    }

    /** The next level down, saturating at {@link #LOW}. */
    public OperationPriority lower() {
        return values()[Math.max(0, ordinal() - 1)];
    }

    /** The level at {@code ordinal}, clamped into range so a stale byte from disk or the wire never throws. */
    public static OperationPriority byOrdinal(final int ordinal) {
        final OperationPriority[] levels = values();
        return levels[Math.max(0, Math.min(levels.length - 1, ordinal))];
    }

    /**
     * Resolves a keyword as typed in a statement or a command, case-insensitively: the enum names, their
     * hyphenated spellings ({@code MEDIUM-HIGH}), and {@code NORMAL} as an alias of {@link #MEDIUM}.
     */
    public static Optional<OperationPriority> fromKeyword(final String keyword) {
        if (keyword == null) {
            return Optional.empty();
        }
        final String normalized = keyword.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (normalized.equals("NORMAL")) {
            return Optional.of(MEDIUM);
        }
        for (final OperationPriority level : values()) {
            if (level.name().equals(normalized)) {
                return Optional.of(level);
            }
        }
        return Optional.empty();
    }
}
