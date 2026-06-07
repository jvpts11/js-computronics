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
        addBlock(ComputingModule.PERIPHERAL_CABLE, "Peripheral Cable");
        addBlock(ComputingModule.PERSONAL_ROUTER, "Personal Router");
        addBlock(ComputingModule.MONITOR, "Monitor");
        add("block.jsc.monitor.unlinked", "No computer linked");
        addBlock(ComputingModule.MAINFRAME, "Mainframe");
        add("item.jsc.mainframe.tooltip", "Forms a 3x2x2 structure when placed");
        addBlock(ComputingModule.PERSONAL_COMPUTER, "Personal Computer");
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
