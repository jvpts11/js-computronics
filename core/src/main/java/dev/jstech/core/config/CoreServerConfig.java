/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The server-side TOML config spec, registered by {@code CoreConfigBridge} and living next to the world save.
 *
 * <p>It has no keys at present: the only server setting was the SQL dialect toggle, removed when the operation surface
 * became IQL (a single language). The empty {@code computing} category keeps the spec valid and gives a future server
 * setting an obvious home.
 */
public final class CoreServerConfig {

    public static final ModConfigSpec SPEC;

    static {
        final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("computing");
        builder.pop();
        SPEC = builder.build();
    }

    private CoreServerConfig() {
    }
}
