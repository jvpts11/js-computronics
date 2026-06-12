/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.program.sql;

/**
 * The outcome of parsing one statement: either a {@link SqlOperation} to run, or an error message explaining what was wrong with the syntax. Never an exception, so callers print a clean diagnostic.
 */
public record SqlParseResult(SqlOperation operation, String error) {

    public static SqlParseResult ok(final SqlOperation operation) {
        return new SqlParseResult(operation, null);
    }

    public static SqlParseResult fail(final String error) {
        return new SqlParseResult(null, error);
    }

    public boolean ok() {
        return operation != null;
    }
}
