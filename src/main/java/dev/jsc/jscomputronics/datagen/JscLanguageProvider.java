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
        add("itemGroup.jsc.main", "J's Computronics");
        addBlock(IndustrialModule.MACERATOR, "Macerator");
        addBlock(IndustrialModule.COAL_GENERATOR, "Coal Generator");
        add(IndustrialModule.IRON_DUST.get(), "Iron Dust");
        addBlock(ComputingModule.ETHERNET_CABLE, "Ethernet Cable");
        addBlock(ComputingModule.HBW_CABLE, "HBW Cable");
        addBlock(ComputingModule.MAINFRAME, "Mainframe");
        add(ComputingModule.MOTHERBOARD_MTX_P.get(), "MTX-P Motherboard");
        add(ComputingModule.CPU_SERVO_2620.get(), "Integra Servo 2620");
        add(ComputingModule.CPU_SERVO_2690.get(), "Integra Servo 2690");
        add(ComputingModule.CPU_SERVO_2699.get(), "Integra Servo 2699");
        add(ComputingModule.RAM_DDR3_8192.get(), "Stratix DDR3-8192");
        add(ComputingModule.GPU_HD_7970.get(), "Pyrix Radiance HD 7970");
        add(ComputingModule.PSU_650G.get(), "MF PowerGold 650G");
    }
}
