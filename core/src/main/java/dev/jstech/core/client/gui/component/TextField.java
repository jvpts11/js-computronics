/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.client.gui.component;

import dev.jstech.core.client.gui.logic.TextEditState;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A single-line text field. It shows the committed value until it is clicked; then it shows what is being
 * typed with a caret, and the value is committed when the keyboard leaves it (a click elsewhere, Enter or
 * Tab), which is when {@link #setOnCommit the commit callback} fires. Escape drops the edits. While the field
 * has the keyboard it takes every key, so nothing behind it reacts to typing.
 */
public class TextField extends UiComponent {

    private final TextEditState state;
    private Supplier<String> placeholder = () -> "";
    @Nullable
    private Consumer<String> onCommit;
    @Nullable
    private Runnable onEdit;
    private boolean revertOnEscape = true;

    public TextField(final int maxLength) {
        state = new TextEditState(maxLength);
    }

    /** Adopts the server's value, unless the player is typing in the field right now. */
    public TextField sync(final String value) {
        if (!isFocused()) {
            state.sync(value);
        }
        return this;
    }

    /** The committed value. */
    public String value() {
        return state.value();
    }

    /** The text as it is being edited. */
    public String edit() {
        return state.edit();
    }

    public boolean dirty() {
        return state.dirty();
    }

    /** Text shown in the field while it is empty and idle. */
    public TextField setPlaceholder(final String value) {
        placeholder = () -> value;
        return this;
    }

    /** Fires with the new value when the keyboard leaves the field and the text changed. */
    public TextField setOnCommit(final Consumer<String> action) {
        onCommit = action;
        return this;
    }

    /** Fires on every keystroke, for a field that filters something as it is typed. */
    public TextField setOnEdit(final Runnable action) {
        onEdit = action;
        return this;
    }

    /** Whether Escape drops the edits before giving the keyboard up; a filter keeps them. */
    public TextField setRevertOnEscape(final boolean value) {
        revertOnEscape = value;
        return this;
    }

    @Override
    public boolean focusable() {
        return true;
    }

    @Override
    public void render(final GuiGraphics g, final UiContext ctx) {
        final boolean focused = isFocused();
        ctx.skin().field(g, x(), y(), width(), height(), focused);
        final String shown = focused ? state.edit() : state.value();
        final String text;
        int color = ctx.skin().text();
        if (focused) {
            text = Texts.tail(ctx.font(), shown, width() - 8) + "_";
        } else if (shown.isEmpty()) {
            text = Texts.clip(ctx.font(), placeholder.get(), width() - 6);
            color = ctx.skin().dim();
        } else {
            text = Texts.clip(ctx.font(), shown, width() - 6);
        }
        g.drawString(ctx.font(), text, x() + 3, y() + (height() - 7) / 2, color, false);
    }

    @Override
    public boolean mouseClicked(final double mx, final double my, final int button) {
        return true;
    }

    @Override
    public boolean charTyped(final char c) {
        if (!isFocused()) {
            return false;
        }
        if (c >= 32 && c != 127) {
            state.type(c);
            edited();
        }
        return true;
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        if (!isFocused()) {
            return false;
        }
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                state.backspace();
                edited();
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_TAB -> blur();
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (revertOnEscape) {
                    state.revert();
                    edited();
                }
                blur();
            }
            default -> {
                // A focused field eats every other key so nothing behind it reacts to typing.
            }
        }
        return true;
    }

    @Override
    protected void onBlur() {
        if (state.dirty()) {
            state.commit();
            if (onCommit != null) {
                onCommit.accept(state.value());
            }
        }
    }

    private void edited() {
        if (onEdit != null) {
            onEdit.run();
        }
    }
}
