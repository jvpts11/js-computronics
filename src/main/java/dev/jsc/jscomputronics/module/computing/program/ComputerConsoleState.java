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
    private final ComputerSettings settings = new ComputerSettings();

    /** The per-computer settings owned by the Settings app and the {@code config} command. */
    public ComputerSettings settings() {
        return settings;
    }

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

    // The mod version each installed package was built against. A mod update leaves packages behind
    // their new build, which is what `pckmgr update` exists to reconcile — the same way a real
    // package manager reconciles a repository that moved on without you.
    private final Map<String, String> installedVersions = new LinkedHashMap<>();

    /** The version a package was installed at, or {@code ""} when it predates version tracking. */
    public String installedVersion(final String programId) {
        return installedVersions.getOrDefault(programId, "");
    }

    public void setInstalledVersion(final String programId, final String version) {
        if (version == null || version.isBlank()) {
            installedVersions.remove(programId);
        } else {
            installedVersions.put(programId, version);
        }
    }

    /** Every installed package whose recorded version is not {@code current}. */
    public java.util.List<String> outdatedPackages(final String current) {
        final java.util.List<String> out = new java.util.ArrayList<>();
        for (final String id : installed) {
            if (!current.equals(installedVersions.get(id))) {
                out.add(id);
            }
        }
        return out;
    }

    public boolean uninstall(final String programId) {
        return installed.remove(programId);
    }

    // A live installation medium booted on this computer (the manual Arch / Gentoo install), until it reboots
    // into the installed system. Persisted so a half-done install survives a reload.
    private dev.jsc.jscomputronics.module.computing.program.install.LiveInstallState liveInstall;

    /** The live installation in progress, or null when the computer is not booted from a live medium. */
    public dev.jsc.jscomputronics.module.computing.program.install.LiveInstallState liveInstall() {
        return liveInstall;
    }

    /** Boots a live medium: starts a fresh manual installation of the given distribution. */
    public void startLiveInstall(final dev.jsc.jscomputronics.module.computing.program.install.LiveInstallState.Distro distro) {
        this.liveInstall = new dev.jsc.jscomputronics.module.computing.program.install.LiveInstallState(distro);
    }

    /** Ends the live session (the install completed, or the medium was abandoned). */
    public void clearLiveInstall() {
        this.liveInstall = null;
    }

    // Packages a source-based package manager (emerge) is still compiling: program id -> the game tick at
    // which the build finishes and the program becomes installed. Settled lazily by the shell on the next
    // command, so no per-tick agent is needed.
    private final Map<String, Long> pendingBuilds = new LinkedHashMap<>();

    /** Starts (or restarts) a source build of {@code programId} that completes at game tick {@code readyAtTick}. */
    public void startBuild(final String programId, final long readyAtTick) {
        pendingBuilds.put(programId, readyAtTick);
    }

    /** As {@link #startBuild(String, long)}, also recording the build's full duration for progress lines. */
    public void startBuild(final String programId, final long readyAtTick, final long totalTicks) {
        pendingBuilds.put(programId, readyAtTick);
        buildTotals.put(programId, totalTicks);
    }

    // The full duration of each running build, so the console can print percentage progress. Persisted
    // beside the completion ticks; entries leave with their build.
    private final Map<String, Long> buildTotals = new LinkedHashMap<>();

    /** The full duration in ticks of a running build, or 0 when unknown. */
    public long buildTotal(final String programId) {
        return buildTotals.getOrDefault(programId, 0L);
    }

    /** Cancels a build still compiling; returns whether one was pending. */
    public boolean cancelBuild(final String programId) {
        buildTotals.remove(programId);
        return pendingBuilds.remove(programId) != null;
    }

    /** The builds still compiling: program id to completion tick. */
    public Map<String, Long> pendingBuilds() {
        return java.util.Collections.unmodifiableMap(pendingBuilds);
    }

    /**
     * Moves every build whose completion tick has passed into the installed set, returning the ids that
     * just finished (in start order). Each finished id is also queued for {@link #drainFinishedBuilds()},
     * so the shell can announce it on the player's next command even though the build settled silently.
     */
    public java.util.List<String> settleBuilds(final long nowTick) {
        final java.util.List<String> done = new java.util.ArrayList<>();
        final java.util.Iterator<Map.Entry<String, Long>> it = pendingBuilds.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<String, Long> e = it.next();
            if (e.getValue() <= nowTick) {
                installed.add(e.getKey());
                done.add(e.getKey());
                finishedBuilds.add(e.getKey());
                buildTotals.remove(e.getKey());
                it.remove();
            }
        }
        return done;
    }

    // Builds that finished but have not been announced to the player yet (persisted, so a build that
    // completes while the world is unloaded is still reported the next time the shell is used).
    private final java.util.List<String> finishedBuilds = new java.util.ArrayList<>();

    /** Returns and clears the finished-but-unannounced build ids, in completion order. */
    public java.util.List<String> drainFinishedBuilds() {
        if (finishedBuilds.isEmpty()) {
            return java.util.List.of();
        }
        final java.util.List<String> out = java.util.List.copyOf(finishedBuilds);
        finishedBuilds.clear();
        return out;
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

    // The machine this session is currently ssh'd into, as a packed block position, or null when the
    // shell is local. In memory like the rest of the session: closing the terminal drops the remote
    // shell, exactly as hanging up a real one does.
    private Long sshTarget;

    /** The packed position of the machine this session is connected to, or null when local. */
    @org.jetbrains.annotations.Nullable
    public Long sshTarget() {
        return sshTarget;
    }

    public void setSshTarget(@org.jetbrains.annotations.Nullable final Long packedPos) {
        this.sshTarget = packedPos;
    }

    // The command line's current drive and per-drive current directory (a DOS-style session). Kept in memory:
    // like closing a real terminal, it resets to the boot drive's root when the computer reloads. Each drive
    // remembers its own directory, so switching back to a drive returns to where you left it.
    private char terminalDrive = 'C';
    private final Map<Character, String> terminalDirs = new LinkedHashMap<>();

    /** The terminal session's current drive letter (upper-cased). */
    public char terminalDrive() {
        return terminalDrive;
    }

    /** The current directory of the current drive as a {@code '/'}-separated storage path; {@code ""} is the root. */
    public String terminalDir() {
        return terminalDirs.getOrDefault(terminalDrive, "");
    }

    /** Whether the session has explicitly set a directory on the current drive (false = fresh session). */
    public boolean hasTerminalLocation() {
        return terminalDirs.containsKey(terminalDrive);
    }

    /** Switches the current drive, restoring that drive's remembered directory. */
    public void setTerminalDrive(final char drive) {
        this.terminalDrive = Character.toUpperCase(drive);
    }

    /**
     * Erases everything the software layer remembered, because the disk it conceptually lived on was just
     * formatted: command history, the terminal session's location, installed programs, and any builds. The
     * next system starts from a genuinely clean console.
     */
    public void wipeSoftware() {
        history.clear();
        terminalDirs.clear();
        terminalDrive = 'C';
        installed.clear();
        pendingBuilds.clear();
        buildTotals.clear();
        finishedBuilds.clear();
    }

    /** Sets the current drive and stores that drive's current directory. */
    public void setTerminalLocation(final char drive, final String dir) {
        this.terminalDrive = Character.toUpperCase(drive);
        this.terminalDirs.put(this.terminalDrive, dir == null ? "" : dir);
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
        if (!installedVersions.isEmpty()) {
            final CompoundTag versions = new CompoundTag();
            installedVersions.forEach(versions::putString);
            tag.put("InstalledVersions", versions);
        }
        if (!pendingBuilds.isEmpty()) {
            final CompoundTag builds = new CompoundTag();
            pendingBuilds.forEach(builds::putLong);
            tag.put("PendingBuilds", builds);
        }
        if (!buildTotals.isEmpty()) {
            final CompoundTag totals = new CompoundTag();
            buildTotals.forEach(totals::putLong);
            tag.put("BuildTotals", totals);
        }
        if (!finishedBuilds.isEmpty()) {
            final ListTag finished = new ListTag();
            for (final String id : finishedBuilds) {
                finished.add(StringTag.valueOf(id));
            }
            tag.put("FinishedBuilds", finished);
        }
        if (liveInstall != null) {
            tag.putString("LiveInstall", liveInstall.serialize());
        }
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
        final CompoundTag s = new CompoundTag();
        s.putInt("Accent", settings.accent());
        s.putBoolean("Clock12h", settings.clock12h());
        s.putInt("GuiScale", settings.guiScale());
        s.putInt("Brightness", settings.brightness());
        s.putString("SaveDrive", String.valueOf(settings.defaultSaveDrive()));
        s.putBoolean("RemovableAutoOpen", settings.removableAutoOpen());
        s.putBoolean("TaskbarCentered", settings.taskbarCentered());
        s.putBoolean("DarkMode", settings.darkMode());
        if (!settings.themePreset().isEmpty()) {
            s.putString("Theme", settings.themePreset());
        }
        if (!settings.defaultApps().isEmpty()) {
            final CompoundTag apps = new CompoundTag();
            settings.defaultApps().forEach(apps::putString);
            s.put("DefaultApps", apps);
        }
        tag.put("Settings", s);
    }

    /**
     * Resets every field to its empty value. Defined as loading an empty tag so it can never drift from
     * {@link #load}: a field added there is reset here for free.
     */
    public void clear() {
        load(new CompoundTag());
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
        installedVersions.clear();
        if (tag.contains("InstalledVersions")) {
            final CompoundTag versions = tag.getCompound("InstalledVersions");
            for (final String id : versions.getAllKeys()) {
                installedVersions.put(id, versions.getString(id));
            }
        }
        liveInstall = tag.contains("LiveInstall")
                ? dev.jsc.jscomputronics.module.computing.program.install.LiveInstallState.deserialize(
                        tag.getString("LiveInstall"))
                : null;
        pendingBuilds.clear();
        if (tag.contains("PendingBuilds")) {
            final CompoundTag builds = tag.getCompound("PendingBuilds");
            for (final String id : builds.getAllKeys()) {
                pendingBuilds.put(id, builds.getLong(id));
            }
        }
        buildTotals.clear();
        if (tag.contains("BuildTotals")) {
            final CompoundTag totals = tag.getCompound("BuildTotals");
            for (final String id : totals.getAllKeys()) {
                buildTotals.put(id, totals.getLong(id));
            }
        }
        finishedBuilds.clear();
        for (final Tag entry : tag.getList("FinishedBuilds", Tag.TAG_STRING)) {
            finishedBuilds.add(entry.getAsString());
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
        final CompoundTag s = tag.getCompound("Settings");
        settings.setAccent(s.getInt("Accent"));
        settings.setClock12h(s.getBoolean("Clock12h"));
        settings.setGuiScale(s.getInt("GuiScale"));
        settings.setBrightness(s.contains("Brightness") ? s.getInt("Brightness") : 100);
        final String saveDrive = s.getString("SaveDrive");
        if (!saveDrive.isEmpty()) {
            settings.setDefaultSaveDrive(saveDrive.charAt(0));
        }
        settings.setRemovableAutoOpen(!s.contains("RemovableAutoOpen") || s.getBoolean("RemovableAutoOpen"));
        settings.setTaskbarCentered(!s.contains("TaskbarCentered") || s.getBoolean("TaskbarCentered"));
        settings.setDarkMode(s.getBoolean("DarkMode"));
        settings.setThemePreset(s.getString("Theme"));
        final Map<String, String> apps = new LinkedHashMap<>();
        final CompoundTag appsTag = s.getCompound("DefaultApps");
        for (final String key : appsTag.getAllKeys()) {
            apps.put(key, appsTag.getString(key));
        }
        settings.putDefaultApps(apps);
    }
}
