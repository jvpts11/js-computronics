/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.config;

import dev.jstech.core.JsCore;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;

/**
 * Registers the server config on the core's mod container and pushes every loaded value, validated and
 * clamped, into the runtime balance. It listens for the load and the reload, so a file edited while the
 * server runs takes effect the moment the config library re-reads it.
 */
public final class CoreConfigBridge {

    private static final ConfigValidator VALIDATOR = new ConfigValidator(JsCore.LOGGER::warn);

    private CoreConfigBridge() {
    }

    public static void register(final IEventBus modEventBus, final ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, CoreServerConfig.SPEC, CoreServerConfig.FILE_NAME);
        modEventBus.addListener(CoreConfigBridge::onLoad);
        modEventBus.addListener(CoreConfigBridge::onReload);
    }

    private static void onLoad(final ModConfigEvent.Loading event) {
        apply(event.getConfig());
    }

    private static void onReload(final ModConfigEvent.Reloading event) {
        apply(event.getConfig());
    }

    private static void apply(final ModConfig config) {
        // Only react to our own spec; other mods' configs raise the same events.
        if (config.getSpec() != CoreServerConfig.SPEC) {
            return;
        }
        applyAll();
    }

    /** Validates the file's current values and pushes them into the balance; also what a reload does. */
    public static void applyAll() {
        for (final ConfigKey<?> key : CoreConfigKeys.registry().allKeys()) {
            final ConfigValidationResult<?> result = VALIDATOR.validate(key, CoreServerConfig.rawValue(key));
            CoreConfigKeys.apply(key, result.value());
        }
    }
}
