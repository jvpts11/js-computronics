/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.gui.layout;

/**
 * The fixed Z (depth) of every desktop render layer, back-to-front. In 1.21.1 {@code GuiGraphics} batches
 * text and renders it last (over everything), so a back layer's text (e.g. a desktop icon's label) bleeds
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
    /** Desktop icons and their labels, the back-most interactive layer, behind every window. */
    public static final int ICONS = 20;
    /** The back-most open window. Each further window sits {@link #WINDOW_STEP} in front; see {@link #windowZ}. */
    public static final int WINDOWS = 40;
    /** Real container-slot items for the focused window's inventory band, over every window. */
    public static final int INVENTORY = 300;
    /** The taskbar (background, Start button, task buttons, clock). */
    public static final int TASKBAR = 340;
    /** The Start menu and the desktop context menu. */
    public static final int MENU = 360;
    /** The icon drag drop-target outline and the drag ghost. */
    public static final int DRAG = 380;
    /** Hover tooltips. Fixed at 400, because the vanilla tooltip renderer translates +400 itself, so the desktop
     *  draws tooltips at the base pose (no extra translate) and they land here. */
    public static final int TOOLTIP = 400;
    /** The carried (cursor) item stack, at the mouse, above the tooltip. */
    public static final int CURSOR = 420;
    /** A modal dialog, over the whole desktop. */
    public static final int POPUP = 540;

    /** A {@code renderItem} model occupies roughly this much depth; item layers must leave this much headroom. */
    public static final int ITEM_DEPTH = 100;

    /*
     * windows get a depth band each
     *
     * An item is not flat: renderItem lifts the model +150 and it spans about ±8 around that, so an item
     * drawn by a window whose chrome sits at Z ends up ~150 in FRONT of it. With every window drawn at one
     * shared Z, a background window's items therefore painted over the window in front of them (and its
     * items over theirs). So each window gets its own band, and an item drawn inside a window is pushed
     * back into that band (near its own chrome) instead of floating 150 above the whole desktop.
     */

    /** The depth reserved for one window: its chrome, the items it draws, and their counts. */
    public static final int WINDOW_STEP = 32;
    /** How many windows get a band of their own; any older ones share the back-most band. */
    public static final int WINDOW_BANDS = 8;
    /** What {@code GuiGraphics.renderItem} adds internally before drawing the model. */
    public static final int ITEM_LIFT = 150;
    /** What {@code GuiGraphics.renderItemDecorations} adds internally before drawing the count. */
    public static final int DECORATION_LIFT = 200;
    /** Where an item drawn inside a window sits above that window's own Z (the model spans about ±8 of it). */
    public static final int BAND_ITEM = 12;
    /** Where an item's count sits above its window's own Z: clear of the model's front face. */
    public static final int BAND_COUNT = 24;

    private DesktopZ() {
    }

    /**
     * The Z of one open window, given its index in the back-to-front list and how many windows are open.
     * The front-most window always gets the front-most band; when more windows are open than there are
     * bands, the oldest ones share the back-most one.
     */
    public static int windowZ(final int indexFromBack, final int windowCount) {
        final int overflow = Math.max(0, windowCount - WINDOW_BANDS);
        final int band = Math.max(0, Math.min(indexFromBack - overflow, WINDOW_BANDS - 1));
        return WINDOWS + band * WINDOW_STEP;
    }

    /** Pose offset to apply before {@code renderItem} so the model lands in the current window's band. */
    public static int itemOffset() {
        return BAND_ITEM - ITEM_LIFT;
    }

    /** Pose offset to apply before {@code renderItemDecorations} so the count lands in the same band. */
    public static int countOffset() {
        return BAND_COUNT - DECORATION_LIFT;
    }

    /** The front-most depth any window band can reach, counts included. */
    public static int windowsTop() {
        return WINDOWS + (WINDOW_BANDS - 1) * WINDOW_STEP + WINDOW_STEP;
    }

    /** Every layer Z, back-to-front; the test asserts this is strictly increasing. */
    public static int[] ordered() {
        return new int[] {WALLPAPER, ICONS, WINDOWS, INVENTORY, TASKBAR, MENU, DRAG, TOOLTIP, CURSOR, POPUP};
    }

    /**
     * The layers that draw items at the raw {@code renderItem} depth, so each must leave {@link #ITEM_DEPTH}
     * before the next. Windows and the inventory band are not among them: they push their items back into
     * their own band with {@link #itemOffset()}, which is what keeps one window's items off another's face.
     */
    public static int[] itemLayers() {
        return new int[] {CURSOR};
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
