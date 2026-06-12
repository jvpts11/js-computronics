/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.program;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The per-computer state behind the Command Prompt: the command history (so it survives closing the prompt or the Monitor, and a world reload) and the set of programs the player has installed on this computer. Held on the host BlockEntity and saved with its NBT.
 */
public final class ComputerConsoleState {

    public static final int MAX_HISTORY = 100;

    private final Deque<String> history = new ArrayDeque<>();
    private final Set<String> installed = new LinkedHashSet<>();

    /** The command history, oldest first. */
    public List<String> history() {
        return new ArrayList<>(history);
    }

    /** Appends a command, de-duplicating so a repeated command moves to the end, capped at {@link #MAX_HISTORY}. */
    public void pushHistory(final String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        history.remove(line);
        history.addLast(line);
        while (history.size() > MAX_HISTORY) {
            history.removeFirst();
        }
    }

    public Set<String> installed() {
        return Set.copyOf(installed);
    }

    public boolean isInstalled(final String programId) {
        return installed.contains(programId);
    }

    /** Installs a program by id; returns false if it was already installed. */
    public boolean install(final String programId) {
        return installed.add(programId);
    }

    public boolean uninstall(final String programId) {
        return installed.remove(programId);
    }

    public void save(final CompoundTag tag) {
        final ListTag historyTag = new ListTag();
        for (final String line : history) {
            historyTag.add(StringTag.valueOf(line));
        }
        tag.put("History", historyTag);
        final ListTag installedTag = new ListTag();
        for (final String id : installed) {
            installedTag.add(StringTag.valueOf(id));
        }
        tag.put("Installed", installedTag);
    }

    public void load(final CompoundTag tag) {
        history.clear();
        for (final Tag entry : tag.getList("History", Tag.TAG_STRING)) {
            history.addLast(entry.getAsString());
        }
        installed.clear();
        for (final Tag entry : tag.getList("Installed", Tag.TAG_STRING)) {
            installed.add(entry.getAsString());
        }
    }
}
