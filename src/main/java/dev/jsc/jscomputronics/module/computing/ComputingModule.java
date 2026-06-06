/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.common.hardware.CpuSocket;
import dev.jsc.jscomputronics.common.hardware.CpuSpec;
import dev.jsc.jscomputronics.common.hardware.GpuSpec;
import dev.jsc.jscomputronics.common.hardware.MotherboardSpec;
import dev.jsc.jscomputronics.common.hardware.PcieGeneration;
import dev.jsc.jscomputronics.common.hardware.PsuSpec;
import dev.jsc.jscomputronics.common.hardware.RamGeneration;
import dev.jsc.jscomputronics.common.hardware.RamSpec;
import dev.jsc.jscomputronics.common.network.DataTier;
import dev.jsc.jscomputronics.common.tier.HardwareEra;
import dev.jsc.jscomputronics.module.computing.block.DataCableBlock;
import dev.jsc.jscomputronics.module.computing.block.MainframeBlock;
import dev.jsc.jscomputronics.module.computing.block.MainframePartBlock;
import dev.jsc.jscomputronics.module.computing.block.PersonalComputerBlock;
import dev.jsc.jscomputronics.module.computing.block.PersonalRouterBlock;
import dev.jsc.jscomputronics.module.computing.blockentity.DataCableBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.MainframePartBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.PersonalComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.PersonalRouterBlockEntity;
import dev.jsc.jscomputronics.module.computing.item.CpuItem;
import dev.jsc.jscomputronics.module.computing.item.GpuItem;
import dev.jsc.jscomputronics.module.computing.item.MotherboardItem;
import dev.jsc.jscomputronics.module.computing.item.PsuItem;
import dev.jsc.jscomputronics.module.computing.item.RamItem;
import dev.jsc.jscomputronics.module.computing.menu.MainframeMenu;
import dev.jsc.jscomputronics.module.computing.menu.PersonalComputerMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;

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

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, JsComputronics.MODID);

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

    // Routers

    public static final DeferredBlock<PersonalRouterBlock> PERSONAL_ROUTER = BLOCKS.register(
            "personal_router", () -> new PersonalRouterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(0.5F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    public static final DeferredItem<BlockItem> PERSONAL_ROUTER_ITEM = ITEMS.register(
            "personal_router", () -> new BlockItem(PERSONAL_ROUTER.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PersonalRouterBlockEntity>> PERSONAL_ROUTER_BE =
            BLOCK_ENTITIES.register("personal_router",
                    () -> BlockEntityType.Builder.of(PersonalRouterBlockEntity::new,
                            PERSONAL_ROUTER.get()).build(null));

    // Hardware components (Standard era — minimal set to build a Mainframe)

    public static final DeferredItem<MotherboardItem> MOTHERBOARD_MTX_P = ITEMS.register(
            "motherboard_mtx_p", () -> new MotherboardItem(new Item.Properties(),
                    new MotherboardSpec(HardwareEra.STANDARD, CpuSocket.LGA_2011, 4,
                            Set.of(RamGeneration.DDR3), 8, PcieGeneration.PCIE_3_0, 6, 8)));

    public static final DeferredItem<CpuItem> CPU_SERVO_2620 = ITEMS.register(
            "cpu_servo_2620", () -> new CpuItem(new Item.Properties(),
                    new CpuSpec(HardwareEra.STANDARD, CpuSocket.LGA_2011, 6, 2000, 95, false)));

    public static final DeferredItem<CpuItem> CPU_SERVO_2690 = ITEMS.register(
            "cpu_servo_2690", () -> new CpuItem(new Item.Properties(),
                    new CpuSpec(HardwareEra.STANDARD, CpuSocket.LGA_2011, 8, 2900, 135, false)));

    public static final DeferredItem<CpuItem> CPU_SERVO_2699 = ITEMS.register(
            "cpu_servo_2699", () -> new CpuItem(new Item.Properties(),
                    new CpuSpec(HardwareEra.STANDARD, CpuSocket.LGA_2011, 18, 2300, 145, false)));

    public static final DeferredItem<RamItem> RAM_DDR3_8192 = ITEMS.register(
            "ram_ddr3_8192", () -> new RamItem(new Item.Properties(),
                    new RamSpec(HardwareEra.STANDARD, RamGeneration.DDR3, 2048, 15)));

    public static final DeferredItem<GpuItem> GPU_HD_7970 = ITEMS.register(
            "gpu_hd_7970", () -> new GpuItem(new Item.Properties(),
                    new GpuSpec(HardwareEra.STANDARD, PcieGeneration.PCIE_3_0, 2048, 3072, 250)));

    public static final DeferredItem<PsuItem> PSU_650G = ITEMS.register(
            "psu_650g", () -> new PsuItem(new Item.Properties(), new PsuSpec(650, 90)));

    public static final DeferredItem<MotherboardItem> MOTHERBOARD_ATX_P = ITEMS.register(
            "motherboard_atx_p", () -> new MotherboardItem(new Item.Properties(),
                    new MotherboardSpec(HardwareEra.STANDARD, CpuSocket.AM3, 1,
                            Set.of(RamGeneration.DDR3), 4, PcieGeneration.PCIE_3_0, 4, 4)));

    public static final DeferredItem<CpuItem> CPU_APEX_3450 = ITEMS.register(
            "cpu_apex_3450", () -> new CpuItem(new Item.Properties(),
                    new CpuSpec(HardwareEra.STANDARD, CpuSocket.AM3, 4, 3450, 95, false)));

    // Mainframe

    private static BlockBehaviour.Properties mainframeProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .strength(3.5F)
                .requiresCorrectToolForDrops();
    }

    public static final DeferredBlock<MainframeBlock> MAINFRAME = BLOCKS.register(
            "mainframe", () -> new MainframeBlock(mainframeProperties()));

    public static final DeferredItem<BlockItem> MAINFRAME_ITEM = ITEMS.register(
            "mainframe", () -> new dev.jsc.jscomputronics.module.computing.item.MainframeBlockItem(
                    MAINFRAME.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MainframeBlockEntity>> MAINFRAME_BE =
            BLOCK_ENTITIES.register("mainframe",
                    () -> BlockEntityType.Builder.of(MainframeBlockEntity::new, MAINFRAME.get()).build(null));

    public static final DeferredBlock<MainframePartBlock> MAINFRAME_PART = BLOCKS.register(
            "mainframe_part", () -> new MainframePartBlock(mainframeProperties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MainframePartBlockEntity>> MAINFRAME_PART_BE =
            BLOCK_ENTITIES.register("mainframe_part",
                    () -> BlockEntityType.Builder.of(MainframePartBlockEntity::new, MAINFRAME_PART.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<MainframeMenu>> MAINFRAME_MENU =
            MENUS.register("mainframe", () -> IMenuTypeExtension.create(MainframeMenu::fromNetwork));

    // Personal Computer

    public static final DeferredBlock<PersonalComputerBlock> PERSONAL_COMPUTER = BLOCKS.register(
            "personal_computer", () -> new PersonalComputerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(2.0F)));

    public static final DeferredItem<BlockItem> PERSONAL_COMPUTER_ITEM = ITEMS.register(
            "personal_computer", () -> new BlockItem(PERSONAL_COMPUTER.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PersonalComputerBlockEntity>> PERSONAL_COMPUTER_BE =
            BLOCK_ENTITIES.register("personal_computer",
                    () -> BlockEntityType.Builder.of(PersonalComputerBlockEntity::new,
                            PERSONAL_COMPUTER.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<PersonalComputerMenu>> PERSONAL_COMPUTER_MENU =
            MENUS.register("personal_computer", () -> IMenuTypeExtension.create(PersonalComputerMenu::fromNetwork));

    public static void register(final IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
    }
}
