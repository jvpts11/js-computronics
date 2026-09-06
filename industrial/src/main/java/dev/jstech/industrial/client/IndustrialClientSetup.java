/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Industrial.
 */
package dev.jstech.industrial.client;

import dev.jstech.industrial.IndustrialModule;
import dev.jstech.industrial.JsIndustrial;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-only wiring for the Industrial module: binds each machine menu type to its screen so right-clicking a machine opens the correct GUI.
 */
@EventBusSubscriber(modid = JsIndustrial.MODID, value = Dist.CLIENT)
public final class IndustrialClientSetup {

    private IndustrialClientSetup() {
    }

    @SubscribeEvent
    public static void registerScreens(final RegisterMenuScreensEvent event) {
        event.register(IndustrialModule.MACERATOR_MENU.get(), MaceratorScreen::new);
        event.register(IndustrialModule.COAL_GENERATOR_MENU.get(), CoalGeneratorScreen::new);
        event.register(IndustrialModule.ELECTRIC_FURNACE_MENU.get(), ElectricFurnaceScreen::new);
        event.register(IndustrialModule.COMPRESSOR_MENU.get(), CompressorScreen::new);
    }
}
