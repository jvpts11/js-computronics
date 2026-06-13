/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.config;

import java.util.List;

/**
 * The canonical declarations of every config entry the mod loads, together with the shared {@link JscConfigRegistry}
 * that owns them. This is the single source of truth for which TOML paths exist and what values are legal; the
 * NeoForge {@code ModConfigSpec} bridge mirrors these declarations, and the {@link ConfigValidator} clamps or rejects
 * loaded values against them. Future keys (including the faction toggles) are added here and picked up by the bridge.
 */
public final class JscConfigKeys {

    /** Allowed values for {@link #SQL_DIALECT}; also the whitelist the bridge mirrors into the {@code ModConfigSpec}. */
    public static final List<String> SQL_DIALECT_VALUES = List.of("SIMPLE", "STANDARD");

    /** Default dialect; "SIMPLE" preserves the historical hardcoded behaviour until an operator changes it. */
    public static final String SQL_DIALECT_DEFAULT = "SIMPLE";

    private static final JscConfigRegistry REGISTRY = new JscConfigRegistry();

    /**
     * SQL dialect the Operation console (the {@code operation} CLI verb and the Network Management Studio) parses.
     * Whitelisted to {SIMPLE, STANDARD}; an unknown value falls back to the default.
     */
    public static final ConfigKey<String> SQL_DIALECT = REGISTRY.register(
            ConfigKey.whitelisted(
                    List.of("computing", "sql_dialect"),
                    SQL_DIALECT_DEFAULT,
                    SQL_DIALECT_VALUES));

    private JscConfigKeys() {
    }

    /** The shared registry holding every declared key. The bridge reads it to validate loaded values. */
    public static JscConfigRegistry registry() {
        return REGISTRY;
    }
}
