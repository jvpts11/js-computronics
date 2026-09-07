/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics;

import dev.jstech.computronics.hardware.CpuSocket;
import dev.jstech.computronics.hardware.CpuSpec;
import dev.jstech.computronics.hardware.DiskSpec;
import dev.jstech.computronics.hardware.FormFactor;
import dev.jstech.computronics.hardware.GpuSpec;
import dev.jstech.computronics.hardware.MotherboardSpec;
import dev.jstech.computronics.hardware.PcieGeneration;
import dev.jstech.computronics.hardware.PsuSpec;
import dev.jstech.computronics.hardware.RamGeneration;
import dev.jstech.computronics.hardware.RamSpec;
import dev.jstech.computronics.hardware.StorageTier;
import dev.jstech.computronics.item.CpuItem;
import dev.jstech.computronics.item.DiskItem;
import dev.jstech.computronics.item.GpuItem;
import dev.jstech.computronics.item.MotherboardItem;
import dev.jstech.computronics.item.PsuItem;
import dev.jstech.computronics.item.RamItem;
import dev.jstech.core.tier.HardwareEra;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The per-era hardware item catalog: every CPU, RAM module, PSU, GPU and motherboard a player can
 * install across the Vintage, Legacy and Standard eras.
 *
 * <p>Derived numbers (orchestration capacity in items/tick, and GPU threads) are never stored here.
 * They come from the formulas on {@link CpuSpec} and {@link GpuSpec}; only the raw inputs are listed.
 *
 * <p>The components already registered in {@link ComputingModule} for the Standard era (the Servo and
 * Ascent CPUs, the DDR3 module, the HD 7970 GPU, the MTX/EEB/ATX standard boards, the 650G PSU and the
 * disks) are not duplicated here; this class adds the items those leave out and completes the partial
 * sets. The specialized Mining (MNG) and Simulation (SIM) boards are intentionally deferred to their own
 * modules and are not registered here.
 *
 * <p>A registration carries the item's id, its display name and its spec, and nothing else is written
 * anywhere: each holder lands in its category list and in {@link #displayNames()}, which datagen (language
 * and item models) and the creative tab iterate. Adding a part is one line here.
 */
public final class HardwareItems {

    private HardwareItems() {
    }

    // Per-category accumulators, populated as the holders below are registered.

    public static final List<DeferredItem<CpuItem>> CPUS = new ArrayList<>();
    public static final List<DeferredItem<RamItem>> RAMS = new ArrayList<>();
    public static final List<DeferredItem<GpuItem>> GPUS = new ArrayList<>();
    public static final List<DeferredItem<PsuItem>> PSUS = new ArrayList<>();
    public static final List<DeferredItem<MotherboardItem>> MOTHERBOARDS = new ArrayList<>();
    public static final List<DeferredItem<DiskItem>> DISKS = new ArrayList<>();

    private static final Map<DeferredItem<? extends Item>, String> NAMES = new LinkedHashMap<>();

    /** Every catalog item with its display name, in registration order: what the language file says. */
    public static Map<DeferredItem<? extends Item>, String> displayNames() {
        return Collections.unmodifiableMap(NAMES);
    }

    private static <T extends Item> DeferredItem<T> register(final String id, final String name,
                                                             final Supplier<T> factory,
                                                             final List<DeferredItem<T>> category) {
        final DeferredItem<T> holder = ComputingModule.ITEMS.register(id, factory);
        category.add(holder);
        NAMES.put(holder, name);
        return holder;
    }

    private static DeferredItem<CpuItem> cpu(final String id, final String name, final CpuSpec spec) {
        return register(id, name, () -> new CpuItem(new Item.Properties(), spec), CPUS);
    }

    private static DeferredItem<RamItem> ram(final String id, final String name, final RamSpec spec) {
        return register(id, name, () -> new RamItem(new Item.Properties(), spec), RAMS);
    }

    private static DeferredItem<GpuItem> gpu(final String id, final String name, final GpuSpec spec) {
        return register(id, name, () -> new GpuItem(new Item.Properties(), spec), GPUS);
    }

    private static DeferredItem<PsuItem> psu(final String id, final String name, final PsuSpec spec) {
        return register(id, name, () -> new PsuItem(new Item.Properties(), spec), PSUS);
    }

    private static DeferredItem<MotherboardItem> board(final String id, final String name, final MotherboardSpec spec) {
        return register(id, name, () -> new MotherboardItem(new Item.Properties(), spec), MOTHERBOARDS);
    }

    private static DeferredItem<DiskItem> disk(final String id, final String name, final DiskSpec spec) {
        return register(id, name, () -> new DiskItem(new Item.Properties(), spec), DISKS);
    }

    //  VINTAGE: ISA/PCI buses, SIMM/EDO RAM, single-core CPUs

    public static final DeferredItem<CpuItem> CPU_INTEGRA_486SX =
            cpu("cpu_integra_486sx", "Integra 486SX",
                    new CpuSpec(HardwareEra.VINTAGE, CpuSocket.SOCKET_3, 1, 25, 3, false));
    public static final DeferredItem<CpuItem> CPU_INTEGRA_486DX2 =
            cpu("cpu_integra_486dx2", "Integra 486DX2",
                    new CpuSpec(HardwareEra.VINTAGE, CpuSocket.SOCKET_3, 1, 66, 5, false));
    public static final DeferredItem<CpuItem> CPU_INTEGRA_486DX4 =
            cpu("cpu_integra_486dx4", "Integra 486DX4",
                    new CpuSpec(HardwareEra.VINTAGE, CpuSocket.SOCKET_3, 1, 100, 5, false));
    public static final DeferredItem<CpuItem> CPU_VELOCION_K6_II =
            cpu("cpu_velocion_k6_ii", "Velocion K6-II",
                    new CpuSpec(HardwareEra.VINTAGE, CpuSocket.SOCKET_7, 1, 350, 15, false));
    public static final DeferredItem<CpuItem> CPU_VELOCION_K6_III =
            cpu("cpu_velocion_k6_iii", "Velocion K6-III",
                    new CpuSpec(HardwareEra.VINTAGE, CpuSocket.SOCKET_7, 1, 400, 20, false));
    public static final DeferredItem<CpuItem> CPU_VELOCION_K6_III_PLUS =
            cpu("cpu_velocion_k6_iii_plus", "Velocion K6-III+",
                    new CpuSpec(HardwareEra.VINTAGE, CpuSocket.SOCKET_7, 1, 450, 22, false));

    public static final DeferredItem<RamItem> RAM_SIMM_4 =
            ram("ram_simm_4", "Stratix Layer SIMM-4", new RamSpec(HardwareEra.VINTAGE, RamGeneration.SIMM, 1, 1));
    public static final DeferredItem<RamItem> RAM_EDO_16 =
            ram("ram_edo_16", "Stratix Layer EDO-16", new RamSpec(HardwareEra.VINTAGE, RamGeneration.EDO, 4, 2));

    /*
     * Vintage GPU ladder (ISA entry to PCI high-end). VGA-256 and the 3D Blaster are the floor; the two
     * PCI cards below extend the era upward with more cores, VRAM and draw. Single-digit cores and a few MB
     * of VRAM is era-appropriate for fixed-function 2D/early-3D accelerators.
     */
    public static final DeferredItem<GpuItem> GPU_VGA_256 =
            gpu("gpu_vga_256", "Visara VGA-256", new GpuSpec(HardwareEra.VINTAGE, PcieGeneration.ISA, 1, 1, 5));
    public static final DeferredItem<GpuItem> GPU_3D_BLASTER =
            gpu("gpu_3d_blaster", "Pyrix 3D Blaster", new GpuSpec(HardwareEra.VINTAGE, PcieGeneration.PCI, 1, 2, 5));
    public static final DeferredItem<GpuItem> GPU_PRISM_4 =
            gpu("gpu_prism_4", "Visara Prism 4", new GpuSpec(HardwareEra.VINTAGE, PcieGeneration.PCI, 2, 4, 12));
    public static final DeferredItem<GpuItem> GPU_VOODOO_GFX =
            gpu("gpu_voodoo_gfx", "Pyrix Voodoo GFX", new GpuSpec(HardwareEra.VINTAGE, PcieGeneration.PCI, 3, 8, 18));

    public static final DeferredItem<PsuItem> PSU_300B =
            psu("psu_300b", "MF PowerBasic 300B", new PsuSpec(300, 80));

    public static final DeferredItem<MotherboardItem> MOTHERBOARD_BABYAT_VINTAGE =
            board("motherboard_babyat_vintage", "MF Baby-AT I Motherboard",
                    new MotherboardSpec(FormFactor.BABY_AT, HardwareEra.VINTAGE,
                            CpuSocket.SOCKET_3, 1, Set.of(RamGeneration.SIMM), 4, PcieGeneration.ISA, 4, 2, 2));
    public static final DeferredItem<MotherboardItem> MOTHERBOARD_AT_VINTAGE =
            board("motherboard_at_vintage", "MF AT Standard Motherboard",
                    new MotherboardSpec(FormFactor.AT, HardwareEra.VINTAGE,
                            CpuSocket.SOCKET_7, 1, Set.of(RamGeneration.SIMM, RamGeneration.EDO), 8,
                            PcieGeneration.PCI, 7, 4, 2));
    public static final DeferredItem<MotherboardItem> MOTHERBOARD_MTX_VINTAGE =
            board("motherboard_mtx_vintage", "MF MTX-V Motherboard",
                    new MotherboardSpec(FormFactor.MTX, HardwareEra.VINTAGE,
                            CpuSocket.SOCKET_7, 2, Set.of(RamGeneration.SIMM, RamGeneration.EDO), 16,
                            PcieGeneration.PCI, 8, 4, 8));
    /*
     * Dual-socket server board for vintage-era rack hardware; more RAM slots and PCIe slots
     * than the desktop MTX variant to match server-class density expectations of the era.
     */
    public static final DeferredItem<MotherboardItem> MOTHERBOARD_EEB_VINTAGE =
            board("motherboard_eeb_vintage", "MF EEB-V Server Board",
                    new MotherboardSpec(FormFactor.EEB, HardwareEra.VINTAGE,
                            CpuSocket.SOCKET_7, 2, Set.of(RamGeneration.SIMM, RamGeneration.EDO), 16,
                            PcieGeneration.PCI, 10, 8, 8));

    /*
     * Vintage spinning disks: MFM/IDE rotating platters of 20 MB and 100 MB. Tiny by design (the floor of
     * the storage ladder) and honest: at 16 bits an item costs 1 MB, so they hold 20 and 100 items.
     */
    public static final DeferredItem<DiskItem> DISK_TRENCH_20M =
            disk("disk_vaultis_trench_20m", "Vaultis Trench HDD 20M",
                    new DiskSpec(StorageTier.HDD, HardwareEra.VINTAGE, 20L, 5));
    public static final DeferredItem<DiskItem> DISK_TRENCH_100M =
            disk("disk_vaultis_trench_100m", "Vaultis Trench HDD 100M",
                    new DiskSpec(StorageTier.HDD, HardwareEra.VINTAGE, 100L, 6));

    //  LEGACY: AGP/PCIe 1.0 buses, SDRAM/DDR/DDR2 RAM, first multi-core CPUs

    public static final DeferredItem<CpuItem> CPU_INTEGRA_VERTEX_700 =
            cpu("cpu_integra_vertex_700", "Integra Vertex 700",
                    new CpuSpec(HardwareEra.LEGACY, CpuSocket.SOCKET_370, 1, 700, 28, false));
    public static final DeferredItem<CpuItem> CPU_INTEGRA_VERTEX_III_S_1000 =
            cpu("cpu_integra_vertex_iii_s_1000", "Integra Vertex III-S 1000",
                    new CpuSpec(HardwareEra.LEGACY, CpuSocket.SOCKET_370, 1, 1000, 30, false));
    public static final DeferredItem<CpuItem> CPU_INTEGRA_VERTEX_III_S_1400 =
            cpu("cpu_integra_vertex_iii_s_1400", "Integra Vertex III-S 1400",
                    new CpuSpec(HardwareEra.LEGACY, CpuSocket.SOCKET_370, 1, 1400, 32, false));
    public static final DeferredItem<CpuItem> CPU_VELOCION_SPRINT_XP_2400 =
            cpu("cpu_velocion_sprint_xp_2400", "Velocion Sprint XP 2400+",
                    new CpuSpec(HardwareEra.LEGACY, CpuSocket.SOCKET_A, 1, 2000, 65, false));
    public static final DeferredItem<CpuItem> CPU_VELOCION_SPRINT_XP_3200 =
            cpu("cpu_velocion_sprint_xp_3200", "Velocion Sprint XP 3200+",
                    new CpuSpec(HardwareEra.LEGACY, CpuSocket.SOCKET_A, 1, 2200, 76, false));
    public static final DeferredItem<CpuItem> CPU_VELOCION_SPRINT_XP_3800 =
            cpu("cpu_velocion_sprint_xp_3800", "Velocion Sprint XP 3800+",
                    new CpuSpec(HardwareEra.LEGACY, CpuSocket.SOCKET_A, 1, 2400, 89, false));
    public static final DeferredItem<CpuItem> CPU_INTEGRA_DUO_E4300 =
            cpu("cpu_integra_duo_e4300", "Integra Duo E4300",
                    new CpuSpec(HardwareEra.LEGACY, CpuSocket.LGA_775, 2, 1800, 65, false));
    public static final DeferredItem<CpuItem> CPU_INTEGRA_DUO_E6600 =
            cpu("cpu_integra_duo_e6600", "Integra Duo E6600",
                    new CpuSpec(HardwareEra.LEGACY, CpuSocket.LGA_775, 2, 2400, 65, false));
    public static final DeferredItem<CpuItem> CPU_INTEGRA_DUO_E8500 =
            cpu("cpu_integra_duo_e8500", "Integra Duo E8500",
                    new CpuSpec(HardwareEra.LEGACY, CpuSocket.LGA_775, 2, 3160, 65, false));
    public static final DeferredItem<CpuItem> CPU_VELOCION_DUAL_240 =
            cpu("cpu_velocion_dual_240", "Velocion Dual 240",
                    new CpuSpec(HardwareEra.LEGACY, CpuSocket.SOCKET_940, 2, 2200, 85, false));
    public static final DeferredItem<CpuItem> CPU_VELOCION_DUAL_280 =
            cpu("cpu_velocion_dual_280", "Velocion Dual 280",
                    new CpuSpec(HardwareEra.LEGACY, CpuSocket.SOCKET_940, 2, 2400, 95, false));
    public static final DeferredItem<CpuItem> CPU_VELOCION_DUAL_285 =
            cpu("cpu_velocion_dual_285", "Velocion Dual 285",
                    new CpuSpec(HardwareEra.LEGACY, CpuSocket.SOCKET_940, 2, 2600, 95, false));
    public static final DeferredItem<CpuItem> CPU_INTEGRA_SERVO_5100 =
            cpu("cpu_integra_servo_5100", "Integra Servo 5100",
                    new CpuSpec(HardwareEra.LEGACY, CpuSocket.LGA_771, 2, 2000, 65, false));
    public static final DeferredItem<CpuItem> CPU_INTEGRA_SERVO_5160 =
            cpu("cpu_integra_servo_5160", "Integra Servo 5160",
                    new CpuSpec(HardwareEra.LEGACY, CpuSocket.LGA_771, 2, 3000, 80, false));
    public static final DeferredItem<CpuItem> CPU_INTEGRA_SERVO_5365 =
            cpu("cpu_integra_servo_5365", "Integra Servo 5365",
                    new CpuSpec(HardwareEra.LEGACY, CpuSocket.LGA_771, 4, 2000, 120, false));

    public static final DeferredItem<RamItem> RAM_SDRAM_128 =
            ram("ram_sdram_128", "Stratix Layer SDRAM-128", new RamSpec(HardwareEra.LEGACY, RamGeneration.SDRAM, 32, 5));
    public static final DeferredItem<RamItem> RAM_DDR_512 =
            ram("ram_ddr_512", "Stratix Layer DDR-512", new RamSpec(HardwareEra.LEGACY, RamGeneration.DDR, 128, 10));
    public static final DeferredItem<RamItem> RAM_DDR2_2048 =
            ram("ram_ddr2_2048", "Stratix Layer DDR2-2048", new RamSpec(HardwareEra.LEGACY, RamGeneration.DDR2, 512, 12));

    /*
     * Legacy GPU ladder (AGP entry to PCIe 1.0 high-end). The Radiance SE is the cheap AGP 4x floor; the
     * GTX 280 caps the era on PCIe 1.0 above the 8800 GT. Tens-to-hundreds of cores and tens-to-hundreds of
     * MB of VRAM track the AGP/early-PCIe generation.
     */
    public static final DeferredItem<GpuItem> GPU_RADIANCE_9200_SE =
            gpu("gpu_radiance_9200_se", "Pyrix Radiance 9200 SE",
                    new GpuSpec(HardwareEra.LEGACY, PcieGeneration.AGP_4X, 2, 16, 30));
    public static final DeferredItem<GpuItem> GPU_VERTEX_256 =
            gpu("gpu_vertex_256", "Visara Vertex 256",
                    new GpuSpec(HardwareEra.LEGACY, PcieGeneration.AGP_4X, 4, 32, 50));
    public static final DeferredItem<GpuItem> GPU_RADIANCE_9800_PRO =
            gpu("gpu_radiance_9800_pro", "Pyrix Radiance 9800 Pro",
                    new GpuSpec(HardwareEra.LEGACY, PcieGeneration.AGP_8X, 8, 128, 70));
    public static final DeferredItem<GpuItem> GPU_VERTEX_8800_GT =
            gpu("gpu_vertex_8800_gt", "Visara Vertex 8800 GT",
                    new GpuSpec(HardwareEra.LEGACY, PcieGeneration.PCIE_1_0, 112, 512, 110));
    public static final DeferredItem<GpuItem> GPU_VERTEX_GTX_280 =
            gpu("gpu_vertex_gtx_280", "Visara Vertex GTX 280",
                    new GpuSpec(HardwareEra.LEGACY, PcieGeneration.PCIE_1_0, 240, 1024, 145));

    public static final DeferredItem<PsuItem> PSU_500B =
            psu("psu_500b", "MF PowerBasic 500B", new PsuSpec(500, 80));

    /*
     * The Legacy ATX board lists "one of Socket A / Socket 370 / LGA 775". A board spec carries a single
     * socket, so this is modeled as one board item per socket, the clean one-value-per-record mapping.
     * Socket A (Athlon XP generation) pre-dates PCIe; its primary GPU slot is AGP 8x.
     */
    public static final DeferredItem<MotherboardItem> MOTHERBOARD_ATX_LEGACY_SKA =
            board("motherboard_atx_legacy_ska", "MF ATX Legacy Motherboard (Socket A)",
                    new MotherboardSpec(FormFactor.ATX, HardwareEra.LEGACY,
                            CpuSocket.SOCKET_A, 1, Set.of(RamGeneration.DDR, RamGeneration.DDR2), 4,
                            PcieGeneration.AGP_8X, 4, 4, 4));
    public static final DeferredItem<MotherboardItem> MOTHERBOARD_ATX_LEGACY_S370 =
            board("motherboard_atx_legacy_s370", "MF ATX Legacy Motherboard (Socket 370)",
                    new MotherboardSpec(FormFactor.ATX, HardwareEra.LEGACY,
                            CpuSocket.SOCKET_370, 1, Set.of(RamGeneration.DDR, RamGeneration.DDR2), 4,
                            PcieGeneration.PCIE_1_0, 4, 4, 4));
    public static final DeferredItem<MotherboardItem> MOTHERBOARD_ATX_LEGACY_LGA775 =
            board("motherboard_atx_legacy_lga775", "MF ATX Legacy Motherboard (LGA 775)",
                    new MotherboardSpec(FormFactor.ATX, HardwareEra.LEGACY,
                            CpuSocket.LGA_775, 1, Set.of(RamGeneration.DDR, RamGeneration.DDR2), 4,
                            PcieGeneration.PCIE_1_0, 4, 4, 4));
    // The Legacy EATX board lists "one of LGA 775 / Socket 940": one board per socket.
    public static final DeferredItem<MotherboardItem> MOTHERBOARD_EATX_LEGACY_LGA775 =
            board("motherboard_eatx_legacy_lga775", "MF EATX Legacy Motherboard (LGA 775)",
                    new MotherboardSpec(FormFactor.EATX, HardwareEra.LEGACY,
                            CpuSocket.LGA_775, 2, Set.of(RamGeneration.DDR2), 8, PcieGeneration.PCIE_1_0, 6, 6, 4));
    public static final DeferredItem<MotherboardItem> MOTHERBOARD_EATX_LEGACY_S940 =
            board("motherboard_eatx_legacy_s940", "MF EATX Legacy Motherboard (Socket 940)",
                    new MotherboardSpec(FormFactor.EATX, HardwareEra.LEGACY,
                            CpuSocket.SOCKET_940, 2, Set.of(RamGeneration.DDR2), 8, PcieGeneration.PCIE_1_0, 6, 6, 4));
    public static final DeferredItem<MotherboardItem> MOTHERBOARD_MTX_LEGACY =
            board("motherboard_mtx_legacy", "MF MTX-L Motherboard",
                    new MotherboardSpec(FormFactor.MTX, HardwareEra.LEGACY,
                            CpuSocket.SOCKET_940, 4, Set.of(RamGeneration.DDR2), 24, PcieGeneration.PCIE_1_0, 8, 6, 8));

    /*
     * Legacy rotating and early solid-state disks: IDE HDDs of 4 GB and 20 GB and the first affordable
     * SATA SSD of 64 GB. At 32 bits an item costs 16 MB, so they hold 256, 1 280 and 4 096 items,
     * between vintage and the standard 500 GB / 1 TB floor.
     */
    public static final DeferredItem<DiskItem> DISK_LINK_IDE_4G =
            disk("disk_vaultis_link_ide_4g", "Vaultis Link IDE-HDD 4G",
                    new DiskSpec(StorageTier.HDD, HardwareEra.LEGACY, 256L, 7));
    public static final DeferredItem<DiskItem> DISK_LINK_IDE_20G =
            disk("disk_vaultis_link_ide_20g", "Vaultis Link IDE-HDD 20G",
                    new DiskSpec(StorageTier.HDD, HardwareEra.LEGACY, 1280L, 8));
    public static final DeferredItem<DiskItem> DISK_LINK_SATA_SSD_64G =
            disk("disk_vaultis_link_sata_ssd_64g", "Vaultis Link SATA-SSD 64G",
                    new DiskSpec(StorageTier.SSD, HardwareEra.LEGACY, 4096L, 3));

    /*
     *  STANDARD: completion of the partially-registered set (PCIe 2.0/3.0, DDR3)
     *  The Servo 2620/2690/2699, the Ascent X4 965, the DDR3-8192, the HD 7970 GPU, the MTX-P /
     *  EEB-P / ATX-P boards and the 650G PSU already live in ComputingModule. These fill the gaps.
     */

    public static final DeferredItem<CpuItem> CPU_ASCENT_X4_955 =
            cpu("cpu_ascent_x4_955", "Velocion Ascent X4 955",
                    new CpuSpec(HardwareEra.STANDARD, CpuSocket.AM3, 4, 3200, 125, false));
    public static final DeferredItem<CpuItem> CPU_ASCENT_X6_1090T =
            cpu("cpu_ascent_x6_1090t", "Velocion Ascent X6 1090T",
                    new CpuSpec(HardwareEra.STANDARD, CpuSocket.AM3, 6, 3200, 125, false));
    public static final DeferredItem<CpuItem> CPU_APEX_5_4590 =
            cpu("cpu_apex_5_4590", "Integra Apex 5 4590",
                    new CpuSpec(HardwareEra.STANDARD, CpuSocket.LGA_1150, 4, 3300, 84, false));
    public static final DeferredItem<CpuItem> CPU_APEX_5_4690K =
            cpu("cpu_apex_5_4690k", "Integra Apex 5 4690K",
                    new CpuSpec(HardwareEra.STANDARD, CpuSocket.LGA_1150, 4, 3500, 88, false));
    public static final DeferredItem<CpuItem> CPU_APEX_7_4790K =
            cpu("cpu_apex_7_4790k", "Integra Apex 7 4790K",
                    new CpuSpec(HardwareEra.STANDARD, CpuSocket.LGA_1150, 4, 4000, 88, false));

    /*
     * Standard GPU ladder (PCIe 2.0 entry to PCIe 3.0 high-end). The HD 7970 (in ComputingModule) is the
     * upper-mid card; the GTX 550 Ti and HD 6850 are the PCIe 2.0 floor, and the GTX 780 Ti tops the era on
     * PCIe 3.0 above the 7970. Hundreds-to-thousands of cores and 1-3 GB of VRAM fit this generation.
     */
    public static final DeferredItem<GpuItem> GPU_RADIANCE_HD_7970 = ComputingModule.GPU_HD_7970;
    public static final DeferredItem<GpuItem> GPU_VERTEX_GTX_550_TI =
            gpu("gpu_vertex_gtx_550_ti", "Visara Vertex GTX 550 Ti",
                    new GpuSpec(HardwareEra.STANDARD, PcieGeneration.PCIE_2_0, 192, 1024, 116));
    public static final DeferredItem<GpuItem> GPU_RADIANCE_HD_6850 =
            gpu("gpu_radiance_hd_6850", "Pyrix Radiance HD 6850",
                    new GpuSpec(HardwareEra.STANDARD, PcieGeneration.PCIE_2_0, 960, 2048, 127));
    public static final DeferredItem<GpuItem> GPU_VERTEX_GTX_780_TI =
            gpu("gpu_vertex_gtx_780_ti", "Visara Vertex GTX 780 Ti",
                    new GpuSpec(HardwareEra.STANDARD, PcieGeneration.PCIE_3_0, 2880, 3072, 250));

    public static final DeferredItem<PsuItem> PSU_850G =
            psu("psu_850g", "MF PowerGold 850G", new PsuSpec(850, 90));

    /*
     * The Standard ATX board comes in an AM3 flavour (already MOTHERBOARD_ATX_P in ComputingModule) and
     * an LGA 1150 flavour for the Apex line, so register the missing socket variant and the WS board.
     */
    public static final DeferredItem<MotherboardItem> MOTHERBOARD_ATX_STANDARD_LGA1150 =
            board("motherboard_atx_standard_lga1150", "MF ATX Standard Motherboard (LGA 1150)",
                    new MotherboardSpec(FormFactor.ATX, HardwareEra.STANDARD,
                            CpuSocket.LGA_1150, 1, Set.of(RamGeneration.DDR3), 4, PcieGeneration.PCIE_3_0, 4, 2, 4));
    public static final DeferredItem<MotherboardItem> MOTHERBOARD_EATX_STANDARD_WS =
            board("motherboard_eatx_standard_ws", "MF EATX Standard Workstation Board",
                    new MotherboardSpec(FormFactor.EATX, HardwareEra.STANDARD,
                            CpuSocket.LGA_2011, 1, Set.of(RamGeneration.DDR3), 8, PcieGeneration.PCIE_3_0, 7, 4, 4));

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
            /*
             * PSUs carry no era field; place them after the era they belong to by registration order is
             * not possible, so they are appended once at the end in registration (era) order below.
             */
        }
        for (final DeferredItem<PsuItem> h : PSUS) {
            ordered.add(h.get());
        }
        for (final DeferredItem<DiskItem> h : DISKS) {
            ordered.add(h.get());
        }
        return ordered;
    }

    /**
     * Forces this class to load so its static holders register onto {@link ComputingModule#ITEMS}. Called
     * from {@link ComputingModule} before the registers are handed to the mod event bus.
     */
    public static void init() {
        /*
         * Intentionally empty: referencing the class triggers static initialization, which performs the
         * registrations above. The accumulator lists are populated as a side effect.
         */
    }
}
