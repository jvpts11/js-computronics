/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.gui.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WindowGeometryTest {

    @Test
    void resolve_floatingNeverRendersBelowTheMinimum() {
        /*
         * The shipped bug: a window persisted below the (since-grown) minimum rendered a tiny content area, so
         * the Network Interactor's details panel was clipped to a few pixels and slid over the grid. The
         * resolved size MUST clamp up to the minimum; this test fails if that clamp is ever dropped again.
         */
        final WindowGeometry.Rect r = WindowGeometry.resolve(10, 20, 200, 100, 313, 158, false, 800, 600, 24);
        assertEquals(313, r.w(), "width clamps up to the minimum");
        assertEquals(158, r.h(), "height clamps up to the minimum");
        assertEquals(10, r.x(), "x is kept");
        assertEquals(20, r.y(), "y is kept");
    }

    @Test
    void resolve_floatingKeepsItsSizeWhenAtOrAboveTheMinimum() {
        final WindowGeometry.Rect r = WindowGeometry.resolve(10, 20, 400, 300, 313, 158, false, 800, 600, 24);
        assertEquals(400, r.w());
        assertEquals(300, r.h());
    }

    @Test
    void resolve_floatingClampsOnlyTheAxisThatIsTooSmall() {
        final WindowGeometry.Rect r = WindowGeometry.resolve(0, 0, 200, 300, 313, 158, false, 800, 600, 24);
        assertEquals(313, r.w(), "narrow width clamps to the minimum");
        assertEquals(300, r.h(), "tall enough height is kept");
    }

    @Test
    void resolve_maximizedFillsTheDesktopAboveTheTaskbar() {
        final WindowGeometry.Rect r = WindowGeometry.resolve(10, 20, 200, 100, 313, 158, true, 800, 600, 24);
        assertEquals(0, r.x());
        assertEquals(0, r.y());
        assertEquals(800, r.w(), "maximized width is the desktop width");
        assertEquals(576, r.h(), "maximized height is the desktop minus the taskbar");
    }

    @Test
    void resolve_maximizedStartsBelowATopPanel() {
        /*
         * A GNOME-style desktop reserves its bar at the top and nothing at the bottom: the maximized window
         * starts under the bar and runs to the bottom edge.
         */
        final WindowGeometry.Rect r = WindowGeometry.resolve(10, 20, 200, 100, 313, 158, true, 800, 600, 0, 24);
        assertEquals(0, r.x());
        assertEquals(24, r.y(), "maximized top is the top panel's bottom edge");
        assertEquals(800, r.w());
        assertEquals(576, r.h(), "maximized height is the desktop minus the top panel");
    }

    @Test
    void resolve_maximizedFitsBetweenTopAndBottomReserves() {
        final WindowGeometry.Rect r = WindowGeometry.resolve(0, 0, 10, 10, 10, 10, true, 800, 600, 24, 24);
        assertEquals(24, r.y());
        assertEquals(552, r.h(), "both reserves are subtracted");
    }

    @Test
    void scissor_addsThePoseTranslationToTheLocalRectangle() {
        /*
         * GuiGraphics.enableScissor ignores the pose, so a desktop app MUST add the pose translation to its
         * window-local scissor coords or the clip is offset from the drawn content, the bug that clipped the
         * Network Interactor's details text and grid cells in the wrong place. Absolute = pose-origin + local.
         */
        final WindowGeometry.Rect r = WindowGeometry.scissor(40, 50, 5, 6, 105, 56);
        assertEquals(45, r.x(), "x = poseX + x1");
        assertEquals(56, r.y(), "y = poseY + y1");
        assertEquals(100, r.w(), "width = x2 - x1");
        assertEquals(50, r.h(), "height = y2 - y1");
    }
}
