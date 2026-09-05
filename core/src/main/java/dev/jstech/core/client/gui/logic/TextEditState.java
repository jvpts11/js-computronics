/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.client.gui.logic;

/**
 * The text of a single-line field: the committed value and the text being edited, which only becomes the
 * value on {@link #commit()}. A field keeps what the player types apart from what the server holds, so a
 * refresh in the middle of typing does not eat the keystrokes and an escape drops them.
 */
public final class TextEditState {

    private final int maxLength;
    private String committed = "";
    private String edit = "";

    public TextEditState(final int maxLength) {
        this.maxLength = Math.max(0, maxLength);
    }

    public int maxLength() {
        return maxLength;
    }

    /** Adopts a value from outside as both the committed text and the text being edited. */
    public void sync(final String value) {
        committed = value == null ? "" : value;
        edit = committed;
    }

    /** The committed value. */
    public String value() {
        return committed;
    }

    /** The text as it is being edited. */
    public String edit() {
        return edit;
    }

    /** Appends a character, unless the text is already as long as it may be. */
    public void type(final char c) {
        if (edit.length() < maxLength) {
            edit += c;
        }
    }

    /** Removes the last character, if any. */
    public void backspace() {
        if (!edit.isEmpty()) {
            edit = edit.substring(0, edit.length() - 1);
        }
    }

    /** Whether the text being edited differs from the committed value. */
    public boolean dirty() {
        return !edit.equals(committed);
    }

    /** Makes the edited text the value. */
    public void commit() {
        committed = edit;
    }

    /** Drops the edits and shows the value again. */
    public void revert() {
        edit = committed;
    }
}
