/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.terminal;

/**
 * The read-only monitoring data a computer exposes to the Monitor terminal's Local tab.
 */
public interface IComputerTerminalHost {

    boolean computerRunning();

    boolean computerBuildValid();

    int networkLinkState();

    long orchestrationCapacity();

    int computerQueues();

    long computerRamBuffer();

    int networkServerCount();

    @org.jetbrains.annotations.Nullable
    dev.jstech.core.uuid.NetworkUuid networkUuid();

    int installedCpus();

    int cpuSlots();

    int installedRam();

    int ramSlots();

    int installedGpus();

    int gpuSlots();

    int installedDisks();

    int diskSlots();

    long localStorageUsed();

    long localStorageCapacity();

    dev.jstech.computronics.storage.IDataSink localStorage();

    dev.jstech.computronics.storage.LocalStore localStore();

    int usableStorageSlots();

    default int pendingOperations() {
        return 0;
    }

    default int runningOperations() {
        return 0;
    }

    default int completedOperations() {
        return 0;
    }

    default int networkPcCount() {
        return 0;
    }

    default int networkSubframeCount() {
        return 0;
    }

    default int indexedTypes() {
        return 0;
    }

    default int indexedServers() {
        return 0;
    }

    default int activeLocks() {
        return 0;
    }

    /**
     * The storage index's health as an {@link dev.jstech.computronics.operation.index.IndexHealth.State}
     * ordinal, so the terminal can show a permanent status strip. Hosts that own no index report OK.
     */
    default int indexHealthState() {
        return dev.jstech.computronics.operation.index.IndexHealth.State.OK.ordinal();
    }

    /** How many item types the index has flagged as unconfirmed or orphaned. */
    default int indexHealthTypeCount() {
        return 0;
    }

    default long networkStorageUsed() {
        return 0L;
    }

    default long networkStorageTotal() {
        return 0L;
    }

    boolean isMainframeHost();

    /**
     * This host's board-derived hardware era, or {@code null} when no motherboard is installed. The terminal uses
     * it to skin the GUI in the host computer's era; a {@code null} era keeps the default look. A board-backed
     * computer overrides this with its installed era; a host with no board reports {@code null}.
     */
    @org.jetbrains.annotations.Nullable
    default dev.jstech.core.tier.HardwareEra installedEra() {
        return null;
    }

    /**
     * The hardware era whose skin the terminal GUI should wear: the chassis era for a per-era computer, or the
     * installed board's era otherwise. Defaults to {@link #installedEra()}; a block entity whose chassis fixes a
     * fixed era overrides this to report that chassis era even when no board is installed.
     */
    @org.jetbrains.annotations.Nullable
    default dev.jstech.core.tier.HardwareEra displayEra() {
        return installedEra();
    }

    /**
     * Whether this computer exposes the per-disk public/private storage slider. A Server's and a Mainframe's own storage is always fully public, so they return {@code false} and the terminal shows a static "always public" badge instead of a dead control.
     */
    default boolean storageHasSlider() {
        return false;
    }

    /** The number of disk slots whose privacy this computer's slider controls (0 when it has no slider). */
    default int diskPrivacyDiskCount() {
        return 0;
    }

    /** The public-share permille (0..1000) of one disk; the private default when out of range. */
    default int diskPrivacyPermille(final int diskIndex) {
        return 0;
    }

    /** The used data weight stored on one disk, for the slider's per-disk readout. */
    default long diskUsedWeight(final int diskIndex) {
        return 0L;
    }

    /** The capacity data weight of one disk, for the slider's per-disk readout. */
    default long diskCapacityWeight(final int diskIndex) {
        return 0L;
    }

    /**
     * This computer's persistent console state — the Command Prompt history and installed programs.
     * A host that cannot store it (none today) returns {@code null} and the console degrades to a
     * fresh, non-persistent session.
     */
    default dev.jstech.computronics.program.ComputerConsoleState console() {
        return null;
    }

    /**
     * This computer's host name, the way the shell's {@code hostname} reports it: the console's computer name,
     * else the custom name from the assembly screen, else the installed system's id, else a plain "computer".
     */
    default String hostname() {
        final dev.jstech.computronics.program.ComputerConsoleState console = console();
        String customName = "";
        String osId = "";
        if (this instanceof dev.jstech.computronics.os.IOsHost computer) {
            customName = computer.customName();
            if (computer.installedOs() != null) {
                osId = computer.installedOs().id().getPath();
            }
        }
        return dev.jstech.computronics.operation.MoveLabels.hostname(
                console == null ? "" : console.computerName(), customName, osId);
    }

    /**
     * The label an Operation's provenance shows for this computer acting through {@code program}, one of the
     * {@link dev.jstech.computronics.operation.MoveLabels} names: {@code "host (program)"}.
     */
    default String originLabel(final String program) {
        return dev.jstech.computronics.operation.MoveLabels.via(hostname(), program);
    }
}
