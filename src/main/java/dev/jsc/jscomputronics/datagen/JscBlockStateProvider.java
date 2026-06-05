/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.datagen;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.module.industrial.IndustrialModule;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.minecraft.data.PackOutput;

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
    }
}
