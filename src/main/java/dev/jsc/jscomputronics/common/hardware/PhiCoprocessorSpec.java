/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.hardware;

import dev.jsc.jscomputronics.common.tier.IndustrialTier;

/**
 * A crafting co-processor for the Supercomputer's six dedicated slots.
 */
public record PhiCoprocessorSpec(IndustrialTier tier, int maxSlot, int cores, int mhz, int tdpWatts)
        implements ExpansionCardSpec {

    public static final int SLOT_COUNT = 6;

    public static long craftsForSlot(final int slotIndex) {
        return 8L << slotIndex;
    }

    public PhiCoprocessorSpec {
        if (maxSlot < 1 || maxSlot > SLOT_COUNT) {
            throw new IllegalArgumentException("maxSlot must be 1.." + SLOT_COUNT + ", got " + maxSlot);
        }
    }

    public boolean fitsSlot(final int slotIndex) {
        return slotIndex >= 0 && slotIndex < maxSlot;
    }

    @Override
    public PcieGeneration bus() {
        return PcieGeneration.PCIE_3_0;
    }

    @Override
    public ExpansionCardKind kind() {
        return ExpansionCardKind.PHI;
    }
}
