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
 * Server-side settings for the program subsystem. It holds the SQL dialect the {@code operation} command and the Network Management Studio parse — simple shorthand by default, switchable to real SQL. The value is loaded from the server TOML config and pushed here on config load/reload, so both consumers read one place. The field is {@code volatile} because the config bridge writes it off the parse threads that read it.
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
