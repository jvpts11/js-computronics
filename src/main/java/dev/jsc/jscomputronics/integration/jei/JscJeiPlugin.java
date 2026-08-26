/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.integration.jei;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.module.computing.client.os.DesktopScreen;
import dev.jsc.jscomputronics.module.computing.client.theme.MonitorFrameStyle;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.client.gui.screens.Screen;
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
        // Every other category (smelting, mod machines, ...) lands in the PROCESSING draft. JEI prefers the
        // category-specific crafting handler above, so the universal one only sees non-crafting recipes.
        registration.addUniversalRecipeTransferHandler(new PatternEncoderProcessingTransferHandler());
    }

    @Override
    public void registerGuiHandlers(final IGuiHandlerRegistration registration) {
        // The Panes desktop is a plain Screen, not an AbstractContainerScreen, so JEI does not show its
        // ingredient list beside it on its own. Report the framed monitor's bounds so the overlay sits to the
        // right of the window instead of disappearing while a desktop OS is open.
        registration.addGuiScreenHandler(DesktopScreen.class, screen -> {
            final MonitorFrameStyle.Geometry b = screen.frameBounds();
            return new MonitorGuiProperties(
                    DesktopScreen.class, b.x(), b.y(), b.w(), b.h(), screen.width, screen.height);
        });
    }

    /** Reports a desktop screen's framed window bounds to JEI so its overlay lays out beside the monitor. */
    private record MonitorGuiProperties(
            Class<? extends Screen> screenClass,
            int guiLeft,
            int guiTop,
            int guiXSize,
            int guiYSize,
            int screenWidth,
            int screenHeight) implements IGuiProperties {
    }
}
