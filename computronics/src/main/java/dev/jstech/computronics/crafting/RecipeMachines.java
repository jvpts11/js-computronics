/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.crafting;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.jstech.computronics.JsComputronics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which machines run which recipe types: the Recipe Book's way of turning "a smelting recipe" into "a furnace,
 * a blast furnace or a smoker". The map is data: every file under {@code data/<namespace>/recipe_machines/}
 * is a JSON object whose keys are recipe type ids and whose values are lists of machine block ids, so a pack
 * (or another mod) can teach the Studio about its machines without code. Types nobody maps fall back to the
 * generic category of their recipe type.
 */
@EventBusSubscriber(modid = JsComputronics.MODID)
public final class RecipeMachines {

    public static final String FOLDER = "recipe_machines";
    private static final Gson GSON = new GsonBuilder().create();

    private static volatile Map<String, List<String>> machinesByType = Map.of();

    private RecipeMachines() {
    }

    /** The machine block ids that run {@code recipeTypeId}, in the order the data listed them; empty when unmapped. */
    public static List<String> machinesFor(final String recipeTypeId) {
        return machinesByType.getOrDefault(recipeTypeId, List.of());
    }

    /** Every mapped recipe type id. */
    public static List<String> mappedTypes() {
        return new ArrayList<>(machinesByType.keySet());
    }

    /** Replaces the map wholesale; what the reload listener does, and what a test can do without a datapack. */
    public static void replace(final Map<String, List<String>> map) {
        final Map<String, List<String>> copy = new LinkedHashMap<>();
        for (final Map.Entry<String, List<String>> e : map.entrySet()) {
            copy.put(e.getKey(), List.copyOf(e.getValue()));
        }
        machinesByType = Collections.unmodifiableMap(copy);
    }

    /**
     * Reads every {@code recipe_machines} file the resource manager can see and replaces the map with them:
     * what the reload listener does on a data reload, callable directly to check what a datapack declares.
     */
    public static void reload(final ResourceManager manager) {
        final Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>();
        SimpleJsonResourceReloadListener.scanDirectory(manager, FOLDER, GSON, files);
        load(files);
    }

    private static void load(final Map<ResourceLocation, JsonElement> files) {
        final Map<String, List<String>> merged = new LinkedHashMap<>();
        for (final Map.Entry<ResourceLocation, JsonElement> file : files.entrySet()) {
            if (!(file.getValue() instanceof JsonObject object)) {
                JsComputronics.LOGGER.warn("recipe_machines file {} is not an object; skipped", file.getKey());
                continue;
            }
            for (final Map.Entry<String, JsonElement> entry : object.entrySet()) {
                final List<String> machines = merged.computeIfAbsent(entry.getKey(), k -> new ArrayList<>());
                if (entry.getValue().isJsonArray()) {
                    for (final JsonElement id : entry.getValue().getAsJsonArray()) {
                        final String machine = id.getAsString();
                        if (!machines.contains(machine)) {
                            machines.add(machine);
                        }
                    }
                } else if (entry.getValue().isJsonPrimitive()) {
                    final String machine = entry.getValue().getAsString();
                    if (!machines.contains(machine)) {
                        machines.add(machine);
                    }
                }
            }
        }
        replace(merged);
        JsComputronics.LOGGER.info("Loaded {} recipe type to machine mappings", merged.size());
    }

    @SubscribeEvent
    public static void onAddReloadListeners(final AddReloadListenerEvent event) {
        event.addListener(new Listener());
    }

    private static final class Listener extends SimpleJsonResourceReloadListener {

        private Listener() {
            super(GSON, FOLDER);
        }

        @Override
        protected void apply(final Map<ResourceLocation, JsonElement> files, final ResourceManager manager,
                             final ProfilerFiller profiler) {
            load(files);
        }
    }
}
