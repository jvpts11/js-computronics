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
    private Supplier<String> suffix = () -> "";
    @Nullable
    private Consumer<String> onCommit;
    @Nullable
    private Runnable onEdit;
    @Nullable
    private Runnable onEscape;
    @Nullable
    private Runnable onBlur;
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

    /** Puts a value in the field whatever its state, as the start of an edit does. */
    public TextField set(final String value) {
        state.sync(value);
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

    /** Text shown after the caret that is not edited: the extension of a file being renamed. */
    public TextField setSuffix(final Supplier<String> value) {
        suffix = value;
        return this;
    }

    /** Fires when Escape is pressed in the field, before the keyboard leaves it. */
    public TextField setOnEscape(final Runnable action) {
        onEscape = action;
        return this;
    }

    /** Fires whenever the keyboard leaves the field, after any commit. */
    public TextField setOnBlur(final Runnable action) {
        onBlur = action;
        return this;
    }

    /** Whether a character may be typed; a file name refuses path separators. */
    protected boolean accepts(final char c) {
        return c >= 32 && c != 127;
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
        final int textY = y() + (height() - 7) / 2;
        if (focused) {
            /*
             * The caret has to stay in view: when the text is wider than the field, what is shown is
             * the stretch that ends at the caret, or the whole text when it fits.
             */
            final String tail = suffix.get();
            final int avail = width() - 8 - ctx.font().width(tail);
            final int caret = state.caret();
            int offset = 0;
            while (offset < caret && ctx.font().width(shown.substring(offset, caret)) > avail) {
                offset++;
            }
            final String visible = Texts.clip(ctx.font(), shown.substring(offset), avail);
            g.drawString(ctx.font(), visible, x() + 3, textY, ctx.skin().text(), false);
            final int caretX = x() + 3 + ctx.font().width(shown.substring(offset, caret));
            g.fill(caretX, textY - 1, caretX + 1, textY + 8, ctx.skin().text());
            if (!tail.isEmpty()) {
                g.drawString(ctx.font(), tail, x() + 3 + ctx.font().width(visible) + 1, textY, ctx.skin().dim(), false);
            }
            return;
        }
        if (shown.isEmpty()) {
            g.drawString(ctx.font(), Texts.clip(ctx.font(), placeholder.get(), width() - 6), x() + 3, textY,
                    ctx.skin().dim(), false);
        } else {
            g.drawString(ctx.font(), Texts.clip(ctx.font(), shown, width() - 6), x() + 3, textY,
                    ctx.skin().text(), false);
        }
    }

    /** Puts the caret at {@code index}: before the extension of a file name, say. */
    public TextField setCaret(final int index) {
        state.setCaret(index);
        return this;
    }

    /** Where the caret is: how many characters of the edit sit before it. */
    public int caret() {
        return state.caret();
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
        if (accepts(c)) {
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
            case GLFW.GLFW_KEY_DELETE -> {
                state.delete();
                edited();
            }
            case GLFW.GLFW_KEY_LEFT -> state.left();
            case GLFW.GLFW_KEY_RIGHT -> state.right();
            case GLFW.GLFW_KEY_HOME -> state.home();
            case GLFW.GLFW_KEY_END -> state.end();
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_TAB -> blur();
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (revertOnEscape) {
                    state.revert();
                    edited();
                }
                if (onEscape != null) {
                    onEscape.run();
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
        if (onBlur != null) {
            onBlur.run();
        }
    }

    private void edited() {
        if (onEdit != null) {
            onEdit.run();
        }
    }
}
