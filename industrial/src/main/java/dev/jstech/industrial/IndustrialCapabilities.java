/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Industrial.
 */
package dev.jstech.industrial;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Exposes the Industrial machines' inventory and FE buffer as NeoForge block capabilities, so hoppers/pipes can move items and generators can push energy into them from any side.
 */
@EventBusSubscriber(modid = JsIndustrial.MODID)
public final class IndustrialCapabilities {

    private IndustrialCapabilities() {
    }

    @SubscribeEvent
    public static void register(final RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                IndustrialModule.MACERATOR_BE.get(),
                (be, side) -> be.getInventory());
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                IndustrialModule.MACERATOR_BE.get(),
                (be, side) -> be.getEnergy());

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                IndustrialModule.COAL_GENERATOR_BE.get(),
                (be, side) -> be.getInventory());
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                IndustrialModule.COAL_GENERATOR_BE.get(),
                (be, side) -> be.getEnergy());

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                IndustrialModule.ELECTRIC_FURNACE_BE.get(),
                (be, side) -> be.getInventory());
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                IndustrialModule.ELECTRIC_FURNACE_BE.get(),
                (be, side) -> be.getEnergy());

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                IndustrialModule.COMPRESSOR_BE.get(),
                (be, side) -> be.getInventory());
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                IndustrialModule.COMPRESSOR_BE.get(),
                (be, side) -> be.getEnergy());
    }
}
