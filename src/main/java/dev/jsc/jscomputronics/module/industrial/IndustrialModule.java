/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.industrial;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.module.industrial.block.MaceratorBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registration entry point for the Industrial module.
 */
public final class IndustrialModule {

    private IndustrialModule() {
    }

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(JsComputronics.MODID);

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(JsComputronics.MODID);

    public static final DeferredBlock<MaceratorBlock> MACERATOR = BLOCKS.register(
            "macerator",
            () -> new MaceratorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()));

    public static final DeferredItem<BlockItem> MACERATOR_ITEM = ITEMS.register(
            "macerator",
            () -> new BlockItem(MACERATOR.get(), new Item.Properties()));

    public static void register(final IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}
