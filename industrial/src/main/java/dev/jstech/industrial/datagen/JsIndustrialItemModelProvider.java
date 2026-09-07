/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Industrial.
 */
package dev.jstech.industrial.datagen;

import dev.jstech.industrial.JsIndustrial;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.List;

/**
 * Generates the item models of the machines: each one is its block model.
 */
public class JsIndustrialItemModelProvider extends ItemModelProvider {

    private static final List<String> MACHINES = List.of(
            "macerator", "compressor", "coal_generator", "electric_furnace");

    public JsIndustrialItemModelProvider(final PackOutput output, final ExistingFileHelper existingFiles) {
        super(output, JsIndustrial.MODID, existingFiles);
    }

    @Override
    protected void registerModels() {
        /*
         * The block model is written by the block-state provider in the same run, so the parent is left
         * unchecked rather than tying the two providers' order together.
         */
        for (final String machine : MACHINES) {
            getBuilder(machine).parent(new ModelFile.UncheckedModelFile(modLoc("block/" + machine)));
        }
    }
}
