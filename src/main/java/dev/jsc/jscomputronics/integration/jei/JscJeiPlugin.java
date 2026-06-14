/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.integration.jei;

import dev.jsc.jscomputronics.JsComputronics;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.ResourceLocation;

/**
 * JEI mod plugin entry point for J's Computronics.
 *
 * Discovered automatically by JEI's ServiceLoader when JEI is present.
 * All integration code is confined to this package — no references to JEI types
 * exist anywhere else in the codebase, so soft-dep isolation is preserved.
 */
@JeiPlugin
public final class JscJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "jei_plugin");
    }

    @Override
    public void registerRecipeTransferHandlers(final IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(
                new PatternEncoderTransferHandler(),
                RecipeTypes.CRAFTING);
    }
}
