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

import java.util.List;

/**
 * Generates item models.
 */
public class JscItemModelProvider extends ItemModelProvider {

    /**
     * GPU ids whose textures are not yet in resources (the artwork is awaiting review). Their expected
     * textures are marked as generated so a basic generated model can still be produced for them during
     * datagen. Drop an id from this list once its real texture is added under resources.
     */
    private static final List<String> PREVIEW_ONLY_GPU_TEXTURES = List.of(
            "gpu_prism_4",
            "gpu_voodoo_gfx",
            "gpu_radiance_9200_se",
            "gpu_vertex_gtx_280",
            "gpu_vertex_gtx_550_ti",
            "gpu_radiance_hd_6850",
            "gpu_vertex_gtx_780_ti");

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
        getBuilder("hpc_cable")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/hpc_cable_core")));
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
        getBuilder("vintage_personal_computer")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/vintage_personal_computer")));
        getBuilder("legacy_personal_computer")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/legacy_personal_computer")));
        getBuilder("crafting_computer")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/crafting_computer")));
        getBuilder("supercomputer_node")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/supercomputer_node")));
        getBuilder("hbw_interface")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/hbw_interface")));
        getBuilder("supercomputer_console")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/supercomputer_console")));
        basicItem(ComputingModule.PHI_5100.get());
        basicItem(ComputingModule.PHI_7120.get());
        basicItem(ComputingModule.PHI_7290.get());
        basicItem(ComputingModule.PHI_9000.get());
        getBuilder("pattern_encoder")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/pattern_encoder")));
        getBuilder("pattern_reader")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/pattern_reader")));
        basicItem(ComputingModule.PATTERN_DISC.get());
        basicItem(ComputingModule.PATTERN_DISC_RW.get());
        basicItem(ComputingModule.MOTHERBOARD_MTX_P.get());
        basicItem(ComputingModule.MOTHERBOARD_ATX_P.get());
        basicItem(ComputingModule.CPU_SERVO_2620.get());
        basicItem(ComputingModule.CPU_SERVO_2690.get());
        basicItem(ComputingModule.CPU_SERVO_2699.get());
        basicItem(ComputingModule.CPU_ASCENT_965.get());
        basicItem(ComputingModule.RAM_DDR3_8192.get());
        basicItem(ComputingModule.GPU_HD_7970.get());
        basicItem(ComputingModule.CRAFTING_CARD_T2.get());
        basicItem(ComputingModule.CRAFTING_CARD_T3.get());
        basicItem(ComputingModule.PSU_650G.get());
        basicItem(ComputingModule.MOTHERBOARD_EEB_P.get());
        basicItem(ComputingModule.SERVER_CASE.get());
        basicItem(ComputingModule.SERVER.get());
        getBuilder("server_rack")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/server_rack_bays_0")));
        getBuilder("import_bus")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/import_bus_part")));
        getBuilder("export_bus")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/export_bus_part")));
        for (final ComputingModule.DiskEntry disk : ComputingModule.DISKS) {
            basicItem(disk.item().get());
        }

        // Per-era hardware catalog — a generated (layer0 = item texture) model for every component.
        dev.jsc.jscomputronics.module.computing.HardwareItems.CPUS.forEach(h -> basicItem(h.get()));
        dev.jsc.jscomputronics.module.computing.HardwareItems.RAMS.forEach(h -> basicItem(h.get()));
        // These newly added GPUs ship without a repo texture yet (the artwork is pending review); mark each
        // expected texture as generated so basicItem can reference it without the datagen existence check
        // failing. Remove the matching id from this set once its real texture lands in resources.
        for (final String previewOnlyGpu : PREVIEW_ONLY_GPU_TEXTURES) {
            existingFileHelper.trackGenerated(modLoc("item/" + previewOnlyGpu), TEXTURE);
        }
        dev.jsc.jscomputronics.module.computing.HardwareItems.GPUS.forEach(h -> basicItem(h.get()));
        dev.jsc.jscomputronics.module.computing.HardwareItems.PSUS.forEach(h -> basicItem(h.get()));
        dev.jsc.jscomputronics.module.computing.HardwareItems.MOTHERBOARDS.forEach(h -> basicItem(h.get()));
    }
}
