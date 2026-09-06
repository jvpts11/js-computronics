/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Tech Series.
 */
package dev.jstech.tests.datagen;

import dev.jstech.tests.JsTests;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Data generation for the test mod: the structure templates the GameTests run inside.
 */
@EventBusSubscriber(modid = JsTests.MODID)
public final class JsTestsDataGenerators {

    private JsTestsDataGenerators() {
    }

    @SubscribeEvent
    public static void onGatherData(final GatherDataEvent event) {
        final DataGenerator generator = event.getGenerator();
        final PackOutput output = generator.getPackOutput();
        generator.addProvider(event.includeServer(), new GameTestStructureProvider(output));
    }
}
