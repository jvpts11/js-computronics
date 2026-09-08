/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client.theme;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jstech.core.client.gui.theme.EraPalette;
import dev.jstech.core.gui.ColorContrast;
import org.junit.jupiter.api.Test;

/**
 * Guards the readability of every text-on-background pairing the Network Management Studio draws. The Studio
 * shipped unreadable text twice (light-grey query text on white, then heavy near-black), so these contrast
 * checks fail before such a regression reaches the game. Pure: the palette is plain data, no Minecraft.
 */
class NmsPaletteTest {

    private static final EraPalette P = NmsPalette.SSMS_LIGHT;
    private static final int WHITE = 0xFFFFFFFF;
    private static final double BODY = 4.5;       // readable body text
    private static final double SECONDARY = 3.0;  // labels / accent / status text

    @Test
    void typedQueryText_isReadableOnTheEditor() {
        // The exact case that broke: the query text (text) on the white editor (panel).
        assertReadable(P.text(), P.panel(), BODY);
    }

    @Test
    void mutedLabels_haveAtLeastModerateContrast() {
        assertReadable(P.dim(), P.panel(), SECONDARY);
    }

    @Test
    void accentText_isReadableOnPanel() {
        // Links and quantities (accent2) on the white grid.
        assertReadable(P.accent2(), P.panel(), SECONDARY);
    }

    @Test
    void statusBarText_isReadableOnTheBlueBar() {
        // White status text drawn on the system-blue status bar (accent).
        assertReadable(WHITE, P.accent(), SECONDARY);
    }

    @Test
    void selectedRowText_staysReadable() {
        // Text drawn over the light-blue selection / hover highlight.
        assertReadable(P.text(), P.hover(), BODY);
    }

    @Test
    void positiveStatusText_isReadableOnPanel() {
        // The green "online" / success text on a light panel.
        assertReadable(P.green(), P.panel(), SECONDARY);
    }

    private static void assertReadable(final int fg, final int bg, final double min) {
        final double ratio = ColorContrast.ratio(fg, bg);
        assertTrue(ratio >= min, String.format("contrast %.2f below %.1f for fg=%06X bg=%06X",
                ratio, min, fg & 0xFFFFFF, bg & 0xFFFFFF));
    }
}
