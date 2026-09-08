/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.program.iql;

import java.util.Optional;

/**
 * The verbs the IQL grammar exposes. Each one maps to a network Operation when executed; this enum is
 * the pure-logic vocabulary the parser produces, so it carries no Minecraft type. {@code SHOW} is not a
 * separate verb; it is accepted as an alias of {@link #QUERY} (both read without moving items).
 */
public enum IqlVerb {
    /** Read/inspect, without moving items. */
    QUERY,
    /** Extract items from the network into the computer that runs the query. */
    SELECT,
    /** Import items into the network (from the computer, or an external source). */
    INSERT,
    /** Export items from the network to an external destination. */
    DELETE,
    /** Move items between two internal locations within the network. */
    MOVE,
    /** Destroy items (trash), or drop a saved definition. */
    DROP,
    /** Craft items on the network. */
    CRAFT,
    /** Count matching items. */
    COUNT,
    /** Reserve items or resources. */
    LOCK,
    /** Release a reservation. */
    UNLOCK,
    /** Index maintenance: refresh statistics. */
    ANALYZE,
    /** Index maintenance: compact. */
    VACUUM,
    /** Index maintenance: rebuild. */
    REINDEX;

    /** Resolves a keyword to a verb, case-insensitively; {@code SHOW} is an alias of {@link #QUERY}. */
    public static Optional<IqlVerb> fromKeyword(final String keyword) {
        if (keyword == null) {
            return Optional.empty();
        }
        final String trimmed = keyword.trim();
        if (trimmed.equalsIgnoreCase("SHOW")) {
            return Optional.of(QUERY);
        }
        for (final IqlVerb verb : values()) {
            if (verb.name().equalsIgnoreCase(trimmed)) {
                return Optional.of(verb);
            }
        }
        return Optional.empty();
    }
}
