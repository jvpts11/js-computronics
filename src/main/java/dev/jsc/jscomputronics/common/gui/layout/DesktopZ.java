/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.gui.layout;

/**
 * The fixed Z (depth) of every desktop render layer, back-to-front. In 1.21.1 {@code GuiGraphics} batches
 * text and renders it last — over everything — so a back layer's text (e.g. a desktop icon's label) bleeds
 * on top of a front layer (an open window) when every layer draws at the same depth. Flushing the batch
 * between layers does not work outside a managed draw, so instead each layer draws at its own Z via
 * {@code pose().translate(0, 0, z)}; the depth buffer then keeps a back layer strictly behind a front one,
 * regardless of the text batch order. This mirrors how the rest of the mod's screens order their overlays.
 *
 * <p>The values are strictly increasing, back-to-front, with deliberate gaps: a layer that draws items with
 * {@code renderItem} needs ~{@link #ITEM_DEPTH} units of headroom before the next layer so the item model's
 * depth cannot poke through. {@link #TOOLTIP} is fixed at 400 because the vanilla tooltip renderer translates
 * +400 internally; the surrounding layers are arranged so a tooltip lands correctly above the windows and
 * the taskbar without anyone applying an extra translate to it. Pure constants holder so {@link #ordered()}
 * can be asserted strictly increasing (and item layers gap-respecting) in a unit test.
 */
public final class DesktopZ {

    /** The dimmed/blurred world and the wallpaper. */
    public static final int WALLPAPER = 0;
    /** Desktop icons and their labels — the back-most interactive layer, behind every window. */
    public static final int ICONS = 20;
    /** Open application windows (chrome + content, including item grids). */
    public static final int WINDOWS = 120;
    /** Real container-slot items for the focused window's inventory band, over that window. */
    public static final int INVENTORY = 240;
    /** The taskbar (background, Start button, task buttons, clock). */
    public static final int TASKBAR = 340;
    /** The Start menu and the desktop context menu. */
    public static final int MENU = 360;
    /** The icon drag drop-target outline and the drag ghost. */
    public static final int DRAG = 380;
    /** Hover tooltips. Fixed at 400 — the vanilla tooltip renderer translates +400 itself, so the desktop
     *  draws tooltips at the base pose (no extra translate) and they land here. */
    public static final int TOOLTIP = 400;
    /** The carried (cursor) item stack, at the mouse — above the tooltip. */
    public static final int CURSOR = 420;
    /** A modal dialog, over the whole desktop. */
    public static final int POPUP = 540;

    /** A {@code renderItem} model occupies roughly this much depth; item layers must leave this much headroom. */
    public static final int ITEM_DEPTH = 100;

    private DesktopZ() {
    }

    /** Every layer Z, back-to-front; the test asserts this is strictly increasing. */
    public static int[] ordered() {
        return new int[] {WALLPAPER, ICONS, WINDOWS, INVENTORY, TASKBAR, MENU, DRAG, TOOLTIP, CURSOR, POPUP};
    }

    /** The layers that draw items with {@code renderItem}; each must leave {@link #ITEM_DEPTH} before the next. */
    public static int[] itemLayers() {
        return new int[] {WINDOWS, INVENTORY, CURSOR};
    }

    /** The Z of the layer drawn immediately in front of {@code z} in {@link #ordered()}, or {@code z} if last. */
    public static int nextAbove(final int z) {
        final int[] all = ordered();
        for (int i = 0; i < all.length - 1; i++) {
            if (all[i] == z) {
                return all[i + 1];
            }
        }
        return z;
    }
}
