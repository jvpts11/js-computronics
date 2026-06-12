/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.program.sql;

/**
 * Which grammar the operation parser accepts. The server config picks one; both compile to the same {@link SqlOperation}.
 */
public enum SqlDialect {
    /** Friendly shorthand, e.g. {@code SELECT 1000 Cobblestone FROM Server A}. */
    SIMPLE,
    /** A subset of real SQL over a {@code network(item, quantity, server)} table. */
    STANDARD
}
