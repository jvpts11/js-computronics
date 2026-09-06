/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.run;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything a process has allocated, counted to the byte.
 *
 * <p>The language has no collector on purpose: what a program allocates stays until it says
 * otherwise, and the count is exact so the number the player reads is the number that is true. That
 * is only worth doing if the arithmetic is the same everywhere, so the sizes live here and nowhere
 * else.
 *
 * <p>The heap also remembers which line each allocation came from. That costs a little and buys the
 * one thing a player needs when a process runs out: the three lines that asked for the most.
 */
public final class Heap {

    /** What every object costs before its own contents. */
    public static final int HEADER = 16;

    /** What a reference costs inside an object. */
    public static final int REFERENCE = 8;

    /** How many of the biggest allocating lines are named when a process runs out. */
    private static final int NAMED_LINES = 3;

    /** One thing on the heap: how big it is, where it was made, and whether it has been freed. */
    private static final class Entry {
        private long bytes;
        private final int line;
        private boolean freed;

        Entry(final long bytes, final int line) {
            this.bytes = bytes;
            this.line = line;
        }
    }

    private final Map<Object, Entry> live = new IdentityHashMap<>();
    private final long budget;
    private long used;

    public Heap(final long budget) {
        this.budget = budget;
    }

    /** How many bytes this process may hold at once. */
    public long budget() {
        return this.budget;
    }

    /** How many bytes it is holding. */
    public long used() {
        return this.used;
    }

    /** How many it could still take. */
    public long free() {
        return Math.max(0, this.budget - this.used);
    }

    /**
     * Records something new and gives it back. Halts when it would not fit, naming what was live and
     * the lines that asked for the most, because that is what tells a player where to look.
     */
    public <T> T allocate(final T value, final long bytes, final int line) {
        if (this.used + bytes > this.budget) {
            throw new Halt(Halt.Reason.OUT_OF_MEMORY, line, this.outOfMemory(bytes));
        }
        this.used += bytes;
        this.live.put(value, new Entry(bytes, line));
        return value;
    }

    /** Makes something already recorded bigger or smaller, as a collection does when it changes. */
    public void resize(final Object value, final long bytes, final int line) {
        final Entry entry = this.live.get(value);
        if (entry == null) {
            return;
        }
        final long after = this.used - entry.bytes + bytes;
        if (after > this.budget) {
            throw new Halt(Halt.Reason.OUT_OF_MEMORY, line, this.outOfMemory(bytes - entry.bytes));
        }
        this.used = after;
        entry.bytes = bytes;
    }

    /**
     * Frees something. A second free of the same thing is not a mistake in itself, because the
     * reference that named it is set to null by the same statement; using a freed one is.
     */
    public void dispose(final Object value, final int line) {
        final Entry entry = this.live.get(value);
        if (entry == null || entry.freed) {
            return;
        }
        entry.freed = true;
        this.used -= entry.bytes;
    }

    /** Whether this was freed and may no longer be read. */
    public boolean isFreed(final Object value) {
        final Entry entry = this.live.get(value);
        return entry != null && entry.freed;
    }

    /** Everything still held, biggest first, for the console to show. */
    public List<String> liveByLine() {
        final Map<Integer, Long> byLine = new LinkedHashMap<>();
        for (final Entry entry : this.live.values()) {
            if (!entry.freed) {
                byLine.merge(entry.line, entry.bytes, Long::sum);
            }
        }
        final List<Map.Entry<Integer, Long>> sorted = new ArrayList<>(byLine.entrySet());
        sorted.sort(Comparator.<Map.Entry<Integer, Long>>comparingLong(Map.Entry::getValue).reversed());
        final List<String> lines = new ArrayList<>();
        for (int i = 0; i < Math.min(NAMED_LINES, sorted.size()); i++) {
            lines.add("line " + sorted.get(i).getKey() + " holds " + sorted.get(i).getValue() + " bytes");
        }
        return lines;
    }

    private String outOfMemory(final long wanted) {
        final StringBuilder message = new StringBuilder("out of memory: ")
                .append(this.used).append(" of ").append(this.budget)
                .append(" bytes are live and ").append(wanted).append(" more were asked for");
        for (final String line : this.liveByLine()) {
            message.append("; ").append(line);
        }
        return message.toString();
    }

    /** What a value of that type costs inside an object or an array. */
    public static int sizeOf(final String type) {
        return switch (type) {
            case "int", "float", "bool", "char" -> 4;
            case "long", "double" -> 8;
            default -> REFERENCE;
        };
    }

    /** What a piece of text costs: its header and two bytes for every character. */
    public static long sizeOfText(final String value) {
        return HEADER + 2L * value.length();
    }
}
