/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.config;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.module.computing.program.ProgramSettings;
import dev.jsc.jscomputronics.module.computing.program.sql.SqlDialect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;

/**
 * Connects the on-disk NeoForge config to the project config model and to the runtime settings that read it.
 *
 * <p>It registers {@link JscServerConfig#SPEC} as a SERVER config on the mod container, then listens for the config
 * being loaded and reloaded. On each event for our own spec it reads the raw value, routes it through the
 * {@link ConfigValidator} against the {@link JscConfigKeys} whitelist (clamp/whitelist house rule: a bad value never
 * crashes and always falls back to the default), and pushes the resolved dialect into {@link ProgramSettings}, which
 * is the single source of truth the {@code operation} CLI verb and the Network Management Studio already read.
 */
public final class JscConfigBridge {

    private static final ConfigValidator VALIDATOR =
            new ConfigValidator(message -> JsComputronics.LOGGER.warn("[config] {}", message));

    private JscConfigBridge() {
    }

    /**
     * Registers the server config spec on the mod container and subscribes the load/reload listeners on the mod bus.
     */
    public static void register(final IEventBus modEventBus, final ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, JscServerConfig.SPEC);
        modEventBus.addListener(JscConfigBridge::onLoad);
        modEventBus.addListener(JscConfigBridge::onReload);
    }

    private static void onLoad(final ModConfigEvent.Loading event) {
        apply(event.getConfig());
    }

    private static void onReload(final ModConfigEvent.Reloading event) {
        apply(event.getConfig());
    }

    private static void apply(final ModConfig config) {
        // Only react to our own spec; other mods' configs raise the same events.
        if (config.getSpec() != JscServerConfig.SPEC) {
            return;
        }
        final ConfigValidationResult<String> result =
                VALIDATOR.validate(JscConfigKeys.SQL_DIALECT, JscServerConfig.SQL_DIALECT.get());
        // The validator guarantees result.value() is one of the whitelisted strings, so valueOf is safe.
        ProgramSettings.setSqlDialect(SqlDialect.valueOf(result.value()));
    }
}
