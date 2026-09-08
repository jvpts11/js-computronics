/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.os.edit;

/**
 * What a run of held keys in Emacs asks for.
 *
 * <p>Emacs has no modes: every key types, and a command is a key held with Control or Meta, sometimes
 * two of them in a row. That second half is the part worth keeping honest, because {@code C-x C-s} and
 * {@code C-x C-c} differ by one key and do very different things, and a run that means nothing has to
 * be dropped rather than half-done.
 *
 * <p>The chord is read as text, which is what the editor already has to show in its echo area anyway,
 * so what a player is told they pressed and what actually happens are read from the same string.
 */
public final class EmacsChord {

    /** What a finished chord does. */
    public enum Action {
        /** Nothing yet: the run so far could still become something. */
        PENDING,
        /** {@code C-x C-s}: write the file. */
        SAVE,
        /** {@code C-x C-c}: leave. */
        QUIT,
        /** {@code M-x compile}: read the file and show what the compiler said. */
        COMPILE,
        /** {@code C-g}: forget whatever was half-typed. */
        CANCEL,
        /** The run means nothing anybody knows; say so and forget it. */
        UNKNOWN
    }

    /** How a key is written in a chord: {@code C-x}, {@code M-x}, or the key on its own. */
    public static String key(final String name, final boolean control, final boolean alt) {
        if (control) {
            return "C-" + name;
        }
        return alt ? "M-" + name : name;
    }

    private EmacsChord() {
    }

    /**
     * What the run means, given every key pressed so far, separated by spaces.
     *
     * <p>A run that is on its way to something is {@link Action#PENDING} and keeps collecting; anything
     * that cannot become a command is {@link Action#UNKNOWN} and is thrown away with a word to the
     * player, the way the real thing does rather than quietly eating the keys.
     */
    public static Action of(final String chord) {
        return switch (chord == null ? "" : chord.trim()) {
            case "C-x" -> Action.PENDING;
            case "C-x C-s" -> Action.SAVE;
            case "C-x C-c" -> Action.QUIT;
            case "C-g" -> Action.CANCEL;
            case "M-x" -> Action.PENDING;
            case "M-x compile" -> Action.COMPILE;
            case "" -> Action.PENDING;
            default -> Action.UNKNOWN;
        };
    }

    /**
     * Whether a run could still become a command with more keys.
     *
     * <p>Read from the commands themselves rather than from a second list of prefixes, so a command
     * added above cannot be one nothing ever waits for.
     */
    public static boolean couldGrow(final String chord) {
        final String soFar = chord == null ? "" : chord.trim();
        if (soFar.isEmpty()) {
            return true;
        }
        for (final String known : KNOWN) {
            if (known.startsWith(soFar)) {
                return true;
            }
        }
        return false;
    }

    /** Every run that finishes as a command, which is what a shorter run is measured against. */
    private static final String[] KNOWN = {"C-x C-s", "C-x C-c", "C-g", "M-x compile"};

    /** How the echo area says a run nobody knows, in the words the real thing uses. */
    public static String unknown(final String chord) {
        return chord + " is undefined";
    }
}
