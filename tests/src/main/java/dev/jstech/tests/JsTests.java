/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Tech Series.
 */
package dev.jstech.tests;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * The test mod of the J's Tech Series: a development tool, never published and never to be installed. It
 * carries the GameTests, the client tests and the test kit for every mod, so a test may span several mods
 * and no shipped jar holds test code. It adds nothing to the game.
 */
@Mod(JsTests.MODID)
public final class JsTests {

    public static final String MODID = "jstests";

    public static final Logger LOGGER = LogUtils.getLogger();

    public JsTests(final IEventBus modEventBus, final ModContainer modContainer) {
        LOGGER.warn("J's Tech Series Tests {} loaded. This is a development-only test mod: it is not part of the"
                + " series, adds nothing to the game and must not be installed.", modContainer.getModInfo().getVersion());
    }
}
