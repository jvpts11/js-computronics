/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client.os;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A program that runs inside a {@link DesktopWindow} on the {@link DesktopScreen}. The window manager
 * owns the chrome (title bar, close box, drag); the app only fills its inner content rectangle.
 */
public interface DesktopApp {

    /** The window title. */
    String title();

    /** The window's default content width in pixels. */
    int defaultWidth();

    /** The window's default content height in pixels. */
    int defaultHeight();

    /** The smallest width a resize may shrink this window to, so its content never collapses. */
    default int minWidth() {
        return 120;
    }

    /** The smallest height a resize may shrink this window to, so its content never collapses. */
    default int minHeight() {
        return 70;
    }

    /**
     * Hands the app the skin of the OS it is running on, each frame before {@link #renderContent}, so the app
     * can draw its content (panels, buttons, fields, tabs, lists) through the same per-OS primitives the window
     * chrome uses. An app that has been migrated to the skin overrides this and keeps the reference; an app not
     * yet migrated ignores it and keeps its old look.
     */
    default void applySkin(OsSkin skin) {
    }

    /**
     * Renders the app's content within the inner rectangle (already offset past the title bar and
     * the window border).
     */
    void renderContent(GuiGraphics graphics, Font font, int x, int y, int width, int height,
                       int mouseX, int mouseY, float partialTick);

    /** Handles a click inside the window body. Coordinates are desktop-local pixels. */
    default void mouseClicked(DesktopWindow window, double mouseX, double mouseY, int button) {
    }

    /** Handles the mouse being dragged with a button held (after a click in the body). Desktop-local. */
    default void mouseDragged(DesktopWindow window, double mouseX, double mouseY, int button) {
    }

    /** Handles the mouse button being released over this app's window. Desktop-local coordinates. */
    default void mouseReleased(DesktopWindow window, double mouseX, double mouseY, int button) {
    }

    /** Handles a typed character while this app's window is focused; returns true if consumed. */
    default boolean charTyped(char c) {
        return false;
    }

    /** Handles a key press while this app's window is focused; returns true if consumed. */
    default boolean keyPressed(int key, int scanCode, int modifiers) {
        return false;
    }

    /** Handles a mouse-wheel scroll over this app's window ({@code delta} &gt; 0 is up); true if consumed. */
    default boolean mouseScrolled(double delta) {
        return false;
    }

    /**
     * Draws hover tooltips, in a pass after {@link #renderContent} and the window chrome so they sit on
     * top of everything. The window manager calls this only while the cursor is over the content
     * rectangle; coordinates are the same absolute pixels passed to {@code renderContent}.
     */
    default void renderTooltip(GuiGraphics graphics, Font font, int x, int y, int width, int height,
                               int mouseX, int mouseY) {
    }
}
