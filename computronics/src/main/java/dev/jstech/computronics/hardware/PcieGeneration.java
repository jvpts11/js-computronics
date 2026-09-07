/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.hardware;

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
     *
     * <p>A newer card in an older slot still runs, and is simply held to the older slot's bandwidth.
     * See {@link #bandwidthFactorIn}.
     */
    public boolean compatibleWith(final PcieGeneration slot) {
        return this.busFamily() == slot.busFamily();
    }

    /**
     * How much of this card's throughput survives in {@code slot}, as a fraction of 1.0. Each bus
     * generation carries roughly twice the bandwidth of the one before it, so a card seated in a slot
     * one generation older gets about half its lane bandwidth, two generations older about a quarter,
     * and so on. A card in a slot of its own generation or newer runs at full speed.
     *
     * <p>The floor keeps an extreme mismatch slow rather than useless: an ancient board is a bad home
     * for a modern card, not a brick wall.
     */
    public double bandwidthFactorIn(final PcieGeneration slot) {
        final int behind = this.ordinal() - slot.ordinal();
        if (behind <= 0) {
            return 1.0;
        }
        return Math.max(0.125, Math.pow(0.5, behind));
    }
}
