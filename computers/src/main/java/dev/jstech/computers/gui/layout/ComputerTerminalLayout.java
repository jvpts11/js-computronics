/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.gui.layout;

import dev.jstech.core.gui.layout.GuiLayout;

/**
 * Pure layout model for the Monitor terminal, the most complex screen, with per-tab content. This models
 * the fixed frame shared by every tab (the tab rail, the content header, the item-grid toolbar/grid/
 * deposit bar, and the player inventory) and is the single source of the inventory slot positions, which
 * {@code ComputerTerminalMenu} consumes, so the validated layout covers the real slots. Per-tab content
 * (popups, sliders, the storage band) is drawn by the individual tab renderers and is not modeled here.
 *
 * <p>The terminal grows when its host is a Mainframe: the inventory drops by {@link #MAINFRAME_INV_DROP}
 * and the panel is that much taller, so both cases must be validated.
 */
public final class ComputerTerminalLayout {

    public static final int WIDTH = 244;
    public static final int BASE_HEIGHT = 230;
    public static final int MAINFRAME_INV_DROP = 22;

    public static final int RAIL_X = 4;
    public static final int RAIL_W = 56;
    public static final int TAB_Y0 = 6;

    public static final int CONTENT_X = 63;

    public static final int NET_X = 68;
    public static final int NET_COLS = 9;
    public static final int NET_ROWS = 4;
    public static final int NET_Y = 52;
    public static final int SLOT = 18;

    public static final int TOOLBAR_Y = 36;
    public static final int TOOLBAR_H = 12;

    public static final int DEPOSIT_Y = NET_Y + NET_ROWS * SLOT + 2;
    public static final int DEPOSIT_W = NET_COLS * SLOT - 2;
    public static final int DEPOSIT_H = 14;

    // Player inventory (the terminal centers it under the rail+content, not at the usual x=8).
    public static final int INV_X = 41;
    public static final int INV_Y = 148;
    public static final int HOTBAR_Y = 206;

    private ComputerTerminalLayout() {
    }

    /** The shared frame for the given host kind ({@code mainframe} drops the inventory and grows the panel). */
    public static GuiLayout layout(final boolean mainframe) {
        final int drop = mainframe ? MAINFRAME_INV_DROP : 0;
        final int height = BASE_HEIGHT + drop;
        final int invY = INV_Y + drop;
        final int railH = invY - TAB_Y0 - 2;
        final int contentW = WIDTH - CONTENT_X - 6;
        final GuiLayout l = new GuiLayout(WIDTH, height)
                .box("rail", RAIL_X, TAB_Y0, RAIL_W, railH)
                .box("contentHeader", CONTENT_X, 6, contentW, 16)
                .box("toolbar", NET_X, TOOLBAR_Y, NET_COLS * SLOT - 2, TOOLBAR_H)
                .box("deposit", NET_X, DEPOSIT_Y, DEPOSIT_W, DEPOSIT_H);
        for (int row = 0; row < NET_ROWS; row++) {
            for (int col = 0; col < NET_COLS; col++) {
                l.box("net_" + row + "_" + col, NET_X + col * SLOT, NET_Y + row * SLOT, SLOT, SLOT);
            }
        }
        l.playerInventory(INV_X, invY);
        return l;
    }
}
