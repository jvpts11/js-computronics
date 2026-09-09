/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os.install;

import dev.jstech.computers.os.media.MediaFormat;

/**
 * How long installing a program takes.
 *
 * <p>The program's size, read at the speed of whatever it comes from: a floppy is slow because it is a
 * floppy, and a fibre link is not. The result is clamped so the window is always seen and nobody ever
 * waits for a game, and removing is a fifth of installing, since throwing files away is quicker than
 * copying them. Every figure here is an estimate to tune in play, like every other number.
 */
public final class SetupTiming {

    /** Ticks per second, the world's clock. */
    public static final int TICKS_PER_SECOND = 20;

    /** The least a setup takes, so the window is seen even for a tiny program on a fast medium. */
    public static final int MIN_SECONDS = 3;

    /** The most a setup takes, so a huge program on a floppy does not become a chore. */
    public static final int MAX_SECONDS = 90;

    /** The share of the install time that removing the same program takes. */
    public static final int REMOVE_DIVISOR = 5;

    /** What the network reads at when a program comes from the Mirror rather than a disc. */
    public static final double NETWORK_MB_PER_SECOND = 8.0;

    private SetupTiming() {
    }

    /** What a medium of that format reads at, in megabytes per second. */
    public static double rateOf(final MediaFormat format) {
        return switch (format) {
            case FLOPPY -> 1.0;
            case CD -> 4.0;
            case DVD -> 16.0;
            case USB -> 32.0;
        };
    }

    /**
     * The ticks a setup takes for a program of {@code sizeMb} read at {@code mbPerSecond}.
     *
     * <p>A program that takes no room on the disk is still a setup that is seen, which is what the
     * lower clamp is for; a rate of nothing would take forever, so it is treated as the slowest medium.
     */
    public static int ticks(final int sizeMb, final double mbPerSecond, final boolean removing) {
        final double rate = mbPerSecond > 0 ? mbPerSecond : rateOf(MediaFormat.FLOPPY);
        double seconds = Math.max(0, sizeMb) / rate;
        seconds = Math.max(MIN_SECONDS, Math.min(MAX_SECONDS, seconds));
        if (removing) {
            seconds = Math.max(1.0, seconds / REMOVE_DIVISOR);
        }
        return (int) Math.round(seconds * TICKS_PER_SECOND);
    }

    /** The same, for a program that comes from a disc of that format. */
    public static int ticks(final int sizeMb, final MediaFormat format, final boolean removing) {
        return ticks(sizeMb, rateOf(format), removing);
    }

    /** The same, for a program that comes over the network from the Mirror. */
    public static int networkTicks(final int sizeMb, final boolean removing) {
        return ticks(sizeMb, NETWORK_MB_PER_SECOND, removing);
    }
}
