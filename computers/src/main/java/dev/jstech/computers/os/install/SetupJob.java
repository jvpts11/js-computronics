/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os.install;

/**
 * One program being set up on one machine: what it is, where it comes from, how it was asked for,
 * and how far along it is.
 *
 * <p>Pure logic: the job counts ticks and answers questions about them; who ticks it and who shows
 * it are the machine's and the windows' business. It is saved with the machine, so a setup half way
 * through when the world went away carries on where it was.
 */
public final class SetupJob {

    /** Asked for with the disc's setup program, This PC, or the prompt's {@code install}. */
    public static final String VIA_SETUP = "setup";
    /** Asked for with the prompt's {@code install} verb, from a disc in a linked drive. */
    public static final String VIA_INSTALL = "install";
    /** Asked for with the Frames package manager, from the Mirror. */
    public static final String VIA_PCKMGR = "pckmgr";

    private final String programId;
    private final String name;
    private final String house;
    private final int sizeMb;
    private final String source;
    private final boolean removing;
    private final int ticksTotal;
    private int ticksLeft;
    /** How it was asked for: a setup program, the install verb, or a package manager's command word. */
    private final String via;
    /** The package's short name, the word the prompt and the managers use for it. */
    private final String packageName;
    /** Whether a prompt has drawn the bar once; not saved, a prompt can draw it again from scratch. */
    private boolean barDrawn;

    /**
     * @param programId   the program, as the registry names it
     * @param name        what the window calls it
     * @param house       who publishes it
     * @param sizeMb      what it takes on the disk
     * @param source      where it comes from, worded for the window: "DVD", "the Mirror"
     * @param removing    taking it off rather than putting it on
     * @param ticksTotal  how long the whole thing takes
     * @param ticksLeft   how much of that is still to go
     * @param via         how it was asked for, which decides what the prompt prints
     * @param packageName the package's short name
     */
    public SetupJob(final String programId, final String name, final String house, final int sizeMb,
                    final String source, final boolean removing, final int ticksTotal, final int ticksLeft,
                    final String via, final String packageName) {
        this.programId = programId;
        this.name = name;
        this.house = house;
        this.sizeMb = sizeMb;
        this.source = source;
        this.removing = removing;
        this.ticksTotal = Math.max(1, ticksTotal);
        this.ticksLeft = Math.max(0, Math.min(ticksLeft, this.ticksTotal));
        this.via = via == null || via.isEmpty() ? VIA_SETUP : via;
        this.packageName = packageName == null || packageName.isEmpty() ? pathOf(programId) : packageName;
    }

    /** A job part way through, asked for with a setup program. */
    public SetupJob(final String programId, final String name, final String house, final int sizeMb,
                    final String source, final boolean removing, final int ticksTotal, final int ticksLeft) {
        this(programId, name, house, sizeMb, source, removing, ticksTotal, ticksLeft, VIA_SETUP, "");
    }

    /** A job at its start, asked for with a setup program. */
    public SetupJob(final String programId, final String name, final String house, final int sizeMb,
                    final String source, final boolean removing, final int ticksTotal) {
        this(programId, name, house, sizeMb, source, removing, ticksTotal, ticksTotal, VIA_SETUP, "");
    }

    /** A job at its start, asked for that way. */
    public SetupJob(final String programId, final String name, final String house, final int sizeMb,
                    final String source, final boolean removing, final int ticksTotal, final String via,
                    final String packageName) {
        this(programId, name, house, sizeMb, source, removing, ticksTotal, ticksTotal, via, packageName);
    }

    private static String pathOf(final String id) {
        final int colon = id == null ? -1 : id.indexOf(':');
        return id == null ? "" : colon >= 0 ? id.substring(colon + 1) : id;
    }

    public String programId() {
        return this.programId;
    }

    public String name() {
        return this.name;
    }

    public String house() {
        return this.house;
    }

    public int sizeMb() {
        return this.sizeMb;
    }

    public String source() {
        return this.source;
    }

    public boolean removing() {
        return this.removing;
    }

    public int ticksTotal() {
        return this.ticksTotal;
    }

    public int ticksLeft() {
        return this.ticksLeft;
    }

    /** How it was asked for: {@link #VIA_SETUP}, {@link #VIA_INSTALL}, or a package manager's command word. */
    public String via() {
        return this.via;
    }

    /** The package's short name, the word the prompt and the managers use for it. */
    public String packageName() {
        return this.packageName;
    }

    /** How far along, in thousandths, which is what a bar and a percentage are drawn from. */
    public int permille() {
        return (int) (1000L * (this.ticksTotal - this.ticksLeft) / this.ticksTotal);
    }

    /** The ticks gone by since it began. */
    public int ticksDone() {
        return this.ticksTotal - this.ticksLeft;
    }

    /** Whether the last tick has gone. */
    public boolean finished() {
        return this.ticksLeft <= 0;
    }

    /** One tick of copying; true when this one was the last. */
    public boolean tick() {
        if (this.ticksLeft > 0) {
            this.ticksLeft--;
        }
        return this.ticksLeft == 0;
    }

    /**
     * Says the bar is about to be drawn at a prompt, and whether this is the first time.
     *
     * <p>The first drawing goes on a line of its own; every one after it goes over that line, the way a
     * bar at a real terminal grows in place rather than filling the screen with copies of itself.
     */
    public boolean drawBar() {
        final boolean first = !this.barDrawn;
        this.barDrawn = true;
        return first;
    }

    /** The seconds still to go, rounded up, for a window that says "about 20 seconds left". */
    public int secondsLeft() {
        return (this.ticksLeft + SetupTiming.TICKS_PER_SECOND - 1) / SetupTiming.TICKS_PER_SECOND;
    }

    /**
     * What the window says is happening right now.
     *
     * <p>A real setup names the file it is copying; this names the stages the disc's own layout has,
     * in the order a setup went through them, so the line changes as the bar moves.
     */
    public String phase() {
        if (this.removing) {
            return this.permille() < 500 ? "Removing files" : "Cleaning up";
        }
        final int p = this.permille();
        if (p < 100) {
            return "Preparing to install";
        }
        if (p < 850) {
            return "Copying files";
        }
        if (p < 1000) {
            return "Registering " + this.name;
        }
        return "Finishing";
    }
}
