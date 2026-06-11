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
        addBlock(ComputingModule.MAINFRAME, "Mainframe");
        add("item.jsc.mainframe.tooltip", "Forms a 3x2x2 structure when placed");
        addBlock(ComputingModule.PERSONAL_COMPUTER, "Personal Computer");
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
        for (final ComputingModule.DiskEntry disk : ComputingModule.DISKS) {
            add(disk.item().get(), disk.displayName());
        }
    }
}
