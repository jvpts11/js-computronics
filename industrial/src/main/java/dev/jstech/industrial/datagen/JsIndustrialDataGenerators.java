/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Industrial.
 */
package dev.jstech.industrial.datagen;

import dev.jstech.industrial.JsIndustrial;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Entry point for the industrial mod's data generation, run via {@code ./gradlew :industrial:runData}.
 */
@EventBusSubscriber(modid = JsIndustrial.MODID)
public final class JsIndustrialDataGenerators {

    private JsIndustrialDataGenerators() {
    }

    @SubscribeEvent
    public static void onGatherData(final GatherDataEvent event) {
        final DataGenerator generator = event.getGenerator();
        final PackOutput output = generator.getPackOutput();
        final ExistingFileHelper existingFiles = event.getExistingFileHelper();

        generator.addProvider(event.includeClient(),
                new JsIndustrialBlockStateProvider(output, existingFiles));
        generator.addProvider(event.includeClient(),
                new JsIndustrialItemModelProvider(output, existingFiles));
        generator.addProvider(event.includeClient(),
                new JsIndustrialLanguageProvider(output));
        generator.addProvider(event.includeServer(),
                new JsIndustrialLootTableProvider(output, event.getLookupProvider()));
        generator.addProvider(event.includeServer(),
                new JsIndustrialRecipeProvider(output, event.getLookupProvider()));
    }
}
