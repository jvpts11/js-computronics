/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.hardware;

/**
 * Physical bus families for expansion cards. Cards are keyed to their family and can only be
 * installed in a slot of the same family, regardless of generation within the family.
 *
 * <p>ISA, PCI, and AGP are distinct buses with no cross-family compatibility. PCIe is one family
 * across all generations (1.0 through 6.0) — any PCIe card fits any PCIe slot electrically.
 */
public enum ExpansionBus {
    ISA("ISA"),
    PCI("PCI"),
    AGP("AGP"),
    PCIE("PCIe");

    private final String label;

    ExpansionBus(final String label) {
        this.label = label;
    }

    /** Human-readable slot family name suitable for display in item tooltips. */
    public String label() {
        return label;
    }
}
