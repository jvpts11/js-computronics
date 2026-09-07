/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.client.gui.skin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The look every component draws through: the colours of a surface and the primitives a control is made of.
 * A skin is a design, not a palette: the computing mod's desktops each implement one in their own shape
 * language, and a component never paints a pixel of chrome itself, so the same button, field or list looks
 * like a Frames 95 control on one machine and like a flat one on another.
 */
public interface ISkin {

    // colours

    /** Primary text. */
    int text();

    /** Secondary text: captions, hints, placeholders. */
    int dim();

    /** The accent: selections, marks, the primary button, progress. */
    int accent();

    /** A 1px separator or border. */
    int edge();

    /** The background of a program's body. */
    int windowBg();

    /** The fill of a content panel. */
    int panelBg();

    /** The fill of a text field or an empty cell. */
    int fieldBg();

    /** The background of a hovered row or cell. */
    int listHover();

    /** The text colour of a list row, given its selection state. */
    int listRowText(boolean selected);

    // primitives

    /** A group panel: a sunken well, a soft border or a hairline, per design. */
    void panel(GuiGraphics g, int x, int y, int w, int h);

    /** A push button, with its hovered and pressed states; the primary one is the default action. */
    void button(GuiGraphics g, Font font, int x, int y, int w, int h, String label, boolean hovered,
                boolean pressed, boolean primary);

    /** A text input field, lit when it has the keyboard. */
    void field(GuiGraphics g, int x, int y, int w, int h, boolean focused);

    /** One tab of a tab strip. */
    void tab(GuiGraphics g, Font font, int x, int y, int w, int h, String label, boolean active);

    /** The background of a list or grid row in its hovered and selected states; nothing when it is neither. */
    void listRow(GuiGraphics g, int x, int y, int w, int h, boolean hovered, boolean selected);

    /** A scrollbar thumb. */
    void scrollThumb(GuiGraphics g, int x, int y, int w, int h);

    /** A status or footer bar background. */
    void statusBar(GuiGraphics g, int x, int y, int w, int h);

    /** The body and border of a floating panel such as a popup. */
    void windowFrame(GuiGraphics g, int x, int y, int w, int h);
}
