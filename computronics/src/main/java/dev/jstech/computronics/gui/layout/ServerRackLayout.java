/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.gui.layout;

import dev.jstech.core.gui.layout.GuiLayout;

/**
 * Pure layout of the Server Rack screen: a rack-unit ruler down the left edge, one row per rack
 * unit holding the server slot and the five front-panel hotswap slots that belong to that row, a
 * per-unit power button, and the player inventory below. The menu places its slots from these
 * constants and the screen draws its frames from the same ones, so a moved control or a longer
 * caption is caught by the unit test before it reaches the game.
 *
 * <p>All coordinates are relative to the panel's top-left corner.
 */
public final class ServerRackLayout {

    // Eight rack rows of 18px plus a player inventory leave no room to spare: the cabinet summary
    // rides in the header (as the mock has it) instead of taking a line of its own, and the panel
    // lands just inside the project's 256px height ceiling.
    public static final int WIDTH = 244;
    public static final int HEIGHT = 254;

    public static final int HEADER_X = 6;
    public static final int HEADER_Y = 6;
    public static final int HEADER_W = 232;
    /** The header bar's own height, drawn by the theme. */
    public static final int HEADER_H = 17;

    public static final int ROWS = 8;
    public static final int ROW_Y0 = 25;
    public static final int ROW_PITCH = 18;
    public static final int SLOT = 18;

    /** The rack-unit ruler labels down the left edge. */
    public static final int RULER_X = 8;
    /** The server slot of each rack-unit row. */
    public static final int SERVER_X = 24;
    /** The five front-panel hotswap slots of each row, drawn edge to edge. */
    public static final int FRONT_X = 46;
    public static final int FRONT_SLOTS = 5;
    /** The per-row status text between the slots and the power button. */
    public static final int STATUS_X = 140;
    /** The per-unit power button, on the unit's top row. */
    public static final int PWR_X = 216;
    public static final int PWR_W = 20;
    public static final int PWR_H = 10;
    public static final int PWR_DY = 4;

    public static final int INV_X = 8;
    public static final int INV_Y = 174;
    public static final int HOTBAR_Y = INV_Y + 58;

    private ServerRackLayout() {
    }

    /**
     * How far an item sits inside the {@link #SLOT}-wide cell drawn for it. A slot cell is 18 px and the
     * item in it is 16, so the item is inset by one — the same relationship vanilla's own slot texture has.
     * The menu adds its slots at these inset coordinates; without it every item (and its hover box) sat a
     * pixel up and to the left of the bay drawn under it.
     */
    public static final int SLOT_INSET = 1;

    /** The y a cell of {@code row} is drawn at. */
    public static int rowY(final int row) {
        return ROW_Y0 + row * ROW_PITCH;
    }

    /** The x a front-panel cell of {@code column} is drawn at. */
    public static int frontSlotX(final int column) {
        return FRONT_X + column * SLOT;
    }

    /** Where the item of a row's server slot goes: inside the cell drawn at {@link #SERVER_X}. */
    public static int serverItemX() {
        return SERVER_X + SLOT_INSET;
    }

    /** Where the item of a front-panel slot goes: inside the cell drawn for that column. */
    public static int frontItemX(final int column) {
        return frontSlotX(column) + SLOT_INSET;
    }

    /** Where a row's items go: inside the cells drawn for that row. */
    public static int itemY(final int row) {
        return rowY(row) + SLOT_INSET;
    }

    /**
     * The full element layout at its densest (every row mounted and powered), ready for the
     * overlap/out-of-bounds validators.
     */
    public static GuiLayout layout() {
        final GuiLayout l = new GuiLayout(WIDTH, HEIGHT);
        for (int row = 0; row < ROWS; row++) {
            final int y = rowY(row);
            l.box("server" + row, SERVER_X, y, SLOT, SLOT);
            for (int column = 0; column < FRONT_SLOTS; column++) {
                l.box("front" + row + "_" + column, frontSlotX(column), y, SLOT, SLOT);
            }
            l.box("pwr" + row, PWR_X, y + PWR_DY, PWR_W, PWR_H);
            l.text("ruler" + row, RULER_X, y + 6, 2, 0.75f);          // "8U"
            l.text("status" + row, STATUS_X, y + 6, 10, 0.75f);       // "INCOMPLETE"
        }
        l.playerInventory(INV_X, INV_Y);
        l.text("title", 12, 11, 11, 1.0f);                            // "SERVER RACK"
        final int pillChars = 15;                                     // "8/8U - OFFLINE"
        l.text("statusPill", HEADER_X + HEADER_W - 6
                - Math.round(pillChars * GuiLayout.GLYPH_WIDTH), 11, pillChars, 1.0f);
        return l;
    }
}
