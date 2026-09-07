/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.program.iql;

import dev.jstech.core.operation.OperationPriority;

import java.util.Objects;

/**
 * A parsed IQL statement — the structured intent the server executes as a network Operation. Pure logic,
 * no Minecraft: the item stays a raw name string (resolved to a real item only when executed), so the
 * parser is unit-tested in plain Java. Optional parts use empty strings / {@code null} / sentinels rather
 * than {@code Optional} fields to keep the record flat; the accessors below say what is present.
 *
 * <p>{@code item} doubles as the schema object for read verbs ({@code QUERY items}). {@code where} is the
 * {@code WHERE} filter (which items are touched); {@code guard} is the {@code IF} guard (whether the
 * action runs at all). {@code orderBy} names a sort field with {@code orderByDescending} for its
 * direction, and {@code limit} caps the rows ({@link #NO_LIMIT} when uncapped). {@code priority} is the
 * scheduling level an action asks for ({@code PRIORITY HIGH}); the default when the clause is absent.
 */
public record IqlOperation(IqlVerb verb,
                           long quantity,
                           String item,
                           String from,
                           String to,
                           IIqlCondition where,
                           IIqlCondition guard,
                           String orderBy,
                           boolean orderByDescending,
                           int limit,
                           OperationPriority priority) {

    /** Quantity sentinel for {@code ALL}. */
    public static final long ALL = -1L;

    /** Quantity sentinel for "not specified" (e.g. a bare {@code SELECT cobblestone} or a read). */
    public static final long NONE = 0L;

    /** Limit sentinel for "no cap". */
    public static final int NO_LIMIT = 0;

    /** Item wildcard for "any item" — matches every item type (e.g. {@code MOVE * FROM A TO B}). */
    public static final String ANY_ITEM = "*";

    /** Whether {@link #item} is the {@code *} wildcard (every item type) rather than a concrete item. */
    public boolean isAnyItem() {
        return ANY_ITEM.equals(item);
    }

    public IqlOperation {
        Objects.requireNonNull(verb, "verb must not be null");
        if (item == null) {
            item = "";
        }
        if (from == null) {
            from = "";
        }
        if (to == null) {
            to = "";
        }
        if (orderBy == null) {
            orderBy = "";
        }
        if (priority == null) {
            priority = OperationPriority.DEFAULT;
        }
    }

    /** A bare action: a verb, a quantity ({@link #ALL}/{@link #NONE} or a count), and an item. */
    public static IqlOperation action(final IqlVerb verb, final long quantity, final String item) {
        return new IqlOperation(verb, quantity, item, "", "", null, null, "", false, NO_LIMIT,
                OperationPriority.DEFAULT);
    }

    /** Whether the statement asked for a level other than the default. */
    public boolean hasPriority() {
        return priority != OperationPriority.DEFAULT;
    }

    public boolean hasFrom() {
        return !from.isEmpty();
    }

    public boolean hasTo() {
        return !to.isEmpty();
    }

    public boolean hasWhere() {
        return where != null;
    }

    public boolean hasGuard() {
        return guard != null;
    }

    public boolean hasOrderBy() {
        return !orderBy.isEmpty();
    }

    public boolean hasLimit() {
        return limit != NO_LIMIT;
    }
}
