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
import net.neoforged.neoforge.common.data.LanguageProvider;

import java.util.Locale;

/**
 * Generates the English text of the core: the names of the material catalogue and the words of the GUI
 * toolkit. A material name is the material followed by the form, both capitalised ("Iron Dust",
 * "Copper Plate"), so a newly activated form is named without a line here.
 */
public class JsCoreLanguageProvider extends LanguageProvider {

    public JsCoreLanguageProvider(final PackOutput output) {
        super(output, JsCore.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        // The toolkit's modal dialog.
        add("gui.jscore.confirm", "Confirm");
        add("gui.jscore.cancel", "Cancel");

        for (final ModMaterial material : ModMaterial.values()) {
            for (final MaterialForm form : material.activeModForms()) {
                add(MaterialItems.get(material, form).get(),
                        capitalise(material.materialName()) + " " + capitalise(form.name()));
            }
        }
    }

    private static String capitalise(final String word) {
        final String lower = word.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
