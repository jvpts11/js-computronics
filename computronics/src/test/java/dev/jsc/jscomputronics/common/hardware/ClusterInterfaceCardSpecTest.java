/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.hardware;

import dev.jsc.jscomputronics.common.hardware.ClusterInterfaceCardSpec.Reach;
import dev.jstech.core.tier.HardwareEra;
import dev.jstech.core.tier.IndustrialTier;
import dev.jsc.jscomputronics.module.computing.rack.RackChassis.RackType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClusterInterfaceCardSpecTest {

    @Test
    void covers_datacentersReachOnlyServerCabinets() {
        assertTrue(Reach.DATACENTERS.covers(RackType.SERVER));
        assertFalse(Reach.DATACENTERS.covers(RackType.SUPERCOMPUTER));
        assertFalse(Reach.DATACENTERS.covers(RackType.AI));
    }

    @Test
    void covers_supercomputersReachServersAndFabrics() {
        assertTrue(Reach.SUPERCOMPUTERS.covers(RackType.SERVER));
        assertTrue(Reach.SUPERCOMPUTERS.covers(RackType.SUPERCOMPUTER));
        assertFalse(Reach.SUPERCOMPUTERS.covers(RackType.AI));
    }

    @Test
    void covers_allReachesEveryCabinetKind() {
        for (final RackType kind : RackType.values()) {
            assertTrue(Reach.ALL.covers(kind), kind.name());
        }
    }

    @Test
    void covers_eachLevelIncludesTheOnesBeforeIt() {
        for (final RackType kind : RackType.values()) {
            if (Reach.DATACENTERS.covers(kind)) {
                assertTrue(Reach.SUPERCOMPUTERS.covers(kind), kind.name());
            }
            if (Reach.SUPERCOMPUTERS.covers(kind)) {
                assertTrue(Reach.ALL.covers(kind), kind.name());
            }
        }
    }

    @Test
    void kind_isTheClusterInterface() {
        final ClusterInterfaceCardSpec spec = new ClusterInterfaceCardSpec(
                HardwareEra.STANDARD, IndustrialTier.T4, PcieGeneration.PCIE_3_0, Reach.ALL, 4, 35);
        assertEquals(ExpansionCardKind.CLUSTER_INTERFACE, spec.kind());
    }

    @Test
    void constructor_rejectsACardThatWritesNoNodes() {
        assertThrows(IllegalArgumentException.class, () -> new ClusterInterfaceCardSpec(
                HardwareEra.VINTAGE, IndustrialTier.T2, PcieGeneration.PCI, Reach.DATACENTERS, 0, 10));
    }

    @Test
    void constructor_rejectsANegativePowerDraw() {
        assertThrows(IllegalArgumentException.class, () -> new ClusterInterfaceCardSpec(
                HardwareEra.VINTAGE, IndustrialTier.T2, PcieGeneration.PCI, Reach.DATACENTERS, 1, -1));
    }
}
