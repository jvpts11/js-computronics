/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;

/**
 * The server-side TOML config spec. One value per world/server picks the server-wide grammar; the file lives next to
 * the world save. The allowed values and default are mirrored from {@link JscConfigKeys} so the project config model
 * and the on-disk file always agree on what is legal.
 *
 * <p>{@code defineInList} already rejects an out-of-list value at the NeoForge layer, but loaded values are still
 * routed through the {@link ConfigValidator} (see {@code JscConfigBridge}) so the project model stays the single
 * source of truth for the whitelist.
 */
public final class JscServerConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<String> SQL_DIALECT;

    static {
        final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("computing");
        // The allowed-values list must tolerate contains(null): during its correction pass NeoForge tests the
        // current value against this list, and at that point the value can be null. List.of(...) is an immutable
        // List12 whose contains(null) throws NPE, crashing server startup, so a null-tolerant ArrayList is used here.
        SQL_DIALECT = builder
                .comment("SQL dialect for the Operation console. Allowed: SIMPLE, STANDARD.")
                .defineInList("sql_dialect",
                        JscConfigKeys.SQL_DIALECT_DEFAULT,
                        new ArrayList<>(JscConfigKeys.SQL_DIALECT_VALUES));
        builder.pop();
        SPEC = builder.build();
    }

    private JscServerConfig() {
    }
}
