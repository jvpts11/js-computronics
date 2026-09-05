/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics;

import com.mojang.logging.LogUtils;
import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.registry.JscCreativeModeTabs;
import dev.jstech.core.config.JscConfigBridge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Main mod entry point for J's Computronics.
 */
@Mod(JsComputronics.MODID)
public class JsComputronics {

    public static final String MODID = "jsc";

    public static final Logger LOGGER = LogUtils.getLogger();

    public JsComputronics(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("J's Computronics {} loaded.", modContainer.getModInfo().getVersion());

        // Server config: registers the TOML spec on the mod container and pushes loaded values into the runtime
        // settings the Operation console reads (e.g. the SQL dialect).
        JscConfigBridge.register(modEventBus, modContainer);

        ComputingModule.register(modEventBus);
        JscCreativeModeTabs.register(modEventBus);

        // Soft integrations: each one checks for its mod and stays a no-op without it.
        dev.jstech.computronics.integration.mekanism.MekanismIntegration.bootstrap();
    }
}