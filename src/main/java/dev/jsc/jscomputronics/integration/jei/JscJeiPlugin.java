/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.integration.jei;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.module.computing.client.CommandPromptScreen;
import dev.jsc.jscomputronics.module.computing.client.ComputerTerminalScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

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
        // A monitor screen is the computer's entire display: no ingredient overlay belongs beside it, and a
        // click outside the glass must never land on an invisible JEI list (looking up recipes while
        // "inside" a computer). Claiming the whole screen as an extra area makes JEI hide its overlay.
        // The Frames desktop is a plain Screen (not a container screen), so JEI already shows nothing there.
        registration.addGenericGuiContainerHandler(CommandPromptScreen.class, new FullScreenBlocker());
        registration.addGenericGuiContainerHandler(ComputerTerminalScreen.class, new FullScreenBlocker());
    }

    /** Claims the entire screen so JEI keeps its overlay (and its click zones) off a monitor screen. */
    private static final class FullScreenBlocker implements IGuiContainerHandler<AbstractContainerScreen<?>> {
        @Override
        public List<Rect2i> getGuiExtraAreas(final AbstractContainerScreen<?> screen) {
            return List.of(new Rect2i(0, 0, screen.width, screen.height));
        }
    }
}
