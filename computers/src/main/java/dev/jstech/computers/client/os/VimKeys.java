/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client.os;

import dev.jstech.computers.os.edit.VimCommand;
import dev.jstech.core.client.gui.logic.TextDocument;
import org.lwjgl.glfw.GLFW;

/**
 * Vim, as far as a keyboard is concerned.
 *
 * <p>The thing that makes it Vim is that a key means something different depending on what the editor
 * is in the middle of: typing puts letters in the file, and not typing makes the same letters into
 * commands. That is kept as a small state rather than spread through the editor, so the editor itself
 * knows nothing about modes and the other flavour can be a different one of these.
 *
 * <p>What is here is what a person uses to write a program and get out: moving by character, word and
 * line, opening a line, deleting and yanking one, undoing, and the colon commands. It is not all of Vim
 * and does not pretend to be.
 */
public final class VimKeys implements TtyEditor.IKeys {

    /** What the editor is in the middle of. */
    private enum Mode { NORMAL, INSERT, COMMAND }

    private Mode mode = Mode.NORMAL;
    /** What has been typed after the colon, before it is run. */
    private String command = "";
    /** The first half of a two-key command, such as the first d of dd or the first g of gg. */
    private char waiting;
    /** The last line yanked or deleted whole, which is what p puts back. */
    private String register = "";

    @Override
    public String status(final TtyEditor editor) {
        if (this.mode == Mode.COMMAND) {
            return ":" + this.command;
        }
        if (!editor.message().isEmpty()) {
            return editor.message();
        }
        final String name = "\"" + editor.name() + "\"" + (editor.dirty() ? " [+]" : "");
        return this.mode == Mode.INSERT ? "-- INSERT --  " + name : name;
    }

    @Override
    public boolean typed(final TtyEditor editor, final char c) {
        if (c < 32 || c == 127) {
            return false;
        }
        switch (this.mode) {
            case COMMAND -> this.command += c;
            case INSERT -> {
                editor.document().insert(c);
                editor.touched();
            }
            case NORMAL -> normalChar(editor, c);
        }
        return true;
    }

    /** A letter pressed while not typing is a command. */
    private void normalChar(final TtyEditor editor, final char c) {
        final TextDocument doc = editor.document();
        if (this.waiting != 0) {
            final char first = this.waiting;
            this.waiting = 0;
            secondChar(editor, first, c);
            return;
        }
        switch (c) {
            case 'i' -> enter(editor, Mode.INSERT);
            case 'a' -> {
                doc.right();
                enter(editor, Mode.INSERT);
            }
            case 'A' -> {
                doc.setCursor(doc.cursorLine(), doc.line(doc.cursorLine()).length());
                enter(editor, Mode.INSERT);
            }
            case 'I' -> {
                doc.setCursor(doc.cursorLine(), 0);
                enter(editor, Mode.INSERT);
            }
            case 'o' -> {
                doc.setCursor(doc.cursorLine(), doc.line(doc.cursorLine()).length());
                doc.newline();
                editor.touched();
                enter(editor, Mode.INSERT);
            }
            case 'O' -> {
                doc.setCursor(doc.cursorLine(), 0);
                doc.newline();
                doc.up();
                editor.touched();
                enter(editor, Mode.INSERT);
            }
            case 'h' -> doc.left();
            case 'l' -> doc.right();
            case 'j' -> doc.down();
            case 'k' -> doc.up();
            case 'w' -> wordForward(doc);
            case 'b' -> wordBack(doc);
            case 'e' -> wordEnd(doc);
            case '0' -> doc.setCursor(doc.cursorLine(), 0);
            case '^' -> doc.setCursor(doc.cursorLine(), firstNonBlank(doc.line(doc.cursorLine())));
            case '$' -> doc.setCursor(doc.cursorLine(), doc.line(doc.cursorLine()).length());
            case 'G' -> doc.setCursor(doc.lineCount() - 1, 0);
            case 'x' -> {
                if (doc.cursorCol() < doc.line(doc.cursorLine()).length()) {
                    doc.delete();
                    editor.touched();
                }
            }
            case 'D' -> {
                deleteToEnd(doc);
                editor.touched();
            }
            case 'u' -> {
                if (doc.undo()) {
                    editor.touched();
                    editor.say("1 change; before");
                } else {
                    editor.say("Already at oldest change");
                }
            }
            case 'p' -> put(editor, true);
            case 'P' -> put(editor, false);
            case 'd', 'y', 'g' -> this.waiting = c;
            case ':' -> {
                this.mode = Mode.COMMAND;
                this.command = "";
                editor.say("");
            }
            default -> { }
        }
    }

    /** The second key of a two-key command. */
    private void secondChar(final TtyEditor editor, final char first, final char second) {
        final TextDocument doc = editor.document();
        switch ("" + first + second) {
            case "dd" -> {
                this.register = doc.line(doc.cursorLine()) + "\n";
                deleteLine(editor);
            }
            case "dw" -> {
                final int line = doc.cursorLine();
                final int from = doc.cursorCol();
                wordForward(doc);
                if (doc.cursorLine() != line) {
                    doc.setCursor(line, doc.line(line).length());
                }
                doc.select(line, from, line, doc.cursorCol());
                doc.deleteSelection();
                editor.touched();
            }
            case "yy" -> {
                this.register = doc.line(doc.cursorLine()) + "\n";
                editor.say("1 line yanked");
            }
            case "gg" -> doc.setCursor(0, 0);
            default -> { }
        }
    }

    /** Puts the register back below (or above) the caret's line when it holds a line, else at the caret. */
    private void put(final TtyEditor editor, final boolean below) {
        final TextDocument doc = editor.document();
        if (this.register.isEmpty()) {
            return;
        }
        if (this.register.endsWith("\n")) {
            final String text = this.register.substring(0, this.register.length() - 1);
            if (below) {
                doc.setCursor(doc.cursorLine(), doc.line(doc.cursorLine()).length());
                doc.newline();
                doc.insertText(text);
            } else {
                doc.setCursor(doc.cursorLine(), 0);
                doc.insertText(text);
                doc.newline();
                doc.up();
            }
            doc.setCursor(doc.cursorLine(), 0);
        } else {
            doc.insertText(this.register);
        }
        editor.touched();
    }

    /** Removes the line the caret is on, the way {@code dd} does. */
    private static void deleteLine(final TtyEditor editor) {
        final TextDocument doc = editor.document();
        final int line = doc.cursorLine();
        if (doc.lineCount() == 1) {
            doc.select(0, 0, 0, doc.line(0).length());
            doc.deleteSelection();
        } else if (line + 1 < doc.lineCount()) {
            doc.select(line, 0, line + 1, 0);
            doc.deleteSelection();
        } else {
            doc.select(line - 1, doc.line(line - 1).length(), line, doc.line(line).length());
            doc.deleteSelection();
            doc.setCursor(line - 1, 0);
        }
        editor.touched();
    }

    private static void deleteToEnd(final TextDocument doc) {
        final int line = doc.cursorLine();
        doc.select(line, doc.cursorCol(), line, doc.line(line).length());
        doc.deleteSelection();
    }

    private static int firstNonBlank(final String line) {
        int at = 0;
        while (at < line.length() && Character.isWhitespace(line.charAt(at))) {
            at++;
        }
        return at;
    }

    /** The start of the next word, going on to the next line when this one has none left. */
    private static void wordForward(final TextDocument doc) {
        int line = doc.cursorLine();
        int col = doc.cursorCol();
        String text = doc.line(line);
        final boolean onWord = col < text.length() && isWordChar(text.charAt(col));
        while (col < text.length() && isWordChar(text.charAt(col)) == onWord && !Character.isWhitespace(text.charAt(col))) {
            col++;
        }
        while (true) {
            while (col < text.length() && Character.isWhitespace(text.charAt(col))) {
                col++;
            }
            if (col < text.length() || line + 1 >= doc.lineCount()) {
                break;
            }
            line++;
            col = 0;
            text = doc.line(line);
        }
        doc.setCursor(line, Math.min(col, text.length()));
    }

    /** The start of the word before the caret, going back a line when this one has none before. */
    private static void wordBack(final TextDocument doc) {
        int line = doc.cursorLine();
        int col = doc.cursorCol();
        String text = doc.line(line);
        while (true) {
            while (col > 0 && Character.isWhitespace(text.charAt(col - 1))) {
                col--;
            }
            if (col > 0 || line == 0) {
                break;
            }
            line--;
            text = doc.line(line);
            col = text.length();
        }
        if (col > 0) {
            final boolean onWord = isWordChar(text.charAt(col - 1));
            while (col > 0 && isWordChar(text.charAt(col - 1)) == onWord && !Character.isWhitespace(text.charAt(col - 1))) {
                col--;
            }
        }
        doc.setCursor(line, col);
    }

    /** The last character of the word the caret is on, or of the next one. */
    private static void wordEnd(final TextDocument doc) {
        final int line = doc.cursorLine();
        final String text = doc.line(line);
        int col = Math.min(doc.cursorCol() + 1, text.length());
        while (col < text.length() && Character.isWhitespace(text.charAt(col))) {
            col++;
        }
        if (col >= text.length()) {
            doc.setCursor(line, Math.max(0, text.length() - 1));
            return;
        }
        final boolean onWord = isWordChar(text.charAt(col));
        while (col + 1 < text.length() && isWordChar(text.charAt(col + 1)) == onWord
                && !Character.isWhitespace(text.charAt(col + 1))) {
            col++;
        }
        doc.setCursor(line, col);
    }

    private static boolean isWordChar(final char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private void enter(final TtyEditor editor, final Mode next) {
        this.mode = next;
        editor.say("");
    }

    @Override
    public boolean key(final TtyEditor editor, final int key, final int modifiers) {
        final TextDocument doc = editor.document();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (this.mode == Mode.INSERT) {
                doc.breakUndo();
            }
            this.mode = Mode.NORMAL;
            this.command = "";
            this.waiting = 0;
            return true;
        }
        if (this.mode == Mode.COMMAND) {
            return commandKey(editor, key);
        }
        final boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (control && key == GLFW.GLFW_KEY_R && this.mode == Mode.NORMAL) {
            if (doc.redo()) {
                editor.touched();
                editor.say("1 change; after");
            } else {
                editor.say("Already at newest change");
            }
            return true;
        }
        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> doc.left();
            case GLFW.GLFW_KEY_RIGHT -> doc.right();
            case GLFW.GLFW_KEY_UP -> doc.up();
            case GLFW.GLFW_KEY_DOWN -> doc.down();
            case GLFW.GLFW_KEY_HOME -> doc.setCursor(doc.cursorLine(), 0);
            case GLFW.GLFW_KEY_END -> doc.setCursor(doc.cursorLine(), doc.line(doc.cursorLine()).length());
            case GLFW.GLFW_KEY_PAGE_UP -> doc.setCursor(Math.max(0, doc.cursorLine() - 10), 0);
            case GLFW.GLFW_KEY_PAGE_DOWN -> doc.setCursor(Math.min(doc.lineCount() - 1, doc.cursorLine() + 10), 0);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (this.mode == Mode.INSERT) {
                    doc.newlineIndented(4);
                    editor.touched();
                } else {
                    doc.down();
                }
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (this.mode == Mode.INSERT) {
                    doc.backspace();
                    editor.touched();
                } else {
                    doc.left();
                }
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (this.mode == Mode.INSERT) {
                    doc.delete();
                    editor.touched();
                }
            }
            case GLFW.GLFW_KEY_TAB -> {
                if (this.mode == Mode.INSERT) {
                    for (int i = 0; i < 4; i++) {
                        doc.insert(' ');
                    }
                    editor.touched();
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** A key pressed while a colon command is being typed. */
    private boolean commandKey(final TtyEditor editor, final int key) {
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (this.command.isEmpty()) {
                    this.mode = Mode.NORMAL;
                } else {
                    this.command = this.command.substring(0, this.command.length() - 1);
                }
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> run(editor);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Runs what was typed after the colon. */
    private void run(final TtyEditor editor) {
        final VimCommand asked = VimCommand.of(this.command);
        this.mode = Mode.NORMAL;
        this.command = "";
        if (!asked.ok()) {
            editor.say(asked.error());
            return;
        }
        if (asked.goTo() > 0) {
            final TextDocument doc = editor.document();
            doc.setCursor(Math.min(asked.goTo(), doc.lineCount()) - 1, 0);
            return;
        }
        if (asked.write()) {
            editor.save();
        }
        if (!asked.quit()) {
            return;
        }
        /*
         * Leaving with changes nobody wrote is refused unless it was insisted on, which is the one
         * habit of this editor everybody who has met it remembers.
         */
        if (editor.dirty() && !asked.force()) {
            editor.say(VimCommand.unwritten());
            return;
        }
        editor.quit();
    }
}
