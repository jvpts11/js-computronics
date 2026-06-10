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
        pipeCable(ComputingModule.PERIPHERAL_CABLE.get(), "peripheral_cable");

        // The Mainframe is a 3x2x2 server rack. The controller carries the control
        // panel on its front, casing on the sides, a ventilation grille on top.
        final ModelFile mainframeModel = models().orientable(
                "mainframe",
                modLoc("block/mainframe_side"),
                modLoc("block/mainframe_front"),
                modLoc("block/mainframe_top"));
        horizontalBlock(ComputingModule.MAINFRAME.get(), mainframeModel);

        // Parts: the central column wears the control-panel face, the side columns
        final ModelFile partCasing = models().cubeColumn(
                "mainframe_part", modLoc("block/mainframe_side"), modLoc("block/mainframe_top"));
        final ModelFile partCore = models().cubeColumn(
                "mainframe_part_core", modLoc("block/mainframe_front"), modLoc("block/mainframe_top"));
        getVariantBuilder(ComputingModule.MAINFRAME_PART.get()).forAllStates(state ->
                net.neoforged.neoforge.client.model.generators.ConfiguredModel.builder()
                        .modelFile(state.getValue(
                                dev.jsc.jscomputronics.module.computing.block.MainframePartBlock.CORE)
                                ? partCore : partCasing)
                        .build());

        simpleBlock(ComputingModule.PERSONAL_ROUTER.get(),
                models().cubeAll("personal_router", modLoc("block/personal_router")));

        // Tank: glass walls in a metal casing frame, so it reads as a containment vessel rather than a
        simpleBlock(ComputingModule.TANK.get(), models()
                .cubeBottomTop("tank", mcLoc("block/glass"),
                        modLoc("block/mainframe_side"), modLoc("block/mainframe_side"))
                .renderType("cutout"));

        // Server Rack: a 2x3x2 multiblock cabinet that reads as one tall rack. Every
        final ModelFile rackBay = models().cubeColumn(
                "server_rack", modLoc("block/server_rack_front"), modLoc("block/mainframe_top"));
        final ModelFile rackHeader = models().cubeColumn(
                "server_rack_part", modLoc("block/server_rack_upper"), modLoc("block/mainframe_top"));
        getVariantBuilder(ComputingModule.SERVER_RACK.get()).forAllStates(state ->
                net.neoforged.neoforge.client.model.generators.ConfiguredModel.builder()
                        .modelFile(rackBay).build());
        getVariantBuilder(ComputingModule.SERVER_RACK_PART.get()).forAllStates(state ->
                net.neoforged.neoforge.client.model.generators.ConfiguredModel.builder()
                        .modelFile(state.getValue(
                                dev.jsc.jscomputronics.module.computing.block.ServerRackPartBlock.TOP)
                                ? rackHeader : rackBay)
                        .build());

        final ModelFile personalComputerModel = models().orientable(
                "personal_computer",
                modLoc("block/personal_computer_side"),
                modLoc("block/personal_computer_front"),
                modLoc("block/personal_computer_top"));
        horizontalBlock(ComputingModule.PERSONAL_COMPUTER.get(), personalComputerModel);

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
