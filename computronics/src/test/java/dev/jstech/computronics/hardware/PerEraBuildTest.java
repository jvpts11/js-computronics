/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.hardware;

import dev.jstech.core.tier.HardwareEra;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One representative build per shipped hardware era (Vintage, Legacy, Standard), asserting that the
 * validation rules hold across the progression: a coherent era build powers on, a socket mismatch is
 * rejected, and a wrong RAM generation is rejected. The specs mirror the registered item catalog; the
 * pure-logic layer cannot touch the Minecraft item wrappers, so it works on the records.
 */
class PerEraBuildTest {

    private static PsuSpec psu(final int watts) {
        return new PsuSpec(watts, 90);
    }

    private static ComputerBuild build(final MotherboardSpec board, final CpuSpec cpu,
                                       final RamSpec ram, final PsuSpec psu) {
        return new ComputerBuild(board, List.of(cpu), List.of(), List.of(ram), psu);
    }

    // ---- Vintage ----

    private static MotherboardSpec vintageBoard() {
        return new MotherboardSpec(FormFactor.BABY_AT, HardwareEra.VINTAGE, CpuSocket.SOCKET_3, 1,
                Set.of(RamGeneration.SIMM), 4, PcieGeneration.PCI, 4, 2, 2);
    }

    private static CpuSpec vintageCpu() {
        return new CpuSpec(HardwareEra.VINTAGE, CpuSocket.SOCKET_3, 1, 100, 5, false);
    }

    private static RamSpec vintageRam() {
        return new RamSpec(HardwareEra.VINTAGE, RamGeneration.SIMM, 1, 1);
    }

    @Test
    void vintageBuild_isPowered() {
        assertTrue(build(vintageBoard(), vintageCpu(), vintageRam(), psu(300)).isPowered());
    }

    @Test
    void vintageBuild_wrongSocket_isNotPowered() {
        final CpuSpec wrong = new CpuSpec(HardwareEra.VINTAGE, CpuSocket.SOCKET_7, 1, 350, 15, false);
        assertFalse(build(vintageBoard(), wrong, vintageRam(), psu(300)).isPowered());
    }

    @Test
    void vintageBuild_wrongRamGeneration_isNotPowered() {
        final RamSpec ddr = new RamSpec(HardwareEra.LEGACY, RamGeneration.DDR, 128, 10);
        assertFalse(build(vintageBoard(), vintageCpu(), ddr, psu(300)).isPowered());
    }

    // ---- Legacy ----

    private static MotherboardSpec legacyBoard() {
        return new MotherboardSpec(FormFactor.ATX, HardwareEra.LEGACY, CpuSocket.LGA_775, 1,
                Set.of(RamGeneration.DDR, RamGeneration.DDR2), 4, PcieGeneration.PCIE_1_0, 4, 4, 4);
    }

    private static CpuSpec legacyCpu() {
        return new CpuSpec(HardwareEra.LEGACY, CpuSocket.LGA_775, 2, 2400, 65, false);
    }

    private static RamSpec legacyRam() {
        return new RamSpec(HardwareEra.LEGACY, RamGeneration.DDR2, 512, 12);
    }

    @Test
    void legacyBuild_isPowered() {
        assertTrue(build(legacyBoard(), legacyCpu(), legacyRam(), psu(500)).isPowered());
    }

    @Test
    void legacyBuild_wrongSocket_isNotPowered() {
        final CpuSpec wrong = new CpuSpec(HardwareEra.LEGACY, CpuSocket.SOCKET_A, 1, 2000, 65, false);
        assertFalse(build(legacyBoard(), wrong, legacyRam(), psu(500)).isPowered());
    }

    @Test
    void legacyBuild_wrongRamGeneration_isNotPowered() {
        final RamSpec ddr3 = new RamSpec(HardwareEra.STANDARD, RamGeneration.DDR3, 2048, 15);
        assertFalse(build(legacyBoard(), legacyCpu(), ddr3, psu(500)).isPowered());
    }

    // ---- Standard ----

    private static MotherboardSpec standardBoard() {
        return new MotherboardSpec(FormFactor.ATX, HardwareEra.STANDARD, CpuSocket.LGA_1150, 1,
                Set.of(RamGeneration.DDR3), 4, PcieGeneration.PCIE_3_0, 4, 2, 4);
    }

    private static CpuSpec standardCpu() {
        return new CpuSpec(HardwareEra.STANDARD, CpuSocket.LGA_1150, 4, 4000, 88, false);
    }

    private static RamSpec standardRam() {
        return new RamSpec(HardwareEra.STANDARD, RamGeneration.DDR3, 2048, 15);
    }

    @Test
    void standardBuild_isPowered() {
        assertTrue(build(standardBoard(), standardCpu(), standardRam(), psu(650)).isPowered());
    }

    @Test
    void standardBuild_wrongSocket_isNotPowered() {
        final CpuSpec wrong = new CpuSpec(HardwareEra.STANDARD, CpuSocket.AM3, 4, 3400, 125, false);
        assertFalse(build(standardBoard(), wrong, standardRam(), psu(650)).isPowered());
    }

    @Test
    void standardBuild_wrongRamGeneration_isNotPowered() {
        // A Standard board wants DDR3; an older DDR2 module from the Legacy era must not power it.
        final RamSpec ddr2 = new RamSpec(HardwareEra.LEGACY, RamGeneration.DDR2, 512, 12);
        assertFalse(build(standardBoard(), standardCpu(), ddr2, psu(650)).isPowered());
    }
}
