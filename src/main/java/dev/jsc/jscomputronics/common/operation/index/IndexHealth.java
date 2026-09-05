/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.operation.index;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The storage index's own health, tracked so it stops being invisible: hot events (a drive pulled
 * out of a running bay) leave entries the index has not confirmed, and mass removals leave ghost
 * entries behind. Each has its own remedy — a reindex for unconfirmed entries, a vacuum for ghosts
 * — and the surface names exactly which item types are affected so the operator knows what the
 * maintenance run is actually about.
 *
 * <p>Pure state with no Minecraft types: a network's health is a set of flagged type labels plus
 * where each came from, and the severity is derived from which sets are non-empty.
 */
public final class IndexHealth {

    /** What the index is currently worth trusting. */
    public enum State {
        /** Consistent: every entry was confirmed by the last pass. */
        OK,
        /** Hot events left entries the index has not re-read. Recommends a reindex. */
        STALE,
        /** Mass removals left ghost entries pointing at storage that is gone. Recommends a vacuum. */
        FRAGMENTED
    }

    /** One flagged item type and the machine the doubt came from. */
    public record Entry(String type, String source, State severity) {
    }

    // Insertion-ordered so the strip lists what happened in the order it happened.
    private final Map<String, Entry> stale = new LinkedHashMap<>();
    private final Map<String, Entry> ghosts = new LinkedHashMap<>();

    /** Flags item types the index could not confirm after a hot event on {@code source}. */
    public void markStale(final Iterable<String> types, final String source) {
        for (final String type : types) {
            stale.put(type, new Entry(type, source, State.STALE));
        }
    }

    /** Flags item types left pointing at storage that no longer exists. */
    public void markGhosts(final Iterable<String> types, final String source) {
        for (final String type : types) {
            ghosts.put(type, new Entry(type, source, State.FRAGMENTED));
        }
    }

    /**
     * The health to show. Ghost entries outrank unconfirmed ones: a vacuum is the heavier problem
     * and its remedy also settles what a reindex would have found.
     */
    public State state() {
        if (!ghosts.isEmpty()) {
            return State.FRAGMENTED;
        }
        return stale.isEmpty() ? State.OK : State.STALE;
    }

    /** The maintenance action this health recommends, or an empty string when the index is clean. */
    public String recommendedAction() {
        return switch (state()) {
            case OK -> "";
            case STALE -> "REINDEX";
            case FRAGMENTED -> "VACUUM";
        };
    }

    /** Every flagged entry, ghosts first, for the strip's expanded list. */
    public List<Entry> entries() {
        final List<Entry> out = new ArrayList<>(ghosts.size() + stale.size());
        out.addAll(ghosts.values());
        for (final Entry entry : stale.values()) {
            if (!ghosts.containsKey(entry.type())) {
                out.add(entry);
            }
        }
        return out;
    }

    /** The distinct item types the strip names, ghosts first. */
    public Set<String> affectedTypes() {
        final Set<String> types = new LinkedHashSet<>(ghosts.keySet());
        types.addAll(stale.keySet());
        return types;
    }

    public boolean isClean() {
        return stale.isEmpty() && ghosts.isEmpty();
    }

    /** A reindex confirms what the hot events left in doubt; ghost entries are not its business. */
    public void onReindex() {
        stale.clear();
    }

    /** A vacuum drops the ghost entries, and with them the doubt those types carried. */
    public void onVacuum() {
        for (final String type : ghosts.keySet()) {
            stale.remove(type);
        }
        ghosts.clear();
    }

    /** A full rebuild settles everything. */
    public void onFullRebuild() {
        stale.clear();
        ghosts.clear();
    }
}
