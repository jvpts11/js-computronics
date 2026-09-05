/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client.theme;

/**
 * The Network Management Studio's color palette, kept apart from {@link NmsThemes} so it stays pure (an
 * {@link EraPalette} is plain data, while {@link NmsThemes} drags in Minecraft through {@link EraTheme}).
 * That lets the palette's text/background contrast be unit-tested — the Studio shipped unreadable-text bugs
 * twice, so the readable-contrast guarantee now lives in a test over these exact values.
 */
public final class NmsPalette {

    private NmsPalette() {
    }

    /** The classic SSMS light skin: light-grey window, white panels, grey separators, system-blue accents. */
    public static final EraPalette SSMS_LIGHT = new EraPalette(
            0xFF6E6E6E, 0xFFECECEC, 0xFFEEEEEE, 0xFFFFFFFF, 0xFFC4C4C4, 0xFFD4D4D4,
            0xFFFFFFFF, 0xFFC4C4C4,
            0xFF007ACC, 0xFF0A6CBA,
            0xFF2E8B2E, 0xFFE6B800, 0xFFC0392B,
            0xFF1E1E1E, 0xFF8A8A8A,
            0xFFFFFFFF, 0xFF1E1E1E, 0xFFCCE8FF);
}
