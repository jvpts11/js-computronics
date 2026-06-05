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
    }
}
