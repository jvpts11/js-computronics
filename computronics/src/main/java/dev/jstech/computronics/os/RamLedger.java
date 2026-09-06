/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.os;

import dev.jstech.core.tier.HardwareEra;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * The memory ledger of one computer: what its RAM is spent on, in megabytes, and whether one more thing
 * fits. The system takes its share first, then the desktop it booted, then the services it runs, then the
 * windows the player opened; a script process joins the list the same way. Pure arithmetic: the hosts and
 * the desktop screen fill it from the registries, and the same rules run on both sides.
 */
public final class RamLedger {

    /**
     * A RAM module's marketed size per buffer item it stages: a 4 MB SIMM stages one item, a 2 GB DDR2 stick
     * five hundred and twelve. The buffer is what the network uses; the megabytes are what the programs use.
     */
    public static final int MB_PER_BUFFER_ITEM = 4;

    /** What an entry of the ledger is. */
    public enum Kind { SYSTEM, DESKTOP, SERVICE, WINDOW, PROCESS }

    /** One thing holding memory: a label to show, its megabytes and what it is. */
    public record Entry(String name, int mb, Kind kind) {
    }

    private final int totalMb;
    private final List<Entry> entries = new ArrayList<>();

    public RamLedger(final int totalMb) {
        this.totalMb = Math.max(0, totalMb);
    }

    /** Records {@code mb} held by {@code name}; nothing is recorded for a zero or negative size. */
    public RamLedger add(final String name, final int mb, final Kind kind) {
        if (mb > 0) {
            entries.add(new Entry(name, mb, kind));
        }
        return this;
    }

    public int totalMb() {
        return totalMb;
    }

    public int usedMb() {
        int sum = 0;
        for (final Entry entry : entries) {
            sum += entry.mb;
        }
        return sum;
    }

    /** The megabytes held by entries of one kind. */
    public int usedMb(final Kind kind) {
        int sum = 0;
        for (final Entry entry : entries) {
            if (entry.kind == kind) {
                sum += entry.mb;
            }
        }
        return sum;
    }

    public int freeMb() {
        return Math.max(0, totalMb - usedMb());
    }

    /** Whether {@code mb} more would still fit. */
    public boolean fits(final int mb) {
        return mb <= totalMb - usedMb();
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    /**
     * The megabytes a program of {@code kind} written in {@code era} holds when it declared no size of its own.
     * Newer software is heavier: a Vintage tool is a megabyte, a Standard one nearly a hundred. A service is
     * half of an application of its generation; a desktop environment twice one.
     */
    public static int eraWeightMb(final HardwareEra era, final ProgramKind kind) {
        final int base = switch (era) {
            case VINTAGE -> 1;
            case LEGACY -> 16;
            case STANDARD -> 96;
            case ADVANCED -> 256;
            case EXA -> 512;
            case SINGULARITY -> 1024;
        };
        return switch (kind) {
            case SERVICE -> Math.max(1, base / 2);
            case DESKTOP_ENVIRONMENT -> base * 2;
            default -> base;
        };
    }

    /**
     * The megabytes a program bundled with a system holds: a quarter of the system's own share, never less
     * than one. The same Files is a few megabytes on a nineties system and a couple of hundred on a modern one.
     */
    public static int bundledWeightMb(final int systemRamMb) {
        return Math.max(1, systemRamMb / 4);
    }

    /**
     * The leading items of {@code items} whose weights add up to {@code budgetMb} or less, in order: the
     * first item past the budget and everything after it are dropped, so the oldest windows survive.
     */
    public static <T> List<T> withinBudget(final List<T> items, final ToIntFunction<T> weight, final int budgetMb) {
        final List<T> kept = new ArrayList<>(items.size());
        int used = 0;
        for (final T item : items) {
            final int mb = Math.max(0, weight.applyAsInt(item));
            if (used + mb > budgetMb) {
                break;
            }
            used += mb;
            kept.add(item);
        }
        return kept;
    }
}
