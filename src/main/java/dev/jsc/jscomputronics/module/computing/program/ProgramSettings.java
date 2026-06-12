/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.program;

import dev.jsc.jscomputronics.module.computing.program.sql.SqlDialect;

/**
 * Server-side settings for the program subsystem. For now this just holds the SQL dialect the {@code operation} command and the Network Management Studio parse — simple shorthand by default, switchable to real SQL. Wiring this to a TOML config key is a small follow-up; the value lives here so both consumers read one place.
 */
public final class ProgramSettings {

    private static volatile SqlDialect sqlDialect = SqlDialect.SIMPLE;

    private ProgramSettings() {
    }

    public static SqlDialect sqlDialect() {
        return sqlDialect;
    }

    public static void setSqlDialect(final SqlDialect dialect) {
        if (dialect != null) {
            sqlDialect = dialect;
        }
    }
}
