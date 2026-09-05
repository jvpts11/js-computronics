/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.datagen;

import dev.jstech.core.JsCore;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Entry point for the core's data generation, run via {@code ./gradlew :core:runData}: the material items'
 * models, names and common tags.
 */
@EventBusSubscriber(modid = JsCore.MODID)
public final class JsCoreDataGenerators {

    private JsCoreDataGenerators() {
    }

    @SubscribeEvent
    public static void onGatherData(final GatherDataEvent event) {
        final DataGenerator generator = event.getGenerator();
        final PackOutput output = generator.getPackOutput();
        final ExistingFileHelper existingFiles = event.getExistingFileHelper();

        generator.addProvider(event.includeClient(),
                new JsCoreItemModelProvider(output, existingFiles));
        generator.addProvider(event.includeClient(),
                new JsCoreLanguageProvider(output));
        generator.addProvider(event.includeServer(),
                new JsCoreItemTagsProvider(output, event.getLookupProvider(), existingFiles));
    }
}
