/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.datagen;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.JsComputronics;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

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
    private static final List<String> PREVIEW_ONLY_GPU_TEXTURES = List.of();

    public JscItemModelProvider(final PackOutput output, final ExistingFileHelper existingFiles) {
        super(output, JsComputronics.MODID, existingFiles);
    }

    /**
     * A rack cabinet's item: the built-in entity model, so the cabinet's own renderer draws it, with the
     * display transforms a block item uses so it sits in the slot and the hand like any other block.
     */
    private void cabinetItem(final String name) {
        getBuilder(name)
                .parent(new ModelFile.UncheckedModelFile("builtin/entity"))
                .transforms()
                .transform(net.minecraft.world.item.ItemDisplayContext.GUI)
                .rotation(30, 225, 0).scale(0.625F).end()
                .transform(net.minecraft.world.item.ItemDisplayContext.GROUND)
                .translation(0, 3, 0).scale(0.25F).end()
                .transform(net.minecraft.world.item.ItemDisplayContext.FIXED)
                .scale(0.5F).end()
                .transform(net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                .rotation(75, 45, 0).translation(0, 2.5F, 0).scale(0.375F).end()
                .transform(net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)
                .rotation(0, 45, 0).scale(0.4F).end()
                .transform(net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND)
                .rotation(0, 225, 0).scale(0.4F).end()
                .end();
    }

    @Override
    protected void registerModels() {
        /*
         * UncheckedModelFile avoids datagen ordering coupling: the parent
         * block model is produced by the BlockStateProvider in the same run.
         * Cables show their core model in the inventory.
         */
        getBuilder("ethernet_cable")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/ethernet_cable_core")));
        getBuilder("hbw_cable")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/hbw_cable_core")));
        getBuilder("hpc_cable")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/hpc_cable_core")));
        getBuilder("crafting_cable")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/crafting_cable_core")));
        getBuilder("crafting_switch")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/crafting_switch")));
        getBuilder("peripheral_cable")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/peripheral_cable_core")));

        cabinetItem("mainframe");
        cabinetItem("vintage_mainframe");
        cabinetItem("legacy_mainframe");
        getBuilder("personal_router")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/personal_router")));
        getBuilder("server_router")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/server_router")));
        getBuilder("datacenter_station")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/datacenter_station")));
        getBuilder("monitor")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/monitor")));
        getBuilder("vintage_monitor")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/vintage_monitor")));
        getBuilder("legacy_monitor")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/legacy_monitor")));
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
        getBuilder("vintage_crafting_computer")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/vintage_crafting_computer")));
        getBuilder("legacy_crafting_computer")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/legacy_crafting_computer")));
        getBuilder("cluster_management_computer")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/cluster_management_computer")));
        getBuilder("vintage_cluster_management_computer")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/vintage_cluster_management_computer")));
        getBuilder("legacy_cluster_management_computer")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/legacy_cluster_management_computer")));
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
        cabinetItem("pattern_encoder");
        cabinetItem("legacy_pattern_encoder");
        cabinetItem("vintage_pattern_encoder");
        getBuilder("floppy_drive").parent(new ModelFile.UncheckedModelFile(modLoc("block/floppy_drive")));
        getBuilder("cd_drive").parent(new ModelFile.UncheckedModelFile(modLoc("block/cd_drive")));
        getBuilder("dvd_drive").parent(new ModelFile.UncheckedModelFile(modLoc("block/dvd_drive")));
        getBuilder("dock_station").parent(new ModelFile.UncheckedModelFile(modLoc("block/dock_station")));
        basicItem(ComputingModule.FLOPPY_DISK.get());
        basicItem(ComputingModule.CD_ROM.get());
        basicItem(ComputingModule.CD_RW.get());
        basicItem(ComputingModule.DVD_ROM.get());
        basicItem(ComputingModule.DVD_RW.get());
        /*
         * The USB flash drive uses a hand-authored 3D model (models/item/usb_flash_drive.json), not a flat sprite,
         * so it is not generated here.
         */
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
        basicItem(ComputingModule.SERIAL_CONSOLE_CARD.get());
        basicItem(ComputingModule.MANAGEMENT_NIC.get());
        basicItem(ComputingModule.FABRIC_HOST_ADAPTER.get());
        basicItem(ComputingModule.PSU_650G.get());
        basicItem(ComputingModule.MOTHERBOARD_EEB_P.get());
        basicItem(ComputingModule.SERVER_CASE.get());
        basicItem(ComputingModule.SERVER.get());
        basicItem(ComputingModule.LEGACY_SERVER_CASE.get());
        basicItem(ComputingModule.LEGACY_SERVER.get());
        basicItem(ComputingModule.VINTAGE_SERVER_CASE.get());
        basicItem(ComputingModule.VINTAGE_SERVER.get());
        basicItem(ComputingModule.STORAGE_SERVER_CASE.get());
        basicItem(ComputingModule.STORAGE_SERVER.get());
        basicItem(ComputingModule.SUPERCOMPUTER_NODE.get());
        basicItem(ComputingModule.COMPUTE_SERVER_CASE.get());
        basicItem(ComputingModule.COMPUTE_SERVER.get());
        basicItem(ComputingModule.RAID_CONTROLLER.get());
        basicItem(ComputingModule.CACHE_CARD.get());
        basicItem(ComputingModule.KVM_SWITCH.get());
        basicItem(ComputingModule.RACK_UPS.get());
        basicItem(ComputingModule.COOLING_UNIT.get());
        /*
         * The cabinets have no block model at all (the block entity draws them) so their items are drawn
         * by a renderer of their own, showing the same cabinet. Vanilla only asks that renderer when the
         * item's model is the built-in entity one, and the transforms below are what place the cabinet in
         * the slot and in the hand.
         */
        cabinetItem("server_rack");
        cabinetItem("legacy_server_rack");
        cabinetItem("vintage_server_rack");
        cabinetItem("supercomputer_rack");
        getBuilder("import_bus")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/import_bus_part")));
        getBuilder("export_bus")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/export_bus_part")));
        getBuilder("input_bus")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/export_bus_part")));
        getBuilder("receiving_bus")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/import_bus_part")));
        for (final ComputingModule.DiskEntry disk : ComputingModule.DISKS) {
            basicItem(disk.item().get());
        }

        // Per-era hardware catalog: a generated (layer0 = item texture) model for every component.
        dev.jstech.computronics.HardwareItems.DISKS.forEach(h -> basicItem(h.get()));
        dev.jstech.computronics.HardwareItems.CPUS.forEach(h -> basicItem(h.get()));
        dev.jstech.computronics.HardwareItems.RAMS.forEach(h -> basicItem(h.get()));
        /*
         * These newly added GPUs ship without a repo texture yet (the artwork is pending review); mark each
         * expected texture as generated so basicItem can reference it without the datagen existence check
         * failing. Remove the matching id from this set once its real texture lands in resources.
         */
        for (final String previewOnlyGpu : PREVIEW_ONLY_GPU_TEXTURES) {
            existingFileHelper.trackGenerated(modLoc("item/" + previewOnlyGpu), TEXTURE);
        }
        dev.jstech.computronics.HardwareItems.GPUS.forEach(h -> basicItem(h.get()));
        dev.jstech.computronics.HardwareItems.PSUS.forEach(h -> basicItem(h.get()));
        dev.jstech.computronics.HardwareItems.MOTHERBOARDS.forEach(h -> basicItem(h.get()));
    }
}
