/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.client.gui.component;

import net.minecraft.client.gui.Font;

/**
 * Fitting a string into a width, the two ways a control needs it.
 */
public final class Texts {

    private Texts() {
    }

    /** The string, or as much of its start as fits followed by "..": what a label shows of a long value. */
    public static String clip(final Font font, final String s, final int width) {
        if (font.width(s) <= width) {
            return s;
        }
        String out = s;
        while (!out.isEmpty() && font.width(out + "..") > width) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "..";
    }

    /** As much of the string's END as fits: what a field being typed in shows, the caret always in view. */
    public static String tail(final Font font, final String s, final int width) {
        String out = s;
        while (!out.isEmpty() && font.width(out) > width) {
            out = out.substring(1);
        }
        return out;
    }
}
