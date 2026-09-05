/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client.os;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * A modal error dialog drawn on top of the whole desktop, in the classic Windows mould: a square
 * (no rounded corners) panel with a title bar, an error icon, a wrapped message, and a single OK
 * button. While it is open the desktop must route every click and key to it and to nothing behind
 * it, so the dialog truly blocks the surface it sits over. It is dismissed by clicking OK or by
 * pressing Enter or Escape.
 *
 * <p>This is a pure client-side overlay: it owns no server state and is positioned in desktop-local
 * coordinates (the same translated space the {@link DesktopScreen} draws its windows in).
 */
final class DesktopPopup {

    private static final int WIDTH = 196;
    private static final int TITLE_H = 14;
    private static final int PADDING = 8;
    private static final int ICON = 18;
    private static final int LINE_H = 10;
    private static final int BTN_W = 52;
    private static final int BTN_H = 16;
    private static final int MAX_TEXT_WIDTH = WIDTH - PADDING * 2 - ICON - 6;

    private final String title;
    private final List<String> lines = new ArrayList<>();

    // Geometry is resolved against the surface size each render so the dialog stays centred even if
    // the monitor window is resized while it is open.
    private int x;
    private int y;
    private int height;
    private int btnX;
    private int btnY;

    DesktopPopup(final String title, final String message, final Font font) {
        this.title = title;
        this.lines.addAll(wrap(message, font));
    }

    /**
     * Draws the dialog centred on the {@code sw}x{@code sh} desktop surface (desktop-local
     * coordinates). The caller is responsible for dimming the surface behind it first.
     */
    void render(final GuiGraphics g, final Font font, final int sw, final int sh,
                final int mouseX, final int mouseY) {
        final int bodyH = PADDING + Math.max(ICON, lines.size() * LINE_H) + PADDING + BTN_H + PADDING;
        height = TITLE_H + bodyH;
        x = (sw - WIDTH) / 2;
        y = (sh - height) / 2;

        // Drop shadow then the square panel with a 3D bevel (light top/left, dark bottom/right).
        g.fill(x + 3, y + 3, x + WIDTH + 3, y + height + 3, 0x90000000);
        g.fill(x, y, x + WIDTH, y + height, 0xFFC0C0C0);
        g.fill(x, y, x + WIDTH, y + 1, 0xFFFFFFFF);
        g.fill(x, y, x + 1, y + height, 0xFFFFFFFF);
        g.fill(x, y + height - 1, x + WIDTH, y + height, 0xFF606060);
        g.fill(x + WIDTH - 1, y, x + WIDTH, y + height, 0xFF606060);

        // Title bar — the classic dark-blue gradient with the dialog title.
        g.fillGradient(x + 2, y + 2, x + WIDTH - 2, y + 2 + TITLE_H, 0xFF0A246A, 0xFF3A6EA5);
        g.drawString(font, title, x + 6, y + 4, 0xFFFFFFFF, false);

        // Error icon: a red disc with a white cross, drawn left of the message.
        final int iconX = x + PADDING;
        final int iconY = y + TITLE_H + PADDING;
        drawErrorIcon(g, iconX, iconY);

        // Message lines, vertically centred against the icon when there is only one short line.
        final int textX = iconX + ICON + 6;
        int textY = iconY + (lines.size() == 1 ? (ICON - 8) / 2 : 0);
        for (final String line : lines) {
            g.drawString(font, line, textX, textY, 0xFF000000, false);
            textY += LINE_H;
        }

        // OK button, centred horizontally near the bottom, with a raised bevel and a sunken look on hover.
        btnX = x + (WIDTH - BTN_W) / 2;
        btnY = y + height - PADDING - BTN_H;
        final boolean hover = mouseX >= btnX && mouseX <= btnX + BTN_W
                && mouseY >= btnY && mouseY <= btnY + BTN_H;
        g.fill(btnX, btnY, btnX + BTN_W, btnY + BTN_H, 0xFFC0C0C0);
        if (hover) {
            g.fill(btnX, btnY, btnX + BTN_W, btnY + 1, 0xFF606060);
            g.fill(btnX, btnY, btnX + 1, btnY + BTN_H, 0xFF606060);
            g.fill(btnX, btnY + BTN_H - 1, btnX + BTN_W, btnY + BTN_H, 0xFFFFFFFF);
            g.fill(btnX + BTN_W - 1, btnY, btnX + BTN_W, btnY + BTN_H, 0xFFFFFFFF);
        } else {
            g.fill(btnX, btnY, btnX + BTN_W, btnY + 1, 0xFFFFFFFF);
            g.fill(btnX, btnY, btnX + 1, btnY + BTN_H, 0xFFFFFFFF);
            g.fill(btnX, btnY + BTN_H - 1, btnX + BTN_W, btnY + BTN_H, 0xFF606060);
            g.fill(btnX + BTN_W - 1, btnY, btnX + BTN_W, btnY + BTN_H, 0xFF606060);
        }
        final int okW = font.width("OK");
        g.drawString(font, "OK", btnX + (BTN_W - okW) / 2, btnY + (BTN_H - 8) / 2, 0xFF000000, false);
    }

    /**
     * Returns true when the OK button was clicked, signalling the caller to close the dialog. The
     * caller treats every click as consumed regardless (the dialog is modal); only OK dismisses it.
     */
    boolean okClicked(final double localX, final double localY) {
        return localX >= btnX && localX <= btnX + BTN_W && localY >= btnY && localY <= btnY + BTN_H;
    }

    /** A 18x18 red error disc with a white X (Windows critical-stop icon, square-pixel style). */
    private static void drawErrorIcon(final GuiGraphics g, final int ox, final int oy) {
        // A filled circle approximated by rows, so it reads as a disc at this small size.
        final int[] half = {4, 6, 7, 8, 9, 9, 9, 9, 8, 7, 6, 4};
        final int cx = ox + ICON / 2;
        for (int i = 0; i < half.length; i++) {
            final int ry = oy + 3 + i;
            g.fill(cx - half[i], ry, cx + half[i], ry + 1, 0xFFD0021B);
        }
        // White X across the disc.
        for (int i = 0; i < 7; i++) {
            g.fill(cx - 3 + i, oy + 5 + i, cx - 2 + i, oy + 6 + i, 0xFFFFFFFF);
            g.fill(cx + 3 - i, oy + 5 + i, cx + 4 - i, oy + 6 + i, 0xFFFFFFFF);
        }
    }

    /** Greedily wraps {@code message} to {@link #MAX_TEXT_WIDTH} pixels, splitting on spaces. */
    private static List<String> wrap(final String message, final Font font) {
        final List<String> out = new ArrayList<>();
        final String[] words = message.split(" ");
        StringBuilder line = new StringBuilder();
        for (final String word : words) {
            final String candidate = line.length() == 0 ? word : line + " " + word;
            if (font.width(candidate) > MAX_TEXT_WIDTH && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        if (out.isEmpty()) {
            out.add(message);
        }
        return out;
    }
}
