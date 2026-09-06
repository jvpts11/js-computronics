/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client.os;

import dev.jstech.core.client.gui.component.Button;
import dev.jstech.core.client.gui.component.Label;
import dev.jstech.core.client.gui.component.Popup;
import dev.jstech.core.client.gui.component.UiContext;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * A modal error dialog drawn on top of the whole desktop: a titled panel in the desktop's own skin, an
 * error icon, a wrapped message, and a single OK button. While it is open the desktop routes every click
 * and key to it and to nothing behind it, so the dialog truly blocks the surface it sits over. It is
 * dismissed by OK, Enter or Escape; a click outside it does nothing.
 *
 * <p>This is a pure client-side overlay: it owns no server state and is placed in desktop-local
 * coordinates (the same translated space the {@link DesktopScreen} draws its windows in).
 */
final class DesktopPopup extends Popup {

    private static final int WIDTH = 196;
    private static final int TITLE_H = 14;
    private static final int PADDING = 8;
    private static final int ICON = 18;
    private static final int LINE_H = 10;
    private static final int BTN_W = 52;
    private static final int BTN_H = 16;
    private static final int MAX_TEXT_WIDTH = WIDTH - PADDING * 2 - ICON - 6;

    private final List<Label> lines = new ArrayList<>();
    private final Button ok;

    DesktopPopup(final String title, final String message, final Font font) {
        super(title, WIDTH, 0);
        final List<String> wrapped = wrap(message, font);
        for (final String line : wrapped) {
            lines.add(add(new Label(line)));
        }
        ok = add(new Button("OK", this::close).setPrimary(true));
        final int bodyH = PADDING + Math.max(ICON, wrapped.size() * LINE_H) + PADDING + BTN_H + PADDING;
        setPreferredSize(WIDTH, TITLE_H + bodyH);
        setDim(0x80000000);
        setCloseOnOutsideClick(false);
        setLayouter(this::layoutContent);
        // The dialog exists only while it is shown: the desktop drops it once it closes.
        open();
    }

    private void layoutContent(final Popup p) {
        final int iconY = p.y() + TITLE_H + PADDING;
        final int textX = p.x() + PADDING + ICON + 6;
        // A single short line sits centred against the icon; more lines start level with its top.
        int textY = iconY + (lines.size() == 1 ? (ICON - 8) / 2 : 0);
        for (final Label line : lines) {
            line.setBounds(textX, textY, p.right() - PADDING - textX, 8);
            textY += LINE_H;
        }
        ok.setBounds(p.x() + (p.width() - BTN_W) / 2, p.bottom() - PADDING - BTN_H, BTN_W, BTN_H);
    }

    @Override
    public void render(final GuiGraphics g, final UiContext ctx) {
        super.render(g, ctx);
        drawErrorIcon(g, x() + PADDING, y() + TITLE_H + PADDING);
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            close();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    /** An 18x18 red error disc with a white X, square-pixel style. */
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
