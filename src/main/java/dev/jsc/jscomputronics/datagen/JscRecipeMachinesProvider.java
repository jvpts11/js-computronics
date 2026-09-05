/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.module.computing.crafting.RecipeMachines;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Writes the recipe-type-to-machine map for the vanilla recipe types, the data the Pattern Studio's Recipe
 * Book reads to pair a recipe with the block that runs it. Another mod or a pack adds its own machines with a
 * file of the same shape in its own namespace.
 */
public final class JscRecipeMachinesProvider implements DataProvider {

    /** The vanilla recipe types and the blocks that run them. */
    static final Map<String, List<String>> VANILLA = Map.of(
            "minecraft:smelting", List.of("minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker"),
            "minecraft:blasting", List.of("minecraft:blast_furnace"),
            "minecraft:smoking", List.of("minecraft:smoker"),
            "minecraft:campfire_cooking", List.of("minecraft:campfire", "minecraft:soul_campfire"),
            "minecraft:stonecutting", List.of("minecraft:stonecutter"),
            "minecraft:smithing", List.of("minecraft:smithing_table"));

    private final PackOutput output;

    public JscRecipeMachinesProvider(final PackOutput output) {
        this.output = output;
    }

    @Override
    public String getName() {
        return "J's Computronics Recipe Machines";
    }

    @Override
    public CompletableFuture<?> run(final CachedOutput cache) {
        final JsonObject root = new JsonObject();
        // Sorted, so the file is stable across runs.
        VANILLA.keySet().stream().sorted().forEach(type -> {
            final JsonArray machines = new JsonArray();
            for (final String machine : VANILLA.get(type)) {
                machines.add(machine);
            }
            root.add(type, machines);
        });
        final Path path = output.getOutputFolder(PackOutput.Target.DATA_PACK)
                .resolve(JsComputronics.MODID).resolve(RecipeMachines.FOLDER).resolve("vanilla.json");
        return DataProvider.saveStable(cache, root, path);
    }
}
