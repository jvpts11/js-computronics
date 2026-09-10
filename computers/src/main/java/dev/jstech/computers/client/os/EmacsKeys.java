/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client.os;

import dev.jstech.computers.os.edit.EmacsChord;
import dev.jstech.core.client.gui.logic.TextDocument;
import dev.jstech.core.language.IProgrammingLanguage;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.glfw.GLFW;

/**
 * Emacs, as far as a keyboard is concerned.
 *
 * <p>Where the other one has modes, this has none: every key types, and a command is a key held with
 * Control or Meta, sometimes two in a row. What a run of them means is read elsewhere; what is here is
 * collecting the run and doing what it turned out to be.
 *
 * <p>{@code M-x compile} is the one that earns this editor its place: it reads the open file the way
 * the compiler would and puts what it said in a second buffer under it, so the mistake and the line
 * that caused it are on the glass at the same time, on a terminal, which nothing else here can do.
 */
public final class EmacsKeys implements TtyEditor.IKeys {

    /** The keys held so far, written the way the echo area shows them. */
    private String chord = "";
    /** What the last kill took, which is what a yank puts back. */
    private String killed = "";
    /** Whether the echo area is asking about leaving with changes unwritten. */
    private boolean askingToQuit;

    @Override
    public String status(final TtyEditor editor) {
        if (this.askingToQuit) {
            return EmacsChord.modifiedOnQuit();
        }
        if (!this.chord.isEmpty()) {
            return this.chord + "-";
        }
        if (!editor.message().isEmpty()) {
            return editor.message();
        }
        return "-UUU:" + (editor.dirty() ? "**" : "--") + "--F1  " + editor.name();
    }

    @Override
    public boolean typed(final TtyEditor editor, final char c) {
        if (c < 32 || c == 127) {
            return false;
        }
        if (this.askingToQuit) {
            answerQuit(editor, c);
            return true;
        }
        /*
         * While a run is being collected the letters belong to it, which is what makes the "compile"
         * of M-x compile part of the command rather than part of the file.
         */
        if (!this.chord.isEmpty()) {
            collect(editor, String.valueOf(c), true);
            return true;
        }
        editor.document().insert(c);
        editor.touched();
        return true;
    }

    /** The y or n typed to the question about leaving with changes unwritten. */
    private void answerQuit(final TtyEditor editor, final char c) {
        if (c == 'y' || c == 'Y') {
            this.askingToQuit = false;
            editor.quit();
        } else if (c == 'n' || c == 'N') {
            this.askingToQuit = false;
            editor.say("");
        } else {
            editor.say("Please answer y or n.");
        }
    }

    @Override
    public boolean key(final TtyEditor editor, final int key, final int modifiers) {
        final boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        final boolean alt = (modifiers & GLFW.GLFW_MOD_ALT) != 0;
        final TextDocument doc = editor.document();

        if (this.askingToQuit) {
            if (key == GLFW.GLFW_KEY_ESCAPE || (control && key == GLFW.GLFW_KEY_G)) {
                this.askingToQuit = false;
                editor.say("Quit");
            }
            return true;
        }
        if (control || alt) {
            final String name = nameOf(key, modifiers);
            if (!name.isEmpty()) {
                collect(editor, EmacsChord.key(name, control, alt), false);
                return true;
            }
        }
        if (!this.chord.isEmpty() && key == GLFW.GLFW_KEY_ENTER) {
            // A run waiting for the rest of a word is finished by pressing return, as M-x is.
            settle(editor);
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
                doc.newlineIndented(4);
                editor.touched();
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                doc.backspace();
                editor.touched();
            }
            case GLFW.GLFW_KEY_DELETE -> {
                doc.delete();
                editor.touched();
            }
            case GLFW.GLFW_KEY_TAB -> {
                for (int i = 0; i < 4; i++) {
                    doc.insert(' ');
                }
                editor.touched();
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                this.chord = "";
                editor.say("Quit");
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** The name of a key as a chord writes it, or empty for one that is not part of any. */
    private static String nameOf(final int key, final int modifiers) {
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
            return String.valueOf((char) ('a' + key - GLFW.GLFW_KEY_A));
        }
        // M-< and M-> are the comma and the period with Shift held, which is how they are typed.
        final boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (shift && key == GLFW.GLFW_KEY_COMMA) {
            return "<";
        }
        if (shift && key == GLFW.GLFW_KEY_PERIOD) {
            return ">";
        }
        return "";
    }

    /** Adds a key to the run, and acts once it has become something. */
    private void collect(final TtyEditor editor, final String key, final boolean plain) {
        /*
         * A key is its own word in the run, "C-x u", "M-x compile"; the letters of a word typed after
         * one run together. So a letter after a key opens a new word, and a letter after a letter
         * continues the one being typed.
         */
        final String last = this.chord.substring(this.chord.lastIndexOf(' ') + 1);
        final boolean continuing = plain && !this.chord.isEmpty() && !last.startsWith("C-") && !last.startsWith("M-");
        this.chord = this.chord.isEmpty() ? key : this.chord + (continuing ? "" : " ") + key;
        if (plain) {
            /*
             * A word being typed after M-x is only a command once it is whole, so it collects quietly
             * and is judged when the player presses return. The u of C-x u is a whole command by itself.
             */
            if (EmacsChord.of(this.chord) != EmacsChord.Action.PENDING
                    && EmacsChord.of(this.chord) != EmacsChord.Action.UNKNOWN) {
                settle(editor);
                return;
            }
            if (!EmacsChord.couldGrow(this.chord)) {
                editor.say(EmacsChord.unknown(this.chord));
                this.chord = "";
            }
            return;
        }
        settleIfDone(editor);
    }

    private void settleIfDone(final TtyEditor editor) {
        final EmacsChord.Action action = EmacsChord.of(this.chord);
        if (action == EmacsChord.Action.PENDING) {
            return;
        }
        settle(editor);
    }

    /** Does what the run turned out to be, and forgets it. */
    private void settle(final TtyEditor editor) {
        final EmacsChord.Action action = EmacsChord.of(this.chord);
        final String was = this.chord;
        final TextDocument doc = editor.document();
        this.chord = "";
        switch (action) {
            case SAVE -> editor.save();
            case QUIT -> {
                if (editor.dirty()) {
                    this.askingToQuit = true;
                } else {
                    editor.quit();
                }
            }
            case CANCEL -> editor.say("Quit");
            case COMPILE -> compile(editor);
            case BACKWARD_CHAR -> doc.left();
            case FORWARD_CHAR -> doc.right();
            case PREVIOUS_LINE -> doc.up();
            case NEXT_LINE -> doc.down();
            case LINE_START -> doc.setCursor(doc.cursorLine(), 0);
            case LINE_END -> doc.setCursor(doc.cursorLine(), doc.line(doc.cursorLine()).length());
            case BUFFER_START -> doc.setCursor(0, 0);
            case BUFFER_END -> doc.setCursor(doc.lineCount() - 1, doc.line(doc.lineCount() - 1).length());
            case DELETE_CHAR -> {
                doc.delete();
                editor.touched();
            }
            case KILL_LINE -> killLine(editor);
            case YANK -> {
                if (!this.killed.isEmpty()) {
                    doc.insertText(this.killed);
                    editor.touched();
                }
            }
            case UNDO -> {
                if (doc.undo()) {
                    editor.touched();
                    editor.say("Undo");
                } else {
                    editor.say("No further undo information");
                }
            }
            case PENDING, UNKNOWN -> editor.say(EmacsChord.unknown(was));
        }
    }

    /**
     * Cuts from the caret to the end of the line, or the line break when the caret is already there,
     * and keeps what was cut for a yank, the way the real thing does.
     */
    private void killLine(final TtyEditor editor) {
        final TextDocument doc = editor.document();
        final int line = doc.cursorLine();
        final String text = doc.line(line);
        if (doc.cursorCol() < text.length()) {
            doc.select(line, doc.cursorCol(), line, text.length());
            this.killed = doc.selectedText();
            doc.deleteSelection();
        } else if (line + 1 < doc.lineCount()) {
            doc.select(line, text.length(), line + 1, 0);
            this.killed = "\n";
            doc.deleteSelection();
        } else {
            return;
        }
        editor.touched();
    }

    /**
     * Reads the file the way the compiler would and shows what it said underneath.
     *
     * <p>The lower buffer is what makes this editor worth its megabytes on a terminal: the complaint
     * and the line that caused it are readable at once, without leaving and coming back.
     */
    private static void compile(final TtyEditor editor) {
        final IProgrammingLanguage language = CodeWorkspace.languageOf(editor.path());
        if (language == null) {
            editor.showLower("*compilation*", List.of("no compiler knows " + editor.name()));
            editor.say("Compilation finished");
            return;
        }
        final IProgrammingLanguage.CompileResult result = language.compile(
                List.of(new IProgrammingLanguage.SourceText(editor.name(), editor.text())));
        final List<String> out = new ArrayList<>();
        out.add(language.displayName() + " " + editor.name());
        if (result.ok()) {
            out.add(editor.name() + " -> " + result.binary().split("\n", -1).length + " lines of assembly");
            out.add("Compilation finished");
        } else {
            for (final IProgrammingLanguage.Complaint complaint : result.complaints()) {
                out.add(complaint.format());
            }
            out.add("Compilation exited abnormally with " + result.complaints().size() + " error(s)");
        }
        editor.showLower("*compilation*", out);
        editor.say("Compilation finished");
    }
}
