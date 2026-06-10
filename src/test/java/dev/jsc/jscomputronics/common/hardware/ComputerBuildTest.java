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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComputerBuildTest {

    private static MotherboardSpec mtxStandard() {
        return new MotherboardSpec(FormFactor.MTX, HardwareEra.STANDARD, CpuSocket.LGA_2011, 4,
                Set.of(RamGeneration.DDR3), 24, PcieGeneration.PCIE_3_0, 10, 4, 8);
    }

    private static CpuSpec standardCpu() {
        // 8 cores at 3500 MHz -> 4480 items/tick
        return new CpuSpec(HardwareEra.STANDARD, CpuSocket.LGA_2011, 8, 3500, 130, false);
    }

    private static RamSpec ddr3() {
        return new RamSpec(HardwareEra.STANDARD, RamGeneration.DDR3, 2048, 15);
    }

    private static GpuSpec standardGpu() {
        return new GpuSpec(HardwareEra.STANDARD, PcieGeneration.PCIE_3_0, 2048, 3072, 250);
    }

    private static PsuSpec psu(final int watts) {
        return new PsuSpec(watts, 90);
    }

    private static DiskSpec disk(final StorageTier tier, final long capacityItems, final int tdp) {
        return new DiskSpec(tier, capacityItems, tdp);
    }

    @Test
    void validBuild_isPowered() {
        final ComputerBuild build = new ComputerBuild(mtxStandard(),
                List.of(standardCpu()), List.of(), List.of(ddr3()), psu(650));
        assertTrue(build.isPowered());
        assertTrue(build.validate().problems().isEmpty());
    }

    @Test
    void noCpu_isNotPowered() {
        final ComputerBuild build = new ComputerBuild(mtxStandard(),
                List.of(), List.of(), List.of(ddr3()), psu(650));
        assertFalse(build.validate().valid());
    }

    @Test
    void noRam_isNotPowered() {
        final ComputerBuild build = new ComputerBuild(mtxStandard(),
                List.of(standardCpu()), List.of(), List.of(), psu(650));
        assertFalse(build.isPowered(), "a computer with no RAM is not a working build");
    }

    @Test
    void tooManyCpus_isNotPowered() {
        final ComputerBuild build = new ComputerBuild(mtxStandard(),
                List.of(standardCpu(), standardCpu(), standardCpu(), standardCpu(), standardCpu()),
                List.of(), List.of(ddr3()), psu(3000));
        assertFalse(build.isPowered());
    }

    @Test
    void wrongSocketCpu_isNotPowered() {
        final CpuSpec sp5Cpu = new CpuSpec(HardwareEra.STANDARD, CpuSocket.SP5, 8, 3500, 130, false);
        final ComputerBuild build = new ComputerBuild(mtxStandard(),
                List.of(sp5Cpu), List.of(), List.of(ddr3()), psu(650));
        assertFalse(build.isPowered());
    }

    @Test
    void gpuBusNewerThanBoard_isNotPowered() {
        final GpuSpec pcie5Gpu = new GpuSpec(HardwareEra.EXA, PcieGeneration.PCIE_5_0, 19456, 192000, 750);
        final ComputerBuild build = new ComputerBuild(mtxStandard(),
                List.of(standardCpu()), List.of(pcie5Gpu), List.of(ddr3()), psu(2000));
        assertFalse(build.isPowered());
    }

    @Test
    void gpuBusOlderThanBoard_isPowered() {
        // Backward compatible: a PCIe 1.0 card fits a PCIe 3.0 board.
        final GpuSpec pcie1Gpu = new GpuSpec(HardwareEra.LEGACY, PcieGeneration.PCIE_1_0, 112, 512, 110);
        final ComputerBuild build = new ComputerBuild(mtxStandard(),
                List.of(standardCpu()), List.of(pcie1Gpu), List.of(ddr3()), psu(650));
        assertTrue(build.isPowered());
    }

    @Test
    void wrongRamGeneration_isNotPowered() {
        final RamSpec ddr4 = new RamSpec(HardwareEra.ADVANCED, RamGeneration.DDR4, 4096, 20);
        final ComputerBuild build = new ComputerBuild(mtxStandard(),
                List.of(standardCpu()), List.of(), List.of(ddr4), psu(650));
        assertFalse(build.isPowered());
    }

    @Test
    void psuInsufficient_isNotPowered() {
        // 4 x 130W CPU + 250W GPU + 15W RAM = 785W, exceeds a 300W PSU.
        final ComputerBuild build = new ComputerBuild(mtxStandard(),
                List.of(standardCpu(), standardCpu(), standardCpu(), standardCpu()),
                List.of(standardGpu()), List.of(ddr3()), psu(300));
        assertFalse(build.isPowered());
    }

    @Test
    void totalCapacity_sumsCpus() {
        // Two CPUs at 4480 each = 8960.
        final ComputerBuild build = new ComputerBuild(mtxStandard(),
                List.of(standardCpu(), standardCpu()), List.of(), List.of(ddr3()), psu(650));
        assertEquals(8960L, build.totalCapacity());
    }

    @Test
    void parallelQueues_oneBasePlusOnePerGpu() {
        final ComputerBuild build = new ComputerBuild(mtxStandard(),
                List.of(standardCpu()), List.of(standardGpu(), standardGpu()), List.of(ddr3()), psu(1000));
        assertEquals(3, build.parallelQueues());
    }

    @Test
    void parallelQueues_noGpu_isOne() {
        final ComputerBuild build = new ComputerBuild(mtxStandard(),
                List.of(standardCpu()), List.of(), List.of(ddr3()), psu(650));
        assertEquals(1, build.parallelQueues());
    }

    @Test
    void ramBuffer_sumsModules() {
        final ComputerBuild build = new ComputerBuild(mtxStandard(),
                List.of(standardCpu()), List.of(), List.of(ddr3(), ddr3(), ddr3()), psu(650));
        assertEquals(6144L, build.ramBuffer());
    }

    @Test
    void powerDraw_sumsAllComponents() {
        // 130W CPU + 250W GPU + 15W RAM = 395W.
        final ComputerBuild build = new ComputerBuild(mtxStandard(),
                List.of(standardCpu()), List.of(standardGpu()), List.of(ddr3()), psu(650));
        assertEquals(395, build.powerDraw());
    }

    @Test
    void disklessConvenienceConstructor_hasNoDisks() {
        final ComputerBuild build = new ComputerBuild(mtxStandard(),
                List.of(standardCpu()), List.of(), List.of(ddr3()), psu(650));
        assertTrue(build.disks().isEmpty());
        assertEquals(0L, build.totalStorageItems());
        assertEquals(0L, build.storageMb());
    }

    @Test
    void totalStorageItems_sumsDisks() {
        final ComputerBuild build = new ComputerBuild(mtxStandard(),
                List.of(standardCpu()), List.of(), List.of(ddr3()), psu(650),
                List.of(disk(StorageTier.HDD, 100_000, 6), disk(StorageTier.SSD, 50_000, 3)));
        assertEquals(150_000L, build.totalStorageItems());
    }

    @Test
    void storageMb_isItemsTimesFour() {
        final ComputerBuild build = new ComputerBuild(mtxStandard(),
                List.of(standardCpu()), List.of(), List.of(ddr3()), psu(650),
                List.of(disk(StorageTier.NVME, 1_000, 4)));
        assertEquals(4_000L, build.storageMb());
    }

    @Test
    void powerDraw_includesDisks() {
        // 130W CPU + 15W RAM + (6W + 3W) disks = 154W.
        final ComputerBuild build = new ComputerBuild(mtxStandard(),
                List.of(standardCpu()), List.of(), List.of(ddr3()), psu(650),
                List.of(disk(StorageTier.HDD, 100_000, 6), disk(StorageTier.SSD, 50_000, 3)));
        assertEquals(154, build.powerDraw());
    }

    @Test
    void tooManyDisks_isNotPowered() {
        // mtxStandard has 4 disk slots; 5 disks must fail validation.
        final ComputerBuild build = new ComputerBuild(mtxStandard(),
                List.of(standardCpu()), List.of(), List.of(ddr3()), psu(650),
                List.of(disk(StorageTier.HDD, 100, 6), disk(StorageTier.HDD, 100, 6),
                        disk(StorageTier.HDD, 100, 6), disk(StorageTier.HDD, 100, 6),
                        disk(StorageTier.HDD, 100, 6)));
        assertFalse(build.isPowered());
    }

    @Test
    void disksWithinSlots_isPowered() {
        final ComputerBuild build = new ComputerBuild(mtxStandard(),
                List.of(standardCpu()), List.of(), List.of(ddr3()), psu(650),
                List.of(disk(StorageTier.NVME, 262_144, 5), disk(StorageTier.SSD, 262_144, 3)));
        assertTrue(build.isPowered());
    }

    @Test
    void constructor_rejectsNullPsu() {
        assertThrows(NullPointerException.class,
                () -> new ComputerBuild(mtxStandard(), List.of(standardCpu()), List.of(), List.of(ddr3()), null));
    }
}
