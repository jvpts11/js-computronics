/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.hardware;

import dev.jsc.jscomputronics.common.tier.HardwareEra;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One representative build per hardware era, asserting that the validation rules hold for the whole
 * progression: a coherent era build powers on, a socket mismatch is rejected, a wrong RAM generation is
 * rejected, and the Singularity alien CPU/PSU behave as specified. The specs mirror the registered item
 * catalog; the pure-logic layer cannot touch the Minecraft item wrappers, so it works on the records.
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
        final RamSpec ddr4 = new RamSpec(HardwareEra.ADVANCED, RamGeneration.DDR4, 4096, 20);
        assertFalse(build(standardBoard(), standardCpu(), ddr4, psu(650)).isPowered());
    }

    // ---- Advanced ----

    private static MotherboardSpec advancedBoard() {
        return new MotherboardSpec(FormFactor.MTX, HardwareEra.ADVANCED, CpuSocket.SP3, 4,
                Set.of(RamGeneration.DDR4), 24, PcieGeneration.PCIE_4_0, 12, 6, 8);
    }

    private static CpuSpec advancedCpu() {
        return new CpuSpec(HardwareEra.ADVANCED, CpuSocket.SP3, 64, 2450, 280, false);
    }

    private static RamSpec advancedRam() {
        return new RamSpec(HardwareEra.ADVANCED, RamGeneration.DDR4, 4096, 20);
    }

    @Test
    void advancedBuild_isPowered() {
        assertTrue(build(advancedBoard(), advancedCpu(), advancedRam(), psu(1600)).isPowered());
    }

    @Test
    void advancedBuild_wrongSocket_isNotPowered() {
        final CpuSpec wrong = new CpuSpec(HardwareEra.ADVANCED, CpuSocket.AM4, 16, 3400, 105, false);
        assertFalse(build(advancedBoard(), wrong, advancedRam(), psu(1600)).isPowered());
    }

    @Test
    void advancedBuild_wrongRamGeneration_isNotPowered() {
        final RamSpec ddr5 = new RamSpec(HardwareEra.EXA, RamGeneration.DDR5, 8192, 30);
        assertFalse(build(advancedBoard(), advancedCpu(), ddr5, psu(1600)).isPowered());
    }

    // ---- Exa ----

    private static MotherboardSpec exaBoard() {
        return new MotherboardSpec(FormFactor.MTX, HardwareEra.EXA, CpuSocket.SP5, 4,
                Set.of(RamGeneration.DDR5), 24, PcieGeneration.PCIE_5_0, 16, 6, 8);
    }

    private static CpuSpec exaCpu() {
        return new CpuSpec(HardwareEra.EXA, CpuSocket.SP5, 96, 2400, 360, false);
    }

    private static RamSpec exaRam() {
        return new RamSpec(HardwareEra.EXA, RamGeneration.DDR5, 8192, 30);
    }

    @Test
    void exaBuild_isPowered() {
        assertTrue(build(exaBoard(), exaCpu(), exaRam(), psu(3000)).isPowered());
    }

    @Test
    void exaBuild_wrongSocket_isNotPowered() {
        final CpuSpec wrong = new CpuSpec(HardwareEra.EXA, CpuSocket.STR5, 64, 3200, 350, false);
        assertFalse(build(exaBoard(), wrong, exaRam(), psu(3000)).isPowered());
    }

    @Test
    void exaBuild_wrongRamGeneration_isNotPowered() {
        final RamSpec ddr6 = new RamSpec(HardwareEra.SINGULARITY, RamGeneration.DDR6, 16384, 40);
        assertFalse(build(exaBoard(), exaCpu(), ddr6, psu(3000)).isPowered());
    }

    // ---- Singularity (conventional Quantum line) ----

    private static MotherboardSpec sktQPrimeBoard() {
        return new MotherboardSpec(FormFactor.SOCKET_Q, HardwareEra.SINGULARITY, CpuSocket.SOCKET_Q, 4,
                Set.of(RamGeneration.DDR6, RamGeneration.HBM), 24, PcieGeneration.PCIE_6_0, 12, 6, 8);
    }

    private static CpuSpec quantumCpu() {
        return new CpuSpec(HardwareEra.SINGULARITY, CpuSocket.SOCKET_Q, 256, 6000, 500, false);
    }

    private static RamSpec ddr6() {
        return new RamSpec(HardwareEra.SINGULARITY, RamGeneration.DDR6, 16384, 40);
    }

    @Test
    void singularityBuild_isPowered() {
        assertTrue(build(sktQPrimeBoard(), quantumCpu(), ddr6(), psu(8000)).isPowered());
    }

    @Test
    void singularityBuild_wrongSocket_isNotPowered() {
        // The alien EM Core uses Socket EM, which does not fit the conventional Socket Q board.
        final CpuSpec emCpu = new CpuSpec(HardwareEra.SINGULARITY, CpuSocket.SOCKET_EM, 128, 5500, 450, true);
        assertFalse(build(sktQPrimeBoard(), emCpu, ddr6(), psu(8000)).isPowered());
    }

    @Test
    void singularityBuild_wrongRamGeneration_isNotPowered() {
        final RamSpec ddr5 = new RamSpec(HardwareEra.EXA, RamGeneration.DDR5, 8192, 30);
        assertFalse(build(sktQPrimeBoard(), quantumCpu(), ddr5, psu(8000)).isPowered());
    }

    // ---- Singularity (alien EM line) ----

    private static MotherboardSpec mtxEmBoard() {
        return new MotherboardSpec(FormFactor.MTX, HardwareEra.SINGULARITY, CpuSocket.SOCKET_EM, 1,
                Set.of(RamGeneration.DDR6, RamGeneration.HBM), 8, PcieGeneration.PCIE_6_0, 6, 6, 8);
    }

    private static CpuSpec emCore() {
        return new CpuSpec(HardwareEra.SINGULARITY, CpuSocket.SOCKET_EM, 128, 5500, 450, true);
    }

    @Test
    void emCore_orchestrationCapacity_isTripleTheConventionalCpu() {
        final CpuSpec conventional = new CpuSpec(HardwareEra.SINGULARITY, CpuSocket.SOCKET_Q, 128, 5500, 400, false);
        assertEquals(3L * conventional.orchestrationCapacity(), emCore().orchestrationCapacity(),
                "the alien flag multiplies orchestration capacity by three");
    }

    @Test
    void alienPsu_autoScaling_bypassesPowerCheck() {
        // The EM build's draw (450W CPU + 40W RAM = 490W) is far above the AlienPSU's nominal 8000W only
        // nominally — the point is that an auto-scaling PSU dimensions itself to any draw. Prove it powers a
        // build whose draw exceeds a deliberately tiny nominal wattage.
        final PsuSpec alienPsu = new PsuSpec(1, 100, true);
        final ComputerBuild emBuild = new ComputerBuild(mtxEmBoard(),
                List.of(emCore()), List.of(), List.of(ddr6()), alienPsu);
        assertTrue(emBuild.isPowered(), "the AlienPSU auto-scales and always satisfies the power draw");
        assertTrue(emBuild.validate().problems().isEmpty());
    }

    @Test
    void emCore_onFixedPsuBelowDraw_isNotPowered() {
        // The same EM build on a conventional fixed-wattage PSU below the draw must still fail.
        final ComputerBuild emBuild = new ComputerBuild(mtxEmBoard(),
                List.of(emCore()), List.of(), List.of(ddr6()), psu(100));
        assertFalse(emBuild.isPowered());
    }
}
