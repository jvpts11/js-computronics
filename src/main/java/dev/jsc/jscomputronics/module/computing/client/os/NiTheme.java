/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client.os;

import net.minecraft.resources.ResourceLocation;

/**
 * The Network Interactor's content palette for one Frames era. The window chrome (title bar, border) is
 * already skinned by the desktop's {@link DesktopTheme}; this carries the colors the app paints inside:
 * the body, tab strip, search/sort fields, the bevelled inventory band and details panel, and the status
 * line. The three instances mirror the approved {@code ni-themes-mock}: a bevelled grey 95, a Luna-cream
 * XP, and a flat light 11.
 *
 * @param windowBg    the app body background
 * @param field       a sunken field background (search box, slot wells)
 * @param fieldText   placeholder/sort text inside a field
 * @param edge        a 1px field/panel border
 * @param text        primary text
 * @param dim         secondary/dim text
 * @param bandFill    the inventory band and details panel fill
 * @param bandHi      the band/panel top-left bevel highlight
 * @param bandLo      the band/panel bottom-right bevel shadow
 * @param tabAccent   the selected tab's label + underline
 * @param tabText     an unselected tab's label
 * @param sectionText a details-panel section header (STORED / TAGS / COMPONENTS)
 * @param statusOn    the status line when the Mainframe is online
 * @param statusOff   the status line when the Mainframe is offline
 */
public record NiTheme(int windowBg, int field, int fieldText, int edge, int text, int dim,
                      int bandFill, int bandHi, int bandLo, int tabAccent, int tabText,
                      int sectionText, int statusOn, int statusOff) {

    /** Frames 95: bevelled grey, navy selected tab, dark-green status. */
    public static final NiTheme WIN95 = new NiTheme(
            0xFFC3C7CB, 0xFFFFFFFF, 0xFF555555, 0xFF808080, 0xFF1A1A1A, 0xFF5A5E63,
            0xFFB6BABF, 0xFFFFFFFF, 0xFF808080, 0xFF00007B, 0xFF303030,
            0xFF303030, 0xFF1D4D1D, 0xFF9A4A4A);

    /** Frames XP (Luna): cream body, white fields, blue section headers, green selected tab. */
    public static final NiTheme XP = new NiTheme(
            0xFFECE9D8, 0xFFFFFFFF, 0xFF666666, 0xFFACA899, 0xFF2A2A2A, 0xFF6A6657,
            0xFFFBFBF7, 0xFFFFFFFF, 0xFFACA899, 0xFF1A9A2A, 0xFF303030,
            0xFF0A51B0, 0xFF2A5A2A, 0xFF9A4A4A);

    /** Frames 11: flat light, rounded look, system-blue accent. */
    public static final NiTheme WIN11 = new NiTheme(
            0xFFF3F3F3, 0xFFFFFFFF, 0xFF777777, 0xFFDCDCDC, 0xFF1B1B1B, 0xFF8A8A8A,
            0xFFFAFAFA, 0xFFFFFFFF, 0xFFE6E6E6, 0xFF0067C0, 0xFF555555,
            0xFF0067C0, 0xFF2A6A2A, 0xFF9A4A4A);

    /** The content palette for the given OS id, mirroring {@link DesktopTheme#forOs}. */
    public static NiTheme forOs(final ResourceLocation osId) {
        return switch (osId.getPath()) {
            case "frames_xp" -> XP;
            case "frames_11" -> WIN11;
            default -> WIN95;
        };
    }
}
