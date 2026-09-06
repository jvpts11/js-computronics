/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.config;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;

/**
 * Connects the on-disk NeoForge config to the mod: it registers {@link CoreServerConfig#SPEC} as a SERVER config on the
 * mod container and listens for it being loaded and reloaded.
 *
 * <p>There are no server keys to read yet — the only one was the SQL dialect toggle, removed when the operation surface
 * became IQL (a single language). The load/reload hooks stay so a future key is wired in one place, routed through the
 * {@link ConfigValidator} against the {@link CoreConfigKeys} whitelist (clamp/whitelist house rule: a bad value never
 * crashes and always falls back to the default).
 */
public final class CoreConfigBridge {

    private CoreConfigBridge() {
    }

    /**
     * Registers the server config spec on the mod container and subscribes the load/reload listeners on the mod bus.
     */
    public static void register(final IEventBus modEventBus, final ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, CoreServerConfig.SPEC);
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
        // Only react to our own spec; other mods' configs raise the same events. No keys to read yet, so this is a
        // no-op until a server setting is added back here.
        if (config.getSpec() != CoreServerConfig.SPEC) {
            return;
        }
    }
}
