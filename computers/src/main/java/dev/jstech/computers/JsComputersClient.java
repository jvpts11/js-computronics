/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only mod entry point for J's Computers.
 */
@Mod(value = JsComputers.MODID, dist = Dist.CLIENT)
public class JsComputersClient {

    public JsComputersClient(ModContainer container) {
        /*
         * Allow NeoForge to render a generic config screen for this mod.
         * Accessed via the Mods menu > J's Computers > Config.
         */
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
