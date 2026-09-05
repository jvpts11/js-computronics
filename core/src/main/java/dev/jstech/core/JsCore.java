/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * The shared library of the J's Tech Series. It has no gameplay of its own: it carries what every mod of
 * the series is built on, and every one of them requires it.
 */
@Mod(JsCore.MODID)
public final class JsCore {

    public static final String MODID = "jscore";

    public static final Logger LOGGER = LogUtils.getLogger();

    public JsCore(final IEventBus modEventBus, final ModContainer modContainer) {
        LOGGER.info("J's Core {} loaded.", modContainer.getModInfo().getVersion());
    }
}
