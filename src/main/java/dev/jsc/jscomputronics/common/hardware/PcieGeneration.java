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

    public boolean fitsInto(final PcieGeneration slot) {
        return this.ordinal() <= slot.ordinal();
    }
}
