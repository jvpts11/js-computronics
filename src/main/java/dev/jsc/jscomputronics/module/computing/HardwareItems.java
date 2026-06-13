/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing;

import dev.jsc.jscomputronics.common.hardware.CpuSocket;
import dev.jsc.jscomputronics.common.hardware.CpuSpec;
import dev.jsc.jscomputronics.common.hardware.FormFactor;
import dev.jsc.jscomputronics.common.hardware.GpuSpec;
import dev.jsc.jscomputronics.common.hardware.MotherboardSpec;
import dev.jsc.jscomputronics.common.hardware.PcieGeneration;
import dev.jsc.jscomputronics.common.hardware.PsuSpec;
import dev.jsc.jscomputronics.common.hardware.RamGeneration;
import dev.jsc.jscomputronics.common.hardware.RamSpec;
import dev.jsc.jscomputronics.common.tier.HardwareEra;
import dev.jsc.jscomputronics.module.computing.item.CpuItem;
import dev.jsc.jscomputronics.module.computing.item.GpuItem;
import dev.jsc.jscomputronics.module.computing.item.MotherboardItem;
import dev.jsc.jscomputronics.module.computing.item.PsuItem;
import dev.jsc.jscomputronics.module.computing.item.RamItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The per-era hardware item catalog: every CPU, RAM module, PSU, GPU and motherboard a player can
 * install across the six hardware eras (Vintage through Singularity).
 *
 * <p>Derived numbers — orchestration capacity (items/tick) and GPU threads — are never stored here.
 * They come from the formulas on {@link CpuSpec} and {@link GpuSpec}; only the raw inputs are listed.
 *
 * <p>The components already registered in {@link ComputingModule} for the Standard era (the Servo and
 * Ascent CPUs, the DDR3 module, the HD 7970 GPU, the MTX/EEB/ATX standard boards, the 650G PSU and the
 * disks) are not duplicated here — this class adds the items those leave out and completes the partial
 * sets. The specialized Mining (MNG) and Simulation (SIM) boards are intentionally deferred to their own
 * modules and are not registered here.
 *
 * <p>Each holder is also appended to a per-category list so datagen and the creative tab can iterate the
 * full catalog without restating every id.
 */
public final class HardwareItems {

    private HardwareItems() {
    }

    // Per-category accumulators, populated as the holders below are registered. Iterated by datagen
    // (language + item models) and the creative tab so a new item is wired everywhere by adding it once.

    public static final List<DeferredItem<CpuItem>> CPUS = new ArrayList<>();
    public static final List<DeferredItem<RamItem>> RAMS = new ArrayList<>();
    public static final List<DeferredItem<GpuItem>> GPUS = new ArrayList<>();
    public static final List<DeferredItem<PsuItem>> PSUS = new ArrayList<>();
    public static final List<DeferredItem<MotherboardItem>> MOTHERBOARDS = new ArrayList<>();

    private static DeferredItem<CpuItem> cpu(final String id, final CpuSpec spec) {
        final DeferredItem<CpuItem> holder =
                ComputingModule.ITEMS.register(id, () -> new CpuItem(new Item.Properties(), spec));
        CPUS.add(holder);
        return holder;
    }

    private static DeferredItem<RamItem> ram(final String id, final RamSpec spec) {
        final DeferredItem<RamItem> holder =
                ComputingModule.ITEMS.register(id, () -> new RamItem(new Item.Properties(), spec));
        RAMS.add(holder);
        return holder;
    }

    private static DeferredItem<GpuItem> gpu(final String id, final GpuSpec spec) {
        final DeferredItem<GpuItem> holder =
                ComputingModule.ITEMS.register(id, () -> new GpuItem(new Item.Properties(), spec));
        GPUS.add(holder);
        return holder;
    }

    private static DeferredItem<PsuItem> psu(final String id, final PsuSpec spec) {
        final DeferredItem<PsuItem> holder =
                ComputingModule.ITEMS.register(id, () -> new PsuItem(new Item.Properties(), spec));
        PSUS.add(holder);
        return holder;
    }

    private static DeferredItem<MotherboardItem> board(final String id, final MotherboardSpec spec) {
        final DeferredItem<MotherboardItem> holder =
                ComputingModule.ITEMS.register(id, () -> new MotherboardItem(new Item.Properties(), spec));
        MOTHERBOARDS.add(holder);
        return holder;
    }

    // ==========================================================================================
    //  VINTAGE — ISA/PCI buses, SIMM/EDO RAM, single-core CPUs
    // ==========================================================================================

    public static final DeferredItem<CpuItem> CPU_INTEGRA_486SX =
            cpu("cpu_integra_486sx", new CpuSpec(HardwareEra.VINTAGE, CpuSocket.SOCKET_3, 1, 25, 3, false));
    public static final DeferredItem<CpuItem> CPU_INTEGRA_486DX2 =
            cpu("cpu_integra_486dx2", new CpuSpec(HardwareEra.VINTAGE, CpuSocket.SOCKET_3, 1, 66, 5, false));
    public static final DeferredItem<CpuItem> CPU_INTEGRA_486DX4 =
            cpu("cpu_integra_486dx4", new CpuSpec(HardwareEra.VINTAGE, CpuSocket.SOCKET_3, 1, 100, 5, false));
    public static final DeferredItem<CpuItem> CPU_VELOCION_K6_II =
            cpu("cpu_velocion_k6_ii", new CpuSpec(HardwareEra.VINTAGE, CpuSocket.SOCKET_7, 1, 350, 15, false));
    public static final DeferredItem<CpuItem> CPU_VELOCION_K6_III =
            cpu("cpu_velocion_k6_iii", new CpuSpec(HardwareEra.VINTAGE, CpuSocket.SOCKET_7, 1, 400, 20, false));
    public static final DeferredItem<CpuItem> CPU_VELOCION_K6_III_PLUS =
            cpu("cpu_velocion_k6_iii_plus", new CpuSpec(HardwareEra.VINTAGE, CpuSocket.SOCKET_7, 1, 450, 22, false));

    public static final DeferredItem<RamItem> RAM_SIMM_4 =
            ram("ram_simm_4", new RamSpec(HardwareEra.VINTAGE, RamGeneration.SIMM, 1, 1));
    public static final DeferredItem<RamItem> RAM_EDO_16 =
            ram("ram_edo_16", new RamSpec(HardwareEra.VINTAGE, RamGeneration.EDO, 4, 2));

    public static final DeferredItem<GpuItem> GPU_VGA_256 =
            gpu("gpu_vga_256", new GpuSpec(HardwareEra.VINTAGE, PcieGeneration.ISA, 1, 1, 5));
    public static final DeferredItem<GpuItem> GPU_3D_BLASTER =
            gpu("gpu_3d_blaster", new GpuSpec(HardwareEra.VINTAGE, PcieGeneration.PCI, 1, 2, 5));

    public static final DeferredItem<PsuItem> PSU_300B =
            psu("psu_300b", new PsuSpec(300, 80));

    public static final DeferredItem<MotherboardItem> MOTHERBOARD_BABYAT_VINTAGE =
            board("motherboard_babyat_vintage", new MotherboardSpec(FormFactor.BABY_AT, HardwareEra.VINTAGE,
                    CpuSocket.SOCKET_3, 1, Set.of(RamGeneration.SIMM), 4, PcieGeneration.PCI, 4, 2, 2));
    public static final DeferredItem<MotherboardItem> MOTHERBOARD_AT_VINTAGE =
            board("motherboard_at_vintage", new MotherboardSpec(FormFactor.AT, HardwareEra.VINTAGE,
                    CpuSocket.SOCKET_7, 1, Set.of(RamGeneration.SIMM, RamGeneration.EDO), 8,
                    PcieGeneration.PCI, 7, 4, 2));
    public static final DeferredItem<MotherboardItem> MOTHERBOARD_MTX_VINTAGE =
            board("motherboard_mtx_vintage", new MotherboardSpec(FormFactor.MTX, HardwareEra.VINTAGE,
                    CpuSocket.SOCKET_7, 2, Set.of(RamGeneration.SIMM, RamGeneration.EDO), 16,
                    PcieGeneration.PCI, 8, 4, 8));

    // ==========================================================================================
    //  LEGACY — AGP/PCIe 1.0 buses, SDRAM/DDR/DDR2 RAM, first multi-core CPUs
    // ==========================================================================================

    public static final DeferredItem<CpuItem> CPU_INTEGRA_VERTEX_700 =
            cpu("cpu_integra_vertex_700", new CpuSpec(HardwareEra.LEGACY, CpuSocket.SOCKET_370, 1, 700, 28, false));
    public static final DeferredItem<CpuItem> CPU_INTEGRA_VERTEX_III_S_1000 =
            cpu("cpu_integra_vertex_iii_s_1000", new CpuSpec(HardwareEra.LEGACY, CpuSocket.SOCKET_370, 1, 1000, 30, false));
    public static final DeferredItem<CpuItem> CPU_INTEGRA_VERTEX_III_S_1400 =
            cpu("cpu_integra_vertex_iii_s_1400", new CpuSpec(HardwareEra.LEGACY, CpuSocket.SOCKET_370, 1, 1400, 32, false));
    public static final DeferredItem<CpuItem> CPU_VELOCION_SPRINT_XP_2400 =
            cpu("cpu_velocion_sprint_xp_2400", new CpuSpec(HardwareEra.LEGACY, CpuSocket.SOCKET_A, 1, 2000, 65, false));
    public static final DeferredItem<CpuItem> CPU_VELOCION_SPRINT_XP_3200 =
            cpu("cpu_velocion_sprint_xp_3200", new CpuSpec(HardwareEra.LEGACY, CpuSocket.SOCKET_A, 1, 2200, 76, false));
    public static final DeferredItem<CpuItem> CPU_VELOCION_SPRINT_XP_3800 =
            cpu("cpu_velocion_sprint_xp_3800", new CpuSpec(HardwareEra.LEGACY, CpuSocket.SOCKET_A, 1, 2400, 89, false));
    public static final DeferredItem<CpuItem> CPU_INTEGRA_DUO_E4300 =
            cpu("cpu_integra_duo_e4300", new CpuSpec(HardwareEra.LEGACY, CpuSocket.LGA_775, 2, 1800, 65, false));
    public static final DeferredItem<CpuItem> CPU_INTEGRA_DUO_E6600 =
            cpu("cpu_integra_duo_e6600", new CpuSpec(HardwareEra.LEGACY, CpuSocket.LGA_775, 2, 2400, 65, false));
    public static final DeferredItem<CpuItem> CPU_INTEGRA_DUO_E8500 =
            cpu("cpu_integra_duo_e8500", new CpuSpec(HardwareEra.LEGACY, CpuSocket.LGA_775, 2, 3160, 65, false));
    public static final DeferredItem<CpuItem> CPU_VELOCION_DUAL_240 =
            cpu("cpu_velocion_dual_240", new CpuSpec(HardwareEra.LEGACY, CpuSocket.SOCKET_940, 2, 2200, 85, false));
    public static final DeferredItem<CpuItem> CPU_VELOCION_DUAL_280 =
            cpu("cpu_velocion_dual_280", new CpuSpec(HardwareEra.LEGACY, CpuSocket.SOCKET_940, 2, 2400, 95, false));
    public static final DeferredItem<CpuItem> CPU_VELOCION_DUAL_285 =
            cpu("cpu_velocion_dual_285", new CpuSpec(HardwareEra.LEGACY, CpuSocket.SOCKET_940, 2, 2600, 95, false));
    public static final DeferredItem<CpuItem> CPU_INTEGRA_SERVO_5100 =
            cpu("cpu_integra_servo_5100", new CpuSpec(HardwareEra.LEGACY, CpuSocket.LGA_771, 2, 2000, 65, false));
    public static final DeferredItem<CpuItem> CPU_INTEGRA_SERVO_5160 =
            cpu("cpu_integra_servo_5160", new CpuSpec(HardwareEra.LEGACY, CpuSocket.LGA_771, 2, 3000, 80, false));
    public static final DeferredItem<CpuItem> CPU_INTEGRA_SERVO_5365 =
            cpu("cpu_integra_servo_5365", new CpuSpec(HardwareEra.LEGACY, CpuSocket.LGA_771, 4, 2000, 120, false));

    public static final DeferredItem<RamItem> RAM_SDRAM_128 =
            ram("ram_sdram_128", new RamSpec(HardwareEra.LEGACY, RamGeneration.SDRAM, 32, 5));
    public static final DeferredItem<RamItem> RAM_DDR_512 =
            ram("ram_ddr_512", new RamSpec(HardwareEra.LEGACY, RamGeneration.DDR, 128, 10));
    public static final DeferredItem<RamItem> RAM_DDR2_2048 =
            ram("ram_ddr2_2048", new RamSpec(HardwareEra.LEGACY, RamGeneration.DDR2, 512, 12));

    public static final DeferredItem<GpuItem> GPU_VERTEX_256 =
            gpu("gpu_vertex_256", new GpuSpec(HardwareEra.LEGACY, PcieGeneration.AGP_4X, 4, 32, 50));
    public static final DeferredItem<GpuItem> GPU_RADIANCE_9800_PRO =
            gpu("gpu_radiance_9800_pro", new GpuSpec(HardwareEra.LEGACY, PcieGeneration.AGP_8X, 8, 128, 70));
    public static final DeferredItem<GpuItem> GPU_VERTEX_8800_GT =
            gpu("gpu_vertex_8800_gt", new GpuSpec(HardwareEra.LEGACY, PcieGeneration.PCIE_1_0, 112, 512, 110));

    public static final DeferredItem<PsuItem> PSU_500B =
            psu("psu_500b", new PsuSpec(500, 80));

    // The Legacy ATX board lists "one of Socket A / Socket 370 / LGA 775". A board spec carries a single
    // socket, so this is modeled as one board item per socket — the clean one-value-per-record mapping.
    public static final DeferredItem<MotherboardItem> MOTHERBOARD_ATX_LEGACY_SKA =
            board("motherboard_atx_legacy_ska", new MotherboardSpec(FormFactor.ATX, HardwareEra.LEGACY,
                    CpuSocket.SOCKET_A, 1, Set.of(RamGeneration.DDR, RamGeneration.DDR2), 4,
                    PcieGeneration.PCIE_1_0, 4, 4, 4));
    public static final DeferredItem<MotherboardItem> MOTHERBOARD_ATX_LEGACY_S370 =
            board("motherboard_atx_legacy_s370", new MotherboardSpec(FormFactor.ATX, HardwareEra.LEGACY,
                    CpuSocket.SOCKET_370, 1, Set.of(RamGeneration.DDR, RamGeneration.DDR2), 4,
                    PcieGeneration.PCIE_1_0, 4, 4, 4));
    public static final DeferredItem<MotherboardItem> MOTHERBOARD_ATX_LEGACY_LGA775 =
            board("motherboard_atx_legacy_lga775", new MotherboardSpec(FormFactor.ATX, HardwareEra.LEGACY,
                    CpuSocket.LGA_775, 1, Set.of(RamGeneration.DDR, RamGeneration.DDR2), 4,
                    PcieGeneration.PCIE_1_0, 4, 4, 4));
    // The Legacy EATX board lists "one of LGA 775 / Socket 940": one board per socket.
    public static final DeferredItem<MotherboardItem> MOTHERBOARD_EATX_LEGACY_LGA775 =
            board("motherboard_eatx_legacy_lga775", new MotherboardSpec(FormFactor.EATX, HardwareEra.LEGACY,
                    CpuSocket.LGA_775, 2, Set.of(RamGeneration.DDR2), 8, PcieGeneration.PCIE_1_0, 6, 6, 4));
    public static final DeferredItem<MotherboardItem> MOTHERBOARD_EATX_LEGACY_S940 =
            board("motherboard_eatx_legacy_s940", new MotherboardSpec(FormFactor.EATX, HardwareEra.LEGACY,
                    CpuSocket.SOCKET_940, 2, Set.of(RamGeneration.DDR2), 8, PcieGeneration.PCIE_1_0, 6, 6, 4));
    public static final DeferredItem<MotherboardItem> MOTHERBOARD_MTX_LEGACY =
            board("motherboard_mtx_legacy", new MotherboardSpec(FormFactor.MTX, HardwareEra.LEGACY,
                    CpuSocket.SOCKET_940, 4, Set.of(RamGeneration.DDR2), 24, PcieGeneration.PCIE_1_0, 8, 6, 8));

    // ==========================================================================================
    //  STANDARD — completion of the partially-registered set (PCIe 2.0/3.0, DDR3)
    //  The Servo 2620/2690/2699, the Ascent X4 965, the DDR3-8192, the HD 7970 GPU, the MTX-P /
    //  EEB-P / ATX-P boards and the 650G PSU already live in ComputingModule. These fill the gaps.
    // ==========================================================================================

    public static final DeferredItem<CpuItem> CPU_ASCENT_X4_955 =
            cpu("cpu_ascent_x4_955", new CpuSpec(HardwareEra.STANDARD, CpuSocket.AM3, 4, 3200, 125, false));
    public static final DeferredItem<CpuItem> CPU_ASCENT_X6_1090T =
            cpu("cpu_ascent_x6_1090t", new CpuSpec(HardwareEra.STANDARD, CpuSocket.AM3, 6, 3200, 125, false));
    public static final DeferredItem<CpuItem> CPU_APEX_5_4590 =
            cpu("cpu_apex_5_4590", new CpuSpec(HardwareEra.STANDARD, CpuSocket.LGA_1150, 4, 3300, 84, false));
    public static final DeferredItem<CpuItem> CPU_APEX_5_4690K =
            cpu("cpu_apex_5_4690k", new CpuSpec(HardwareEra.STANDARD, CpuSocket.LGA_1150, 4, 3500, 88, false));
    public static final DeferredItem<CpuItem> CPU_APEX_7_4790K =
            cpu("cpu_apex_7_4790k", new CpuSpec(HardwareEra.STANDARD, CpuSocket.LGA_1150, 4, 4000, 88, false));

    public static final DeferredItem<GpuItem> GPU_RADIANCE_HD_7970 = ComputingModule.GPU_HD_7970;

    public static final DeferredItem<PsuItem> PSU_850G =
            psu("psu_850g", new PsuSpec(850, 90));

    // The Standard ATX board comes in an AM3 flavour (already MOTHERBOARD_ATX_P in ComputingModule) and
    // an LGA 1150 flavour for the Apex line — register the missing socket variant and the WS board.
    public static final DeferredItem<MotherboardItem> MOTHERBOARD_ATX_STANDARD_LGA1150 =
            board("motherboard_atx_standard_lga1150", new MotherboardSpec(FormFactor.ATX, HardwareEra.STANDARD,
                    CpuSocket.LGA_1150, 1, Set.of(RamGeneration.DDR3), 4, PcieGeneration.PCIE_3_0, 4, 2, 4));
    public static final DeferredItem<MotherboardItem> MOTHERBOARD_EATX_STANDARD_WS =
            board("motherboard_eatx_standard_ws", new MotherboardSpec(FormFactor.EATX, HardwareEra.STANDARD,
                    CpuSocket.LGA_2011, 1, Set.of(RamGeneration.DDR3), 8, PcieGeneration.PCIE_3_0, 7, 4, 4));

    // ==========================================================================================
    //  ADVANCED — PCIe 4.0, DDR4
    // ==========================================================================================

    public static final DeferredItem<CpuItem> CPU_ASCENT_9_3900X =
            cpu("cpu_ascent_9_3900x", new CpuSpec(HardwareEra.ADVANCED, CpuSocket.AM4, 12, 3800, 105, false));
    public static final DeferredItem<CpuItem> CPU_ASCENT_9_5900X =
            cpu("cpu_ascent_9_5900x", new CpuSpec(HardwareEra.ADVANCED, CpuSocket.AM4, 12, 3700, 105, false));
    public static final DeferredItem<CpuItem> CPU_ASCENT_9_5950X =
            cpu("cpu_ascent_9_5950x", new CpuSpec(HardwareEra.ADVANCED, CpuSocket.AM4, 16, 3400, 105, false));
    public static final DeferredItem<CpuItem> CPU_APEX_9_12900K =
            cpu("cpu_apex_9_12900k", new CpuSpec(HardwareEra.ADVANCED, CpuSocket.LGA_1700, 16, 3200, 125, false));
    public static final DeferredItem<CpuItem> CPU_SERVO_PLATA_8352Y =
            cpu("cpu_servo_plata_8352y", new CpuSpec(HardwareEra.ADVANCED, CpuSocket.LGA_4189, 32, 2200, 205, false));
    public static final DeferredItem<CpuItem> CPU_SERVO_PLATA_8380 =
            cpu("cpu_servo_plata_8380", new CpuSpec(HardwareEra.ADVANCED, CpuSocket.LGA_4189, 40, 2300, 270, false));
    public static final DeferredItem<CpuItem> CPU_SERVO_PLATA_8380H =
            cpu("cpu_servo_plata_8380h", new CpuSpec(HardwareEra.ADVANCED, CpuSocket.LGA_4189, 40, 2900, 250, false));
    public static final DeferredItem<CpuItem> CPU_EPIC_7302 =
            cpu("cpu_epic_7302", new CpuSpec(HardwareEra.ADVANCED, CpuSocket.SP3, 16, 3000, 155, false));
    public static final DeferredItem<CpuItem> CPU_EPIC_7452 =
            cpu("cpu_epic_7452", new CpuSpec(HardwareEra.ADVANCED, CpuSocket.SP3, 32, 2350, 155, false));
    public static final DeferredItem<CpuItem> CPU_EPIC_7763 =
            cpu("cpu_epic_7763", new CpuSpec(HardwareEra.ADVANCED, CpuSocket.SP3, 64, 2450, 280, false));

    public static final DeferredItem<RamItem> RAM_DDR4_16384 =
            ram("ram_ddr4_16384", new RamSpec(HardwareEra.ADVANCED, RamGeneration.DDR4, 4096, 20));

    public static final DeferredItem<GpuItem> GPU_RTX_4090 =
            gpu("gpu_rtx_4090", new GpuSpec(HardwareEra.ADVANCED, PcieGeneration.PCIE_4_0, 16384, 24576, 450));

    public static final DeferredItem<PsuItem> PSU_1000G =
            psu("psu_1000g", new PsuSpec(1000, 90));
    public static final DeferredItem<PsuItem> PSU_1200P =
            psu("psu_1200p", new PsuSpec(1200, 95));
    public static final DeferredItem<PsuItem> PSU_1600P =
            psu("psu_1600p", new PsuSpec(1600, 95));

    public static final DeferredItem<MotherboardItem> MOTHERBOARD_ATX_ADVANCED =
            board("motherboard_atx_advanced", new MotherboardSpec(FormFactor.ATX, HardwareEra.ADVANCED,
                    CpuSocket.AM4, 1, Set.of(RamGeneration.DDR4), 4, PcieGeneration.PCIE_4_0, 3, 2, 4));
    // The Advanced EATX WS board takes "one of LGA 4189 / SP3": one board per socket.
    public static final DeferredItem<MotherboardItem> MOTHERBOARD_EATX_ADVANCED_WS_LGA4189 =
            board("motherboard_eatx_advanced_ws_lga4189", new MotherboardSpec(FormFactor.EATX, HardwareEra.ADVANCED,
                    CpuSocket.LGA_4189, 1, Set.of(RamGeneration.DDR4), 8, PcieGeneration.PCIE_4_0, 7, 4, 4));
    public static final DeferredItem<MotherboardItem> MOTHERBOARD_EATX_ADVANCED_WS_SP3 =
            board("motherboard_eatx_advanced_ws_sp3", new MotherboardSpec(FormFactor.EATX, HardwareEra.ADVANCED,
                    CpuSocket.SP3, 1, Set.of(RamGeneration.DDR4), 8, PcieGeneration.PCIE_4_0, 7, 4, 4));
    // The Advanced EEB server board takes "two of SP3 / LGA 4189": one board per socket.
    public static final DeferredItem<MotherboardItem> MOTHERBOARD_EEB_ADVANCED_SP3 =
            board("motherboard_eeb_advanced_sp3", new MotherboardSpec(FormFactor.EEB, HardwareEra.ADVANCED,
                    CpuSocket.SP3, 2, Set.of(RamGeneration.DDR4), 16, PcieGeneration.PCIE_4_0, 8, 6, 6));
    public static final DeferredItem<MotherboardItem> MOTHERBOARD_EEB_ADVANCED_LGA4189 =
            board("motherboard_eeb_advanced_lga4189", new MotherboardSpec(FormFactor.EEB, HardwareEra.ADVANCED,
                    CpuSocket.LGA_4189, 2, Set.of(RamGeneration.DDR4), 16, PcieGeneration.PCIE_4_0, 8, 6, 6));
    public static final DeferredItem<MotherboardItem> MOTHERBOARD_MTX_ADVANCED =
            board("motherboard_mtx_advanced", new MotherboardSpec(FormFactor.MTX, HardwareEra.ADVANCED,
                    CpuSocket.SP3, 4, Set.of(RamGeneration.DDR4), 24, PcieGeneration.PCIE_4_0, 12, 6, 8));

    // ==========================================================================================
    //  EXA — PCIe 5.0, DDR5 (LGA 1700 is shared with the Advanced era)
    // ==========================================================================================

    public static final DeferredItem<CpuItem> CPU_APEX_9_13900K =
            cpu("cpu_apex_9_13900k", new CpuSpec(HardwareEra.EXA, CpuSocket.LGA_1700, 24, 3000, 125, false));
    public static final DeferredItem<CpuItem> CPU_APEX_9_13900KS =
            cpu("cpu_apex_9_13900ks", new CpuSpec(HardwareEra.EXA, CpuSocket.LGA_1700, 24, 3200, 150, false));
    public static final DeferredItem<CpuItem> CPU_THREADKILLER_7960X =
            cpu("cpu_threadkiller_7960x", new CpuSpec(HardwareEra.EXA, CpuSocket.STR5, 24, 4200, 280, false));
    public static final DeferredItem<CpuItem> CPU_THREADKILLER_7980X =
            cpu("cpu_threadkiller_7980x", new CpuSpec(HardwareEra.EXA, CpuSocket.STR5, 64, 3200, 350, false));
    public static final DeferredItem<CpuItem> CPU_THREADKILLER_7995WX =
            cpu("cpu_threadkiller_7995wx", new CpuSpec(HardwareEra.EXA, CpuSocket.STR5, 96, 2500, 350, false));
    public static final DeferredItem<CpuItem> CPU_SERVO_SAPPHIRE_4410Y =
            cpu("cpu_servo_sapphire_4410y", new CpuSpec(HardwareEra.EXA, CpuSocket.LGA_4677, 12, 2000, 150, false));
    public static final DeferredItem<CpuItem> CPU_SERVO_SAPPHIRE_6442Y =
            cpu("cpu_servo_sapphire_6442y", new CpuSpec(HardwareEra.EXA, CpuSocket.LGA_4677, 24, 2600, 225, false));
    public static final DeferredItem<CpuItem> CPU_SERVO_SAPPHIRE_6960P =
            cpu("cpu_servo_sapphire_6960p", new CpuSpec(HardwareEra.EXA, CpuSocket.LGA_4677, 60, 2800, 350, false));
    public static final DeferredItem<CpuItem> CPU_EPIC_9124 =
            cpu("cpu_epic_9124", new CpuSpec(HardwareEra.EXA, CpuSocket.SP5, 16, 3000, 200, false));
    public static final DeferredItem<CpuItem> CPU_EPIC_9374F =
            cpu("cpu_epic_9374f", new CpuSpec(HardwareEra.EXA, CpuSocket.SP5, 32, 3350, 320, false));
    public static final DeferredItem<CpuItem> CPU_EPIC_9654 =
            cpu("cpu_epic_9654", new CpuSpec(HardwareEra.EXA, CpuSocket.SP5, 96, 2400, 360, false));

    public static final DeferredItem<RamItem> RAM_DDR5_32768 =
            ram("ram_ddr5_32768", new RamSpec(HardwareEra.EXA, RamGeneration.DDR5, 8192, 30));

    public static final DeferredItem<GpuItem> GPU_MI300X =
            gpu("gpu_mi300x", new GpuSpec(HardwareEra.EXA, PcieGeneration.PCIE_5_0, 19456, 196608, 750));

    public static final DeferredItem<PsuItem> PSU_2000P =
            psu("psu_2000p", new PsuSpec(2000, 95));
    public static final DeferredItem<PsuItem> PSU_3000P =
            psu("psu_3000p", new PsuSpec(3000, 95));

    public static final DeferredItem<MotherboardItem> MOTHERBOARD_ATX_EXA =
            board("motherboard_atx_exa", new MotherboardSpec(FormFactor.ATX, HardwareEra.EXA,
                    CpuSocket.LGA_1700, 1, Set.of(RamGeneration.DDR5), 4, PcieGeneration.PCIE_5_0, 3, 2, 4));
    public static final DeferredItem<MotherboardItem> MOTHERBOARD_EATX_EXA_WS =
            board("motherboard_eatx_exa_ws", new MotherboardSpec(FormFactor.EATX, HardwareEra.EXA,
                    CpuSocket.STR5, 1, Set.of(RamGeneration.DDR5), 8, PcieGeneration.PCIE_5_0, 7, 4, 4));
    // The Exa EEB Pro board takes "one or two of SP5 / LGA 4677": one board per socket.
    public static final DeferredItem<MotherboardItem> MOTHERBOARD_EEB_EXA_PRO_SP5 =
            board("motherboard_eeb_exa_pro_sp5", new MotherboardSpec(FormFactor.EEB, HardwareEra.EXA,
                    CpuSocket.SP5, 2, Set.of(RamGeneration.DDR5), 16, PcieGeneration.PCIE_5_0, 8, 6, 6));
    public static final DeferredItem<MotherboardItem> MOTHERBOARD_EEB_EXA_PRO_LGA4677 =
            board("motherboard_eeb_exa_pro_lga4677", new MotherboardSpec(FormFactor.EEB, HardwareEra.EXA,
                    CpuSocket.LGA_4677, 2, Set.of(RamGeneration.DDR5), 16, PcieGeneration.PCIE_5_0, 8, 6, 6));
    // The Exa EEB Rack server board takes "two of SP5 / LGA 4677": one board per socket.
    public static final DeferredItem<MotherboardItem> MOTHERBOARD_EEB_EXA_RACK_SP5 =
            board("motherboard_eeb_exa_rack_sp5", new MotherboardSpec(FormFactor.EEB, HardwareEra.EXA,
                    CpuSocket.SP5, 2, Set.of(RamGeneration.DDR5), 16, PcieGeneration.PCIE_5_0, 8, 6, 6));
    public static final DeferredItem<MotherboardItem> MOTHERBOARD_EEB_EXA_RACK_LGA4677 =
            board("motherboard_eeb_exa_rack_lga4677", new MotherboardSpec(FormFactor.EEB, HardwareEra.EXA,
                    CpuSocket.LGA_4677, 2, Set.of(RamGeneration.DDR5), 16, PcieGeneration.PCIE_5_0, 8, 6, 6));
    public static final DeferredItem<MotherboardItem> MOTHERBOARD_MTX_EXA =
            board("motherboard_mtx_exa", new MotherboardSpec(FormFactor.MTX, HardwareEra.EXA,
                    CpuSocket.SP5, 4, Set.of(RamGeneration.DDR5), 24, PcieGeneration.PCIE_5_0, 16, 6, 8));

    // ==========================================================================================
    //  SINGULARITY — PCIe 6.0 / optical, DDR6 / HBM, Socket Q quantum line + the alien EM line
    // ==========================================================================================

    public static final DeferredItem<CpuItem> CPU_QUANTUM_CORE_MK_I =
            cpu("cpu_quantum_core_mk_i", new CpuSpec(HardwareEra.SINGULARITY, CpuSocket.SOCKET_Q, 64, 5000, 300, false));
    public static final DeferredItem<CpuItem> CPU_QUANTUM_CORE_MK_II =
            cpu("cpu_quantum_core_mk_ii", new CpuSpec(HardwareEra.SINGULARITY, CpuSocket.SOCKET_Q, 128, 5500, 400, false));
    public static final DeferredItem<CpuItem> CPU_QUANTUM_CORE_MK_III =
            cpu("cpu_quantum_core_mk_iii", new CpuSpec(HardwareEra.SINGULARITY, CpuSocket.SOCKET_Q, 256, 6000, 500, false));
    // The EM Core is alien: the alien flag multiplies its orchestration capacity x3 (a property of the
    // CPU, not of the board). Any computer that accepts the EM board gets the bonus automatically.
    public static final DeferredItem<CpuItem> CPU_EM_CORE =
            cpu("cpu_em_core", new CpuSpec(HardwareEra.SINGULARITY, CpuSocket.SOCKET_EM, 128, 5500, 450, true));

    public static final DeferredItem<RamItem> RAM_DDR6_65536 =
            ram("ram_ddr6_65536", new RamSpec(HardwareEra.SINGULARITY, RamGeneration.DDR6, 16384, 40));
    public static final DeferredItem<RamItem> RAM_HBM_131072 =
            ram("ram_hbm_131072", new RamSpec(HardwareEra.SINGULARITY, RamGeneration.HBM, 32768, 25));

    public static final DeferredItem<GpuItem> GPU_VERTEX_Q_9000 =
            gpu("gpu_vertex_q_9000", new GpuSpec(HardwareEra.SINGULARITY, PcieGeneration.PCIE_6_0, 32768, 294912, 900));

    public static final DeferredItem<PsuItem> PSU_4000Q =
            psu("psu_4000q", new PsuSpec(4000, 99));
    public static final DeferredItem<PsuItem> PSU_8000S =
            psu("psu_8000s", new PsuSpec(8000, 99));
    // The alien EM PSU auto-scales: it dimensions its output to the EM Core's draw and always satisfies
    // the power check. The nominal wattage is only a display value (the build's power gate is bypassed).
    public static final DeferredItem<PsuItem> PSU_ALIEN_EM =
            psu("psu_alien_em", new PsuSpec(8000, 100, true));

    public static final DeferredItem<MotherboardItem> MOTHERBOARD_SKTQ_STD =
            board("motherboard_sktq_std", new MotherboardSpec(FormFactor.SOCKET_Q, HardwareEra.SINGULARITY,
                    CpuSocket.SOCKET_Q, 1, Set.of(RamGeneration.DDR6, RamGeneration.HBM), 8,
                    PcieGeneration.PCIE_6_0, 4, 4, 4));
    public static final DeferredItem<MotherboardItem> MOTHERBOARD_SKTQ_PRIME =
            board("motherboard_sktq_prime", new MotherboardSpec(FormFactor.SOCKET_Q, HardwareEra.SINGULARITY,
                    CpuSocket.SOCKET_Q, 4, Set.of(RamGeneration.DDR6, RamGeneration.HBM), 24,
                    PcieGeneration.PCIE_6_0, 12, 6, 8));
    // MTX-EM: the same MTX-form-factor footprint as the Singularity MTX board, with a single Socket EM
    // for the alien EM Core. It accepts every existing MTX use (Mainframe, Subframe, Server).
    public static final DeferredItem<MotherboardItem> MOTHERBOARD_MTX_EM =
            board("motherboard_mtx_em", new MotherboardSpec(FormFactor.MTX, HardwareEra.SINGULARITY,
                    CpuSocket.SOCKET_EM, 1, Set.of(RamGeneration.DDR6, RamGeneration.HBM), 8,
                    PcieGeneration.PCIE_6_0, 6, 6, 8));

    /**
     * The whole catalog ordered for the creative tab: era by era (Vintage to Singularity), and within
     * each era motherboards, then CPUs, RAM, GPUs and PSUs, so the progression reads cleanly. Built from
     * each item's spec {@code era()} so the order follows the data, not a hand-kept list.
     */
    public static List<Item> creativeOrder() {
        final List<Item> ordered = new ArrayList<>();
        for (final HardwareEra era : HardwareEra.values()) {
            for (final DeferredItem<MotherboardItem> h : MOTHERBOARDS) {
                if (h.get().spec().era() == era) {
                    ordered.add(h.get());
                }
            }
            for (final DeferredItem<CpuItem> h : CPUS) {
                if (h.get().spec().era() == era) {
                    ordered.add(h.get());
                }
            }
            for (final DeferredItem<RamItem> h : RAMS) {
                if (h.get().spec().era() == era) {
                    ordered.add(h.get());
                }
            }
            for (final DeferredItem<GpuItem> h : GPUS) {
                if (h.get().spec().era() == era) {
                    ordered.add(h.get());
                }
            }
            // PSUs carry no era field; place them after the era they belong to by registration order is
            // not possible, so they are appended once at the end in registration (era) order below.
        }
        for (final DeferredItem<PsuItem> h : PSUS) {
            ordered.add(h.get());
        }
        return ordered;
    }

    /**
     * Forces this class to load so its static holders register onto {@link ComputingModule#ITEMS}. Called
     * from {@link ComputingModule} before the registers are handed to the mod event bus.
     */
    public static void init() {
        // Intentionally empty: referencing the class triggers static initialization, which performs the
        // registrations above. The accumulator lists are populated as a side effect.
    }
}
