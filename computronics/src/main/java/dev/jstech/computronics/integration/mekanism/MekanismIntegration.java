/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.integration.mekanism;

import dev.jstech.computronics.storage.ChemicalBridges;
import net.neoforged.fml.ModList;

/**
 * Soft integration with Mekanism: when the mod is present, its chemicals become network data through a
 * {@link MekanismChemicalBridge}. Nothing here touches a Mekanism class unless {@link #isLoaded()} is true,
 * so the mod runs unchanged without Mekanism.
 */
public final class MekanismIntegration {

    public static final String MOD_ID = "mekanism";

    private MekanismIntegration() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /** Registers the chemical bridge; a no-op when Mekanism is absent. */
    public static void bootstrap() {
        if (isLoaded()) {
            registerBridge();
        }
    }

    // Kept in its own method so the bridge class (and the Mekanism API behind it) is only loaded here.
    private static void registerBridge() {
        ChemicalBridges.register(new MekanismChemicalBridge());
    }
}
