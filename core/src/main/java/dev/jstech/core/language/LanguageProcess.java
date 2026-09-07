/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.language;

import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;

/**
 * One program running on one machine, whatever language it was written in.
 *
 * <p>This is everything a computer needs of a running program and nothing else. What a program is made
 * of, how it is interpreted and what it can reach are the language's business; a machine only has to
 * give it a share of the tick, ask how it is doing, and be able to put it away with the world.
 *
 * <p>Nothing here may block. A machine runs its programs on the server thread, so a step spends the
 * instructions it was given and returns, however far through the program that leaves it.
 */
public interface LanguageProcess {

    /** Where a program is up to. */
    enum State {
        /** It has work left to do. */
        RUNNING,
        /** It is waiting on something outside it, and is given no budget until that settles. */
        PARKED,
        /** It ran to the end. */
        FINISHED,
        /** It stopped on a mistake. */
        HALTED
    }

    /**
     * Runs up to {@code budget} instructions and says how many were used.
     *
     * <p>Fewer than asked is a fine answer: a program that finished, parked or stopped uses what it
     * needed and no more, and the machine keeps the rest of the tick.
     */
    int step(int budget);

    /** Where it is up to. */
    State state();

    /** What it said when it stopped, or an empty string while it is still going. */
    String message();

    /** What it has written for a person to read, oldest kept line first. */
    List<String> console();

    /** How many lines it has written since it started, the ones no longer kept included. */
    int written();

    /**
     * How many instructions it has spent since it started.
     *
     * <p>The nearest thing a program has to how long it has been running, and the only honest measure of
     * it: a program on a fast machine gets more done per second than one on a slow machine, and this
     * counts the work, not the seconds.
     */
    int spent();

    /** How many bytes it is holding. */
    long heldBytes();

    /** How many it was allowed. */
    long heapBytes();

    /**
     * Whether this program stays up.
     *
     * <p>One that does is called again for as long as the machine is on; one that does not is done when
     * it returns, and a terminal waiting on it gets its prompt back.
     */
    boolean isService();

    /**
     * Tells a program that stays up that another tick has come.
     *
     * <p>Only ever called on a service that has finished what it was last asked to do. A language whose
     * programs have nothing of the sort need do nothing here.
     */
    void onTick();

    /** Tells it that it is being stopped, and lets it say so, out of a budget of its own. */
    void onStop(int budget);

    /**
     * What this program wants to be told about, as ids the machine can look up in its network.
     *
     * <p>Everything named here is looked up once a tick, however many programs on the machine name it,
     * and handed back through {@link #deliver}.
     */
    default List<String> watching() {
        return List.of();
    }

    /** Hands in what the network now holds of everything this program is watching. */
    default void deliver(final Map<String, Long> totals) {
    }

    /** Writes the whole of it down, so it can be read back after the world has been away. */
    void save(CompoundTag tag);
}
