/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.client.gui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Fitting a string into a width, and the dense small text a packed panel is written in.
 */
public final class Texts {

    /** The scale of the small text dense panels are written in. */
    public static final float SMALL = 0.85f;

    private Texts() {
    }

    /** Draws {@code text} at {@code scale}, anchored at its top-left corner. */
    public static void scaled(final GuiGraphics g, final Font font, final String text, final int x, final int y,
                              final float scale, final int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    /** Draws {@code text} at the small scale, anchored at its top-left corner. */
    public static void small(final GuiGraphics g, final Font font, final String text, final int x, final int y,
                             final int color) {
        scaled(g, font, text, x, y, SMALL, color);
    }

    /** The real width in pixels of {@code text} drawn at the small scale. */
    public static int smallWidth(final Font font, final String text) {
        return Math.round(font.width(text) * SMALL);
    }

    /** The font-unit width that fits in {@code pixels} real pixels at the small scale. */
    public static int smallFits(final int pixels) {
        return (int) (pixels / SMALL);
    }

    /** The string cut hard to {@code width}, without an ellipsis, never emptier than one character. */
    public static String trim(final Font font, final String s, final int width) {
        String out = s;
        while (out.length() > 1 && font.width(out) > width) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
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
