/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.integration.jei;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.module.computing.client.AbstractComputerScreen;
import dev.jsc.jscomputronics.module.computing.client.os.DesktopScreen;
import dev.jsc.jscomputronics.module.computing.client.os.PatternStudioApp;
import dev.jsc.jscomputronics.module.computing.client.theme.MonitorFrameStyle;
import dev.jsc.jscomputronics.module.computing.menu.DesktopMenu;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The recipe viewer plugin. The monitor stays centred and the viewer's ingredient list fits itself into the
 * column beside it (the monitor body is an exclusion area), a recipe transferred from the viewer lands in the
 * Pattern Studio's bench or machine draft, and an ingredient dragged out of the list can be dropped straight
 * onto a Studio cell. All viewer types stay in this package, so the rest of the mod never depends on the
 * viewer being installed.
 */
@JeiPlugin
public final class JscJeiPlugin implements IModPlugin {

    @Nullable
    private static IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "jei_plugin");
    }

    @Override
    public void onRuntimeAvailable(final IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    /**
     * What the viewer makes of {@code screen}: whether its ingredient list shows, the gui rectangle it reads and
     * the areas it keeps clear. The client tests log it to show the list sitting beside the monitor.
     */
    public static String describeOverlay(@Nullable final Screen screen) {
        if (runtime == null || screen == null) {
            return "no viewer runtime";
        }
        final String props = runtime.getScreenHelper().getGuiProperties(screen)
                .map(p -> p.guiLeft() + "," + p.guiTop() + " " + p.guiXSize() + "x" + p.guiYSize()
                        + " on " + p.screenWidth() + "x" + p.screenHeight())
                .orElse("none");
        final List<String> exclusions = runtime.getScreenHelper().getGuiExclusionAreas(screen)
                .map(r -> r.getX() + "," + r.getY() + " " + r.getWidth() + "x" + r.getHeight())
                .toList();
        return "listDisplayed=" + runtime.getIngredientListOverlay().isListDisplayed()
                + " gui=" + props + " exclusions=" + exclusions;
    }

    @Override
    public void registerRecipeTransferHandlers(final IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new StudioBenchTransferHandler(registration.getTransferHelper()),
                RecipeTypes.CRAFTING);
        // Every other category (smelting, mod machines, ...) lands in the machine draft. The viewer prefers the
        // crafting handler above, so the universal one only sees non-crafting recipes.
        registration.addUniversalRecipeTransferHandler(
                new StudioProcessingTransferHandler(registration.getTransferHelper()));
    }

    @Override
    public void registerGuiHandlers(final IGuiHandlerRegistration registration) {
        // The monitor body (bezel and chin) is an exclusion area, so the ingredient list sits beside the monitor
        // instead of over its frame. The glass itself is the container's image rectangle, which the viewer
        // already keeps clear.
        registration.addGuiContainerHandler(DesktopScreen.class, new IGuiContainerHandler<DesktopScreen>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(final DesktopScreen screen) {
                return List.of(rect(screen.frameBounds()));
            }
        });
        registration.addGenericGuiContainerHandler(AbstractComputerScreen.class,
                new IGuiContainerHandler<AbstractComputerScreen<?>>() {
                    @Override
                    public List<Rect2i> getGuiExtraAreas(final AbstractComputerScreen<?> screen) {
                        return List.of(rect(screen.frameBounds()));
                    }
                });
        registration.addGhostIngredientHandler(DesktopScreen.class, new StudioGhostIngredientHandler());
    }

    private static Rect2i rect(final MonitorFrameStyle.Geometry geo) {
        return new Rect2i(geo.x(), geo.y(), geo.w(), geo.h());
    }

    /**
     * Whether the Studio is the front window of the desktop that owns {@code container}. While the player reads
     * recipes, the viewer's recipe screen covers the desktop and the desktop is that screen's parent, so the
     * transfer button has to look behind the viewer rather than at the current screen.
     */
    static boolean studioInFront(final DesktopMenu container) {
        final PatternStudioApp studio = PatternStudioApp.active();
        final Screen screen = desktopBehindTheViewer();
        return studio != null && screen instanceof DesktopScreen desktop && desktop.getMenu() == container
                && desktop.isFront(studio);
    }

    /** The current screen, or the screen under the viewer's own screen when that one is up. */
    @Nullable
    private static Screen desktopBehindTheViewer() {
        final Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof DesktopScreen || runtime == null) {
            return screen;
        }
        return runtime.getRecipesGui().getParentScreen().orElse(screen);
    }

    /** Whether a recipe transfer would reach the Studio right now: what the viewer's transfer button asks. */
    public static boolean studioTransferAllowed() {
        return desktopBehindTheViewer() instanceof DesktopScreen desktop && studioInFront(desktop.getMenu());
    }

    /** Opens the viewer's recipe screen on the recipes that make {@code result}; false without the viewer. */
    public static boolean showRecipesFor(final ItemStack result) {
        if (runtime == null) {
            return false;
        }
        runtime.getRecipesGui().show(runtime.getJeiHelpers().getFocusFactory()
                .createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, result));
        return true;
    }

    /** Whether one of the viewer's own screens (recipes, bookmarks) is the current screen. */
    public static boolean viewerScreenOpen() {
        final Screen screen = Minecraft.getInstance().screen;
        return screen != null && screen.getClass().getName().startsWith("mezz.jei.");
    }
}
