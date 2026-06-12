/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.program.sql;

/**
 * A parsed network operation: the structured intent both SQL dialects compile down to, ready for the server to execute against the network. Minecraft-free so the parser is unit-tested in plain Java; the item is still a raw name string (resolved to a real item only when executed).
 */
public record SqlOperation(Verb verb, String item, long quantity, String source, String dest, int limit) {

    /** The operations the network understands, mirroring the SQL-style core of the mod. */
    public enum Verb {
        /** Read what the network holds (no items move). */
        QUERY,
        /** Pull items out of the network into the issuing computer. */
        SELECT,
        /** Push items from the issuing computer into the network. */
        INSERT,
        /** Move items from one server to another within the network. */
        MOVE,
        /** Craft items on the network. */
        CRAFT,
        /** Destroy items held by the network. */
        DELETE
    }

    public SqlOperation {
        if (item == null) {
            item = "";
        }
        if (source == null) {
            source = "";
        }
        if (dest == null) {
            dest = "";
        }
    }

    public static SqlOperation query(final String filter, final int limit) {
        return new SqlOperation(Verb.QUERY, filter, 0L, "", "", limit);
    }

    public static SqlOperation of(final Verb verb, final String item, final long quantity) {
        return new SqlOperation(verb, item, quantity, "", "", 0);
    }

    public boolean hasSource() {
        return !source.isEmpty();
    }

    public boolean hasDest() {
        return !dest.isEmpty();
    }
}
