/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.hardware;

import dev.jstech.core.tier.HardwareEra;

import java.util.Objects;
import java.util.Set;

/**
 * Immutable specification of a motherboard, the chassis that bounds an assembly: which CPU socket and how many of them, how many RAM slots and which generations they accept, how many PCIe slots and their bus generation, and how many peripheral ports.
 */
public record MotherboardSpec(FormFactor formFactor,
                              HardwareEra era,
                              CpuSocket socket,
                              int cpuSlots,
                              Set<RamGeneration> acceptedRam,
                              int ramSlots,
                              PcieGeneration pcieGeneration,
                              int pcieSlots,
                              int diskSlots,
                              int peripheralPorts) {

    public MotherboardSpec {
        Objects.requireNonNull(formFactor, "formFactor must not be null");
        Objects.requireNonNull(era, "era must not be null");
        Objects.requireNonNull(socket, "socket must not be null");
        Objects.requireNonNull(pcieGeneration, "pcieGeneration must not be null");
        acceptedRam = Set.copyOf(acceptedRam); // defensive copy + null-checks elements
        if (acceptedRam.isEmpty()) {
            throw new IllegalArgumentException("acceptedRam must not be empty");
        }
        if (cpuSlots <= 0) {
            throw new IllegalArgumentException("cpuSlots must be > 0; got " + cpuSlots);
        }
        if (ramSlots <= 0) {
            throw new IllegalArgumentException("ramSlots must be > 0; got " + ramSlots);
        }
        if (pcieSlots < 0) {
            throw new IllegalArgumentException("pcieSlots must be >= 0; got " + pcieSlots);
        }
        if (diskSlots < 0) {
            throw new IllegalArgumentException("diskSlots must be >= 0; got " + diskSlots);
        }
        if (peripheralPorts < 0) {
            throw new IllegalArgumentException("peripheralPorts must be >= 0; got " + peripheralPorts);
        }
    }
}
