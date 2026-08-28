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
import dev.jsc.jscomputronics.common.hardware.DiskSize;
import dev.jsc.jscomputronics.common.hardware.CraftingCardSpec;
import dev.jsc.jscomputronics.common.hardware.DiskSpec;
import dev.jsc.jscomputronics.common.hardware.GpuSpec;
import dev.jsc.jscomputronics.common.hardware.MotherboardSpec;
import dev.jsc.jscomputronics.common.hardware.PcieGeneration;
import dev.jsc.jscomputronics.common.hardware.PsuSpec;
import dev.jsc.jscomputronics.common.hardware.RamGeneration;
import dev.jsc.jscomputronics.common.hardware.RamSpec;
import dev.jsc.jscomputronics.common.hardware.StorageTier;
import dev.jsc.jscomputronics.common.network.DataTier;
import dev.jsc.jscomputronics.common.tier.HardwareEra;
import dev.jsc.jscomputronics.common.tier.IndustrialTier;
import dev.jsc.jscomputronics.module.computing.block.CraftingComputerBlock;
import dev.jsc.jscomputronics.module.computing.block.DataCableBlock;
import dev.jsc.jscomputronics.module.computing.block.MainframeBlock;
import dev.jsc.jscomputronics.module.computing.block.MainframePartBlock;
import dev.jsc.jscomputronics.module.computing.block.LegacyMonitorBlock;
import dev.jsc.jscomputronics.module.computing.block.PersonalComputerBlock;
import dev.jsc.jscomputronics.module.computing.block.PersonalRouterBlock;
import dev.jsc.jscomputronics.module.computing.block.VintageMonitorBlock;
import dev.jsc.jscomputronics.module.computing.os.media.FormattedMediaItem;
import dev.jsc.jscomputronics.module.computing.os.media.MediaDriveType;
import dev.jsc.jscomputronics.module.computing.os.media.MediaFormat;
import dev.jsc.jscomputronics.module.computing.os.media.MediaItem;
import dev.jsc.jscomputronics.module.computing.os.media.MediaKind;
import dev.jsc.jscomputronics.module.computing.os.media.MediaReaderBlock;
import dev.jsc.jscomputronics.module.computing.os.media.MediaReaderBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.CraftingComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.DataCableBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.MainframePartBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.PersonalComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.PersonalRouterBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity;
import dev.jsc.jscomputronics.module.computing.item.CpuItem;
import dev.jsc.jscomputronics.module.computing.item.CraftingCardItem;
import dev.jsc.jscomputronics.module.computing.item.DiskItem;
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

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(JsComputronics.MODID);

    // Data components — a Server item carries its state in its NBT: the items it
    // stores, the hardware it is built from, and its network node identity.

    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<
                    dev.jsc.jscomputronics.module.computing.storage.ServerStorageContents>>
            SERVER_STORAGE = COMPONENTS.registerComponentType("server_storage", b -> b
                    .persistent(dev.jsc.jscomputronics.module.computing.storage.ServerStorageContents.CODEC)
                    .networkSynchronized(
                            dev.jsc.jscomputronics.module.computing.storage.ServerStorageContents.STREAM_CODEC));

    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<
                    dev.jsc.jscomputronics.module.computing.storage.ServerStorageContents>>
            DISK_STORAGE = COMPONENTS.registerComponentType("disk_storage", b -> b
                    .persistent(dev.jsc.jscomputronics.module.computing.storage.ServerStorageContents.CODEC)
                    .networkSynchronized(
                            dev.jsc.jscomputronics.module.computing.storage.ServerStorageContents.STREAM_CODEC));

    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.ItemContainerContents>>
            SERVER_HARDWARE = COMPONENTS.registerComponentType("server_hardware", b -> b
                    .persistent(net.minecraft.world.item.component.ItemContainerContents.CODEC)
                    .networkSynchronized(net.minecraft.world.item.component.ItemContainerContents.STREAM_CODEC));

    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<java.util.UUID>>
            SERVER_NODE_UUID = COMPONENTS.registerComponentType("server_node_uuid", b -> b
                    .persistent(net.minecraft.core.UUIDUtil.CODEC)
                    .networkSynchronized(net.minecraft.core.UUIDUtil.STREAM_CODEC));

    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<String>>
            COMPUTER_NAME = COMPONENTS.registerComponentType("computer_name", b -> b
                    .persistent(com.mojang.serialization.Codec.STRING)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8));

    // How much of a (non-Server) computer disk's storage is public, as a per-mille 0..1000. The
    // component rides on the disk ItemStack so the split travels with the disk when it is pulled
    // and reinserted. An absent component reads as fully private (see DiskItem.publicPermille).
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<Integer>>
            DISK_PUBLIC_PERMILLE = COMPONENTS.registerComponentType("disk_public_permille", b -> b
                    .persistent(com.mojang.serialization.Codec.intRange(0, 1000))
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    // OS media subsystem — components that together describe the content of a MediaItem.
    // A medium carries exactly one kind and the matching content component for that kind.

    // Installer payload (OS_INSTALL / PROGRAM_INSTALL): the OS or program id.
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<net.minecraft.resources.ResourceLocation>>
            MEDIA_PAYLOAD = COMPONENTS.registerComponentType("media_payload", b -> b
                    .persistent(net.minecraft.resources.ResourceLocation.CODEC)
                    .networkSynchronized(net.minecraft.resources.ResourceLocation.STREAM_CODEC));

    // Which of the three content kinds this medium carries. Absent component → OS_INSTALL (safe
    // default that keeps legacy blank media behaving as installer media).
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<MediaKind>>
            MEDIA_KIND = COMPONENTS.registerComponentType("media_kind", b -> b
                    .persistent(com.mojang.serialization.Codec.STRING.xmap(
                            MediaKind::valueOf, MediaKind::name))
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.map(
                            MediaKind::valueOf, MediaKind::name)));

    // Data contents (DATA kind): a portable item/fluid storage snapshot.
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<
                    dev.jsc.jscomputronics.module.computing.storage.ServerStorageContents>>
            MEDIA_DATA = COMPONENTS.registerComponentType("media_data", b -> b
                    .persistent(dev.jsc.jscomputronics.module.computing.storage.ServerStorageContents.CODEC)
                    .networkSynchronized(
                            dev.jsc.jscomputronics.module.computing.storage.ServerStorageContents.STREAM_CODEC));

    // Capacity of a DATA medium in item-equivalents. Absent → MediaItem.DEFAULT_CAPACITY.
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<Integer>>
            MEDIA_CAPACITY = COMPONENTS.registerComponentType("media_capacity", b -> b
                    .persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    // Disk filesystem components — files and the installed OS live on the DiskItem stack so
    // they travel with the disk when it is inserted or removed.

    // The filesystem contents of a disk volume: path-keyed map of stored files.
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<
                    dev.jsc.jscomputronics.module.computing.os.fs.FilesystemContents>>
            FILESYSTEM = COMPONENTS.registerComponentType("filesystem", b -> b
                    .persistent(dev.jsc.jscomputronics.module.computing.os.fs.FilesystemContents.CODEC)
                    .networkSynchronized(
                            dev.jsc.jscomputronics.module.computing.os.fs.FilesystemContents.STREAM_CODEC));

    // The OS installed on a system disk: a ResourceLocation identifying the registered OsDef.
    // Present only on bootable disks; absent on plain data disks.
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<net.minecraft.resources.ResourceLocation>>
            SYSTEM_OS = COMPONENTS.registerComponentType("system_os", b -> b
                    .persistent(net.minecraft.resources.ResourceLocation.CODEC)
                    .networkSynchronized(net.minecraft.resources.ResourceLocation.STREAM_CODEC));

    // A user-chosen label for a disk or media volume, shown in This PC and the explorer drive tree and
    // editable there. Rides on the ItemStack so it travels with the disk/medium. Absent → the volume's
    // default name (e.g. "Local Disk" for a system disk, "Removable Drive" for a medium).
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<String>>
            VOLUME_LABEL = COMPONENTS.registerComponentType("volume_label", b -> b
                    .persistent(com.mojang.serialization.Codec.STRING)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8));

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

    public static final DeferredBlock<DataCableBlock> HPC_CABLE = BLOCKS.register(
            "hpc_cable", () -> new DataCableBlock(cableProperties(), DataTier.HPC));

    public static final DeferredItem<BlockItem> HPC_CABLE_ITEM = ITEMS.register(
            "hpc_cable", () -> new BlockItem(HPC_CABLE.get(), new Item.Properties()));

    // Crafting cable: links a Crafting Switch to its Crafting Computer (a local machine cluster).
    public static final DeferredBlock<DataCableBlock> CRAFTING_CABLE = BLOCKS.register(
            "crafting_cable", () -> new DataCableBlock(cableProperties(), DataTier.CRAFTING));

    public static final DeferredItem<BlockItem> CRAFTING_CABLE_ITEM = ITEMS.register(
            "crafting_cable", () -> new BlockItem(CRAFTING_CABLE.get(), new Item.Properties()));

    // Crafting Switch: declares up to 5 adjacent machines, wired to a Crafting Computer over the crafting cable.
    public static final DeferredBlock<dev.jsc.jscomputronics.module.computing.block.CraftingSwitchBlock> CRAFTING_SWITCH =
            BLOCKS.register("crafting_switch",
                    () -> new dev.jsc.jscomputronics.module.computing.block.CraftingSwitchBlock(
                            BlockBehaviour.Properties.of().strength(1.5F)));

    public static final DeferredItem<BlockItem> CRAFTING_SWITCH_ITEM = ITEMS.register(
            "crafting_switch", () -> new BlockItem(CRAFTING_SWITCH.get(), new Item.Properties()));

    public static final DeferredBlock<dev.jsc.jscomputronics.module.computing.block.PeripheralCableBlock> PERIPHERAL_CABLE =
            BLOCKS.register("peripheral_cable",
                    () -> new dev.jsc.jscomputronics.module.computing.block.PeripheralCableBlock(cableProperties()));

    public static final DeferredItem<BlockItem> PERIPHERAL_CABLE_ITEM = ITEMS.register(
            "peripheral_cable", () -> new BlockItem(PERIPHERAL_CABLE.get(), new Item.Properties()));

    public static final DeferredBlock<dev.jsc.jscomputronics.module.computing.block.MonitorBlock> MONITOR =
            BLOCKS.register("monitor", () -> new dev.jsc.jscomputronics.module.computing.block.MonitorBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BLACK)
                            .strength(1.0F)
                            .sound(SoundType.METAL)
                            .noOcclusion()));

    public static final DeferredItem<BlockItem> MONITOR_ITEM = ITEMS.register(
            "monitor", () -> new BlockItem(MONITOR.get(), new Item.Properties()));

    public static final DeferredBlock<VintageMonitorBlock> VINTAGE_MONITOR =
            BLOCKS.register("vintage_monitor",
                    () -> new VintageMonitorBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.COLOR_BLACK)
                                    .strength(1.0F)
                                    .sound(SoundType.METAL)
                                    .noOcclusion()));

    public static final DeferredItem<BlockItem> VINTAGE_MONITOR_ITEM = ITEMS.register(
            "vintage_monitor", () -> new BlockItem(VINTAGE_MONITOR.get(), new Item.Properties()));

    public static final DeferredBlock<LegacyMonitorBlock> LEGACY_MONITOR =
            BLOCKS.register("legacy_monitor",
                    () -> new LegacyMonitorBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.COLOR_BLACK)
                                    .strength(1.0F)
                                    .sound(SoundType.METAL)
                                    .noOcclusion()));

    public static final DeferredItem<BlockItem> LEGACY_MONITOR_ITEM = ITEMS.register(
            "legacy_monitor", () -> new BlockItem(LEGACY_MONITOR.get(), new Item.Properties()));

    // Block entities

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataCableBlockEntity>> DATA_CABLE_BE =
            BLOCK_ENTITIES.register("data_cable",
                    () -> BlockEntityType.Builder.of(DataCableBlockEntity::new,
                            ETHERNET_CABLE.get(), HBW_CABLE.get(), HPC_CABLE.get(),
                            CRAFTING_CABLE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<dev.jsc.jscomputronics.module.computing.blockentity.CraftingSwitchBlockEntity>>
            CRAFTING_SWITCH_BE = BLOCK_ENTITIES.register("crafting_switch",
                    () -> BlockEntityType.Builder.of(
                            dev.jsc.jscomputronics.module.computing.blockentity.CraftingSwitchBlockEntity::new,
                            CRAFTING_SWITCH.get()).build(null));

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

    public static final DeferredBlock<dev.jsc.jscomputronics.module.computing.block.ServerRouterBlock> SERVER_ROUTER =
            BLOCKS.register("server_router",
                    () -> new dev.jsc.jscomputronics.module.computing.block.ServerRouterBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.COLOR_GRAY)
                                    .strength(0.6F)
                                    .sound(SoundType.METAL)
                                    .noOcclusion()));

    public static final DeferredItem<BlockItem> SERVER_ROUTER_ITEM = ITEMS.register(
            "server_router", () -> new BlockItem(SERVER_ROUTER.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<dev.jsc.jscomputronics.module.computing.blockentity.ServerRouterBlockEntity>> SERVER_ROUTER_BE =
            BLOCK_ENTITIES.register("server_router",
                    () -> BlockEntityType.Builder.of(
                            dev.jsc.jscomputronics.module.computing.blockentity.ServerRouterBlockEntity::new,
                            SERVER_ROUTER.get()).build(null));

    public static final DeferredBlock<dev.jsc.jscomputronics.module.computing.block.DatacenterStationBlock> DATACENTER_STATION =
            BLOCKS.register("datacenter_station",
                    () -> new dev.jsc.jscomputronics.module.computing.block.DatacenterStationBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.COLOR_GRAY)
                                    .strength(0.6F)
                                    .sound(SoundType.METAL)
                                    .noOcclusion()));

    public static final DeferredItem<BlockItem> DATACENTER_STATION_ITEM = ITEMS.register(
            "datacenter_station", () -> new BlockItem(DATACENTER_STATION.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<dev.jsc.jscomputronics.module.computing.blockentity.DatacenterStationBlockEntity>> DATACENTER_STATION_BE =
            BLOCK_ENTITIES.register("datacenter_station",
                    () -> BlockEntityType.Builder.of(
                            dev.jsc.jscomputronics.module.computing.blockentity.DatacenterStationBlockEntity::new,
                            DATACENTER_STATION.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<dev.jsc.jscomputronics.module.computing.blockentity.MonitorBlockEntity>> MONITOR_BE =
            BLOCK_ENTITIES.register("monitor",
                    () -> BlockEntityType.Builder.of(
                            dev.jsc.jscomputronics.module.computing.blockentity.MonitorBlockEntity::new,
                            MONITOR.get(), VINTAGE_MONITOR.get(), LEGACY_MONITOR.get()).build(null));

    public static final DeferredBlock<dev.jsc.jscomputronics.module.computing.block.TankBlock> TANK =
            BLOCKS.register("tank", () -> new dev.jsc.jscomputronics.module.computing.block.TankBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_LIGHT_BLUE)
                            .strength(0.6F)
                            .sound(SoundType.GLASS)
                            .noOcclusion()));

    public static final DeferredItem<BlockItem> TANK_ITEM = ITEMS.register(
            "tank", () -> new BlockItem(TANK.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<dev.jsc.jscomputronics.module.computing.blockentity.TankBlockEntity>> TANK_BE =
            BLOCK_ENTITIES.register("tank",
                    () -> BlockEntityType.Builder.of(
                            dev.jsc.jscomputronics.module.computing.blockentity.TankBlockEntity::new,
                            TANK.get()).build(null));

    // Interaction buses — move items between the network and adjacent inventories

    public static final DeferredItem<dev.jsc.jscomputronics.module.computing.block.part.CablePartItem> IMPORT_BUS_ITEM =
            ITEMS.register("import_bus", () -> new dev.jsc.jscomputronics.module.computing.block.part.CablePartItem(
                    new Item.Properties(),
                    dev.jsc.jscomputronics.module.computing.block.part.CablePartType.IMPORT));

    public static final DeferredItem<dev.jsc.jscomputronics.module.computing.block.part.CablePartItem> EXPORT_BUS_ITEM =
            ITEMS.register("export_bus", () -> new dev.jsc.jscomputronics.module.computing.block.part.CablePartItem(
                    new Item.Properties(),
                    dev.jsc.jscomputronics.module.computing.block.part.CablePartType.EXPORT));

    public static final DeferredItem<dev.jsc.jscomputronics.module.computing.block.part.CablePartItem> INPUT_BUS_ITEM =
            ITEMS.register("input_bus", () -> new dev.jsc.jscomputronics.module.computing.block.part.CablePartItem(
                    new Item.Properties(),
                    dev.jsc.jscomputronics.module.computing.block.part.CablePartType.INPUT));

    public static final DeferredItem<dev.jsc.jscomputronics.module.computing.block.part.CablePartItem> RECEIVING_BUS_ITEM =
            ITEMS.register("receiving_bus", () -> new dev.jsc.jscomputronics.module.computing.block.part.CablePartItem(
                    new Item.Properties(),
                    dev.jsc.jscomputronics.module.computing.block.part.CablePartType.RECEIVING));

    public static final DeferredHolder<MenuType<?>,
            MenuType<dev.jsc.jscomputronics.module.computing.menu.ExportBusMenu>> EXPORT_BUS_MENU =
            MENUS.register("export_bus", () -> IMenuTypeExtension.create(
                    dev.jsc.jscomputronics.module.computing.menu.ExportBusMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>,
            MenuType<dev.jsc.jscomputronics.module.computing.menu.ImportBusMenu>> IMPORT_BUS_MENU =
            MENUS.register("import_bus", () -> IMenuTypeExtension.create(
                    dev.jsc.jscomputronics.module.computing.menu.ImportBusMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>,
            MenuType<dev.jsc.jscomputronics.module.computing.menu.CraftingSwitchMenu>> CRAFTING_SWITCH_MENU =
            MENUS.register("crafting_switch", () -> IMenuTypeExtension.create(
                    dev.jsc.jscomputronics.module.computing.menu.CraftingSwitchMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>,
            MenuType<dev.jsc.jscomputronics.module.computing.menu.InputBusMenu>> INPUT_BUS_MENU =
            MENUS.register("input_bus", () -> IMenuTypeExtension.create(
                    dev.jsc.jscomputronics.module.computing.menu.InputBusMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>,
            MenuType<dev.jsc.jscomputronics.module.computing.menu.ReceivingBusMenu>> RECEIVING_BUS_MENU =
            MENUS.register("receiving_bus", () -> IMenuTypeExtension.create(
                    dev.jsc.jscomputronics.module.computing.menu.ReceivingBusMenu::fromNetwork));

    // Server Rack — houses Server items as network nodes

    public static final DeferredBlock<dev.jsc.jscomputronics.module.computing.block.ServerRackBlock> SERVER_RACK =
            BLOCKS.register("server_rack",
                    () -> new dev.jsc.jscomputronics.module.computing.block.ServerRackBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .strength(1.5F)
                                    .sound(SoundType.METAL)
                                    .noOcclusion()));

    public static final DeferredItem<BlockItem> SERVER_RACK_ITEM = ITEMS.register(
            "server_rack", () -> new BlockItem(SERVER_RACK.get(), new Item.Properties()));

    public static final DeferredBlock<dev.jsc.jscomputronics.module.computing.block.ServerRackPartBlock> SERVER_RACK_PART =
            BLOCKS.register("server_rack_part",
                    () -> new dev.jsc.jscomputronics.module.computing.block.ServerRackPartBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .strength(1.5F)
                                    .sound(SoundType.METAL)
                                    .noOcclusion()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ServerRackBlockEntity>> SERVER_RACK_BE =
            BLOCK_ENTITIES.register("server_rack",
                    () -> BlockEntityType.Builder.of(ServerRackBlockEntity::new, SERVER_RACK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<dev.jsc.jscomputronics.module.computing.blockentity.ServerRackPartBlockEntity>> SERVER_RACK_PART_BE =
            BLOCK_ENTITIES.register("server_rack_part",
                    () -> BlockEntityType.Builder.of(
                            dev.jsc.jscomputronics.module.computing.blockentity.ServerRackPartBlockEntity::new,
                            SERVER_RACK_PART.get()).build(null));

    public static final DeferredHolder<MenuType<?>,
            MenuType<dev.jsc.jscomputronics.module.computing.menu.ServerRackMenu>> SERVER_RACK_MENU =
            MENUS.register("server_rack", () -> IMenuTypeExtension.create(
                    dev.jsc.jscomputronics.module.computing.menu.ServerRackMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>,
            MenuType<dev.jsc.jscomputronics.module.computing.menu.ServerAssemblyMenu>> SERVER_ASSEMBLY_MENU =
            MENUS.register("server_assembly", () -> IMenuTypeExtension.create(
                    dev.jsc.jscomputronics.module.computing.menu.ServerAssemblyMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>,
            MenuType<dev.jsc.jscomputronics.module.computing.menu.ComputerTerminalMenu>> COMPUTER_TERMINAL_MENU =
            MENUS.register("computer_terminal", () -> IMenuTypeExtension.create(
                    dev.jsc.jscomputronics.module.computing.menu.ComputerTerminalMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>,
            MenuType<dev.jsc.jscomputronics.module.computing.menu.CommandPromptMenu>> COMMAND_PROMPT_MENU =
            MENUS.register("command_prompt", () -> IMenuTypeExtension.create(
                    dev.jsc.jscomputronics.module.computing.menu.CommandPromptMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>,
            MenuType<dev.jsc.jscomputronics.module.computing.menu.DesktopMenu>> DESKTOP_MENU =
            MENUS.register("desktop", () -> IMenuTypeExtension.create(
                    dev.jsc.jscomputronics.module.computing.menu.DesktopMenu::fromNetwork));

    // Hardware components (Standard era — minimal set to build a Mainframe)

    public static final DeferredItem<MotherboardItem> MOTHERBOARD_MTX_P = ITEMS.register(
            "motherboard_mtx_p", () -> new MotherboardItem(new Item.Properties(),
                    new MotherboardSpec(dev.jsc.jscomputronics.common.hardware.FormFactor.MTX,
                            HardwareEra.STANDARD, CpuSocket.LGA_2011, 4,
                            Set.of(RamGeneration.DDR3), 8, PcieGeneration.PCIE_3_0, 6, 4, 8)));

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

    public static final DeferredItem<CraftingCardItem> CRAFTING_CARD_T2 = ITEMS.register(
            "crafting_card_t2", () -> new CraftingCardItem(new Item.Properties(),
                    new CraftingCardSpec(IndustrialTier.T2, PcieGeneration.PCIE_1_0, 0.05, 2, 75)));

    public static final DeferredItem<CraftingCardItem> CRAFTING_CARD_T3 = ITEMS.register(
            "crafting_card_t3", () -> new CraftingCardItem(new Item.Properties(),
                    new CraftingCardSpec(IndustrialTier.T3, PcieGeneration.PCIE_2_0, 0.1, 4, 100)));

    public static final DeferredItem<PsuItem> PSU_650G = ITEMS.register(
            "psu_650g", () -> new PsuItem(new Item.Properties(), new PsuSpec(650, 90)));

    // Pattern system — the Pattern Encoder writes .craft files onto removable media

    public static final DeferredBlock<dev.jsc.jscomputronics.module.computing.block.PatternEncoderBlock> PATTERN_ENCODER =
            BLOCKS.register("pattern_encoder",
                    () -> new dev.jsc.jscomputronics.module.computing.block.PatternEncoderBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.COLOR_GRAY)
                                    .strength(1.5F)
                                    .sound(SoundType.METAL)));

    public static final DeferredItem<BlockItem> PATTERN_ENCODER_ITEM = ITEMS.register(
            "pattern_encoder", () -> new BlockItem(PATTERN_ENCODER.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<dev.jsc.jscomputronics.module.computing.blockentity.PatternEncoderBlockEntity>> PATTERN_ENCODER_BE =
            BLOCK_ENTITIES.register("pattern_encoder",
                    () -> BlockEntityType.Builder.of(
                            dev.jsc.jscomputronics.module.computing.blockentity.PatternEncoderBlockEntity::new,
                            PATTERN_ENCODER.get()).build(null));

    public static final DeferredHolder<MenuType<?>,
            MenuType<dev.jsc.jscomputronics.module.computing.menu.PatternEncoderMenu>> PATTERN_ENCODER_MENU =
            MENUS.register("pattern_encoder", () -> IMenuTypeExtension.create(
                    dev.jsc.jscomputronics.module.computing.menu.PatternEncoderMenu::fromNetwork));

    // OS media subsystem — a peripheral block that holds one MediaItem and exposes the
    // installer payload or data contents so the firmware boot screen and transfer logic can read it.

    // Media reader drives: one block per drive type, each linked to a computer via the Peripheral Cable.
    public static final DeferredBlock<MediaReaderBlock> FLOPPY_DRIVE = BLOCKS.register("floppy_drive",
            () -> new MediaReaderBlock(MediaDriveType.FLOPPY_DRIVE, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY).strength(1.5F).sound(SoundType.METAL)));
    public static final DeferredBlock<MediaReaderBlock> CD_DRIVE = BLOCKS.register("cd_drive",
            () -> new MediaReaderBlock(MediaDriveType.CD_DRIVE, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY).strength(1.5F).sound(SoundType.METAL)));
    public static final DeferredBlock<MediaReaderBlock> DVD_DRIVE = BLOCKS.register("dvd_drive",
            () -> new MediaReaderBlock(MediaDriveType.DVD_DRIVE, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK).strength(1.5F).sound(SoundType.METAL)));
    public static final DeferredBlock<MediaReaderBlock> DOCK_STATION = BLOCKS.register("dock_station",
            () -> new MediaReaderBlock(MediaDriveType.DOCK_STATION, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK).strength(1.5F).sound(SoundType.METAL)));

    public static final DeferredItem<BlockItem> FLOPPY_DRIVE_ITEM = ITEMS.register("floppy_drive",
            () -> new BlockItem(FLOPPY_DRIVE.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> CD_DRIVE_ITEM = ITEMS.register("cd_drive",
            () -> new BlockItem(CD_DRIVE.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> DVD_DRIVE_ITEM = ITEMS.register("dvd_drive",
            () -> new BlockItem(DVD_DRIVE.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> DOCK_STATION_ITEM = ITEMS.register("dock_station",
            () -> new BlockItem(DOCK_STATION.get(), new Item.Properties()));

    // Typed physical media. The format is the item's identity; the content lives in components.
    public static final DeferredItem<FormattedMediaItem> FLOPPY_DISK = ITEMS.register("floppy_disk",
            () -> new FormattedMediaItem(new Item.Properties(), MediaFormat.FLOPPY, true));
    public static final DeferredItem<FormattedMediaItem> CD_ROM = ITEMS.register("cd_rom",
            () -> new FormattedMediaItem(new Item.Properties(), MediaFormat.CD, false));
    public static final DeferredItem<FormattedMediaItem> CD_RW = ITEMS.register("cd_rw",
            () -> new FormattedMediaItem(new Item.Properties(), MediaFormat.CD, true));
    public static final DeferredItem<FormattedMediaItem> DVD_ROM = ITEMS.register("dvd_rom",
            () -> new FormattedMediaItem(new Item.Properties(), MediaFormat.DVD, false));
    public static final DeferredItem<FormattedMediaItem> DVD_RW = ITEMS.register("dvd_rw",
            () -> new FormattedMediaItem(new Item.Properties(), MediaFormat.DVD, true));
    public static final DeferredItem<FormattedMediaItem> USB_FLASH_DRIVE = ITEMS.register("usb_flash_drive",
            () -> new FormattedMediaItem(new Item.Properties(), MediaFormat.USB, true));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MediaReaderBlockEntity>> MEDIA_READER_BE =
            BLOCK_ENTITIES.register("media_reader",
                    () -> BlockEntityType.Builder.of(MediaReaderBlockEntity::new,
                            FLOPPY_DRIVE.get(), CD_DRIVE.get(), DVD_DRIVE.get(), DOCK_STATION.get()).build(null));

    public static final DeferredItem<MotherboardItem> MOTHERBOARD_EEB_P = ITEMS.register(
            "motherboard_eeb_p", () -> new MotherboardItem(new Item.Properties(),
                    new MotherboardSpec(dev.jsc.jscomputronics.common.hardware.FormFactor.EEB,
                            HardwareEra.STANDARD, CpuSocket.LGA_2011, 2,
                            Set.of(RamGeneration.DDR3), 8, PcieGeneration.PCIE_3_0, 6, 6, 6)));

    /**
     * A registered disk item with its tier and size, for datagen and creative-tab iteration.
     */
    public record DiskEntry(StorageTier tier, DiskSize size, DeferredItem<DiskItem> item) {
        public String displayName() {
            return tier.productName() + " " + size.displayName();
        }
    }

    public static final java.util.List<DiskEntry> DISKS = registerDisks();

    private static java.util.List<DiskEntry> registerDisks() {
        final java.util.List<DiskEntry> disks = new java.util.ArrayList<>();
        for (final StorageTier tier : StorageTier.values()) {
            for (final DiskSize size : DiskSize.values()) {
                final String id = "disk_" + tier.name().toLowerCase(java.util.Locale.ROOT) + "_" + size.id();
                final DeferredItem<DiskItem> item = ITEMS.register(id, () -> new DiskItem(
                        new Item.Properties(),
                        new DiskSpec(tier, size.capacityItems(), tier.tdpWatts())));
                disks.add(new DiskEntry(tier, size, item));
            }
        }
        return java.util.List.copyOf(disks);
    }

    public static DiskItem disk(final StorageTier tier, final DiskSize size) {
        for (final DiskEntry entry : DISKS) {
            if (entry.tier() == tier && entry.size() == size) {
                return entry.item().get();
            }
        }
        throw new IllegalArgumentException("no registered disk for " + tier + " " + size);
    }

    // Server items

    public static final DeferredItem<dev.jsc.jscomputronics.module.computing.item.ServerCaseItem> SERVER_CASE =
            ITEMS.register("server_case",
                    () -> new dev.jsc.jscomputronics.module.computing.item.ServerCaseItem(new Item.Properties()));

    public static final DeferredItem<dev.jsc.jscomputronics.module.computing.item.ServerItem> SERVER =
            ITEMS.register("server", () -> new dev.jsc.jscomputronics.module.computing.item.ServerItem(
                    new Item.Properties()));

    public static net.minecraft.world.item.ItemStack defaultServer() {
        final net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(SERVER.get());
        final net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> hardware =
                net.minecraft.core.NonNullList.withSize(
                        dev.jsc.jscomputronics.module.computing.item.ServerHardwareHandler.SLOTS,
                        net.minecraft.world.item.ItemStack.EMPTY);
        hardware.set(dev.jsc.jscomputronics.module.computing.item.ServerHardwareHandler.MOBO,
                new net.minecraft.world.item.ItemStack(MOTHERBOARD_EEB_P.get()));
        hardware.set(dev.jsc.jscomputronics.module.computing.item.ServerHardwareHandler.CPU_START,
                new net.minecraft.world.item.ItemStack(CPU_SERVO_2620.get()));
        hardware.set(dev.jsc.jscomputronics.module.computing.item.ServerHardwareHandler.RAM_START,
                new net.minecraft.world.item.ItemStack(RAM_DDR3_8192.get()));
        hardware.set(dev.jsc.jscomputronics.module.computing.item.ServerHardwareHandler.PSU,
                new net.minecraft.world.item.ItemStack(PSU_650G.get()));
        hardware.set(dev.jsc.jscomputronics.module.computing.item.ServerHardwareHandler.DISK_START,
                new net.minecraft.world.item.ItemStack(disk(StorageTier.NVME, DiskSize.TB_1)));
        hardware.set(dev.jsc.jscomputronics.module.computing.item.ServerHardwareHandler.DISK_START + 1,
                new net.minecraft.world.item.ItemStack(disk(StorageTier.NVME, DiskSize.TB_1)));
        stack.set(SERVER_HARDWARE.get(),
                net.minecraft.world.item.component.ItemContainerContents.fromItems(hardware));
        return stack;
    }

    public static net.minecraft.world.item.ItemStack cpulessServer() {
        final net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(SERVER.get());
        final net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> hardware =
                net.minecraft.core.NonNullList.withSize(
                        dev.jsc.jscomputronics.module.computing.item.ServerHardwareHandler.SLOTS,
                        net.minecraft.world.item.ItemStack.EMPTY);
        hardware.set(dev.jsc.jscomputronics.module.computing.item.ServerHardwareHandler.MOBO,
                new net.minecraft.world.item.ItemStack(MOTHERBOARD_EEB_P.get()));
        hardware.set(dev.jsc.jscomputronics.module.computing.item.ServerHardwareHandler.RAM_START,
                new net.minecraft.world.item.ItemStack(RAM_DDR3_8192.get()));
        hardware.set(dev.jsc.jscomputronics.module.computing.item.ServerHardwareHandler.PSU,
                new net.minecraft.world.item.ItemStack(PSU_650G.get()));
        hardware.set(dev.jsc.jscomputronics.module.computing.item.ServerHardwareHandler.DISK_START,
                new net.minecraft.world.item.ItemStack(disk(StorageTier.NVME, DiskSize.TB_1)));
        stack.set(SERVER_HARDWARE.get(),
                net.minecraft.world.item.component.ItemContainerContents.fromItems(hardware));
        return stack;
    }

    public static final DeferredItem<MotherboardItem> MOTHERBOARD_ATX_P = ITEMS.register(
            "motherboard_atx_p", () -> new MotherboardItem(new Item.Properties(),
                    new MotherboardSpec(dev.jsc.jscomputronics.common.hardware.FormFactor.ATX,
                            HardwareEra.STANDARD, CpuSocket.AM3, 1,
                            Set.of(RamGeneration.DDR3), 4, PcieGeneration.PCIE_3_0, 4, 2, 4)));

    public static final DeferredItem<CpuItem> CPU_ASCENT_965 = ITEMS.register(
            "cpu_ascent_965", () -> new CpuItem(new Item.Properties(),
                    new CpuSpec(HardwareEra.STANDARD, CpuSocket.AM3, 4, 3400, 125, false)));

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

    // Earlier-era Mainframes — the same orchestrator and block entity, differing only by era, accepted
    // MTX board and skin. Same 3x2x2 multiblock geometry and shared parts.
    public static final DeferredBlock<dev.jsc.jscomputronics.module.computing.block.VintageMainframeBlock>
            VINTAGE_MAINFRAME = BLOCKS.register("vintage_mainframe",
                    () -> new dev.jsc.jscomputronics.module.computing.block.VintageMainframeBlock(mainframeProperties()));

    public static final DeferredItem<BlockItem> VINTAGE_MAINFRAME_ITEM = ITEMS.register(
            "vintage_mainframe", () -> new dev.jsc.jscomputronics.module.computing.item.MainframeBlockItem(
                    VINTAGE_MAINFRAME.get(), new Item.Properties()));

    public static final DeferredBlock<dev.jsc.jscomputronics.module.computing.block.LegacyMainframeBlock>
            LEGACY_MAINFRAME = BLOCKS.register("legacy_mainframe",
                    () -> new dev.jsc.jscomputronics.module.computing.block.LegacyMainframeBlock(mainframeProperties()));

    public static final DeferredItem<BlockItem> LEGACY_MAINFRAME_ITEM = ITEMS.register(
            "legacy_mainframe", () -> new dev.jsc.jscomputronics.module.computing.item.MainframeBlockItem(
                    LEGACY_MAINFRAME.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MainframeBlockEntity>> MAINFRAME_BE =
            BLOCK_ENTITIES.register("mainframe",
                    () -> BlockEntityType.Builder.of(MainframeBlockEntity::new,
                            MAINFRAME.get(), VINTAGE_MAINFRAME.get(), LEGACY_MAINFRAME.get()).build(null));

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

    // Earlier-era Personal Computers — the same machine and block entity, differing only by era, accepted
    // board and skin.
    public static final DeferredBlock<dev.jsc.jscomputronics.module.computing.block.VintagePersonalComputerBlock>
            VINTAGE_PERSONAL_COMPUTER = BLOCKS.register("vintage_personal_computer",
                    () -> new dev.jsc.jscomputronics.module.computing.block.VintagePersonalComputerBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.COLOR_GRAY)
                                    .strength(2.0F)));

    public static final DeferredItem<BlockItem> VINTAGE_PERSONAL_COMPUTER_ITEM = ITEMS.register(
            "vintage_personal_computer",
            () -> new BlockItem(VINTAGE_PERSONAL_COMPUTER.get(), new Item.Properties()));

    public static final DeferredBlock<dev.jsc.jscomputronics.module.computing.block.LegacyPersonalComputerBlock>
            LEGACY_PERSONAL_COMPUTER = BLOCKS.register("legacy_personal_computer",
                    () -> new dev.jsc.jscomputronics.module.computing.block.LegacyPersonalComputerBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.COLOR_GRAY)
                                    .strength(2.0F)));

    public static final DeferredItem<BlockItem> LEGACY_PERSONAL_COMPUTER_ITEM = ITEMS.register(
            "legacy_personal_computer",
            () -> new BlockItem(LEGACY_PERSONAL_COMPUTER.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PersonalComputerBlockEntity>> PERSONAL_COMPUTER_BE =
            BLOCK_ENTITIES.register("personal_computer",
                    () -> BlockEntityType.Builder.of(PersonalComputerBlockEntity::new,
                            PERSONAL_COMPUTER.get(), VINTAGE_PERSONAL_COMPUTER.get(),
                            LEGACY_PERSONAL_COMPUTER.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<PersonalComputerMenu>> PERSONAL_COMPUTER_MENU =
            MENUS.register("personal_computer", () -> IMenuTypeExtension.create(PersonalComputerMenu::fromNetwork));

    // Crafting Computer — an ATX computer that executes recipes once a Crafting Card is installed

    public static final DeferredBlock<CraftingComputerBlock> CRAFTING_COMPUTER = BLOCKS.register(
            "crafting_computer", () -> new CraftingComputerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(2.0F)));

    public static final DeferredItem<BlockItem> CRAFTING_COMPUTER_ITEM = ITEMS.register(
            "crafting_computer", () -> new BlockItem(CRAFTING_COMPUTER.get(), new Item.Properties()));

    // Earlier-era Crafting Computers — the same machine and block entity, differing only by era, accepted
    // board and skin.
    public static final DeferredBlock<dev.jsc.jscomputronics.module.computing.block.VintageCraftingComputerBlock>
            VINTAGE_CRAFTING_COMPUTER = BLOCKS.register("vintage_crafting_computer",
                    () -> new dev.jsc.jscomputronics.module.computing.block.VintageCraftingComputerBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.COLOR_GRAY)
                                    .strength(2.0F)));

    public static final DeferredItem<BlockItem> VINTAGE_CRAFTING_COMPUTER_ITEM = ITEMS.register(
            "vintage_crafting_computer",
            () -> new BlockItem(VINTAGE_CRAFTING_COMPUTER.get(), new Item.Properties()));

    public static final DeferredBlock<dev.jsc.jscomputronics.module.computing.block.LegacyCraftingComputerBlock>
            LEGACY_CRAFTING_COMPUTER = BLOCKS.register("legacy_crafting_computer",
                    () -> new dev.jsc.jscomputronics.module.computing.block.LegacyCraftingComputerBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.COLOR_GRAY)
                                    .strength(2.0F)));

    public static final DeferredItem<BlockItem> LEGACY_CRAFTING_COMPUTER_ITEM = ITEMS.register(
            "legacy_crafting_computer",
            () -> new BlockItem(LEGACY_CRAFTING_COMPUTER.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CraftingComputerBlockEntity>> CRAFTING_COMPUTER_BE =
            BLOCK_ENTITIES.register("crafting_computer",
                    () -> BlockEntityType.Builder.of(CraftingComputerBlockEntity::new,
                            CRAFTING_COMPUTER.get(), VINTAGE_CRAFTING_COMPUTER.get(),
                            LEGACY_CRAFTING_COMPUTER.get()).build(null));

    public static final DeferredHolder<MenuType<?>,
            MenuType<dev.jsc.jscomputronics.module.computing.menu.CraftingComputerMenu>> CRAFTING_COMPUTER_MENU =
            MENUS.register("crafting_computer", () -> IMenuTypeExtension.create(
                    dev.jsc.jscomputronics.module.computing.menu.CraftingComputerMenu::fromNetwork));

    // Supercomputer — a cluster of interconnected nodes uplinked by an HBW Interface

    public static final DeferredBlock<dev.jsc.jscomputronics.module.computing.block.SupercomputerNodeBlock>
            SUPERCOMPUTER_NODE = BLOCKS.register("supercomputer_node",
                    () -> new dev.jsc.jscomputronics.module.computing.block.SupercomputerNodeBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.COLOR_GRAY)
                                    .strength(3.0F)));

    public static final DeferredItem<BlockItem> SUPERCOMPUTER_NODE_ITEM = ITEMS.register(
            "supercomputer_node", () -> new BlockItem(SUPERCOMPUTER_NODE.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<dev.jsc.jscomputronics.module.computing.blockentity.SupercomputerNodeBlockEntity>>
            SUPERCOMPUTER_NODE_BE = BLOCK_ENTITIES.register("supercomputer_node",
                    () -> BlockEntityType.Builder.of(
                            dev.jsc.jscomputronics.module.computing.blockentity.SupercomputerNodeBlockEntity::new,
                            SUPERCOMPUTER_NODE.get()).build(null));

    public static final DeferredBlock<dev.jsc.jscomputronics.module.computing.block.SupercomputerNodePartBlock>
            SUPERCOMPUTER_NODE_PART = BLOCKS.register("supercomputer_node_part",
                    () -> new dev.jsc.jscomputronics.module.computing.block.SupercomputerNodePartBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.COLOR_GRAY)
                                    .strength(3.0F)));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<dev.jsc.jscomputronics.module.computing.blockentity.SupercomputerNodePartBlockEntity>>
            SUPERCOMPUTER_NODE_PART_BE = BLOCK_ENTITIES.register("supercomputer_node_part",
                    () -> BlockEntityType.Builder.of(
                            dev.jsc.jscomputronics.module.computing.blockentity.SupercomputerNodePartBlockEntity::new,
                            SUPERCOMPUTER_NODE_PART.get()).build(null));

    public static final DeferredHolder<MenuType<?>,
            MenuType<dev.jsc.jscomputronics.module.computing.menu.SupercomputerNodeMenu>>
            SUPERCOMPUTER_NODE_MENU = MENUS.register("supercomputer_node",
                    () -> IMenuTypeExtension.create(
                            dev.jsc.jscomputronics.module.computing.menu.SupercomputerNodeMenu::fromNetwork));

    public static final DeferredBlock<dev.jsc.jscomputronics.module.computing.block.HbwInterfaceBlock>
            HBW_INTERFACE = BLOCKS.register("hbw_interface",
                    () -> new dev.jsc.jscomputronics.module.computing.block.HbwInterfaceBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.COLOR_GRAY)
                                    .strength(2.0F)
                                    .noOcclusion()));

    public static final DeferredItem<BlockItem> HBW_INTERFACE_ITEM = ITEMS.register(
            "hbw_interface", () -> new BlockItem(HBW_INTERFACE.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<dev.jsc.jscomputronics.module.computing.blockentity.HbwInterfaceBlockEntity>>
            HBW_INTERFACE_BE = BLOCK_ENTITIES.register("hbw_interface",
                    () -> BlockEntityType.Builder.of(
                            dev.jsc.jscomputronics.module.computing.blockentity.HbwInterfaceBlockEntity::new,
                            HBW_INTERFACE.get()).build(null));

    public static final DeferredBlock<dev.jsc.jscomputronics.module.computing.block.SupercomputerConsoleBlock>
            SUPERCOMPUTER_CONSOLE = BLOCKS.register("supercomputer_console",
                    () -> new dev.jsc.jscomputronics.module.computing.block.SupercomputerConsoleBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.COLOR_GRAY)
                                    .strength(0.6F)
                                    .sound(SoundType.METAL)
                                    .noOcclusion()));

    public static final DeferredItem<BlockItem> SUPERCOMPUTER_CONSOLE_ITEM = ITEMS.register(
            "supercomputer_console", () -> new BlockItem(SUPERCOMPUTER_CONSOLE.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<dev.jsc.jscomputronics.module.computing.blockentity.SupercomputerConsoleBlockEntity>>
            SUPERCOMPUTER_CONSOLE_BE = BLOCK_ENTITIES.register("supercomputer_console",
                    () -> BlockEntityType.Builder.of(
                            dev.jsc.jscomputronics.module.computing.blockentity.SupercomputerConsoleBlockEntity::new,
                            SUPERCOMPUTER_CONSOLE.get()).build(null));

    public static final DeferredHolder<MenuType<?>,
            MenuType<dev.jsc.jscomputronics.module.computing.menu.SupercomputerConsoleMenu>>
            SUPERCOMPUTER_CONSOLE_MENU = MENUS.register("supercomputer_console",
                    () -> IMenuTypeExtension.create(
                            dev.jsc.jscomputronics.module.computing.menu.SupercomputerConsoleMenu::fromNetwork));

    public static final DeferredItem<dev.jsc.jscomputronics.module.computing.item.PhiCoprocessorItem> PHI_5100 =
            ITEMS.register("phi_5100", () -> new dev.jsc.jscomputronics.module.computing.item.PhiCoprocessorItem(
                    new Item.Properties(), new dev.jsc.jscomputronics.common.hardware.PhiCoprocessorSpec(
                            IndustrialTier.T3, 2, 60, 1050, 225)));

    public static final DeferredItem<dev.jsc.jscomputronics.module.computing.item.PhiCoprocessorItem> PHI_7120 =
            ITEMS.register("phi_7120", () -> new dev.jsc.jscomputronics.module.computing.item.PhiCoprocessorItem(
                    new Item.Properties(), new dev.jsc.jscomputronics.common.hardware.PhiCoprocessorSpec(
                            IndustrialTier.T4, 3, 61, 1240, 250)));

    public static final DeferredItem<dev.jsc.jscomputronics.module.computing.item.PhiCoprocessorItem> PHI_7290 =
            ITEMS.register("phi_7290", () -> new dev.jsc.jscomputronics.module.computing.item.PhiCoprocessorItem(
                    new Item.Properties(), new dev.jsc.jscomputronics.common.hardware.PhiCoprocessorSpec(
                            IndustrialTier.T4, 4, 72, 1500, 270)));

    public static final DeferredItem<dev.jsc.jscomputronics.module.computing.item.PhiCoprocessorItem> PHI_9000 =
            ITEMS.register("phi_9000", () -> new dev.jsc.jscomputronics.module.computing.item.PhiCoprocessorItem(
                    new Item.Properties(), new dev.jsc.jscomputronics.common.hardware.PhiCoprocessorSpec(
                            IndustrialTier.T5, 6, 96, 1800, 300)));

    public static final DeferredHolder<MenuType<?>,
            MenuType<dev.jsc.jscomputronics.module.computing.menu.ServerRouterMenu>> SERVER_ROUTER_MENU =
            MENUS.register("server_router", () -> IMenuTypeExtension.create(
                    dev.jsc.jscomputronics.module.computing.menu.ServerRouterMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>,
            MenuType<dev.jsc.jscomputronics.module.computing.menu.DatacenterStationMenu>> DATACENTER_STATION_MENU =
            MENUS.register("datacenter_station", () -> IMenuTypeExtension.create(
                    dev.jsc.jscomputronics.module.computing.menu.DatacenterStationMenu::fromNetwork));

    public static void register(final IEventBus modEventBus) {
        // Force the per-era hardware catalog to load so its items register onto ITEMS before the
        // DeferredRegister is handed to the mod event bus below.
        HardwareItems.init();
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        COMPONENTS.register(modEventBus);
    }
}
