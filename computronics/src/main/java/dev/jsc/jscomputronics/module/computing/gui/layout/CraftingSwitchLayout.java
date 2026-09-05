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
 * Pure layout for the Crafting Switch screen, with no Minecraft dependency so {@link #layout()} can be
 * unit-tested. Master-detail: a left list of the six faces (five can host a machine, one carries the crafting
 * cable to the computer) and a right detail panel for the selected face — its detected machine, an editable
 * name, and an active toggle. The menu places the player inventory from these constants and the screen draws
 * its frame from the same ones, so screen, menu and test share one source of truth.
 *
 * <p>All coordinates are relative to the panel's top-left corner; the screen adds {@code leftPos}/{@code topPos}.
 */
public final class CraftingSwitchLayout {

    public static final int WIDTH = 200;
    public static final int HEIGHT = 198;

    public static final int FACES = 6;

    // Header.
    public static final int HEADER_X = 8;
    public static final int HEADER_Y = 7;
    public static final int HEADER_W = 184;

    // Left face list: six rows, one per face.
    public static final int LIST_X = 8;
    public static final int LIST_W = 88;
    public static final int FIRST_ROW_Y = 24;
    public static final int ROW_H = 13;
    public static final int ROW_BOX_H = 12;

    // Right detail panel.
    public static final int DETAIL_X = 100;
    public static final int DETAIL_W = 92;
    public static final int MACHINE_LABEL_Y = 26;
    public static final int NAME_LABEL_Y = 42;
    public static final int NAME_X = 104;
    public static final int NAME_Y = 52;
    public static final int NAME_W = 88;
    public static final int NAME_H = 13;
    public static final int ACTIVE_X = 104;
    public static final int ACTIVE_Y = 72;
    public static final int ACTIVE_W = 88;
    public static final int ACTIVE_H = 14;
    // Generic machine category button, below the active toggle.
    public static final int CATEGORY_X = 104;
    public static final int CATEGORY_Y = 89;
    public static final int CATEGORY_W = 88;
    public static final int CATEGORY_H = 14;

    // Player inventory, centered.
    public static final int INV_LABEL_Y = 106;
    public static final int INV_X = 19;
    public static final int INV_Y = 116;
    public static final int HOTBAR_Y = INV_Y + 58;

    private CraftingSwitchLayout() {
    }

    /** The y of face row {@code i}'s top edge. */
    public static int rowY(final int row) {
        return FIRST_ROW_Y + row * ROW_H;
    }

    /**
     * The full element layout for the overlap/out-of-bounds validators, using the longest captions. The face
     * rows and the detail controls are solid boxes; the face names and labels are text drawn over them.
     */
    public static GuiLayout layout() {
        final GuiLayout l = new GuiLayout(WIDTH, HEIGHT);
        for (int i = 0; i < FACES; i++) {
            l.box("faceRow" + i, LIST_X, rowY(i), LIST_W, ROW_BOX_H);
        }
        l.box("nameField", NAME_X, NAME_Y, NAME_W, NAME_H)
                .box("activeToggle", ACTIVE_X, ACTIVE_Y, ACTIVE_W, ACTIVE_H)
                .box("categoryBtn", CATEGORY_X, CATEGORY_Y, CATEGORY_W, CATEGORY_H)
                .playerInventory(INV_X, INV_Y);

        l.text("title", HEADER_X, HEADER_Y + 1, 15, 1.0f);              // "CRAFTING SWITCH"
        final int pillChars = 8;                                        // "UNLINKED" (longer than "LINKED")
        l.text("statusPill", HEADER_X + HEADER_W - Math.round(pillChars * GuiLayout.GLYPH_WIDTH),
                HEADER_Y + 1, pillChars, 1.0f);
        for (int i = 0; i < FACES; i++) {
            l.text("faceName" + i, LIST_X + 3, rowY(i) + 2, 13, 1.0f);  // face label + machine name
        }
        l.text("machineLabel", DETAIL_X + 4, MACHINE_LABEL_Y, 14, 1.0f);
        l.text("nameLabel", DETAIL_X + 4, NAME_LABEL_Y, 4, 1.0f);       // "NAME"
        l.text("activeValue", ACTIVE_X + 6, ACTIVE_Y + 4, 14, 1.0f);    // "Active: accepting"
        l.text("invLabel", INV_X, INV_LABEL_Y, 9, 1.0f);               // "INVENTORY"
        return l;
    }
}
