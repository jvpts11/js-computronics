/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.blockentity;

/**
 * The hardware-slot map of a computer block: where each component category sits in the assembly {@code ItemStackHandler}.
 */
public record ComputerHardwareLayout(
        int motherboardSlot,
        int cpuStart, int cpuCount,
        int ramStart, int ramCount,
        int pcieStart, int pcieCount,
        int psuSlot,
        int diskStart, int diskCount,
        int totalSlots) {

    public boolean isCpu(final int slot) {
        return slot >= cpuStart && slot < cpuStart + cpuCount;
    }

    public boolean isRam(final int slot) {
        return slot >= ramStart && slot < ramStart + ramCount;
    }

    public boolean isPcie(final int slot) {
        return slot >= pcieStart && slot < pcieStart + pcieCount;
    }

    public boolean isDisk(final int slot) {
        return slot >= diskStart && slot < diskStart + diskCount;
    }
}
