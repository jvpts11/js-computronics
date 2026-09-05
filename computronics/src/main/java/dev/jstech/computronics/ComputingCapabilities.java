/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics;

import dev.jstech.computronics.JsComputronics;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Exposes the Computing module's block capabilities.
 */
@EventBusSubscriber(modid = JsComputronics.MODID)
public final class ComputingCapabilities {

    private ComputingCapabilities() {
    }

    @SubscribeEvent
    public static void register(final RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ComputingModule.TANK_BE.get(),
                (be, side) -> be.fluidHandler());
    }
}
