/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client.os;

import dev.jstech.computronics.os.edit.EmacsChord;
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

    @Override
    public String status(final TtyEditor editor) {
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

    @Override
    public boolean key(final TtyEditor editor, final int key, final int modifiers) {
        final boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        final boolean alt = (modifiers & GLFW.GLFW_MOD_ALT) != 0;
        final TextDocument doc = editor.document();

        if (control || alt) {
            final String name = nameOf(key);
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
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                doc.newline();
                editor.touched();
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                doc.backspace();
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
    private static String nameOf(final int key) {
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
            return String.valueOf((char) ('a' + key - GLFW.GLFW_KEY_A));
        }
        return "";
    }

    /** Adds a key to the run, and acts once it has become something. */
    private void collect(final TtyEditor editor, final String key, final boolean plain) {
        this.chord = this.chord.isEmpty() ? key : this.chord + (plain ? "" : " ") + key;
        if (plain) {
            /*
             * A word being typed after M-x is only a command once it is whole, so it collects quietly
             * and is judged when the player presses return.
             */
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
        this.chord = "";
        switch (action) {
            case SAVE -> editor.save();
            case QUIT -> editor.quit();
            case CANCEL -> editor.say("Quit");
            case COMPILE -> compile(editor);
            case PENDING, UNKNOWN -> editor.say(EmacsChord.unknown(was));
        }
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
