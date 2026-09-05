/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.config;

/**
 * The canonical declarations of every config entry the mod loads, together with the shared {@link JscConfigRegistry}
 * that owns them. This is the single source of truth for which TOML paths exist and what values are legal; the
 * NeoForge {@code ModConfigSpec} bridge mirrors these declarations, and the {@link ConfigValidator} clamps or rejects
 * loaded values against them. Future keys are added here and picked up by the bridge.
 *
 * <p>There are no server keys at present: the only one was the SQL dialect toggle, dropped when the operation surface
 * became IQL (a single language). The registry stays so a future key is a one-line addition.
 */
public final class JscConfigKeys {

    private static final JscConfigRegistry REGISTRY = new JscConfigRegistry();

    private JscConfigKeys() {
    }

    /** The shared registry holding every declared key. The bridge reads it to validate loaded values. */
    public static JscConfigRegistry registry() {
        return REGISTRY;
    }
}
