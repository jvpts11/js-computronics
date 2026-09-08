/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client.os;

import dev.jstech.computronics.os.edit.VimCommand;
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
 * <p>What is here is what a person uses to write a program and get out: moving, opening a line,
 * deleting one, and the colon commands. It is not all of Vim and does not pretend to be.
 */
public final class VimKeys implements TtyEditor.IKeys {

    /** What the editor is in the middle of. */
    private enum Mode { NORMAL, INSERT, COMMAND }

    private Mode mode = Mode.NORMAL;
    /** What has been typed after the colon, before it is run. */
    private String command = "";
    /** The first half of a two-key command, such as the first d of dd. */
    private char waiting;

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
        if (this.waiting == 'd') {
            this.waiting = 0;
            if (c == 'd') {
                deleteLine(editor);
            }
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
            case '0' -> doc.setCursor(doc.cursorLine(), 0);
            case '$' -> doc.setCursor(doc.cursorLine(), doc.line(doc.cursorLine()).length());
            case 'x' -> {
                doc.right();
                doc.backspace();
                editor.touched();
            }
            case 'd' -> this.waiting = 'd';
            case ':' -> {
                this.mode = Mode.COMMAND;
                this.command = "";
                editor.say("");
            }
            default -> { }
        }
    }

    /** Removes the line the caret is on, the way {@code dd} does. */
    private static void deleteLine(final TtyEditor editor) {
        final TextDocument doc = editor.document();
        final int line = doc.cursorLine();
        doc.setCursor(line, doc.line(line).length());
        for (int i = doc.line(line).length(); i > 0; i--) {
            doc.backspace();
        }
        /*
         * The line is empty now, and one more backspace takes the break that made it a line at all,
         * which is what leaves the file with one fewer rather than with a blank in the middle.
         */
        if (doc.lineCount() > 1) {
            doc.backspace();
        }
        editor.touched();
    }

    private void enter(final TtyEditor editor, final Mode next) {
        this.mode = next;
        editor.say("");
    }

    @Override
    public boolean key(final TtyEditor editor, final int key, final int modifiers) {
        final TextDocument doc = editor.document();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            this.mode = Mode.NORMAL;
            this.command = "";
            this.waiting = 0;
            return true;
        }
        if (this.mode == Mode.COMMAND) {
            return commandKey(editor, key);
        }
        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> doc.left();
            case GLFW.GLFW_KEY_RIGHT -> doc.right();
            case GLFW.GLFW_KEY_UP -> doc.up();
            case GLFW.GLFW_KEY_DOWN -> doc.down();
            case GLFW.GLFW_KEY_HOME -> doc.setCursor(doc.cursorLine(), 0);
            case GLFW.GLFW_KEY_END -> doc.setCursor(doc.cursorLine(), doc.line(doc.cursorLine()).length());
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (this.mode == Mode.INSERT) {
                    doc.newline();
                    editor.touched();
                }
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (this.mode == Mode.INSERT) {
                    doc.backspace();
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
