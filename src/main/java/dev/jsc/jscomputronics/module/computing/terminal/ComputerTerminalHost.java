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

    boolean isMainframeHost();
}
