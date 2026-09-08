/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.datagen;

import dev.jstech.computers.JsComputers;
import dev.jstech.computers.block.MainframeBlock;
import dev.jstech.computers.block.MainframePartBlock;
import dev.jstech.computers.block.ServerRackBlock;
import dev.jstech.computers.block.ServerRackPartBlock;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Generates block loot tables. Every block with an item drops itself; the multiblock controllers (Mainframe, Server Rack, Supercomputer node) and their part blocks are left with no loot table because they pop their controller item and spill their contents by hand on break (their structure is taken down with non-dropping removals). Without this provider the mod ships no loot tables at all, so most blocks vanish when broken.
 */
public final class JscLootTableProvider extends LootTableProvider {

    public JscLootTableProvider(final PackOutput output,
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
                if (manuallyDropped(block) || block.asItem() == Items.AIR) {
                    add(block, noDrop()); // the structure drops itself by hand, or has no item at all
                } else {
                    dropSelf(block);
                }
            }
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return modBlocks();
        }

        private static List<Block> modBlocks() {
            return BuiltInRegistries.BLOCK.entrySet().stream()
                    .filter(entry -> entry.getKey().location().getNamespace().equals(JsComputers.MODID))
                    .map(Map.Entry::getValue)
                    .toList();
        }

        private static boolean manuallyDropped(final Block block) {
            return block instanceof MainframeBlock || block instanceof MainframePartBlock
                    || block instanceof ServerRackBlock || block instanceof ServerRackPartBlock;
        }
    }
}
