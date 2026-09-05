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
    private final int preferredWidth;
    private final int preferredHeight;
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
     * Dims the content rectangle and draws the popup centred in it, no wider or taller than the rectangle
     * allows. The caller decides when: an open popup is drawn in the program's modal pass.
     */
    public void renderIn(final GuiGraphics g, final UiContext ctx, final int cx, final int cy, final int cw, final int ch) {
        final int pw = Math.min(cw - MARGIN, preferredWidth);
        final int ph = Math.min(ch - MARGIN, preferredHeight);
        setBounds(cx + (cw - pw) / 2, cy + (ch - ph) / 2, pw, ph);
        layouter.accept(this);
        g.fill(cx, cy, cx + cw, cy + ch, 0x88000000);
        render(g, ctx);
    }

    @Override
    public void render(final GuiGraphics g, final UiContext ctx) {
        ctx.skin().windowFrame(g, x(), y(), width(), height());
        g.fill(x() + 1, y() + 1, right() - 1, bottom() - 1, ctx.skin().windowBg());
        g.drawString(ctx.font(), title.get(), x() + TITLE_X, y() + TITLE_Y, ctx.skin().text(), false);
        super.render(g, ctx);
    }

    @Override
    public boolean mouseClicked(final double mx, final double my, final int button) {
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
