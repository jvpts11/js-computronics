/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.cannon.edit;

import java.util.List;

/**
 * What the player was in the middle of writing when they asked for help.
 *
 * <p>An editor has to answer two questions before it can offer anything: what is being reached into,
 * and how much of the name has already been typed. Both are read backwards from the caret over the line
 * it sits on, which is all the information there is at the moment a list has to appear.
 *
 * <p>Reading text is not the same as understanding it, and this makes no claim to. It says what is
 * written; whether that name means anything is the checker's answer, not this one's.
 */
public final class CompletionContext {

    /**
     * Where a list would be offered.
     *
     * @param receiver what is being reached into, names joined with dots ({@code a.b}), or empty when
     *                 the name stands on its own
     * @param prefix   how much of the name has been typed
     * @param from     the column the prefix starts at, counting from one, which is what a chosen name
     *                 replaces
     */
    public record Where(String receiver, String prefix, int from) {

        /** Whether this is a reach into something rather than a name being typed on its own. */
        public boolean intoMember() {
            return !this.receiver.isEmpty();
        }

        /** The names of the receiver, first to last: {@code a}, {@code b} for {@code a.b}. */
        public List<String> chain() {
            return this.receiver.isEmpty() ? List.of() : List.of(this.receiver.split("\\."));
        }
    }

    private CompletionContext() {
    }

    /**
     * What is being written at {@code caret} on {@code line}, counting the caret in characters from the
     * start of the line, or null where nothing could be offered.
     *
     * <p>Nothing is offered in the middle of a word, because a list that appears while the caret sits
     * inside a name the player already finished is a list in the way.
     */
    public static Where at(final String line, final int caret) {
        if (line == null || caret < 0 || caret > line.length()) {
            return null;
        }
        if (caret < line.length() && isNameChar(line.charAt(caret))) {
            return null;
        }
        int start = caret;
        while (start > 0 && isNameChar(line.charAt(start - 1))) {
            start--;
        }
        final String prefix = line.substring(start, caret);
        if (start > 0 && line.charAt(start - 1) == '.') {
            /*
             * Everything reached through before this: names joined with dots, read back until something
             * that is neither. A call or an index on the way is not read through; the list stops there.
             */
            int owner = start - 1;
            while (owner > 0 && (isNameChar(line.charAt(owner - 1)) || line.charAt(owner - 1) == '.')) {
                owner--;
            }
            String receiver = line.substring(owner, start - 1);
            while (receiver.startsWith(".")) {
                receiver = receiver.substring(1);
            }
            return receiver.isEmpty() || receiver.endsWith(".") || receiver.contains("..")
                    ? null : new Where(receiver, prefix, start + 1);
        }
        /*
         * A bare name is worth offering types for, but only once there is something to narrow them by:
         * every type in the language on an empty line is a list nobody asked for.
         */
        return prefix.isEmpty() ? null : new Where("", prefix, start + 1);
    }

    /** Whether a character can be part of a name. */
    private static boolean isNameChar(final char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
