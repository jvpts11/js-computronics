/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.client.gui.component;

import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A modal panel floating over a program's content: it dims what is behind it, draws a frame and a title,
 * lays its children out through a layouter each frame, and takes every click while it is open. A click
 * outside it or Escape closes it.
 */
public class Popup extends Panel {

    private static final int TITLE_X = 5;
    private static final int TITLE_Y = 4;
    private static final int MARGIN = 16;

    private final Supplier<String> title;
    private int preferredWidth;
    private int preferredHeight;
    private int dim = 0x88000000;
    private Consumer<Popup> layouter = popup -> { };
    private Runnable onClose = () -> { };
    private boolean closeOnOutsideClick = true;
    private boolean open;

    public Popup(final String title, final int preferredWidth, final int preferredHeight) {
        this(() -> title, preferredWidth, preferredHeight);
    }

    public Popup(final Supplier<String> title, final int preferredWidth, final int preferredHeight) {
        this.title = title;
        this.preferredWidth = preferredWidth;
        this.preferredHeight = preferredHeight;
    }

    /** Places the children inside the popup's bounds; called every frame after the popup is placed. */
    public Popup setLayouter(final Consumer<Popup> value) {
        layouter = value;
        return this;
    }

    /** The size the popup takes when the content rectangle allows it; a popup that grows sets it again. */
    public Popup setPreferredSize(final int width, final int height) {
        preferredWidth = width;
        preferredHeight = height;
        return this;
    }

    /** The colour laid over the content behind the popup. */
    public Popup setDim(final int argb) {
        dim = argb;
        return this;
    }

    /** The title drawn at the top-left, empty when the popup draws its own header. */
    public String title() {
        return title.get();
    }

    public Popup setOnClose(final Runnable action) {
        onClose = action;
        return this;
    }

    public Popup setCloseOnOutsideClick(final boolean value) {
        closeOnOutsideClick = value;
        return this;
    }

    public boolean isOpen() {
        return open;
    }

    public void open() {
        open = true;
    }

    public void close() {
        if (!open) {
            return;
        }
        open = false;
        focus(null);
        onClose.run();
    }

    /** The top of the area below the title, where the children start. */
    public int contentTop() {
        return y() + TITLE_Y + 10;
    }

    /**
     * Centres the popup in the content rectangle, no wider or taller than the rectangle allows, and lays its
     * children out. Called on every modal render, and worth calling right after {@link #open()} so a click
     * that arrives before the first frame already finds the controls in place.
     */
    public void placeIn(final int cx, final int cy, final int cw, final int ch) {
        final int pw = Math.min(cw - MARGIN, preferredWidth);
        final int ph = Math.min(ch - MARGIN, preferredHeight);
        setBounds(cx + (cw - pw) / 2, cy + (ch - ph) / 2, pw, ph);
        layouter.accept(this);
    }

    /**
     * Dims the content rectangle and draws the popup centred in it. The caller decides when: an open popup is
     * drawn in the program's modal pass.
     */
    public void renderIn(final GuiGraphics g, final UiContext ctx, final int cx, final int cy, final int cw, final int ch) {
        placeIn(cx, cy, cw, ch);
        g.fill(cx, cy, cx + cw, cy + ch, dim);
        render(g, ctx);
    }

    @Override
    public void render(final GuiGraphics g, final UiContext ctx) {
        ctx.skin().windowFrame(g, x(), y(), width(), height());
        g.fill(x() + 1, y() + 1, right() - 1, bottom() - 1, ctx.skin().windowBg());
        final String text = title.get();
        if (!text.isEmpty()) {
            g.drawString(ctx.font(), text, x() + TITLE_X, y() + TITLE_Y, ctx.skin().text(), false);
        }
        super.render(g, ctx);
    }

    @Override
    public boolean mouseClicked(final double mx, final double my, final int button) {
        if (width() == 0 || height() == 0) {
            /*
             * Not laid out yet (opened this very tick): the click can be placed nowhere, and it is not a
             * click outside either.
             */
            return true;
        }
        if (!contains(mx, my)) {
            if (closeOnOutsideClick) {
                close();
            }
            return true;
        }
        super.mouseClicked(mx, my, button);
        return true; // modal: nothing behind the popup gets the click
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        super.keyPressed(key, scanCode, modifiers);
        return true;
    }

    @Override
    public boolean charTyped(final char c) {
        super.charTyped(c);
        return true;
    }
}
