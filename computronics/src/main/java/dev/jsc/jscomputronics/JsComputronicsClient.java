/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only mod entry point for J's Computronics.
 */
@Mod(value = JsComputronics.MODID, dist = Dist.CLIENT)
public class JsComputronicsClient {

    public JsComputronicsClient(ModContainer container) {
        // Allow NeoForge to render a generic config screen for this mod.
        // Accessed via the Mods menu > J's Computronics > Config.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}