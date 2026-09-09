/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.client.gui.logic;

/**
 * The text of one field being edited: the value that was committed, the edit in progress, and where
 * the caret is in it.
 *
 * <p>Pure logic, so a field's behaviour (typing in the middle, arrows, Home and End, the length cap)
 * is tested without a screen. The caret sits between characters, from 0 (before the first) to the
 * length of the edit (after the last); typing puts the character there and moves past it.
 */
public final class TextEditState {

    private final int maxLength;
    private String committed = "";
    private String edit = "";
    private int caret;

    public TextEditState(final int maxLength) {
        this.maxLength = maxLength;
    }

    public int maxLength() {
        return maxLength;
    }

    /** Adopts a value as both the committed text and the edit, with the caret after it. */
    public void sync(final String value) {
        committed = value == null ? "" : value;
        edit = committed;
        caret = edit.length();
    }

    public String value() {
        return committed;
    }

    public String edit() {
        return edit;
    }

    /** Where the caret is: how many characters of the edit sit before it. */
    public int caret() {
        return caret;
    }

    /** Puts the caret at {@code index}, held within the edit. */
    public void setCaret(final int index) {
        caret = Math.max(0, Math.min(index, edit.length()));
    }

    public void left() {
        setCaret(caret - 1);
    }

    public void right() {
        setCaret(caret + 1);
    }

    public void home() {
        setCaret(0);
    }

    public void end() {
        setCaret(edit.length());
    }

    /** Types a character at the caret, unless the edit is as long as it may be. */
    public void type(final char c) {
        if (edit.length() < maxLength) {
            edit = edit.substring(0, caret) + c + edit.substring(caret);
            caret++;
        }
    }

    /** Removes the character before the caret, if there is one. */
    public void backspace() {
        if (caret > 0) {
            edit = edit.substring(0, caret - 1) + edit.substring(caret);
            caret--;
        }
    }

    /** Removes the character after the caret, if there is one. */
    public void delete() {
        if (caret < edit.length()) {
            edit = edit.substring(0, caret) + edit.substring(caret + 1);
        }
    }

    public boolean dirty() {
        return !edit.equals(committed);
    }

    public void commit() {
        committed = edit;
    }

    public void revert() {
        edit = committed;
        caret = edit.length();
    }
}
