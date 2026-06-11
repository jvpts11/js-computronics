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

        final ModelFile coalGeneratorModel = models().orientable(
                "coal_generator",
                modLoc("block/coal_generator_side"),
                modLoc("block/coal_generator_front"),
                modLoc("block/coal_generator_top"));

        horizontalBlock(IndustrialModule.COAL_GENERATOR.get(), coalGeneratorModel);

        pipeCable(ComputingModule.ETHERNET_CABLE.get(), "ethernet_cable");
        pipeCable(ComputingModule.HBW_CABLE.get(), "hbw_cable");
        pipeCable(ComputingModule.HPC_CABLE.get(), "hpc_cable");
        pipeCable(ComputingModule.PERIPHERAL_CABLE.get(), "peripheral_cable");

        // The Mainframe is a 3x2x2 server rack. The controller carries the control
        // panel on its front, casing on the sides, a ventilation grille on top.
        final ModelFile mainframeModel = models().orientable(
                "mainframe",
                modLoc("block/mainframe_side"),
                modLoc("block/mainframe_front"),
                modLoc("block/mainframe_top"));
        horizontalBlock(ComputingModule.MAINFRAME.get(), mainframeModel);

        // Parts: the central column wears a lit data-spine face, the side columns wear
        final ModelFile partCasing = models().cubeColumn(
                "mainframe_part", modLoc("block/mainframe_panel"), modLoc("block/mainframe_top"));
        final ModelFile partCore = models().cubeColumn(
                "mainframe_part_core", modLoc("block/mainframe_core"), modLoc("block/mainframe_top"));
        getVariantBuilder(ComputingModule.MAINFRAME_PART.get()).forAllStates(state ->
                net.neoforged.neoforge.client.model.generators.ConfiguredModel.builder()
                        .modelFile(state.getValue(
                                dev.jsc.jscomputronics.module.computing.block.MainframePartBlock.CORE)
                                ? partCore : partCasing)
                        .build());

        // Routers: the facing carries the status/port panel; the other faces are casing.
        final ModelFile personalRouterModel = models().orientable(
                "personal_router",
                modLoc("block/personal_router_side"),
                modLoc("block/personal_router_front"),
                modLoc("block/personal_router_top"));
        horizontalBlock(ComputingModule.PERSONAL_ROUTER.get(), personalRouterModel);

        final ModelFile serverRouterModel = models().orientable(
                "server_router",
                modLoc("block/server_router_side"),
                modLoc("block/server_router_front"),
                modLoc("block/server_router_top"));
        horizontalBlock(ComputingModule.SERVER_ROUTER.get(), serverRouterModel);

        // Datacenter Station: a hand-written element model (pedestal + tilted console
        // screen) shipped in main resources; the blockstate only rotates it.
        horizontalBlock(ComputingModule.DATACENTER_STATION.get(),
                models().getExistingFile(modLoc("block/datacenter_station")));

        // Tank: glass walls in a metal casing frame, so it reads as a containment vessel rather than a
        simpleBlock(ComputingModule.TANK.get(), models()
                .cubeBottomTop("tank", mcLoc("block/glass"),
                        modLoc("block/mainframe_side"), modLoc("block/mainframe_side"))
                .renderType("cutout"));

        // Server Rack: a 2x3x2 multiblock cabinet. The four front bay blocks each show
        final ModelFile[] rackBays = new ModelFile[4];
        for (int bays = 0; bays < 4; bays++) {
            rackBays[bays] = models().getExistingFile(modLoc("block/server_rack_bays_" + bays));
        }
        final ModelFile rackHeader = models().cubeColumn(
                "server_rack_part", modLoc("block/server_rack_upper"), modLoc("block/server_rack_top"));
        final ModelFile rackCasing = models().cubeColumn(
                "server_rack_casing", modLoc("block/server_rack_side"), modLoc("block/server_rack_top"));

        getVariantBuilder(ComputingModule.SERVER_RACK.get()).forAllStates(state ->
                net.neoforged.neoforge.client.model.generators.ConfiguredModel.builder()
                        .modelFile(rackBays[state.getValue(
                                dev.jsc.jscomputronics.module.computing.block.ServerRackBlock.BAYS)])
                        .rotationY(((int) state.getValue(
                                net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)
                                .toYRot() + 180) % 360)
                        .build());
        getVariantBuilder(ComputingModule.SERVER_RACK_PART.get()).forAllStates(state -> {
            if (state.getValue(dev.jsc.jscomputronics.module.computing.block.ServerRackPartBlock.TOP)) {
                return net.neoforged.neoforge.client.model.generators.ConfiguredModel.builder()
                        .modelFile(rackHeader).build();
            }
            if (!state.getValue(dev.jsc.jscomputronics.module.computing.block.ServerRackPartBlock.FRONT)) {
                return net.neoforged.neoforge.client.model.generators.ConfiguredModel.builder()
                        .modelFile(rackCasing).build();
            }
            return net.neoforged.neoforge.client.model.generators.ConfiguredModel.builder()
                    .modelFile(rackBays[state.getValue(
                            dev.jsc.jscomputronics.module.computing.block.ServerRackBlock.BAYS)])
                    .rotationY(((int) state.getValue(
                            dev.jsc.jscomputronics.module.computing.block.ServerRackPartBlock.FACING)
                            .toYRot() + 180) % 360)
                    .build();
        });

        final ModelFile personalComputerModel = models().orientable(
                "personal_computer",
                modLoc("block/personal_computer_side"),
                modLoc("block/personal_computer_front"),
                modLoc("block/personal_computer_top"));
        horizontalBlock(ComputingModule.PERSONAL_COMPUTER.get(), personalComputerModel);

        final ModelFile craftingComputerModel = models().orientable(
                "crafting_computer",
                modLoc("block/crafting_computer_side"),
                modLoc("block/crafting_computer_front"),
                modLoc("block/crafting_computer_top"));
        horizontalBlock(ComputingModule.CRAFTING_COMPUTER.get(), craftingComputerModel);

        // Supercomputer cluster: nodes light up when a co-processor is seated; the
        // HBW Interface is the uplink; the console is a hand-written kiosk model.
        final ModelFile nodeEmpty = models().cubeColumn(
                "supercomputer_node", modLoc("block/supercomputer_node_side"),
                modLoc("block/supercomputer_node_top"));
        final ModelFile nodeFilled = models().cubeColumn(
                "supercomputer_node_filled", modLoc("block/supercomputer_node_side_filled"),
                modLoc("block/supercomputer_node_top"));
        getVariantBuilder(ComputingModule.SUPERCOMPUTER_NODE.get()).forAllStates(state ->
                net.neoforged.neoforge.client.model.generators.ConfiguredModel.builder()
                        .modelFile(state.getValue(
                                dev.jsc.jscomputronics.module.computing.block.SupercomputerNodeBlock.FILLED)
                                ? nodeFilled : nodeEmpty)
                        .build());
        final ModelFile nodeMid = models().cubeColumn(
                "supercomputer_node_mid", modLoc("block/supercomputer_node_mid"),
                modLoc("block/supercomputer_node_top"));
        final ModelFile nodeCap = models().cubeColumn(
                "supercomputer_node_cap", modLoc("block/supercomputer_node_cap"),
                modLoc("block/supercomputer_node_top"));
        getVariantBuilder(ComputingModule.SUPERCOMPUTER_NODE_PART.get()).forAllStates(state ->
                net.neoforged.neoforge.client.model.generators.ConfiguredModel.builder()
                        .modelFile(state.getValue(
                                dev.jsc.jscomputronics.module.computing.block.SupercomputerNodePartBlock.TOP)
                                ? nodeCap : nodeMid)
                        .build());

        simpleBlock(ComputingModule.HBW_INTERFACE.get(), models().cubeColumn(
                "hbw_interface", modLoc("block/hbw_interface_side"), modLoc("block/hbw_interface_top")));

        horizontalBlock(ComputingModule.SUPERCOMPUTER_CONSOLE.get(),
                models().getExistingFile(modLoc("block/supercomputer_console")));

        // Pattern Encoder and Reader share the workstation casing; the front face tells them apart.
        final ModelFile patternEncoderModel = models().orientable(
                "pattern_encoder",
                modLoc("block/pattern_station_side"),
                modLoc("block/pattern_encoder_front"),
                modLoc("block/pattern_station_top"));
        horizontalBlock(ComputingModule.PATTERN_ENCODER.get(), patternEncoderModel);

        final ModelFile patternReaderModel = models().orientable(
                "pattern_reader",
                modLoc("block/pattern_station_side"),
                modLoc("block/pattern_reader_front"),
                modLoc("block/pattern_station_top"));
        horizontalBlock(ComputingModule.PATTERN_READER.get(), patternReaderModel);

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

        // Interaction buses are not blocks: they are parts mounted on a data cable's face,
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
