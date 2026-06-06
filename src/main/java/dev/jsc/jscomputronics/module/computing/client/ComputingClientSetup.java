/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-only wiring for the Computing module: binds each computer menu to its screen.
 */
@EventBusSubscriber(modid = JsComputronics.MODID, value = Dist.CLIENT)
public final class ComputingClientSetup {

    private ComputingClientSetup() {
    }

    @SubscribeEvent
    public static void registerScreens(final RegisterMenuScreensEvent event) {
        event.register(ComputingModule.MAINFRAME_MENU.get(), MainframeScreen::new);
        event.register(ComputingModule.PERSONAL_COMPUTER_MENU.get(), PersonalComputerScreen::new);
        event.register(ComputingModule.SERVER_RACK_MENU.get(), ServerRackScreen::new);
        event.register(ComputingModule.SERVER_ASSEMBLY_MENU.get(), ServerAssemblyScreen::new);
    }
}
