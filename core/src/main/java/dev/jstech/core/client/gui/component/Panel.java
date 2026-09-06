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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A group of components drawn in order and given input in reverse order, so the last one added is in front.
 * The panel owns the keyboard focus of its children: a click hands the keyboard to the focusable child that
 * took it and takes it from whoever had it, keys go to the focused child, and a child that gives the keyboard
 * up through {@link UiComponent#blur()} loses it here. A panel draws no chrome of its own; put a
 * {@link Label} or a background where one is wanted.
 */
public class Panel extends UiComponent {

    private final List<UiComponent> children = new ArrayList<>();
    @Nullable
    private UiComponent focusedChild;
    @Nullable
    private UiComponent pressedChild;

    /** Adds a child in front of the others; returns it, so a field can be declared and added in one line. */
    public <T extends UiComponent> T add(final T child) {
        children.add(child);
        child.attach(this);
        return child;
    }

    public void remove(final UiComponent child) {
        if (children.remove(child)) {
            child.attach(null);
            if (focusedChild == child) {
                focusedChild = null;
                child.setFocused(false);
            }
            if (pressedChild == child) {
                pressedChild = null;
            }
        }
    }

    public void clear() {
        for (final UiComponent child : new ArrayList<>(children)) {
            remove(child);
        }
    }

    public List<UiComponent> children() {
        return Collections.unmodifiableList(children);
    }

    @Nullable
    public UiComponent focusedChild() {
        return focusedChild;
    }

    /** Hands the keyboard to {@code child} (which must be one of this panel's), or to nobody when null. */
    public void focus(@Nullable final UiComponent child) {
        if (focusedChild == child) {
            return;
        }
        final UiComponent previous = focusedChild;
        focusedChild = child;
        if (previous != null) {
            previous.setFocused(false);
        }
        if (child != null) {
            child.setFocused(true);
        }
    }

    /** Takes the keyboard from {@code child} when it has it; what {@link UiComponent#blur()} calls. */
    void blur(final UiComponent child) {
        if (focusedChild == child) {
            focusedChild = null;
            child.setFocused(false);
        }
    }

    @Override
    public boolean focusable() {
        return true;
    }

    @Override
    protected void onBlur() {
        focus(null);
    }

    @Override
    public void render(final GuiGraphics g, final UiContext ctx) {
        for (final UiComponent child : children) {
            if (child.visible()) {
                child.render(g, ctx);
            }
        }
    }

    @Override
    public boolean mouseClicked(final double mx, final double my, final int button) {
        for (int i = children.size() - 1; i >= 0; i--) {
            final UiComponent child = children.get(i);
            if (!child.visible() || !child.enabled() || !child.contains(mx, my)) {
                continue;
            }
            if (child.mouseClicked(mx, my, button)) {
                pressedChild = child;
                focus(child.focusable() ? child : null);
                return true;
            }
        }
        // A click that no child took still moves the keyboard away from whoever had it.
        focus(null);
        return false;
    }

    @Override
    public boolean mouseDragged(final double mx, final double my, final int button) {
        return pressedChild != null && pressedChild.mouseDragged(mx, my, button);
    }

    @Override
    public boolean mouseReleased(final double mx, final double my, final int button) {
        final UiComponent pressed = pressedChild;
        pressedChild = null;
        return pressed != null && pressed.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(final double mx, final double my, final double delta) {
        for (int i = children.size() - 1; i >= 0; i--) {
            final UiComponent child = children.get(i);
            if (child.visible() && child.enabled() && child.contains(mx, my) && child.mouseScrolled(mx, my, delta)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(final char c) {
        return focusedChild != null && focusedChild.charTyped(c);
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        return focusedChild != null && focusedChild.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public List<Component> tooltip(final double mx, final double my) {
        for (int i = children.size() - 1; i >= 0; i--) {
            final UiComponent child = children.get(i);
            if (child.visible() && child.contains(mx, my)) {
                final List<Component> lines = child.tooltip(mx, my);
                if (!lines.isEmpty()) {
                    return lines;
                }
            }
        }
        return List.of();
    }
}
