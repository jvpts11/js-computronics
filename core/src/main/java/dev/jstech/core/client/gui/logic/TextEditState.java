/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.client.gui.logic;

/**
 * The text of one field being edited: the value that was committed, the edit in progress, where the
 * caret is in it, and what part of it is selected.
 *
 * <p>Pure logic, so a field's behaviour (typing in the middle, arrows, Home and End, the length cap,
 * a selection made with Shift and what typing over it does) is tested without a screen. The caret sits
 * between characters, from 0 (before the first) to the length of the edit (after the last); typing puts
 * the character there and moves past it. A selection runs from an anchor to the caret: the anchor is
 * where the selecting started and stays put, the caret is the end that moves.
 */
public final class TextEditState {

    private final int maxLength;
    private String committed = "";
    private String edit = "";
    private int caret;
    /** The still end of the selection, or -1 when nothing is selected. */
    private int anchor = -1;

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
        anchor = -1;
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

    /** Puts the caret at {@code index}, held within the edit, and drops any selection. */
    public void setCaret(final int index) {
        caret = clamp(index);
        anchor = -1;
    }

    /**
     * Moves the caret to {@code index}; with {@code extend} the selection grows or starts there, as a
     * move with Shift held does, and without it any selection is dropped.
     */
    public void moveTo(final int index, final boolean extend) {
        if (extend) {
            if (anchor < 0) {
                anchor = caret;
            }
        } else {
            anchor = -1;
        }
        caret = clamp(index);
    }

    /* The selection */

    /** Whether some of the edit is selected. */
    public boolean hasSelection() {
        return anchor >= 0 && anchor != caret;
    }

    /** Where the selection begins, or the caret when nothing is selected. */
    public int selectionStart() {
        return hasSelection() ? Math.min(anchor, caret) : caret;
    }

    /** Where the selection ends, or the caret when nothing is selected. */
    public int selectionEnd() {
        return hasSelection() ? Math.max(anchor, caret) : caret;
    }

    /** The selected text, or empty. */
    public String selectedText() {
        return edit.substring(selectionStart(), selectionEnd());
    }

    /** Selects the whole edit, with the caret at its end. */
    public void selectAll() {
        anchor = edit.isEmpty() ? -1 : 0;
        caret = edit.length();
    }

    /** Drops the selection, leaving the caret where it is. */
    public void clearSelection() {
        anchor = -1;
    }

    /** Removes the selected text, leaving the caret where it was; false when nothing was selected. */
    public boolean deleteSelection() {
        if (!hasSelection()) {
            return false;
        }
        final int from = selectionStart();
        edit = edit.substring(0, from) + edit.substring(selectionEnd());
        caret = from;
        anchor = -1;
        return true;
    }

    /* Moving */

    public void left() {
        left(false);
    }

    /** One character back; without {@code extend}, a selection collapses to its start instead. */
    public void left(final boolean extend) {
        if (!extend && hasSelection()) {
            setCaret(selectionStart());
            return;
        }
        moveTo(caret - 1, extend);
    }

    public void right() {
        right(false);
    }

    /** One character on; without {@code extend}, a selection collapses to its end instead. */
    public void right(final boolean extend) {
        if (!extend && hasSelection()) {
            setCaret(selectionEnd());
            return;
        }
        moveTo(caret + 1, extend);
    }

    public void home() {
        home(false);
    }

    public void home(final boolean extend) {
        moveTo(0, extend);
    }

    public void end() {
        end(false);
    }

    public void end(final boolean extend) {
        moveTo(edit.length(), extend);
    }

    /** Moves the caret to the start of the word before it, the way Ctrl with Left does. */
    public void wordLeft() {
        wordLeft(false);
    }

    public void wordLeft(final boolean extend) {
        int at = caret;
        while (at > 0 && !Character.isLetterOrDigit(edit.charAt(at - 1))) {
            at--;
        }
        while (at > 0 && Character.isLetterOrDigit(edit.charAt(at - 1))) {
            at--;
        }
        moveTo(at, extend);
    }

    /** Moves the caret past the end of the word after it, the way Ctrl with Right does. */
    public void wordRight() {
        wordRight(false);
    }

    public void wordRight(final boolean extend) {
        int at = caret;
        while (at < edit.length() && !Character.isLetterOrDigit(edit.charAt(at))) {
            at++;
        }
        while (at < edit.length() && Character.isLetterOrDigit(edit.charAt(at))) {
            at++;
        }
        moveTo(at, extend);
    }

    /* Editing */

    /** Types a character at the caret, in place of the selection when there is one, within the cap. */
    public void type(final char c) {
        deleteSelection();
        if (edit.length() < maxLength) {
            edit = edit.substring(0, caret) + c + edit.substring(caret);
            caret++;
        }
    }

    /** Removes the selection, or the character before the caret if there is one. */
    public void backspace() {
        if (deleteSelection()) {
            return;
        }
        if (caret > 0) {
            edit = edit.substring(0, caret - 1) + edit.substring(caret);
            caret--;
        }
    }

    /** Removes the selection, or the character after the caret if there is one. */
    public void delete() {
        if (deleteSelection()) {
            return;
        }
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
        anchor = -1;
    }

    private int clamp(final int index) {
        return Math.max(0, Math.min(index, edit.length()));
    }
}
