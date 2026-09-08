/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.datagen;

import dev.jstech.computers.JsComputers;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Entry point for all data generation, run via {@code ./gradlew runData}.
 */
@EventBusSubscriber(modid = JsComputers.MODID)
public final class JscDataGenerators {

    private JscDataGenerators() {
    }

    @SubscribeEvent
    public static void onGatherData(final GatherDataEvent event) {
        final DataGenerator generator = event.getGenerator();
        final PackOutput output = generator.getPackOutput();
        final ExistingFileHelper existingFiles = event.getExistingFileHelper();

        generator.addProvider(event.includeClient(),
                new JscBlockStateProvider(output, existingFiles));
        generator.addProvider(event.includeClient(),
                new JscItemModelProvider(output, existingFiles));
        generator.addProvider(event.includeClient(),
                new JscLanguageProvider(output));
        generator.addProvider(event.includeServer(),
                new JscLootTableProvider(output, event.getLookupProvider()));
        generator.addProvider(event.includeServer(),
                new JscRecipeMachinesProvider(output));
        generator.addProvider(event.includeServer(),
                new JscAdvancementProvider(output, event.getLookupProvider(), existingFiles));
    }
}
