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
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.minecraft.data.PackOutput;

/**
 * Generates item models.
 */
public class JscItemModelProvider extends ItemModelProvider {

    public JscItemModelProvider(final PackOutput output, final ExistingFileHelper existingFiles) {
        super(output, JsComputronics.MODID, existingFiles);
    }

    @Override
    protected void registerModels() {
        // UncheckedModelFile avoids datagen ordering coupling: the parent
        // block model is produced by the BlockStateProvider in the same run.
        getBuilder("macerator")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/macerator")));
        getBuilder("coal_generator")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/coal_generator")));

        basicItem(IndustrialModule.IRON_DUST.get());

        // Cables show their core model in the inventory.
        getBuilder("ethernet_cable")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/ethernet_cable_core")));
        getBuilder("hbw_cable")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/hbw_cable_core")));
        getBuilder("peripheral_cable")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/peripheral_cable_core")));

        getBuilder("mainframe")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/mainframe")));
        getBuilder("personal_router")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/personal_router")));
        getBuilder("server_router")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/server_router")));
        getBuilder("datacenter_station")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/datacenter_station")));
        getBuilder("monitor")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/monitor")));
        getBuilder("tank")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/tank")));
        getBuilder("personal_computer")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/personal_computer")));
        basicItem(ComputingModule.MOTHERBOARD_MTX_P.get());
        basicItem(ComputingModule.MOTHERBOARD_ATX_P.get());
        basicItem(ComputingModule.CPU_SERVO_2620.get());
        basicItem(ComputingModule.CPU_SERVO_2690.get());
        basicItem(ComputingModule.CPU_SERVO_2699.get());
        basicItem(ComputingModule.CPU_ASCENT_965.get());
        basicItem(ComputingModule.RAM_DDR3_8192.get());
        basicItem(ComputingModule.GPU_HD_7970.get());
        basicItem(ComputingModule.CRAFTING_CARD_T2.get());
        basicItem(ComputingModule.PSU_650G.get());
        basicItem(ComputingModule.MOTHERBOARD_EEB_P.get());
        basicItem(ComputingModule.SERVER_CASE.get());
        basicItem(ComputingModule.SERVER.get());
        getBuilder("server_rack")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/server_rack")));
        getBuilder("import_bus")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/import_bus_part")));
        getBuilder("export_bus")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/export_bus_part")));
        for (final ComputingModule.DiskEntry disk : ComputingModule.DISKS) {
            basicItem(disk.item().get());
        }
    }
}
