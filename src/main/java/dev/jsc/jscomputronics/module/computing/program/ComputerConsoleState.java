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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The per-computer state behind the Command Prompt: the command history (so it survives closing the prompt or the Monitor, and a world reload) and the set of programs the player has installed on this computer. Held on the host BlockEntity and saved with its NBT.
 */
public final class ComputerConsoleState {

    public static final int MAX_HISTORY = 100;

    private final Deque<String> history = new ArrayDeque<>();
    private final Set<String> installed = new LinkedHashSet<>();
    private String wallpaper = "";
    private String computerName = "";

    /**
     * Free-positioned desktop icon cells, keyed by the icon's stable id ({@code app:<label>} for a program
     * launcher, {@code file:<name>} for a desktop file or folder). Each value packs the grid column in the
     * high 16 bits and the row in the low 16 bits, so the slot stays put across monitor sizes (snap-to-grid).
     * Icons with no entry fall back to the auto-flow layout, exactly like before this was added.
     */
    private final Map<String, Integer> iconCells = new LinkedHashMap<>();

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

    /** The chosen desktop wallpaper id ({@code ""} means the OS default). */
    public String wallpaper() {
        return wallpaper;
    }

    public void setWallpaper(final String id) {
        this.wallpaper = id == null ? "" : id;
    }

    /** The player-given computer name ({@code ""} means unset). */
    public String computerName() {
        return computerName;
    }

    public void setComputerName(final String name) {
        this.computerName = name == null ? "" : name;
    }

    /** Packs a desktop grid column and row into a single value for {@link #iconCells}. */
    public static int packCell(final int column, final int row) {
        return (column << 16) | (row & 0xFFFF);
    }

    public static int cellColumn(final int packed) {
        return packed >> 16;
    }

    public static int cellRow(final int packed) {
        return packed & 0xFFFF;
    }

    /** A read-only view of every pinned desktop icon's cell, keyed by its stable id. */
    public Map<String, Integer> iconCells() {
        return Map.copyOf(iconCells);
    }

    /** Pins a desktop icon ({@code key}) to a packed grid cell, replacing any previous position for it. */
    public void setIconCell(final String key, final int packedCell) {
        if (key != null && !key.isEmpty()) {
            iconCells.put(key, packedCell);
        }
    }

    /** Forgets a pinned icon position (e.g. when its file is deleted or moved off the desktop). */
    public void clearIconCell(final String key) {
        iconCells.remove(key);
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
        if (!wallpaper.isEmpty()) {
            tag.putString("Wallpaper", wallpaper);
        }
        if (!computerName.isEmpty()) {
            tag.putString("ComputerName", computerName);
        }
        if (!iconCells.isEmpty()) {
            final ListTag cells = new ListTag();
            for (final Map.Entry<String, Integer> e : iconCells.entrySet()) {
                final CompoundTag c = new CompoundTag();
                c.putString("Key", e.getKey());
                c.putInt("Cell", e.getValue());
                cells.add(c);
            }
            tag.put("IconCells", cells);
        }
    }

    public void load(final CompoundTag tag) {
        history.clear();
        for (final Tag entry : tag.getList("History", Tag.TAG_STRING)) {
            history.addLast(entry.getAsString());
        }
        // A tampered or legacy tag may hold more entries than the live cap; keep only the most recent.
        while (history.size() > MAX_HISTORY) {
            history.removeFirst();
        }
        installed.clear();
        for (final Tag entry : tag.getList("Installed", Tag.TAG_STRING)) {
            installed.add(entry.getAsString());
        }
        wallpaper = tag.getString("Wallpaper");
        computerName = tag.getString("ComputerName");
        iconCells.clear();
        for (final Tag entry : tag.getList("IconCells", Tag.TAG_COMPOUND)) {
            final CompoundTag c = (CompoundTag) entry;
            final String key = c.getString("Key");
            if (!key.isEmpty()) {
                iconCells.put(key, c.getInt("Cell"));
            }
        }
    }
}
