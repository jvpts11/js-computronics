/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.program.iql;

/**
 * The outcome of parsing one IQL statement: an {@link IqlOperation} to run, an {@link IqlDefinition} for
 * the IQL Engine to store/run (a CREATE/DROP/EXEC of a saved object), or an error message. Never an
 * exception, so the surfaces (Command Prompt, the studio) print a clean diagnostic instead of a stack
 * trace.
 *
 * <p>{@code position} is the 0-based index of the token where parsing stopped (it is {@link #NO_POSITION}
 * when there is no meaningful spot, e.g. an empty statement). Exactly one of {@code operation}/
 * {@code definition}/{@code error} is non-null on a given result; use {@link #isDefinition()} to tell an
 * action from a Layer-2 definition.
 */
public record IqlParseResult(IqlOperation operation, IqlDefinition definition, String error, int position) {

    /** Sentinel for "no particular token" (empty input, or a whole-statement error). */
    public static final int NO_POSITION = -1;

    public static IqlParseResult ok(final IqlOperation operation) {
        return new IqlParseResult(operation, null, null, NO_POSITION);
    }

    public static IqlParseResult okDefinition(final IqlDefinition definition) {
        return new IqlParseResult(null, definition, null, NO_POSITION);
    }

    public static IqlParseResult error(final String error, final int position) {
        return new IqlParseResult(null, null, error, position);
    }

    public boolean ok() {
        return error == null;
    }

    /** Whether this parsed to a Layer-2 definition (CREATE/DROP/EXEC) rather than an immediate action. */
    public boolean isDefinition() {
        return definition != null;
    }
}
