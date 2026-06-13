/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.terminal;

/**
 * The read-only monitoring data a computer exposes to the Monitor terminal's Local tab.
 */
public interface ComputerTerminalHost {

    boolean computerRunning();

    boolean computerBuildValid();

    int networkLinkState();

    long orchestrationCapacity();

    int computerQueues();

    long computerRamBuffer();

    int networkServerCount();

    @org.jetbrains.annotations.Nullable
    dev.jsc.jscomputronics.common.uuid.NetworkUuid networkUuid();

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

    dev.jsc.jscomputronics.module.computing.storage.DataSink localStorage();

    dev.jsc.jscomputronics.module.computing.storage.LocalStore localStore();

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
    default dev.jsc.jscomputronics.common.tier.HardwareEra installedEra() {
        return null;
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
    default dev.jsc.jscomputronics.module.computing.program.ComputerConsoleState console() {
        return null;
    }
}
