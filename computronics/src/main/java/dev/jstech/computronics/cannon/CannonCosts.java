/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What reaching out of a program costs it.
 *
 * <p>A Cannon program is given a share of the tick to spend, and an instruction that only moves numbers
 * about inside it costs one. Asking the machine something costs more, because the machine has to go and
 * look: a glance at what the computer is costs a little, reading the network costs more, and asking the
 * network to actually do something costs most of all. A program that sweeps everything every tick should
 * feel it.
 *
 * <p>The numbers live here so an editor can tell the player what a line will cost before they write it,
 * which is the whole point of an editor knowing the language. What the machine charges is checked
 * against this by a test on a real computer, so the two can never quietly drift apart.
 */
public final class CannonCosts {

    /** A look at something the computer already knows about itself. */
    public static final int GLANCE = 5;
    /** A look at something the network already knows. */
    public static final int GLANCE_NETWORK = 10;
    /** Gathering what the computer holds: its disks, its programs, what it is running. */
    public static final int GATHER = 30;
    /** Reading, whether from a disk or from the network. */
    public static final int READ = 50;
    /** Writing, which the machine cannot take back. */
    public static final int WRITE = 100;
    /** Asking the network to do something, which becomes work for the whole base. */
    public static final int SUBMIT = 200;

    /**
     * What one call costs.
     *
     * @param fixed  what it costs before anything is counted
     * @param perRow whether every row it brings back costs one more, so asking for one thing and asking
     *               for a hundred thousand are not the same question
     */
    public record Cost(int fixed, boolean perRow) {

        /** How a tooltip says it. */
        public String describe() {
            if (this.fixed == 0 && !this.perRow) {
                return "free";
            }
            return this.perRow ? this.fixed + " plus one for every row it brings back"
                    : String.valueOf(this.fixed);
        }

        /** What it comes to when it brought back {@code rows} of them. */
        public int at(final int rows) {
            return this.fixed + (this.perRow ? Math.max(0, rows) : 0);
        }
    }

    private static final Cost FREE = new Cost(0, false);
    private static final Map<String, Cost> COSTS = new LinkedHashMap<>();

    static {
        /*
         * The computer talking about itself. What it is costs a glance; what it holds has to be gathered,
         * because the answer is a list the machine has to walk to build.
         */
        put("Computer", GLANCE, false, "Name", "Cpu", "Os", "RamMb", "FreeRamMb", "Online");
        put("Computer", GATHER, false, "Disks", "Programs", "Processes");

        /*
         * The disk. Asking whether something is there is cheap; reading it costs a read, and writing
         * costs twice that, because a write is a thing the machine cannot take back.
         */
        put("File", GLANCE_NETWORK, false, "Exists");
        put("File", READ, false, "Read", "TryRead", "List");
        put("File", WRITE, false, "Write", "Append", "Delete", "MkDir");

        // The mainframe's own accounting. What it has done is a list, and a list is priced by its length.
        put("Mainframe", GLANCE_NETWORK, false, "Online", "PeakToday");
        put("Mainframe", READ, false, "Stats");
        put("Mainframe", READ, true, "Work");

        /*
         * The network. Whether there is one is a glance; what is on it is a read, and a read that brings
         * back rows is priced by how many.
         */
        put("Network", GLANCE_NETWORK, false, "Online", "Current");
        put("Network", READ, false, "Capacity", "Used", "Total");
        put("Network", READ, true, "Types", "Find", "Servers");
        /*
         * Asking to be told costs nothing, and is meant to: a program that says once that it wants to
         * know when the iron runs low is doing the cheap thing, and one that asks every tick is not.
         */
        put("Network", 0, false, "Watch", "WatchBelow", "WatchAbove");

        // Operations. Asking the network to move or make something is work for the whole base.
        put("Operations", SUBMIT, false, "Pull", "Push", "Craft", "Cancel", "Reprioritise");
        put("Operations", READ, false, "Get");
        put("Operations", READ, true, "List");
    }

    private CannonCosts() {
    }

    private static void put(final String owner, final int fixed, final boolean perRow, final String... members) {
        for (final String member : members) {
            COSTS.put(owner + "." + member, new Cost(fixed, perRow));
        }
    }

    /** What {@code owner.member} costs, or free for anything the machine is not asked about. */
    public static Cost of(final String owner, final String member) {
        return COSTS.getOrDefault(owner + "." + member, FREE);
    }

    /** Whether the machine is asked about that member at all. */
    public static boolean known(final String owner, final String member) {
        return COSTS.containsKey(owner + "." + member);
    }

    /** Every call the machine answers, as {@code Owner.Member}, in the order they were written. */
    public static Iterable<String> all() {
        return COSTS.keySet();
    }
}
