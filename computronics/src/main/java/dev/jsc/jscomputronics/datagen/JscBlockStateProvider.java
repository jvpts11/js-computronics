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
import dev.jsc.jscomputronics.module.computing.block.DataCableBlock;
import dev.jsc.jscomputronics.module.industrial.IndustrialModule;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.client.model.generators.MultiPartBlockStateBuilder;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.PipeBlock;

/**
 * Generates blockstates and block models.
 */
public class JscBlockStateProvider extends BlockStateProvider {

    public JscBlockStateProvider(final PackOutput output, final ExistingFileHelper existingFiles) {
        super(output, JsComputronics.MODID, existingFiles);
    }

    @Override
    protected void registerStatesAndModels() {
        final ModelFile maceratorModel = models().orientable(
                "macerator",
                modLoc("block/macerator_side"),
                modLoc("block/macerator_front"),
                modLoc("block/macerator_top"));

        horizontalBlock(IndustrialModule.MACERATOR.get(), maceratorModel);

        final ModelFile compressorModel = models().orientable(
                "compressor",
                modLoc("block/compressor_side"),
                modLoc("block/compressor_front"),
                modLoc("block/compressor_top"));
        horizontalBlock(IndustrialModule.COMPRESSOR.get(), compressorModel);

        final ModelFile coalGeneratorModel = models().orientable(
                "coal_generator",
                modLoc("block/coal_generator_side"),
                modLoc("block/coal_generator_front"),
                modLoc("block/coal_generator_top"));

        horizontalBlock(IndustrialModule.COAL_GENERATOR.get(), coalGeneratorModel);

        final ModelFile electricFurnaceModel = models().orientable(
                "electric_furnace",
                modLoc("block/electric_furnace_side"),
                modLoc("block/electric_furnace_front"),
                modLoc("block/electric_furnace_top"));
        horizontalBlock(IndustrialModule.ELECTRIC_FURNACE.get(), electricFurnaceModel);

        pipeCable(ComputingModule.ETHERNET_CABLE.get(), "ethernet_cable");
        pipeCable(ComputingModule.HBW_CABLE.get(), "hbw_cable");
        pipeCable(ComputingModule.HPC_CABLE.get(), "hpc_cable");
        pipeCable(ComputingModule.CRAFTING_CABLE.get(), "crafting_cable");
        pipeCable(ComputingModule.PERIPHERAL_CABLE.get(), "peripheral_cable");

        // Mainframes: 3x2x2 cabinets drawn as ONE model each by the controller's block-entity renderer,
        // one model per era. The twelve blocks themselves are invisible; the only model they need
        // carries the particle texture for breaking effects.
        final ModelFile mainframeInvisible = models().getBuilder("mainframe_cabinet")
                .texture("particle", modLoc("block/mainframe_particle"));
        for (final net.minecraft.world.level.block.Block cabinet : java.util.List.of(
                ComputingModule.MAINFRAME.get(), ComputingModule.VINTAGE_MAINFRAME.get(),
                ComputingModule.LEGACY_MAINFRAME.get(), ComputingModule.MAINFRAME_PART.get())) {
            getVariantBuilder(cabinet).forAllStates(state ->
                    net.neoforged.neoforge.client.model.generators.ConfiguredModel.builder()
                            .modelFile(mainframeInvisible).build());
        }

        // Routers: the facing carries the status/port panel; the other faces are casing.
        final ModelFile personalRouterModel = models().orientable(
                "personal_router",
                modLoc("block/personal_router_side"),
                modLoc("block/personal_router_front"),
                modLoc("block/personal_router_top"));
        horizontalBlock(ComputingModule.PERSONAL_ROUTER.get(), personalRouterModel);

        // The back face is the dedicated Mainframe uplink, so it gets its own port texture.
        final ModelFile serverRouterModel = models().cube(
                "server_router",
                modLoc("block/server_router_top"),
                modLoc("block/server_router_top"),
                modLoc("block/server_router_front"),
                modLoc("block/server_router_back"),
                modLoc("block/server_router_side"),
                modLoc("block/server_router_side"))
                .texture("particle", modLoc("block/server_router_side"));
        horizontalBlock(ComputingModule.SERVER_ROUTER.get(), serverRouterModel);

        // Tank: glass walls in a metal casing frame, so it reads as a containment vessel rather than a
        // solid block.
        simpleBlock(ComputingModule.TANK.get(), models()
                .cubeBottomTop("tank", mcLoc("block/glass"),
                        modLoc("block/mainframe_side"), modLoc("block/mainframe_side"))
                .renderType("cutout"));

        simpleBlock(ComputingModule.CRAFTING_SWITCH.get(),
                models().cubeAll("crafting_switch", modLoc("block/crafting_switch")));

        // Server Racks and the Supercomputer Rack: 2x3x2 cabinets drawn as ONE model each by the
        // controller's block-entity renderer. The blocks themselves are invisible; the only model they
        // need carries the particle texture for breaking effects.
        final ModelFile rackInvisible = models().getBuilder("rack")
                .texture("particle", modLoc("block/rack_particle"));
        for (final net.minecraft.world.level.block.Block cabinet : java.util.List.of(
                ComputingModule.SERVER_RACK.get(), ComputingModule.LEGACY_SERVER_RACK.get(),
                ComputingModule.VINTAGE_SERVER_RACK.get(), ComputingModule.SUPERCOMPUTER_RACK.get())) {
            getVariantBuilder(cabinet).forAllStates(state ->
                    net.neoforged.neoforge.client.model.generators.ConfiguredModel.builder()
                            .modelFile(rackInvisible).build());
        }
        getVariantBuilder(ComputingModule.SERVER_RACK_PART.get()).forAllStates(state ->
                net.neoforged.neoforge.client.model.generators.ConfiguredModel.builder()
                        .modelFile(rackInvisible).build());

        final ModelFile personalComputerModel = models().orientable(
                "personal_computer",
                modLoc("block/personal_computer_side"),
                modLoc("block/personal_computer_front"),
                modLoc("block/personal_computer_top"));
        horizontalBlock(ComputingModule.PERSONAL_COMPUTER.get(), personalComputerModel);

        // Earlier-era Personal Computers: same orientable model, era-specific faces.
        final ModelFile vintagePersonalComputerModel = models().orientable(
                "vintage_personal_computer",
                modLoc("block/vintage_personal_computer_side"),
                modLoc("block/vintage_personal_computer_front"),
                modLoc("block/vintage_personal_computer_top"));
        horizontalBlock(ComputingModule.VINTAGE_PERSONAL_COMPUTER.get(), vintagePersonalComputerModel);

        final ModelFile legacyPersonalComputerModel = models().orientable(
                "legacy_personal_computer",
                modLoc("block/legacy_personal_computer_side"),
                modLoc("block/legacy_personal_computer_front"),
                modLoc("block/legacy_personal_computer_top"));
        horizontalBlock(ComputingModule.LEGACY_PERSONAL_COMPUTER.get(), legacyPersonalComputerModel);

        final ModelFile craftingComputerModel = models().orientable(
                "crafting_computer",
                modLoc("block/crafting_computer_side"),
                modLoc("block/crafting_computer_front"),
                modLoc("block/crafting_computer_top"));
        horizontalBlock(ComputingModule.CRAFTING_COMPUTER.get(), craftingComputerModel);

        // Earlier-era Crafting Computers: same orientable model, era-specific faces.
        final ModelFile vintageCraftingComputerModel = models().orientable(
                "vintage_crafting_computer",
                modLoc("block/vintage_crafting_computer_side"),
                modLoc("block/vintage_crafting_computer_front"),
                modLoc("block/vintage_crafting_computer_top"));
        horizontalBlock(ComputingModule.VINTAGE_CRAFTING_COMPUTER.get(), vintageCraftingComputerModel);

        final ModelFile legacyCraftingComputerModel = models().orientable(
                "legacy_crafting_computer",
                modLoc("block/legacy_crafting_computer_side"),
                modLoc("block/legacy_crafting_computer_front"),
                modLoc("block/legacy_crafting_computer_top"));
        horizontalBlock(ComputingModule.LEGACY_CRAFTING_COMPUTER.get(), legacyCraftingComputerModel);

        // Cluster Management Computers: the same orientable case per era, in their own liveries.
        horizontalBlock(ComputingModule.CLUSTER_MANAGEMENT_COMPUTER.get(), models().orientable(
                "cluster_management_computer",
                modLoc("block/cluster_management_computer_side"),
                modLoc("block/cluster_management_computer_front"),
                modLoc("block/cluster_management_computer_top")));
        horizontalBlock(ComputingModule.VINTAGE_CLUSTER_MANAGEMENT_COMPUTER.get(), models().orientable(
                "vintage_cluster_management_computer",
                modLoc("block/vintage_cluster_management_computer_side"),
                modLoc("block/vintage_cluster_management_computer_front"),
                modLoc("block/vintage_cluster_management_computer_top")));
        horizontalBlock(ComputingModule.LEGACY_CLUSTER_MANAGEMENT_COMPUTER.get(), models().orientable(
                "legacy_cluster_management_computer",
                modLoc("block/legacy_cluster_management_computer_side"),
                modLoc("block/legacy_cluster_management_computer_front"),
                modLoc("block/legacy_cluster_management_computer_top")));

        // Supercomputer cluster: the node is a rack-sized cabinet whose front lights up while it
        // runs; the HBW Interface is the uplink; the console is a hand-written kiosk model.
        simpleBlock(ComputingModule.HBW_INTERFACE.get(), models().cubeColumn(
                "hbw_interface", modLoc("block/hbw_interface_side"), modLoc("block/hbw_interface_top")));

        // Pattern Encoders: one burner body per era, drawn as a model by the block entity. The blocks
        // themselves are invisible; the only model they need carries the particle texture.
        final ModelFile encoderInvisible = models().getBuilder("pattern_encoder_body")
                .texture("particle", modLoc("block/pattern_encoder_particle"));
        for (final net.minecraft.world.level.block.Block body : java.util.List.of(
                ComputingModule.PATTERN_ENCODER.get(), ComputingModule.LEGACY_PATTERN_ENCODER.get(),
                ComputingModule.VINTAGE_PATTERN_ENCODER.get())) {
            getVariantBuilder(body).forAllStates(state ->
                    net.neoforged.neoforge.client.model.generators.ConfiguredModel.builder()
                            .modelFile(encoderInvisible).build());
        }

        // Media reader drives: the front carries the drive face, the sides and top use the drive's
        // own casing texture, and the LOADED blockstate swaps the front to the lit "_active" face.
        final ModelFile floppyIdle = models().orientable("floppy_drive",
                modLoc("block/floppy_drive_casing"), modLoc("block/floppy_drive_front"), modLoc("block/floppy_drive_casing"));
        final ModelFile floppyActive = models().orientable("floppy_drive_active",
                modLoc("block/floppy_drive_casing"), modLoc("block/floppy_drive_active"), modLoc("block/floppy_drive_casing"));
        horizontalBlock(ComputingModule.FLOPPY_DRIVE.get(),
                s -> s.getValue(dev.jsc.jscomputronics.module.computing.os.media.MediaReaderBlock.LOADED) ? floppyActive : floppyIdle);
        final ModelFile cdIdle = models().orientable("cd_drive",
                modLoc("block/cd_drive_casing"), modLoc("block/cd_drive_front"), modLoc("block/cd_drive_casing"));
        final ModelFile cdActive = models().orientable("cd_drive_active",
                modLoc("block/cd_drive_casing"), modLoc("block/cd_drive_active"), modLoc("block/cd_drive_casing"));
        horizontalBlock(ComputingModule.CD_DRIVE.get(),
                s -> s.getValue(dev.jsc.jscomputronics.module.computing.os.media.MediaReaderBlock.LOADED) ? cdActive : cdIdle);
        final ModelFile dvdIdle = models().orientable("dvd_drive",
                modLoc("block/dvd_drive_casing"), modLoc("block/dvd_drive_front"), modLoc("block/dvd_drive_casing"));
        final ModelFile dvdActive = models().orientable("dvd_drive_active",
                modLoc("block/dvd_drive_casing"), modLoc("block/dvd_drive_active"), modLoc("block/dvd_drive_casing"));
        horizontalBlock(ComputingModule.DVD_DRIVE.get(),
                s -> s.getValue(dev.jsc.jscomputronics.module.computing.os.media.MediaReaderBlock.LOADED) ? dvdActive : dvdIdle);
        // The Dock Station is a low hub on the desk, not a cube: two hand-authored element models, the
        // empty hub and the hub with the flash drive standing out of its port, picked by LOADED.
        final ModelFile dockIdle = models().getExistingFile(modLoc("block/dock_station"));
        final ModelFile dockDocked = models().getExistingFile(modLoc("block/dock_station_docked"));
        horizontalBlock(ComputingModule.DOCK_STATION.get(),
                s -> s.getValue(dev.jsc.jscomputronics.module.computing.os.media.MediaReaderBlock.LOADED) ? dockDocked : dockIdle);

        // Monitor: a screen on the front, casing on the other faces. The screen has
        final ModelFile monitorOff = models().orientable(
                "monitor",
                modLoc("block/monitor_side"),
                modLoc("block/monitor_front"),
                modLoc("block/monitor_side"));
        final ModelFile monitorOn = models().orientable(
                "monitor_on",
                modLoc("block/monitor_side"),
                modLoc("block/monitor_front_on"),
                modLoc("block/monitor_side"));
        getVariantBuilder(ComputingModule.MONITOR.get()).forAllStates(state -> {
            final boolean lit = state.getValue(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT);
            final net.minecraft.core.Direction facing = state.getValue(
                    net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
            return net.neoforged.neoforge.client.model.generators.ConfiguredModel.builder()
                    .modelFile(lit ? monitorOn : monitorOff)
                    .rotationY((int) facing.toYRot() % 360)
                    .build();
        });

        // Vintage Monitor: same lit/facing logic, era-specific textures.
        final ModelFile vintageMonitorOff = models().orientable(
                "vintage_monitor",
                modLoc("block/vintage_monitor_side"),
                modLoc("block/vintage_monitor_front"),
                modLoc("block/vintage_monitor_side"));
        final ModelFile vintageMonitorOn = models().orientable(
                "vintage_monitor_on",
                modLoc("block/vintage_monitor_side"),
                modLoc("block/vintage_monitor_front_on"),
                modLoc("block/vintage_monitor_side"));
        getVariantBuilder(ComputingModule.VINTAGE_MONITOR.get()).forAllStates(state -> {
            final boolean lit = state.getValue(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT);
            final net.minecraft.core.Direction facing = state.getValue(
                    net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
            return net.neoforged.neoforge.client.model.generators.ConfiguredModel.builder()
                    .modelFile(lit ? vintageMonitorOn : vintageMonitorOff)
                    .rotationY((int) facing.toYRot() % 360)
                    .build();
        });

        // Legacy Monitor: same lit/facing logic, era-specific textures.
        final ModelFile legacyMonitorOff = models().orientable(
                "legacy_monitor",
                modLoc("block/legacy_monitor_side"),
                modLoc("block/legacy_monitor_front"),
                modLoc("block/legacy_monitor_side"));
        final ModelFile legacyMonitorOn = models().orientable(
                "legacy_monitor_on",
                modLoc("block/legacy_monitor_side"),
                modLoc("block/legacy_monitor_front_on"),
                modLoc("block/legacy_monitor_side"));
        getVariantBuilder(ComputingModule.LEGACY_MONITOR.get()).forAllStates(state -> {
            final boolean lit = state.getValue(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT);
            final net.minecraft.core.Direction facing = state.getValue(
                    net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
            return net.neoforged.neoforge.client.model.generators.ConfiguredModel.builder()
                    .modelFile(lit ? legacyMonitorOn : legacyMonitorOff)
                    .rotationY((int) facing.toYRot() % 360)
                    .build();
        });

        // Interaction buses are not blocks: they are parts mounted on a data cable's face,
    }

    /**
     * The model rotation for a block whose access port sits on its rear face: spin the front-facing model
     * a half-turn so its back lines up with the placement direction.
     */
    private static int rearYRot(final net.minecraft.core.Direction facing) {
        return ((int) facing.toYRot() + 180) % 360;
    }

    private void pipeCable(final net.minecraft.world.level.block.Block block, final String name) {
        final ResourceLocation texture = modLoc("block/" + name);
        final ModelFile core = models()
                .withExistingParent(name + "_core", modLoc("block/cable_core"))
                .texture("cable", texture);
        final ModelFile arm = models()
                .withExistingParent(name + "_arm", modLoc("block/cable_arm"))
                .texture("cable", texture);

        final MultiPartBlockStateBuilder builder = getMultipartBuilder(block);
        builder.part().modelFile(core).addModel().end();
        builder.part().modelFile(arm).addModel()
                .condition(PipeBlock.DOWN, true).end();
        builder.part().modelFile(arm).rotationX(180).addModel()
                .condition(PipeBlock.UP, true).end();
        builder.part().modelFile(arm).rotationX(270).addModel()
                .condition(PipeBlock.NORTH, true).end();
        builder.part().modelFile(arm).rotationX(270).rotationY(180).addModel()
                .condition(PipeBlock.SOUTH, true).end();
        builder.part().modelFile(arm).rotationX(270).rotationY(90).addModel()
                .condition(PipeBlock.EAST, true).end();
        builder.part().modelFile(arm).rotationX(270).rotationY(270).addModel()
                .condition(PipeBlock.WEST, true).end();
    }
}
