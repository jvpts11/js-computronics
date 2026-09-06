/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.datagen;

import dev.jstech.core.JsCore;
import dev.jstech.core.material.MaterialForm;
import dev.jstech.core.material.MaterialItems;
import dev.jstech.core.material.ModMaterial;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/**
 * Generates the item models of the material catalogue: one flat sprite per item, drawn from the texture of
 * the same name. Activating a form for a material is enough; no model needs to be listed here.
 */
public class JsCoreItemModelProvider extends ItemModelProvider {

    public JsCoreItemModelProvider(final PackOutput output, final ExistingFileHelper existingFiles) {
        super(output, JsCore.MODID, existingFiles);
    }

    @Override
    protected void registerModels() {
        for (final ModMaterial material : ModMaterial.values()) {
            for (final MaterialForm form : material.activeModForms()) {
                basicItem(MaterialItems.get(material, form).get());
            }
        }
    }
}
