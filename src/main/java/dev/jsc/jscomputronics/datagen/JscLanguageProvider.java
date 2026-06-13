/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.datagen;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.HardwareItems;
import dev.jsc.jscomputronics.module.industrial.IndustrialModule;
import net.neoforged.neoforge.common.data.LanguageProvider;
import net.minecraft.data.PackOutput;

/**
 * Generates the base English ({@code en_us}) language file.
 */
public class JscLanguageProvider extends LanguageProvider {

    public JscLanguageProvider(final PackOutput output) {
        super(output, JsComputronics.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        add("itemGroup.jsc.industrial", "J's Computronics: Industrial");
        add("itemGroup.jsc.computing", "J's Computronics: Computing");
        addBlock(IndustrialModule.MACERATOR, "Macerator");
        addBlock(IndustrialModule.COAL_GENERATOR, "Coal Generator");
        add(IndustrialModule.IRON_DUST.get(), "Iron Dust");
        addBlock(ComputingModule.ETHERNET_CABLE, "Ethernet Cable");
        addBlock(ComputingModule.HBW_CABLE, "HBW Cable");
        addBlock(ComputingModule.HPC_CABLE, "High Compute Cable");
        addBlock(ComputingModule.PERIPHERAL_CABLE, "Peripheral Cable");
        addBlock(ComputingModule.PERSONAL_ROUTER, "Personal Router");
        addBlock(ComputingModule.SERVER_ROUTER, "Server Router");
        addBlock(ComputingModule.DATACENTER_STATION, "Datacenter Station");
        addBlock(ComputingModule.MONITOR, "Monitor");
        addBlock(ComputingModule.TANK, "Tank");
        add("block.jsc.monitor.unlinked", "No computer linked");
        add("block.jsc.monitor.no_computer", "No computer found in range over a Peripheral Cable");
        add("block.jsc.monitor.no_gpu", "The computer has no GPU - install a GPU to host monitors (4 per GPU)");
        add("block.jsc.monitor.at_capacity", "The computer's monitor outputs are all in use");
        add("block.jsc.monitor.hint_command_prompt", "Sneak-use to open the Command Prompt");
        add("program.jsc.command_prompt", "Command Prompt");
        add("program.jsc.nms", "Network Management Studio");
        addBlock(ComputingModule.MAINFRAME, "Mainframe");
        add("item.jsc.mainframe.tooltip", "Forms a 3x2x2 structure when placed");
        addBlock(ComputingModule.PERSONAL_COMPUTER, "Personal Computer");
        addBlock(ComputingModule.VINTAGE_PERSONAL_COMPUTER, "Vintage Personal Computer");
        addBlock(ComputingModule.LEGACY_PERSONAL_COMPUTER, "Legacy Personal Computer");
        addBlock(ComputingModule.CRAFTING_COMPUTER, "Crafting Computer");
        addBlock(ComputingModule.PATTERN_ENCODER, "Pattern Encoder");
        addBlock(ComputingModule.PATTERN_READER, "Pattern Reader");
        addBlock(ComputingModule.SUPERCOMPUTER_NODE, "Supercomputer Node");
        add("item.jsc.supercomputer_node.tooltip", "A full computer: assemble it, seat a Phi, wire it with High Compute Cable");
        addBlock(ComputingModule.HBW_INTERFACE, "HBW Interface");
        add("item.jsc.hbw_interface.tooltip", "Uplinks a node cluster to the HBW backbone");
        addBlock(ComputingModule.SUPERCOMPUTER_CONSOLE, "Supercomputer Console");
        add(ComputingModule.PHI_5100.get(), "Integra Phi 5100 Co-processor");
        add(ComputingModule.PHI_7120.get(), "Integra Phi 7120 Co-processor");
        add(ComputingModule.PHI_7290.get(), "Integra Phi 7290 Co-processor");
        add(ComputingModule.PHI_9000.get(), "Integra Phi 9000 Co-processor");
        add(ComputingModule.PATTERN_DISC.get(), "Pattern Disc");
        add(ComputingModule.PATTERN_DISC_RW.get(), "Rewritable Pattern Disc");
        addBlock(ComputingModule.SERVER_RACK, "Server Rack");
        add(ComputingModule.IMPORT_BUS_ITEM.get(), "Import Bus");
        add("item.jsc.import_bus.tooltip", "Right-click a data cable to attach; pulls items into the network");
        add(ComputingModule.EXPORT_BUS_ITEM.get(), "Export Bus");
        add("item.jsc.export_bus.tooltip", "Right-click a data cable to attach; pushes the filtered item out");
        add(ComputingModule.MOTHERBOARD_MTX_P.get(), "MTX-P Motherboard");
        add(ComputingModule.MOTHERBOARD_ATX_P.get(), "ATX-P Motherboard");
        add(ComputingModule.CPU_SERVO_2620.get(), "Integra Servo 2620");
        add(ComputingModule.CPU_SERVO_2690.get(), "Integra Servo 2690");
        add(ComputingModule.CPU_SERVO_2699.get(), "Integra Servo 2699");
        add(ComputingModule.CPU_ASCENT_965.get(), "Velocion Ascent X4 965");
        add(ComputingModule.RAM_DDR3_8192.get(), "Stratix DDR3-8192");
        add(ComputingModule.GPU_HD_7970.get(), "Pyrix Radiance HD 7970");
        add(ComputingModule.CRAFTING_CARD_T2.get(), "Forge Logic Crafting Card");
        add(ComputingModule.PSU_650G.get(), "MF PowerGold 650G");
        add(ComputingModule.MOTHERBOARD_EEB_P.get(), "EEB-P Server Board");
        add(ComputingModule.SERVER_CASE.get(), "Server Case");
        add(ComputingModule.SERVER.get(), "Server");
        add("item.jsc.server.tooltip", "Operates only inside a Server Rack");
        add("item.jsc.server_case.tooltip", "Crafting ingredient for a Server");
        add("menu.jsc.server_assembly", "Server Assembly");
        add("menu.jsc.network_overview", "Network Overview");
        add("gui.jsc.confirm", "Confirm");
        add("gui.jsc.cancel", "Cancel");
        // Storage tab — the per-disk public/private slider and its readouts.
        add("jsc.gui.storage.public_private", "Public / Private");
        add("jsc.gui.storage.public", "%s%% public");
        add("jsc.gui.storage.private", "%s%% private");
        add("jsc.gui.storage.always_public", "Public - network storage");
        add("jsc.gui.storage.no_disk", "no disk");
        add("jsc.gui.storage.disk", "Disk %s");
        for (final ComputingModule.DiskEntry disk : ComputingModule.DISKS) {
            add(disk.item().get(), disk.displayName());
        }
        addHardwareCatalog();
    }

    /**
     * Display names for the per-era hardware catalog. Names follow the product lines (Integra,
     * Velocion, Stratix, Visara, Pyrix, MF). The Standard era's first items already have names in
     * {@link #addTranslations()}; this method covers the items added across the Vintage, Legacy and
     * Standard eras.
     */
    private void addHardwareCatalog() {
        // Vintage
        add(HardwareItems.CPU_INTEGRA_486SX.get(), "Integra 486SX");
        add(HardwareItems.CPU_INTEGRA_486DX2.get(), "Integra 486DX2");
        add(HardwareItems.CPU_INTEGRA_486DX4.get(), "Integra 486DX4");
        add(HardwareItems.CPU_VELOCION_K6_II.get(), "Velocion K6-II");
        add(HardwareItems.CPU_VELOCION_K6_III.get(), "Velocion K6-III");
        add(HardwareItems.CPU_VELOCION_K6_III_PLUS.get(), "Velocion K6-III+");
        add(HardwareItems.RAM_SIMM_4.get(), "Stratix Layer SIMM-4");
        add(HardwareItems.RAM_EDO_16.get(), "Stratix Layer EDO-16");
        add(HardwareItems.GPU_VGA_256.get(), "Visara VGA-256");
        add(HardwareItems.GPU_3D_BLASTER.get(), "Pyrix 3D Blaster");
        add(HardwareItems.PSU_300B.get(), "MF PowerBasic 300B");
        add(HardwareItems.MOTHERBOARD_BABYAT_VINTAGE.get(), "MF Baby-AT I Motherboard");
        add(HardwareItems.MOTHERBOARD_AT_VINTAGE.get(), "MF AT Standard Motherboard");
        add(HardwareItems.MOTHERBOARD_MTX_VINTAGE.get(), "MF MTX-V Motherboard");

        // Legacy
        add(HardwareItems.CPU_INTEGRA_VERTEX_700.get(), "Integra Vertex 700");
        add(HardwareItems.CPU_INTEGRA_VERTEX_III_S_1000.get(), "Integra Vertex III-S 1000");
        add(HardwareItems.CPU_INTEGRA_VERTEX_III_S_1400.get(), "Integra Vertex III-S 1400");
        add(HardwareItems.CPU_VELOCION_SPRINT_XP_2400.get(), "Velocion Sprint XP 2400+");
        add(HardwareItems.CPU_VELOCION_SPRINT_XP_3200.get(), "Velocion Sprint XP 3200+");
        add(HardwareItems.CPU_VELOCION_SPRINT_XP_3800.get(), "Velocion Sprint XP 3800+");
        add(HardwareItems.CPU_INTEGRA_DUO_E4300.get(), "Integra Duo E4300");
        add(HardwareItems.CPU_INTEGRA_DUO_E6600.get(), "Integra Duo E6600");
        add(HardwareItems.CPU_INTEGRA_DUO_E8500.get(), "Integra Duo E8500");
        add(HardwareItems.CPU_VELOCION_DUAL_240.get(), "Velocion Dual 240");
        add(HardwareItems.CPU_VELOCION_DUAL_280.get(), "Velocion Dual 280");
        add(HardwareItems.CPU_VELOCION_DUAL_285.get(), "Velocion Dual 285");
        add(HardwareItems.CPU_INTEGRA_SERVO_5100.get(), "Integra Servo 5100");
        add(HardwareItems.CPU_INTEGRA_SERVO_5160.get(), "Integra Servo 5160");
        add(HardwareItems.CPU_INTEGRA_SERVO_5365.get(), "Integra Servo 5365");
        add(HardwareItems.RAM_SDRAM_128.get(), "Stratix Layer SDRAM-128");
        add(HardwareItems.RAM_DDR_512.get(), "Stratix Layer DDR-512");
        add(HardwareItems.RAM_DDR2_2048.get(), "Stratix Layer DDR2-2048");
        add(HardwareItems.GPU_VERTEX_256.get(), "Visara Vertex 256");
        add(HardwareItems.GPU_RADIANCE_9800_PRO.get(), "Pyrix Radiance 9800 Pro");
        add(HardwareItems.GPU_VERTEX_8800_GT.get(), "Visara Vertex 8800 GT");
        add(HardwareItems.PSU_500B.get(), "MF PowerBasic 500B");
        add(HardwareItems.MOTHERBOARD_ATX_LEGACY_SKA.get(), "MF ATX Legacy Motherboard (Socket A)");
        add(HardwareItems.MOTHERBOARD_ATX_LEGACY_S370.get(), "MF ATX Legacy Motherboard (Socket 370)");
        add(HardwareItems.MOTHERBOARD_ATX_LEGACY_LGA775.get(), "MF ATX Legacy Motherboard (LGA 775)");
        add(HardwareItems.MOTHERBOARD_EATX_LEGACY_LGA775.get(), "MF EATX Legacy Motherboard (LGA 775)");
        add(HardwareItems.MOTHERBOARD_EATX_LEGACY_S940.get(), "MF EATX Legacy Motherboard (Socket 940)");
        add(HardwareItems.MOTHERBOARD_MTX_LEGACY.get(), "MF MTX-L Motherboard");

        // Standard (completion)
        add(HardwareItems.CPU_ASCENT_X4_955.get(), "Velocion Ascent X4 955");
        add(HardwareItems.CPU_ASCENT_X6_1090T.get(), "Velocion Ascent X6 1090T");
        add(HardwareItems.CPU_APEX_5_4590.get(), "Integra Apex 5 4590");
        add(HardwareItems.CPU_APEX_5_4690K.get(), "Integra Apex 5 4690K");
        add(HardwareItems.CPU_APEX_7_4790K.get(), "Integra Apex 7 4790K");
        add(HardwareItems.PSU_850G.get(), "MF PowerGold 850G");
        add(HardwareItems.MOTHERBOARD_ATX_STANDARD_LGA1150.get(), "MF ATX Standard Motherboard (LGA 1150)");
        add(HardwareItems.MOTHERBOARD_EATX_STANDARD_WS.get(), "MF EATX Standard Workstation Board");
    }
}
