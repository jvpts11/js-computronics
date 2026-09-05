/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.client.gui.component;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One control of a program's content: a rectangle that draws itself through the skin, takes the input that
 * lands on it and keeps its own state (pressed, focused, scrolled) between frames. The owner lays it out by
 * {@link #setBounds} every frame, so a window that resizes or a tab that changes moves its controls without
 * the control knowing; input arrives in the same coordinates the bounds were given in.
 *
 * <p>Components live in a {@link Panel}, which routes clicks, keys and the keyboard focus among them.
 */
public abstract class UiComponent {

    private int x;
    private int y;
    private int width;
    private int height;
    private boolean visible = true;
    private boolean enabled = true;
    private boolean focused;
    @Nullable
    private Panel parent;

    // ---- geometry ----

    /** Places the component; called by its owner before every render. Returns this, for chaining. */
    public UiComponent setBounds(final int x, final int y, final int width, final int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        return this;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** The first x past the right edge. */
    public int right() {
        return x + width;
    }

    /** The first y past the bottom edge. */
    public int bottom() {
        return y + height;
    }

    /** Whether the point lies inside the bounds. */
    public boolean contains(final double mx, final double my) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }

    /** The centre of the bounds, where a test clicks to hit the control. */
    public int[] center() {
        return new int[] {x + width / 2, y + height / 2};
    }

    // ---- state ----

    public boolean visible() {
        return visible;
    }

    public UiComponent setVisible(final boolean value) {
        visible = value;
        return this;
    }

    public boolean enabled() {
        return enabled;
    }

    public UiComponent setEnabled(final boolean value) {
        enabled = value;
        return this;
    }

    /** Whether the component takes the keyboard when clicked. */
    public boolean focusable() {
        return false;
    }

    public boolean isFocused() {
        return focused;
    }

    /** Hands the keyboard to the component or takes it away; a component losing it gets {@link #onBlur()}. */
    void setFocused(final boolean value) {
        if (focused == value) {
            return;
        }
        focused = value;
        if (!value) {
            onBlur();
        }
    }

    /** Called when the component loses the keyboard: the place to commit what was typed. */
    protected void onBlur() {
    }

    /** Gives the keyboard up, so the owner moves the focus away from this component. */
    protected final void blur() {
        if (parent != null) {
            parent.blur(this);
        } else {
            setFocused(false);
        }
    }

    void attach(@Nullable final Panel owner) {
        parent = owner;
    }

    @Nullable
    protected Panel parent() {
        return parent;
    }

    /** Whether the cursor is over the component and the component can react to it. */
    protected boolean hovered(final UiContext ctx) {
        return enabled && visible && contains(ctx.mouseX(), ctx.mouseY());
    }

    // ---- drawing and input ----

    /** Draws the component in its bounds. */
    public abstract void render(GuiGraphics g, UiContext ctx);

    /** A click inside the bounds; true when the component took it. */
    public boolean mouseClicked(final double mx, final double my, final int button) {
        return false;
    }

    /** The mouse moving with a button held after a click this component took. */
    public boolean mouseDragged(final double mx, final double my, final int button) {
        return false;
    }

    /** The button of a click this component took being released, wherever the cursor is now. */
    public boolean mouseReleased(final double mx, final double my, final int button) {
        return false;
    }

    /** The wheel turning over the component ({@code delta} &gt; 0 is up); true when the component took it. */
    public boolean mouseScrolled(final double mx, final double my, final double delta) {
        return false;
    }

    /** A character typed while the component has the keyboard; true when it took it. */
    public boolean charTyped(final char c) {
        return false;
    }

    /** A key pressed while the component has the keyboard; true when it took it. */
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        return false;
    }

    /** The tooltip for the cursor at this point, or an empty list for none. */
    public List<Component> tooltip(final double mx, final double my) {
        return List.of();
    }
}
