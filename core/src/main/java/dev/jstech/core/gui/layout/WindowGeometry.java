/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.gui.layout;

/**
 * Pure, Minecraft-free resolution of a desktop window's on-screen rectangle for one frame, so the rule that a
 * window NEVER renders below its app's minimum size lives in one unit-tested place instead of inline in the
 * Minecraft-bound window class (where it escaped the test source set and a missing clamp shipped a bug: a
 * window persisted at a stale small size rendered a tiny content area that clipped the Network Interactor's
 * details panel and slid it over the grid).
 */
public final class WindowGeometry {

    /** The resolved window rectangle (top-left and size) in desktop-local pixels. */
    public record Rect(int x, int y, int w, int h) {
    }

    private WindowGeometry() {
    }

    /**
     * Resolves where a window sits this frame. A maximized window fills the desktop above the taskbar. A
     * floating window keeps its position but its size is clamped UP to the app minimum, so it can never render
     * smaller than the minimum even if a saved/persisted geometry asks for less.
     *
     * @param x         the floating top-left x
     * @param y         the floating top-left y
     * @param w         the floating (possibly stale/too-small) width
     * @param h         the floating (possibly stale/too-small) height
     * @param minW      the app's minimum window width
     * @param minH      the app's minimum window height
     * @param maximized whether the window is maximized
     * @param screenW   the desktop width
     * @param screenH   the desktop height
     * @param taskbarH  the taskbar height reserved at the bottom when maximized
     * @return the resolved rectangle; for a floating window {@code w/h} are never below {@code minW/minH}
     */
    public static Rect resolve(final int x, final int y, final int w, final int h,
                               final int minW, final int minH, final boolean maximized,
                               final int screenW, final int screenH, final int taskbarH) {
        return resolve(x, y, w, h, minW, minH, maximized, screenW, screenH, taskbarH, 0);
    }

    /**
     * As {@link #resolve(int, int, int, int, int, int, boolean, int, int, int)}, for a desktop whose panel may
     * sit at the top: {@code workTop} pixels are reserved above the work area (a GNOME top bar) and
     * {@code taskbarH} below it (a bottom taskbar), so a maximized window fills exactly the band between them.
     */
    public static Rect resolve(final int x, final int y, final int w, final int h,
                               final int minW, final int minH, final boolean maximized,
                               final int screenW, final int screenH, final int taskbarH, final int workTop) {
        if (maximized) {
            return new Rect(0, workTop, screenW, Math.max(0, screenH - taskbarH - workTop));
        }
        return new Rect(x, y, Math.max(w, minW), Math.max(h, minH));
    }

    /**
     * The absolute scissor rectangle for content drawn at window-local {@code (x1,y1)-(x2,y2)} under a pose
     * translated by {@code (poseX, poseY)}. {@code GuiGraphics.enableScissor} ignores the pose in 1.21.1, so a
     * desktop app MUST add the pose translation itself or the clip is offset from the drawn content (clipping
     * text and cells in the wrong place, the bug that took hours to find). Centralised and tested so that trap
     * can never silently recur.
     */
    public static Rect scissor(final int poseX, final int poseY, final int x1, final int y1,
                               final int x2, final int y2) {
        return scissor(poseX, poseY, 1.0f, 1.0f, x1, y1, x2, y2);
    }

    /**
     * The same, under a pose that also scales: a desktop drawn smaller puts its content closer to its
     * origin, and a clip that ignored the scale would cut the wrong part of it. The rectangle is in the
     * pose's own units and comes back in screen units.
     */
    public static Rect scissor(final float poseX, final float poseY, final float scaleX, final float scaleY,
                               final int x1, final int y1, final int x2, final int y2) {
        final int sx1 = Math.round(poseX + x1 * scaleX);
        final int sy1 = Math.round(poseY + y1 * scaleY);
        final int sx2 = Math.round(poseX + x2 * scaleX);
        final int sy2 = Math.round(poseY + y2 * scaleY);
        return new Rect(sx1, sy1, sx2 - sx1, sy2 - sy1);
    }
}
