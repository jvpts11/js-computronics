/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os.install;

/**
 * A program being installed on, or removed from, a machine: what, from where, and how far along.
 *
 * <p>The machine holds it, not the window: it ticks on the server, survives the player walking away
 * and the world unloading, and every window looking at the machine is only a view of it. The install
 * itself happens when the last tick goes, which is what lets Cancel leave nothing behind.
 */
public final class SetupJob {

    private final String programId;
    private final String name;
    private final String house;
    private final int sizeMb;
    private final String source;
    private final boolean removing;
    private final int ticksTotal;
    private int ticksLeft;

    /** The last share of the bar a prompt was told about; not saved, a prompt can miss a step. */
    private int announced;

    /**
     * @param programId the program, as the registry names it
     * @param name      what the window calls it
     * @param house     who publishes it
     * @param sizeMb    what it takes on the disk
     * @param source    where it comes from, worded for the window: "DVD", "the Mirror"
     * @param removing  taking it off rather than putting it on
     * @param ticksTotal how long the whole thing takes
     * @param ticksLeft  how much of that is still to go
     */
    public SetupJob(final String programId, final String name, final String house, final int sizeMb,
                    final String source, final boolean removing, final int ticksTotal, final int ticksLeft) {
        this.programId = programId;
        this.name = name;
        this.house = house;
        this.sizeMb = sizeMb;
        this.source = source;
        this.removing = removing;
        this.ticksTotal = Math.max(1, ticksTotal);
        this.ticksLeft = Math.max(0, Math.min(ticksLeft, this.ticksTotal));
    }

    /** A job at its start. */
    public SetupJob(final String programId, final String name, final String house, final int sizeMb,
                    final String source, final boolean removing, final int ticksTotal) {
        this(programId, name, house, sizeMb, source, removing, ticksTotal, ticksTotal);
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

    /** How far along, in thousandths, which is what a bar and a percentage are drawn from. */
    public int permille() {
        return (int) (1000L * (this.ticksTotal - this.ticksLeft) / this.ticksTotal);
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
     * Whether the bar has crossed the next multiple of {@code stepPermille} since the last time this
     * said yes, which is what spaces the lines a prompt prints out.
     */
    public boolean announce(final int stepPermille) {
        final int step = Math.max(1, stepPermille);
        final int now = this.permille() / step;
        if (now > this.announced) {
            this.announced = now;
            return true;
        }
        return false;
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
