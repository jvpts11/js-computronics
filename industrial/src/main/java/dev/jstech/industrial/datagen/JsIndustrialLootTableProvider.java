/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Industrial.
 */
package dev.jstech.industrial.datagen;

import dev.jstech.industrial.JsIndustrial;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Generates the block loot tables: every machine drops itself. Its contents are spilled by the block when a
 * survival player breaks it, not by the loot table.
 */
public final class JsIndustrialLootTableProvider extends LootTableProvider {

    public JsIndustrialLootTableProvider(final PackOutput output,
                                         final CompletableFuture<HolderLookup.Provider> registries) {
        super(output, Set.of(),
                List.of(new SubProviderEntry(BlockLoot::new, LootContextParamSets.BLOCK)),
                registries);
    }

    private static final class BlockLoot extends BlockLootSubProvider {

        private BlockLoot(final HolderLookup.Provider registries) {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
        }

        @Override
        protected void generate() {
            for (final Block block : modBlocks()) {
                dropSelf(block);
            }
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return modBlocks();
        }

        private static List<Block> modBlocks() {
            return BuiltInRegistries.BLOCK.entrySet().stream()
                    .filter(entry -> entry.getKey().location().getNamespace().equals(JsIndustrial.MODID))
                    .map(Map.Entry::getValue)
                    .toList();
        }
    }
}
