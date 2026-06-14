/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.hardware;

/**
 * Expansion-bus generations for graphics/expansion cards, oldest to newest (the legacy ISA/PCI/AGP buses precede the PCIe line).
 */
public enum PcieGeneration {
    ISA,
    PCI,
    AGP_4X,
    AGP_8X,
    PCIE_1_0,
    PCIE_2_0,
    PCIE_3_0,
    PCIE_4_0,
    PCIE_5_0,
    PCIE_6_0;

    /** The physical bus family this generation belongs to. */
    public ExpansionBus busFamily() {
        return switch (this) {
            case ISA -> ExpansionBus.ISA;
            case PCI -> ExpansionBus.PCI;
            case AGP_4X, AGP_8X -> ExpansionBus.AGP;
            case PCIE_1_0, PCIE_2_0, PCIE_3_0, PCIE_4_0, PCIE_5_0, PCIE_6_0 -> ExpansionBus.PCIE;
        };
    }

    /**
     * Whether this card bus is compatible with the given motherboard slot. ISA, PCI, and AGP are
     * physically distinct and reject each other; all PCIe generations are cross-compatible.
     */
    public boolean compatibleWith(final PcieGeneration slot) {
        return this.busFamily() == slot.busFamily();
    }
}
