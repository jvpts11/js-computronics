/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os.edit;

/**
 * What a line typed after a colon in Vim asks for.
 *
 * <p>Reading it is arithmetic over a short string and it is where the mistakes hide: {@code :wq} is two
 * things, {@code :q!} means one thing and {@code :q} another, and a line that means nothing has to say
 * so rather than quietly doing half of it. So it is read here, on its own, where every one of those can
 * be written down as a case.
 *
 * @param goTo the line asked for by a bare number, counted from one, or zero when none was
 */
public record VimCommand(boolean write, boolean quit, boolean force, int goTo, String error) {

    /** A line that asked for nothing anybody knows. */
    public static VimCommand unknown(final String typed) {
        return new VimCommand(false, false, false, 0, "E492: not an editor command: " + typed);
    }

    /** Whether it could be read at all. */
    public boolean ok() {
        return this.error.isEmpty();
    }

    /**
     * Reads the line, without the colon.
     *
     * <p>The forms that exist are the ones a person actually types: write, quit, both, either of the
     * last two insisted on with a bang, and a number to go to that line. Anything else is refused by
     * name, the way the real thing refuses it, instead of being guessed at.
     */
    public static VimCommand of(final String typed) {
        final String line = typed == null ? "" : typed.trim();
        if (!line.isEmpty() && line.chars().allMatch(Character::isDigit)) {
            final int number = line.length() > 6 ? Integer.MAX_VALUE : Integer.parseInt(line);
            return new VimCommand(false, false, false, Math.max(1, number), "");
        }
        return switch (line) {
            case "w" -> new VimCommand(true, false, false, 0, "");
            case "q" -> new VimCommand(false, true, false, 0, "");
            case "q!" -> new VimCommand(false, true, true, 0, "");
            case "wq", "x" -> new VimCommand(true, true, false, 0, "");
            case "wq!", "x!" -> new VimCommand(true, true, true, 0, "");
            case "" -> new VimCommand(false, false, false, 0, "");
            default -> unknown(line);
        };
    }

    /**
     * What to say when a quit was asked for with changes still unwritten and no bang.
     *
     * <p>Vim's own words, because a player who has met Vim knows what they mean and one who has not is
     * told exactly what to do about it.
     */
    public static String unwritten() {
        return "E37: no write since last change (add ! to override)";
    }
}
