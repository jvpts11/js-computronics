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
 * Geometry of the Pattern Encoder's bay panel, shared by its menu (the slot and inventory positions) and its
 * screen (what it draws around them). A pure layout so a unit test can prove nothing overlaps or overflows.
 *
 * <p>Top to bottom: the title, the media bay with four short lines beside it (link, era, medium, job), the
 * progress bar, the two buttons, then the player's inventory under its label. The panel is wider than the
 * inventory so the four lines have room at the small font without being cut.
 */
public final class PatternEncoderLayout {

    public static final int WIDTH = 200;
    public static final int HEIGHT = 194;

    public static final int SLOT = 18;
    public static final int TITLE_X = 8;
    public static final int TITLE_Y = 6;

    /** The media bay slot frame (the slot itself sits one pixel inside). */
    public static final int MEDIA_X = 8;
    public static final int MEDIA_Y = 20;

    /** The text column right of the bay: link, era, medium and job lines, at the small font. */
    public static final int INFO_X = 32;
    public static final int INFO_Y = 20;
    public static final int INFO_W = WIDTH - INFO_X - 8;
    public static final int LINE_H = 10;
    public static final float INFO_SCALE = 0.75f;
    /** How many characters one info line may hold before the screen clips it. */
    public static final int INFO_CHARS = 32;

    /** The progress bar under the info lines. */
    public static final int BAR_X = 8;
    public static final int BAR_Y = 62;
    public static final int BAR_W = WIDTH - 16;
    public static final int BAR_H = 7;

    /** The two buttons: eject on the left, cancel on the right. */
    public static final int BTN_Y = 75;
    public static final int BTN_H = 16;
    public static final int EJECT_X = 8;
    public static final int EJECT_W = 88;
    public static final int CANCEL_X = 104;
    public static final int CANCEL_W = 88;

    /** The player's inventory, centred under its label. */
    public static final int INV_X = (WIDTH - 9 * SLOT) / 2;
    public static final int INV_Y = 110;
    public static final int INV_LABEL_Y = INV_Y - 11;

    private PatternEncoderLayout() {
    }

    public static GuiLayout layout() {
        final GuiLayout layout = new GuiLayout(WIDTH, HEIGHT);
        layout.text("title", TITLE_X, TITLE_Y, 15, 1.0f);
        layout.box("media", MEDIA_X, MEDIA_Y, SLOT, SLOT);
        layout.text("link", INFO_X, INFO_Y, INFO_CHARS, INFO_SCALE);
        layout.text("era", INFO_X, INFO_Y + LINE_H, INFO_CHARS, INFO_SCALE);
        layout.text("medium", INFO_X, INFO_Y + LINE_H * 2, INFO_CHARS, INFO_SCALE);
        layout.text("status", INFO_X, INFO_Y + LINE_H * 3, INFO_CHARS, INFO_SCALE);
        layout.box("bar", BAR_X, BAR_Y, BAR_W, BAR_H);
        layout.box("eject", EJECT_X, BTN_Y, EJECT_W, BTN_H);
        layout.box("cancel", CANCEL_X, BTN_Y, CANCEL_W, BTN_H);
        layout.text("inventoryLabel", INV_X, INV_LABEL_Y, 9, 1.0f);
        layout.playerInventory(INV_X, INV_Y);
        return layout;
    }
}
