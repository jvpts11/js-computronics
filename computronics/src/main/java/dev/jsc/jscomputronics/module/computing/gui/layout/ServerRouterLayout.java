/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.gui.layout;

import dev.jstech.core.gui.layout.GuiLayout;

/**
 * Pure layout model for the Server Router screen, mirroring the positions drawn in
 * {@code ServerRouterScreen} so {@link #layout(int)} can be validated for overlap/overflow without
 * Minecraft. The section list grows one row per datacenter section, so the worst case ({@link
 * #MAX_SECTIONS}, one per block face other than the input) is what must still fit the panel.
 */
public final class ServerRouterLayout {

    public static final int WIDTH = 190;
    public static final int HEIGHT = 176;

    public static final int NAME_X = 8;
    public static final int NAME_Y = 36;
    public static final int NAME_H = 14;

    public static final int TILE_Y = 56;
    public static final int TILE_W = 84;
    public static final int TILE_H = 22;
    public static final int TILE1_X = 8;
    public static final int TILE2_X = 98;

    public static final int ROW_Y0 = 96;
    public static final int ROW_PITCH = 15;
    public static final int MODE_X = 108;
    public static final int MODE_W = 74;
    public static final int MODE_H = 12;

    /** A block has six faces; one is the auto-detected input, leaving at most five output sections. */
    public static final int MAX_SECTIONS = 5;

    private ServerRouterLayout() {
    }

    public static GuiLayout layout(final int sections) {
        final GuiLayout l = new GuiLayout(WIDTH, HEIGHT)
                .box("nameField", NAME_X, NAME_Y, WIDTH - 16, NAME_H)
                .box("inputTile", TILE1_X, TILE_Y, TILE_W, TILE_H)
                .box("racksTile", TILE2_X, TILE_Y, TILE_W, TILE_H);
        for (int i = 0; i < sections; i++) {
            l.box("modeBtn_" + i, MODE_X, ROW_Y0 + i * ROW_PITCH, MODE_W, MODE_H);
        }
        l.text("title", 12, 10, 13, 1.0f);                 // "SERVER ROUTER"
        l.text("tier", WIDTH - 12 - 12, 10, 2, 1.0f);      // "T3", right-aligned
        l.text("nameLabel", 10, 28, 4, 1.0f);              // "NAME"
        l.text("sectionsLabel", 10, 84, 8, 1.0f);          // "SECTIONS"
        // Each section row: face label, rack/server count, centered mode caption (longest = "ROUND-ROBIN").
        for (int i = 0; i < sections; i++) {
            l.text("rowMode_" + i, MODE_X + MODE_W / 2 - Math.round(11 * GuiLayout.GLYPH_WIDTH) / 2,
                    ROW_Y0 + i * ROW_PITCH + 3, 11, 1.0f);
        }
        return l;
    }
}
