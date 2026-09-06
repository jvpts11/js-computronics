/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Industrial.
 */
package dev.jstech.industrial.datagen;

import dev.jstech.industrial.IndustrialModule;
import dev.jstech.industrial.JsIndustrial;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/**
 * Generates the block states and block models of the machines.
 */
public class JsIndustrialBlockStateProvider extends BlockStateProvider {

    public JsIndustrialBlockStateProvider(final PackOutput output, final ExistingFileHelper existingFiles) {
        super(output, JsIndustrial.MODID, existingFiles);
    }

    @Override
    protected void registerStatesAndModels() {
        machine(IndustrialModule.MACERATOR.get(), "macerator");
        machine(IndustrialModule.COMPRESSOR.get(), "compressor");
        machine(IndustrialModule.COAL_GENERATOR.get(), "coal_generator");
        machine(IndustrialModule.ELECTRIC_FURNACE.get(), "electric_furnace");
    }

    /** A machine is a cube that faces the way it was placed: a front, the same texture on the other sides, a top. */
    private void machine(final Block block, final String name) {
        final ModelFile model = models().orientable(name,
                modLoc("block/" + name + "_side"),
                modLoc("block/" + name + "_front"),
                modLoc("block/" + name + "_top"));
        horizontalBlock(block, model);
    }
}
