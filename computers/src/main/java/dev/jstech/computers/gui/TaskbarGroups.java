/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How a panel turns a desktop's windows into one entry per program.
 *
 * <p>A program has one place on the panel however many windows it has open, and a program pinned there
 * keeps its place while it has none. The entries come out in the order the panel shows them: the pinned
 * programs first, in the order they were pinned, then every other open program in the order it was
 * first opened, which is why a window carries the serial it was opened with rather than its place in
 * the stacking order. Pure, so the grouping and the states can be tested without a screen.
 */
public final class TaskbarGroups {

    /** How an entry reads on the panel. */
    public enum State {
        /** Pinned with no window open: the icon alone. */
        PINNED,
        /** One or more windows open, none of them in front. */
        OPEN,
        /** The window in front belongs to this program. */
        ACTIVE,
        /** Every window of the program is minimized. */
        MINIMIZED
    }

    /**
     * One window as the panel needs to know it.
     *
     * @param key       the program the window belongs to (a dialog carries its owner's)
     * @param minimized whether it sits on the panel only
     * @param serial    the order it was opened in, lowest first
     */
    public record Window(String key, boolean minimized, int serial) {
    }

    /**
     * One entry on the panel.
     *
     * @param key     the program
     * @param windows how many windows it has open, dialogs included
     * @param pinned  whether it keeps its place with no window open
     * @param state   how it reads
     */
    public record Entry(String key, int windows, boolean pinned, State state) {

        /** Whether the entry has a window at all. */
        public boolean open() {
            return windows > 0;
        }
    }

    private TaskbarGroups() {
    }

    /**
     * The entries for {@code windows}, given the programs {@code pinned} to the panel and the program
     * whose window is in front ({@code frontKey}, null when none is).
     */
    public static List<Entry> group(final List<Window> windows, final List<String> pinned, final String frontKey) {
        final Map<String, List<Window>> byKey = new LinkedHashMap<>();
        for (final String key : pinned) {
            if (!key.isEmpty()) {
                byKey.putIfAbsent(key, new ArrayList<>());
            }
        }
        final List<Window> opened = new ArrayList<>(windows);
        opened.sort((a, b) -> Integer.compare(a.serial(), b.serial()));
        for (final Window window : opened) {
            byKey.computeIfAbsent(window.key(), k -> new ArrayList<>()).add(window);
        }
        final List<Entry> out = new ArrayList<>(byKey.size());
        for (final Map.Entry<String, List<Window>> e : byKey.entrySet()) {
            final List<Window> mine = e.getValue();
            final boolean isPinned = pinned.contains(e.getKey());
            out.add(new Entry(e.getKey(), mine.size(), isPinned, stateOf(e.getKey(), mine, frontKey)));
        }
        return out;
    }

    private static State stateOf(final String key, final List<Window> mine, final String frontKey) {
        if (mine.isEmpty()) {
            return State.PINNED;
        }
        if (key.equals(frontKey)) {
            return State.ACTIVE;
        }
        for (final Window window : mine) {
            if (!window.minimized()) {
                return State.OPEN;
            }
        }
        return State.MINIMIZED;
    }

    /** The index of the entry for {@code key} in {@code entries}, or -1 when there is none. */
    public static int indexOf(final List<Entry> entries, final String key) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).key().equals(key)) {
                return i;
            }
        }
        return -1;
    }
}
