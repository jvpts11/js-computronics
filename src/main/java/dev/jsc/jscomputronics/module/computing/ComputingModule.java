/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.common.network.DataTier;
import dev.jsc.jscomputronics.module.computing.block.DataCableBlock;
import dev.jsc.jscomputronics.module.computing.blockentity.DataCableBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registration entry point for the Computing module: the data network's physical blocks (cables now; computers, routers and racks later).
 */
public final class ComputingModule {

    private ComputingModule() {
    }

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(JsComputronics.MODID);

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(JsComputronics.MODID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, JsComputronics.MODID);

    private static BlockBehaviour.Properties cableProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .strength(0.3F)
                .sound(SoundType.WOOL)
                .noOcclusion();
    }

    // Cables

    public static final DeferredBlock<DataCableBlock> ETHERNET_CABLE = BLOCKS.register(
            "ethernet_cable", () -> new DataCableBlock(cableProperties(), DataTier.T1_ETHERNET));

    public static final DeferredItem<BlockItem> ETHERNET_CABLE_ITEM = ITEMS.register(
            "ethernet_cable", () -> new BlockItem(ETHERNET_CABLE.get(), new Item.Properties()));

    public static final DeferredBlock<DataCableBlock> HBW_CABLE = BLOCKS.register(
            "hbw_cable", () -> new DataCableBlock(cableProperties(), DataTier.T2_HBW));

    public static final DeferredItem<BlockItem> HBW_CABLE_ITEM = ITEMS.register(
            "hbw_cable", () -> new BlockItem(HBW_CABLE.get(), new Item.Properties()));

    // Block entities

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataCableBlockEntity>> DATA_CABLE_BE =
            BLOCK_ENTITIES.register("data_cable",
                    () -> BlockEntityType.Builder.of(DataCableBlockEntity::new,
                            ETHERNET_CABLE.get(), HBW_CABLE.get()).build(null));

    public static void register(final IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
    }
}
