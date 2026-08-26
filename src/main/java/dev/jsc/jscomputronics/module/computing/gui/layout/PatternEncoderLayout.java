/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.gui.layout;

import dev.jsc.jscomputronics.common.gui.layout.GuiLayout;

/**
 * Pure layout math for the Pattern Encoder's densest face — the PROCESSING tab: a 3x3 visible input grid, a 3x3
 * visible output grid, the centre machine/timeout controls, the shared media bay, the write button, and the
 * player inventory. Kept MC-free so an overlap/overflow test can run it; the constants mirror
 * {@code PatternEncoderScreen} (keep them in step).
 */
public final class PatternEncoderLayout {

    public static final int WIDTH = 200;
    public static final int HEIGHT = 218;

    private static final int SLOT = 18;
    private static final int IN_X = 8;
    private static final int IN_Y = 44;
    private static final int OUT_X = 138;
    private static final int OUT_Y = 44;
    private static final int CENTER_X = 70;
    private static final int MACHINE_Y = 44;
    private static final int CYCLE_Y = 58;
    private static final int TIMEOUT_Y = 82;
    private static final int MEDIA_X = 8;
    private static final int MEDIA_Y = 108;
    private static final int WRITE_X = 96;
    private static final int WRITE_Y = 109;
    private static final int WRITE_W = 96;
    private static final int WRITE_H = 14;
    private static final int INV_X = 8;
    private static final int INV_Y = 138;

    private PatternEncoderLayout() {
    }

    public static GuiLayout processing() {
        final GuiLayout layout = new GuiLayout(WIDTH, HEIGHT);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                layout.box("in_" + row + "_" + col, IN_X + col * SLOT, IN_Y + row * SLOT, SLOT, SLOT);
                layout.box("out_" + row + "_" + col, OUT_X + col * SLOT, OUT_Y + row * SLOT, SLOT, SLOT);
            }
        }
        layout.box("machine", CENTER_X, MACHINE_Y, 62, 12);
        layout.box("cycle", CENTER_X, CYCLE_Y, 62, 11);
        layout.box("timeout", CENTER_X, TIMEOUT_Y, 40, 12);
        layout.box("media", MEDIA_X, MEDIA_Y, SLOT, SLOT);
        layout.box("write", WRITE_X, WRITE_Y, WRITE_W, WRITE_H);
        layout.playerInventory(INV_X, INV_Y);
        // Tab labels at their real text width, so the audit catches a tab caption overflowing or colliding with
        // its neighbour — exactly the bug that reached the game (the original narrow tabs ran into each other).
        tabLabel(layout, "tabCrafting", 8, 50, 8);     // CRAFTING
        tabLabel(layout, "tabProcessing", 60, 58, 10); // PROCESSING
        tabLabel(layout, "tabMulti", 120, 72, 11);     // MULTI-STAGE
        return layout;
    }

    private static void tabLabel(final GuiLayout l, final String name, final int tabX, final int tabW,
                                 final int chars) {
        final int textW = Math.round(chars * GuiLayout.GLYPH_WIDTH);
        l.box(name, tabX + Math.max(0, (tabW - textW) / 2), 21, textW, 8);
    }
}
