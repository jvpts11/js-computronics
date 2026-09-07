/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.hardware;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PcieGenerationTest {

    // busFamily(): correct family mapping

    @Test
    void busFamily_isa_returnsIsa() {
        assertEquals(ExpansionBus.ISA, PcieGeneration.ISA.busFamily());
    }

    @Test
    void busFamily_pci_returnsPci() {
        assertEquals(ExpansionBus.PCI, PcieGeneration.PCI.busFamily());
    }

    @Test
    void busFamily_agp4x_returnsAgp() {
        assertEquals(ExpansionBus.AGP, PcieGeneration.AGP_4X.busFamily());
    }

    @Test
    void busFamily_agp8x_returnsAgp() {
        assertEquals(ExpansionBus.AGP, PcieGeneration.AGP_8X.busFamily());
    }

    @Test
    void busFamily_pcieGenerations_allReturnPcie() {
        for (final PcieGeneration gen : new PcieGeneration[]{
                PcieGeneration.PCIE_1_0, PcieGeneration.PCIE_2_0, PcieGeneration.PCIE_3_0,
                PcieGeneration.PCIE_4_0, PcieGeneration.PCIE_5_0, PcieGeneration.PCIE_6_0}) {
            assertEquals(ExpansionBus.PCIE, gen.busFamily(),
                    "expected PCIE family for " + gen);
        }
    }

    // compatibleWith(): same family = compatible, cross-family = incompatible

    @Test
    void compatibleWith_sameFamily_isa() {
        assertTrue(PcieGeneration.ISA.compatibleWith(PcieGeneration.ISA));
    }

    @Test
    void compatibleWith_sameFamily_pci() {
        assertTrue(PcieGeneration.PCI.compatibleWith(PcieGeneration.PCI));
    }

    @Test
    void compatibleWith_agp4xInAgp8xSlot_compatible() {
        assertTrue(PcieGeneration.AGP_4X.compatibleWith(PcieGeneration.AGP_8X));
    }

    @Test
    void compatibleWith_agp8xInAgp4xSlot_compatible() {
        assertTrue(PcieGeneration.AGP_8X.compatibleWith(PcieGeneration.AGP_4X));
    }

    @Test
    void compatibleWith_pcie1InPcie3Slot_compatible() {
        assertTrue(PcieGeneration.PCIE_1_0.compatibleWith(PcieGeneration.PCIE_3_0));
    }

    @Test
    void compatibleWith_pcie5InPcie3Slot_compatible() {
        assertTrue(PcieGeneration.PCIE_5_0.compatibleWith(PcieGeneration.PCIE_3_0));
    }

    @Test
    void compatibleWith_isaInPciSlot_incompatible() {
        assertFalse(PcieGeneration.ISA.compatibleWith(PcieGeneration.PCI));
    }

    @Test
    void compatibleWith_pciInIsaSlot_incompatible() {
        assertFalse(PcieGeneration.PCI.compatibleWith(PcieGeneration.ISA));
    }

    @Test
    void compatibleWith_agpInPcieSlot_incompatible() {
        assertFalse(PcieGeneration.AGP_8X.compatibleWith(PcieGeneration.PCIE_3_0));
    }

    @Test
    void compatibleWith_pcieInAgpSlot_incompatible() {
        assertFalse(PcieGeneration.PCIE_1_0.compatibleWith(PcieGeneration.AGP_4X));
    }

    @Test
    void compatibleWith_isaInPcieSlot_incompatible() {
        assertFalse(PcieGeneration.ISA.compatibleWith(PcieGeneration.PCIE_3_0));
    }

    @Test
    void bandwidthFactorIn_cardInItsOwnSlot_isFullSpeed() {
        assertEquals(1.0, PcieGeneration.PCIE_3_0.bandwidthFactorIn(PcieGeneration.PCIE_3_0));
    }

    @Test
    void bandwidthFactorIn_olderCardInNewerSlot_isFullSpeed() {
        assertEquals(1.0, PcieGeneration.PCIE_1_0.bandwidthFactorIn(PcieGeneration.PCIE_3_0));
    }

    @Test
    void bandwidthFactorIn_oneGenerationBehind_isHalfSpeed() {
        assertEquals(0.5, PcieGeneration.PCIE_3_0.bandwidthFactorIn(PcieGeneration.PCIE_2_0));
    }

    @Test
    void bandwidthFactorIn_twoGenerationsBehind_isQuarterSpeed() {
        assertEquals(0.25, PcieGeneration.PCIE_3_0.bandwidthFactorIn(PcieGeneration.PCIE_1_0));
    }

    @Test
    void bandwidthFactorIn_farBehind_stopsAtTheFloor() {
        // An extreme mismatch stays slow rather than becoming worthless.
        assertEquals(0.125, PcieGeneration.PCIE_6_0.bandwidthFactorIn(PcieGeneration.PCIE_1_0));
    }
}
