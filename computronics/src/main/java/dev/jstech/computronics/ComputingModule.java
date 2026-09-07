/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics;

import dev.jstech.computronics.JsComputronics;
import dev.jstech.computronics.block.CraftingComputerBlock;
import dev.jstech.computronics.block.DataCableBlock;
import dev.jstech.computronics.block.LegacyMonitorBlock;
import dev.jstech.computronics.block.MainframeBlock;
import dev.jstech.computronics.block.MainframePartBlock;
import dev.jstech.computronics.block.PersonalComputerBlock;
import dev.jstech.computronics.block.PersonalRouterBlock;
import dev.jstech.computronics.block.VintageMonitorBlock;
import dev.jstech.computronics.blockentity.CraftingComputerBlockEntity;
import dev.jstech.computronics.blockentity.DataCableBlockEntity;
import dev.jstech.computronics.blockentity.MainframeBlockEntity;
import dev.jstech.computronics.blockentity.MainframePartBlockEntity;
import dev.jstech.computronics.blockentity.PersonalComputerBlockEntity;
import dev.jstech.computronics.blockentity.PersonalRouterBlockEntity;
import dev.jstech.computronics.blockentity.ServerRackBlockEntity;
import dev.jstech.computronics.hardware.CpuSocket;
import dev.jstech.computronics.hardware.CpuSpec;
import dev.jstech.computronics.hardware.CraftingCardSpec;
import dev.jstech.computronics.hardware.DiskSize;
import dev.jstech.computronics.hardware.DiskSpec;
import dev.jstech.computronics.hardware.GpuSpec;
import dev.jstech.computronics.hardware.MotherboardSpec;
import dev.jstech.computronics.hardware.PcieGeneration;
import dev.jstech.computronics.hardware.PsuSpec;
import dev.jstech.computronics.hardware.RamGeneration;
import dev.jstech.computronics.hardware.RamSpec;
import dev.jstech.computronics.hardware.StorageTier;
import dev.jstech.computronics.item.CpuItem;
import dev.jstech.computronics.item.CraftingCardItem;
import dev.jstech.computronics.item.DiskItem;
import dev.jstech.computronics.item.GpuItem;
import dev.jstech.computronics.item.MotherboardItem;
import dev.jstech.computronics.item.PsuItem;
import dev.jstech.computronics.item.RamItem;
import dev.jstech.computronics.menu.MainframeMenu;
import dev.jstech.computronics.menu.PersonalComputerMenu;
import dev.jstech.computronics.os.media.FormattedMediaItem;
import dev.jstech.computronics.os.media.MediaDriveType;
import dev.jstech.computronics.os.media.MediaFormat;
import dev.jstech.computronics.os.media.MediaItem;
import dev.jstech.computronics.os.media.MediaKind;
import dev.jstech.computronics.os.media.MediaReaderBlock;
import dev.jstech.computronics.os.media.MediaReaderBlockEntity;
import dev.jstech.core.network.DataTier;
import dev.jstech.core.tier.HardwareEra;
import dev.jstech.core.tier.IndustrialTier;
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

    /** Advancement criteria triggers: booting a computer into an OS (the Arch / Gentoo challenges use it). */
    public static final DeferredRegister<net.minecraft.advancements.CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.TRIGGER_TYPE, JsComputronics.MODID);
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.advancements.CriterionTrigger<?>,
            dev.jstech.computronics.advancement.OsFirstBootTrigger> OS_FIRST_BOOT =
            TRIGGERS.register("os_first_boot",
                    dev.jstech.computronics.advancement.OsFirstBootTrigger::new);

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, JsComputronics.MODID);

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(JsComputronics.MODID);

    /*
     * Data components: a Server item carries its state in its NBT: the items it
     * stores, the hardware it is built from, and its network node identity.
     */
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<
                    dev.jstech.computronics.storage.ServerStorageContents>>
            SERVER_STORAGE = COMPONENTS.registerComponentType("server_storage", b -> b
                    .persistent(dev.jstech.computronics.storage.ServerStorageContents.CODEC)
                    .networkSynchronized(
                            dev.jstech.computronics.storage.ServerStorageContents.STREAM_CODEC));

    /*
     * A drive's stored items live in the save-wide volume store, not on the item: the item carries the
     * volume's id and a usage summary, so a drive holding thousands of types stays a tiny item.
     */
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<java.util.UUID>>
            DISK_VOLUME = COMPONENTS.registerComponentType("disk_volume", b -> b
                    .persistent(net.minecraft.core.UUIDUtil.CODEC)
                    .networkSynchronized(net.minecraft.core.UUIDUtil.STREAM_CODEC));

    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<
                    dev.jstech.computronics.storage.DiskUsage>>
            DISK_USAGE = COMPONENTS.registerComponentType("disk_usage", b -> b
                    .persistent(dev.jstech.computronics.storage.DiskUsage.CODEC)
                    .networkSynchronized(dev.jstech.computronics.storage.DiskUsage.STREAM_CODEC));

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

    /*
     * A RAID Controller carries its array configuration: the mode it runs and how many member
     * drives the array was formed with (so a missing member reads as degraded rather than smaller).
     */
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<String>>
            RAID_MODE = COMPONENTS.registerComponentType("raid_mode", b -> b
                    .persistent(com.mojang.serialization.Codec.STRING)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8));

    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<Integer>>
            RAID_MEMBERS = COMPONENTS.registerComponentType("raid_members", b -> b
                    .persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    /*
     * A rack server's software state (console history, installed programs, settings) persists WITH
     * the item, so it moves between racks with the machine. Server-side only: never network-synced.
     */
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<net.minecraft.nbt.CompoundTag>>
            SERVER_CONSOLE = COMPONENTS.registerComponentType("server_console", b -> b
                    .persistent(net.minecraft.nbt.CompoundTag.CODEC));

    /*
     * The software a disk carries: installed programs and their versions, the desktop preferences, and
     * the shell history. It rides on the DISK, not on the computer, because that is what it is: moving
     * a system disk to another machine takes its programs along, and a fresh disk boots clean. Keeping
     * this on the block entity meant a newly installed system still believed the old one's programs
     * were present. Server-side only: never network-synced.
     */
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<net.minecraft.nbt.CompoundTag>>
            DISK_CONSOLE = COMPONENTS.registerComponentType("disk_console", b -> b
                    .persistent(net.minecraft.nbt.CompoundTag.CODEC));

    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<String>>
            COMPUTER_NAME = COMPONENTS.registerComponentType("computer_name", b -> b
                    .persistent(com.mojang.serialization.Codec.STRING)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8));

    /*
     * How much of a (non-Server) computer disk's storage is public, as a per-mille 0..1000. The
     * component rides on the disk ItemStack so the split travels with the disk when it is pulled
     * and reinserted. An absent component reads as fully private (see DiskItem.publicPermille).
     */
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<Integer>>
            DISK_PUBLIC_PERMILLE = COMPONENTS.registerComponentType("disk_public_permille", b -> b
                    .persistent(com.mojang.serialization.Codec.intRange(0, 1000))
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    /*
     * OS media subsystem: components that together describe the content of a MediaItem.
     * A medium carries exactly one kind and the matching content component for that kind.
     */

    // Installer payload (OS_INSTALL / PROGRAM_INSTALL): the OS or program id.
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<net.minecraft.resources.ResourceLocation>>
            MEDIA_PAYLOAD = COMPONENTS.registerComponentType("media_payload", b -> b
                    .persistent(net.minecraft.resources.ResourceLocation.CODEC)
                    .networkSynchronized(net.minecraft.resources.ResourceLocation.STREAM_CODEC));

    /*
     * Which of the three content kinds this medium carries. Absent component → OS_INSTALL (safe
     * default that keeps legacy blank media behaving as installer media).
     */
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
                    dev.jstech.computronics.storage.ServerStorageContents>>
            MEDIA_DATA = COMPONENTS.registerComponentType("media_data", b -> b
                    .persistent(dev.jstech.computronics.storage.ServerStorageContents.CODEC)
                    .networkSynchronized(
                            dev.jstech.computronics.storage.ServerStorageContents.STREAM_CODEC));

    // Capacity of a DATA medium in item-equivalents. Absent → MediaItem.DEFAULT_CAPACITY.
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<Integer>>
            MEDIA_CAPACITY = COMPONENTS.registerComponentType("media_capacity", b -> b
                    .persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    /*
     * Disk filesystem components: files and the installed OS live on the DiskItem stack so
     * they travel with the disk when it is inserted or removed.
     */

    // The filesystem contents of a disk volume: path-keyed map of stored files.
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<
                    dev.jstech.computronics.os.fs.FilesystemContents>>
            FILESYSTEM = COMPONENTS.registerComponentType("filesystem", b -> b
                    .persistent(dev.jstech.computronics.os.fs.FilesystemContents.CODEC)
                    .networkSynchronized(
                            dev.jstech.computronics.os.fs.FilesystemContents.STREAM_CODEC));

    /*
     * The OS installed on a system disk: a ResourceLocation identifying the registered OsDef.
     * Present only on bootable disks; absent on plain data disks.
     */
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<net.minecraft.resources.ResourceLocation>>
            SYSTEM_OS = COMPONENTS.registerComponentType("system_os", b -> b
                    .persistent(net.minecraft.resources.ResourceLocation.CODEC)
                    .networkSynchronized(net.minecraft.resources.ResourceLocation.STREAM_CODEC));

    /*
     * A user-chosen label for a disk or media volume, shown in This PC and the explorer drive tree and
     * editable there. Rides on the ItemStack so it travels with the disk/medium. Absent → the volume's
     * default name (e.g. "Local Disk" for a system disk, "Removable Drive" for a medium).
     */
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
    public static final DeferredBlock<dev.jstech.computronics.block.CraftingSwitchBlock> CRAFTING_SWITCH =
            BLOCKS.register("crafting_switch",
                    () -> new dev.jstech.computronics.block.CraftingSwitchBlock(
                            BlockBehaviour.Properties.of().strength(1.5F)));

    public static final DeferredItem<BlockItem> CRAFTING_SWITCH_ITEM = ITEMS.register(
            "crafting_switch", () -> new BlockItem(CRAFTING_SWITCH.get(), new Item.Properties()));

    public static final DeferredBlock<dev.jstech.computronics.block.PeripheralCableBlock> PERIPHERAL_CABLE =
            BLOCKS.register("peripheral_cable",
                    () -> new dev.jstech.computronics.block.PeripheralCableBlock(cableProperties()));

    public static final DeferredItem<BlockItem> PERIPHERAL_CABLE_ITEM = ITEMS.register(
            "peripheral_cable", () -> new BlockItem(PERIPHERAL_CABLE.get(), new Item.Properties()));

    public static final DeferredBlock<dev.jstech.computronics.block.MonitorBlock> MONITOR =
            BLOCKS.register("monitor", () -> new dev.jstech.computronics.block.MonitorBlock(
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
            BlockEntityType<dev.jstech.computronics.blockentity.CraftingSwitchBlockEntity>>
            CRAFTING_SWITCH_BE = BLOCK_ENTITIES.register("crafting_switch",
                    () -> BlockEntityType.Builder.of(
                            dev.jstech.computronics.blockentity.CraftingSwitchBlockEntity::new,
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

    public static final DeferredBlock<dev.jstech.computronics.block.ServerRouterBlock> SERVER_ROUTER =
            BLOCKS.register("server_router",
                    () -> new dev.jstech.computronics.block.ServerRouterBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.COLOR_GRAY)
                                    .strength(0.6F)
                                    .sound(SoundType.METAL)
                                    .noOcclusion()));

    public static final DeferredItem<BlockItem> SERVER_ROUTER_ITEM = ITEMS.register(
            "server_router", () -> new BlockItem(SERVER_ROUTER.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<dev.jstech.computronics.blockentity.ServerRouterBlockEntity>> SERVER_ROUTER_BE =
            BLOCK_ENTITIES.register("server_router",
                    () -> BlockEntityType.Builder.of(
                            dev.jstech.computronics.blockentity.ServerRouterBlockEntity::new,
                            SERVER_ROUTER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<dev.jstech.computronics.blockentity.MonitorBlockEntity>> MONITOR_BE =
            BLOCK_ENTITIES.register("monitor",
                    () -> BlockEntityType.Builder.of(
                            dev.jstech.computronics.blockentity.MonitorBlockEntity::new,
                            MONITOR.get(), VINTAGE_MONITOR.get(), LEGACY_MONITOR.get()).build(null));

    public static final DeferredBlock<dev.jstech.computronics.block.TankBlock> TANK =
            BLOCKS.register("tank", () -> new dev.jstech.computronics.block.TankBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_LIGHT_BLUE)
                            .strength(0.6F)
                            .sound(SoundType.GLASS)
                            .noOcclusion()));

    public static final DeferredItem<BlockItem> TANK_ITEM = ITEMS.register(
            "tank", () -> new BlockItem(TANK.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<dev.jstech.computronics.blockentity.TankBlockEntity>> TANK_BE =
            BLOCK_ENTITIES.register("tank",
                    () -> BlockEntityType.Builder.of(
                            dev.jstech.computronics.blockentity.TankBlockEntity::new,
                            TANK.get()).build(null));

    // Interaction buses: move items between the network and adjacent inventories

    public static final DeferredItem<dev.jstech.computronics.block.part.CablePartItem> IMPORT_BUS_ITEM =
            ITEMS.register("import_bus", () -> new dev.jstech.computronics.block.part.CablePartItem(
                    new Item.Properties(),
                    dev.jstech.computronics.block.part.CablePartType.IMPORT));

    public static final DeferredItem<dev.jstech.computronics.block.part.CablePartItem> EXPORT_BUS_ITEM =
            ITEMS.register("export_bus", () -> new dev.jstech.computronics.block.part.CablePartItem(
                    new Item.Properties(),
                    dev.jstech.computronics.block.part.CablePartType.EXPORT));

    public static final DeferredItem<dev.jstech.computronics.block.part.CablePartItem> INPUT_BUS_ITEM =
            ITEMS.register("input_bus", () -> new dev.jstech.computronics.block.part.CablePartItem(
                    new Item.Properties(),
                    dev.jstech.computronics.block.part.CablePartType.INPUT));

    public static final DeferredItem<dev.jstech.computronics.block.part.CablePartItem> RECEIVING_BUS_ITEM =
            ITEMS.register("receiving_bus", () -> new dev.jstech.computronics.block.part.CablePartItem(
                    new Item.Properties(),
                    dev.jstech.computronics.block.part.CablePartType.RECEIVING));

    public static final DeferredHolder<MenuType<?>,
            MenuType<dev.jstech.computronics.menu.ExportBusMenu>> EXPORT_BUS_MENU =
            MENUS.register("export_bus", () -> IMenuTypeExtension.create(
                    dev.jstech.computronics.menu.ExportBusMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>,
            MenuType<dev.jstech.computronics.menu.ImportBusMenu>> IMPORT_BUS_MENU =
            MENUS.register("import_bus", () -> IMenuTypeExtension.create(
                    dev.jstech.computronics.menu.ImportBusMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>,
            MenuType<dev.jstech.computronics.menu.CraftingSwitchMenu>> CRAFTING_SWITCH_MENU =
            MENUS.register("crafting_switch", () -> IMenuTypeExtension.create(
                    dev.jstech.computronics.menu.CraftingSwitchMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>,
            MenuType<dev.jstech.computronics.menu.InputBusMenu>> INPUT_BUS_MENU =
            MENUS.register("input_bus", () -> IMenuTypeExtension.create(
                    dev.jstech.computronics.menu.InputBusMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>,
            MenuType<dev.jstech.computronics.menu.ReceivingBusMenu>> RECEIVING_BUS_MENU =
            MENUS.register("receiving_bus", () -> IMenuTypeExtension.create(
                    dev.jstech.computronics.menu.ReceivingBusMenu::fromNetwork));

    // Server Rack: houses Server items as network nodes

    public static final DeferredBlock<dev.jstech.computronics.block.ServerRackBlock> SERVER_RACK =
            BLOCKS.register("server_rack",
                    () -> new dev.jstech.computronics.block.ServerRackBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .strength(1.5F)
                                    .sound(SoundType.METAL)
                                    .noOcclusion()));

    /**
     * How a rack cabinet sits in an item slot: 3 blocks tall is its longest side, and its model spans
     * x -1.5..0.5, y 0..3, z -0.5..1.5 blocks around the controller, so its middle moves by this much.
     */
    private static final dev.jstech.computronics.item.CabinetBlockItem.Fit RACK_FIT =
            new dev.jstech.computronics.item.CabinetBlockItem.Fit(48.0F, 0.5F, -1.5F, -0.5F);

    public static final DeferredItem<BlockItem> SERVER_RACK_ITEM = ITEMS.register(
            "server_rack", () -> new dev.jstech.computronics.item.CabinetBlockItem(
                    SERVER_RACK.get(), new Item.Properties(), "rack", "server_rack", "rack", RACK_FIT));

    /*
     * The supercomputer cabinet: the same foundation, but it seats only Supercomputer Nodes and its
     * rear port takes only the high-compute fabric.
     */
    public static final DeferredBlock<dev.jstech.computronics.block.SupercomputerRackBlock>
            SUPERCOMPUTER_RACK = BLOCKS.register("supercomputer_rack",
                    () -> new dev.jstech.computronics.block.SupercomputerRackBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .strength(1.5F)
                                    .sound(SoundType.METAL)
                                    .noOcclusion()));

    public static final DeferredItem<BlockItem> SUPERCOMPUTER_RACK_ITEM = ITEMS.register(
            "supercomputer_rack", () -> new dev.jstech.computronics.item.CabinetBlockItem(
                    SUPERCOMPUTER_RACK.get(), new Item.Properties(), "rack", "supercomputer_rack",
                    "rack", RACK_FIT));

    /*
     * The Server Rack of the earlier eras: the same cabinet in its decade's materials, seating only
     * servers of its own era or earlier.
     */
    public static final DeferredBlock<dev.jstech.computronics.block.LegacyServerRackBlock>
            LEGACY_SERVER_RACK = BLOCKS.register("legacy_server_rack",
                    () -> new dev.jstech.computronics.block.LegacyServerRackBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .strength(1.5F)
                                    .sound(SoundType.METAL)
                                    .noOcclusion()));

    public static final DeferredItem<BlockItem> LEGACY_SERVER_RACK_ITEM = ITEMS.register(
            "legacy_server_rack", () -> new dev.jstech.computronics.item.CabinetBlockItem(
                    LEGACY_SERVER_RACK.get(), new Item.Properties(), "rack", "legacy_server_rack",
                    "rack", RACK_FIT));

    public static final DeferredBlock<dev.jstech.computronics.block.VintageServerRackBlock>
            VINTAGE_SERVER_RACK = BLOCKS.register("vintage_server_rack",
                    () -> new dev.jstech.computronics.block.VintageServerRackBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .strength(1.5F)
                                    .sound(SoundType.METAL)
                                    .noOcclusion()));

    public static final DeferredItem<BlockItem> VINTAGE_SERVER_RACK_ITEM = ITEMS.register(
            "vintage_server_rack", () -> new dev.jstech.computronics.item.CabinetBlockItem(
                    VINTAGE_SERVER_RACK.get(), new Item.Properties(), "rack", "vintage_server_rack",
                    "rack", RACK_FIT));

    public static final DeferredBlock<dev.jstech.computronics.block.ServerRackPartBlock> SERVER_RACK_PART =
            BLOCKS.register("server_rack_part",
                    () -> new dev.jstech.computronics.block.ServerRackPartBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .strength(1.5F)
                                    .sound(SoundType.METAL)
                                    .noOcclusion()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ServerRackBlockEntity>> SERVER_RACK_BE =
            BLOCK_ENTITIES.register("server_rack",
                    () -> BlockEntityType.Builder.of(ServerRackBlockEntity::new,
                            SERVER_RACK.get(), SUPERCOMPUTER_RACK.get(), LEGACY_SERVER_RACK.get(),
                            VINTAGE_SERVER_RACK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<dev.jstech.computronics.blockentity.ServerRackPartBlockEntity>> SERVER_RACK_PART_BE =
            BLOCK_ENTITIES.register("server_rack_part",
                    () -> BlockEntityType.Builder.of(
                            dev.jstech.computronics.blockentity.ServerRackPartBlockEntity::new,
                            SERVER_RACK_PART.get()).build(null));

    public static final DeferredHolder<MenuType<?>,
            MenuType<dev.jstech.computronics.menu.ServerRackMenu>> SERVER_RACK_MENU =
            MENUS.register("server_rack", () -> IMenuTypeExtension.create(
                    dev.jstech.computronics.menu.ServerRackMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>,
            MenuType<dev.jstech.computronics.menu.ServerAssemblyMenu>> SERVER_ASSEMBLY_MENU =
            MENUS.register("server_assembly", () -> IMenuTypeExtension.create(
                    dev.jstech.computronics.menu.ServerAssemblyMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>,
            MenuType<dev.jstech.computronics.menu.ComputerTerminalMenu>> COMPUTER_TERMINAL_MENU =
            MENUS.register("computer_terminal", () -> IMenuTypeExtension.create(
                    dev.jstech.computronics.menu.ComputerTerminalMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>,
            MenuType<dev.jstech.computronics.menu.CommandPromptMenu>> COMMAND_PROMPT_MENU =
            MENUS.register("command_prompt", () -> IMenuTypeExtension.create(
                    dev.jstech.computronics.menu.CommandPromptMenu::fromNetwork));

    /*
     * Each terminal platform opens its own screen: MC-DOS and the Linux TTY carry the Command Prompt's
     * data but are separate menu types, so the MC-NET window is never reused for another system's console.
     */
    public static final DeferredHolder<MenuType<?>,
            MenuType<dev.jstech.computronics.menu.DosTerminalMenu>> DOS_TERMINAL_MENU =
            MENUS.register("dos_terminal", () -> IMenuTypeExtension.create(
                    dev.jstech.computronics.menu.DosTerminalMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>,
            MenuType<dev.jstech.computronics.menu.LinuxTtyMenu>> LINUX_TTY_MENU =
            MENUS.register("linux_tty", () -> IMenuTypeExtension.create(
                    dev.jstech.computronics.menu.LinuxTtyMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>,
            MenuType<dev.jstech.computronics.menu.DesktopMenu>> DESKTOP_MENU =
            MENUS.register("desktop", () -> IMenuTypeExtension.create(
                    dev.jstech.computronics.menu.DesktopMenu::fromNetwork));

    // Hardware components (Standard era, minimal set to build a Mainframe)

    public static final DeferredItem<MotherboardItem> MOTHERBOARD_MTX_P = ITEMS.register(
            "motherboard_mtx_p", () -> new MotherboardItem(new Item.Properties(),
                    new MotherboardSpec(dev.jstech.computronics.hardware.FormFactor.MTX,
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

    /*
     * The Cluster Interface Cards: exclusive to the Cluster Management Computer, one per era. Each era
     * reaches further and writes more nodes at once. Numbers are estimates.
     */
    public static final DeferredItem<dev.jstech.computronics.item.ClusterInterfaceCardItem> SERIAL_CONSOLE_CARD =
            ITEMS.register("serial_console_card", () -> new dev.jstech.computronics.item.ClusterInterfaceCardItem(
                    new Item.Properties(), new dev.jstech.computronics.hardware.ClusterInterfaceCardSpec(
                            HardwareEra.VINTAGE, IndustrialTier.T2, PcieGeneration.PCI,
                            dev.jstech.computronics.hardware.ClusterInterfaceCardSpec.Reach.DATACENTERS, 1, 10)));
    public static final DeferredItem<dev.jstech.computronics.item.ClusterInterfaceCardItem> MANAGEMENT_NIC =
            ITEMS.register("management_nic", () -> new dev.jstech.computronics.item.ClusterInterfaceCardItem(
                    new Item.Properties(), new dev.jstech.computronics.hardware.ClusterInterfaceCardSpec(
                            HardwareEra.LEGACY, IndustrialTier.T3, PcieGeneration.PCIE_1_0,
                            dev.jstech.computronics.hardware.ClusterInterfaceCardSpec.Reach.SUPERCOMPUTERS, 2, 20)));
    public static final DeferredItem<dev.jstech.computronics.item.ClusterInterfaceCardItem> FABRIC_HOST_ADAPTER =
            ITEMS.register("fabric_host_adapter", () -> new dev.jstech.computronics.item.ClusterInterfaceCardItem(
                    new Item.Properties(), new dev.jstech.computronics.hardware.ClusterInterfaceCardSpec(
                            HardwareEra.STANDARD, IndustrialTier.T4, PcieGeneration.PCIE_3_0,
                            dev.jstech.computronics.hardware.ClusterInterfaceCardSpec.Reach.ALL, 4, 35)));

    public static final DeferredItem<PsuItem> PSU_650G = ITEMS.register(
            "psu_650g", () -> new PsuItem(new Item.Properties(), new PsuSpec(650, 90)));

    /*
     * Pattern system: the Pattern Encoder burns .craft files onto removable media, one encoder per era:
     * the Standard one writes DVDs, CDs and USB sticks, the Legacy one CDs, the Vintage one floppies.
     */

    public static final DeferredBlock<dev.jstech.computronics.block.PatternEncoderBlock> PATTERN_ENCODER =
            BLOCKS.register("pattern_encoder",
                    () -> new dev.jstech.computronics.block.PatternEncoderBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.COLOR_GRAY)
                                    .strength(1.5F)
                                    .sound(SoundType.METAL)
                                    /*
                                     * The body is drawn by the block entity: without this a full cube would
                                     * block its own light and cull the faces of its neighbours.
                                     */
                                    .noOcclusion(), HardwareEra.STANDARD));

    /**
     * How an encoder body sits in an item slot: the body is one full block (x -0.5..0.5, y 0..1,
     * z -0.5..0.5 around its origin), so only the height needs re-centring.
     */
    private static final dev.jstech.computronics.item.CabinetBlockItem.Fit ENCODER_FIT =
            new dev.jstech.computronics.item.CabinetBlockItem.Fit(16.0F, 0.0F, -0.5F, 0.0F);

    public static final DeferredItem<BlockItem> PATTERN_ENCODER_ITEM = ITEMS.register(
            "pattern_encoder", () -> new dev.jstech.computronics.item.CabinetBlockItem(
                    PATTERN_ENCODER.get(), new Item.Properties(), "pattern_encoder", "pattern_encoder",
                    "pattern_encoder", ENCODER_FIT));

    public static final DeferredBlock<dev.jstech.computronics.block.PatternEncoderBlock> LEGACY_PATTERN_ENCODER =
            BLOCKS.register("legacy_pattern_encoder",
                    () -> new dev.jstech.computronics.block.PatternEncoderBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.COLOR_LIGHT_GRAY)
                                    .strength(1.5F)
                                    .sound(SoundType.METAL)
                                    .noOcclusion(), HardwareEra.LEGACY));

    public static final DeferredItem<BlockItem> LEGACY_PATTERN_ENCODER_ITEM = ITEMS.register(
            "legacy_pattern_encoder", () -> new dev.jstech.computronics.item.CabinetBlockItem(
                    LEGACY_PATTERN_ENCODER.get(), new Item.Properties(), "pattern_encoder", "legacy_pattern_encoder",
                    "pattern_encoder", ENCODER_FIT));

    public static final DeferredBlock<dev.jstech.computronics.block.PatternEncoderBlock> VINTAGE_PATTERN_ENCODER =
            BLOCKS.register("vintage_pattern_encoder",
                    () -> new dev.jstech.computronics.block.PatternEncoderBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.TERRACOTTA_WHITE)
                                    .strength(1.5F)
                                    .sound(SoundType.METAL)
                                    .noOcclusion(), HardwareEra.VINTAGE));

    public static final DeferredItem<BlockItem> VINTAGE_PATTERN_ENCODER_ITEM = ITEMS.register(
            "vintage_pattern_encoder", () -> new dev.jstech.computronics.item.CabinetBlockItem(
                    VINTAGE_PATTERN_ENCODER.get(), new Item.Properties(), "pattern_encoder", "vintage_pattern_encoder",
                    "pattern_encoder", ENCODER_FIT));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<dev.jstech.computronics.blockentity.PatternEncoderBlockEntity>> PATTERN_ENCODER_BE =
            BLOCK_ENTITIES.register("pattern_encoder",
                    () -> BlockEntityType.Builder.of(
                            dev.jstech.computronics.blockentity.PatternEncoderBlockEntity::new,
                            PATTERN_ENCODER.get(), LEGACY_PATTERN_ENCODER.get(), VINTAGE_PATTERN_ENCODER.get()).build(null));

    public static final DeferredHolder<MenuType<?>,
            MenuType<dev.jstech.computronics.menu.PatternEncoderMenu>> PATTERN_ENCODER_MENU =
            MENUS.register("pattern_encoder", () -> IMenuTypeExtension.create(
                    dev.jstech.computronics.menu.PatternEncoderMenu::fromNetwork));

    /*
     * OS media subsystem: a peripheral block that holds one MediaItem and exposes the
     * installer payload or data contents so the firmware boot screen and transfer logic can read it.
     */

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
                    new MotherboardSpec(dev.jstech.computronics.hardware.FormFactor.EEB,
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
                        new DiskSpec(tier, dev.jstech.core.tier.HardwareEra.STANDARD,
                                size.capacityItems(), tier.tdpWatts())));
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

    public static final DeferredItem<dev.jstech.computronics.item.ServerCaseItem> SERVER_CASE =
            ITEMS.register("server_case",
                    () -> new dev.jstech.computronics.item.ServerCaseItem(new Item.Properties()));

    public static final DeferredItem<dev.jstech.computronics.item.ServerItem> SERVER =
            ITEMS.register("server", () -> new dev.jstech.computronics.item.ServerItem(
                    new Item.Properties()));

    /*
     * The servers of the earlier eras: a case of its era takes only boards of that era, and seats in a
     * cabinet of its era or later.
     */
    public static final DeferredItem<dev.jstech.computronics.item.ServerCaseItem> LEGACY_SERVER_CASE =
            ITEMS.register("legacy_server_case",
                    () -> new dev.jstech.computronics.item.ServerCaseItem(new Item.Properties()));

    public static final DeferredItem<dev.jstech.computronics.item.ServerItem> LEGACY_SERVER =
            ITEMS.register("legacy_server", () -> new dev.jstech.computronics.item.ServerItem(
                    new Item.Properties(),
                    dev.jstech.computronics.rack.RackChassis.LEGACY_SERVER));

    public static final DeferredItem<dev.jstech.computronics.item.ServerCaseItem> VINTAGE_SERVER_CASE =
            ITEMS.register("vintage_server_case",
                    () -> new dev.jstech.computronics.item.ServerCaseItem(new Item.Properties()));

    public static final DeferredItem<dev.jstech.computronics.item.ServerItem> VINTAGE_SERVER =
            ITEMS.register("vintage_server", () -> new dev.jstech.computronics.item.ServerItem(
                    new Item.Properties(),
                    dev.jstech.computronics.rack.RackChassis.VINTAGE_SERVER));

    public static final DeferredItem<dev.jstech.computronics.item.ServerCaseItem> STORAGE_SERVER_CASE =
            ITEMS.register("storage_server_case",
                    () -> new dev.jstech.computronics.item.ServerCaseItem(new Item.Properties()));

    public static final DeferredItem<dev.jstech.computronics.item.ServerItem> STORAGE_SERVER =
            ITEMS.register("storage_server", () -> new dev.jstech.computronics.item.ServerItem(
                    new Item.Properties(),
                    dev.jstech.computronics.rack.RackChassis.STORAGE_SERVER));

    // Rack units: equipment that serves the cabinet and spends the same rack-unit budget servers do.
    public static final DeferredItem<dev.jstech.computronics.item.RackUnitItem> KVM_SWITCH =
            ITEMS.register("kvm_switch",
                    () -> new dev.jstech.computronics.item.RackUnitItem(new Item.Properties(),
                            dev.jstech.computronics.item.RackUnitItem.Kind.KVM_SWITCH));

    public static final DeferredItem<dev.jstech.computronics.item.RackUnitItem> RACK_UPS =
            ITEMS.register("rack_ups",
                    () -> new dev.jstech.computronics.item.RackUnitItem(new Item.Properties(),
                            dev.jstech.computronics.item.RackUnitItem.Kind.RACK_UPS));

    public static final DeferredItem<dev.jstech.computronics.item.RackUnitItem> COOLING_UNIT =
            ITEMS.register("cooling_unit",
                    () -> new dev.jstech.computronics.item.RackUnitItem(new Item.Properties(),
                            dev.jstech.computronics.item.RackUnitItem.Kind.COOLING_UNIT));

    /*
     * Bay gadgets: they occupy a gadget slot on the rack's front panel and serve the machine
     * mounted in that row.
     */
    public static final DeferredItem<dev.jstech.computronics.item.RackGadgetItem> RAID_CONTROLLER =
            ITEMS.register("raid_controller",
                    () -> new dev.jstech.computronics.item.RackGadgetItem(new Item.Properties(),
                            dev.jstech.computronics.item.RackGadgetItem.Kind.RAID_CONTROLLER));

    public static final DeferredItem<dev.jstech.computronics.item.RackGadgetItem> CACHE_CARD =
            ITEMS.register("cache_card",
                    () -> new dev.jstech.computronics.item.RackGadgetItem(new Item.Properties(),
                            dev.jstech.computronics.item.RackGadgetItem.Kind.CACHE_CARD));

    public static final DeferredItem<dev.jstech.computronics.item.ServerCaseItem> COMPUTE_SERVER_CASE =
            ITEMS.register("compute_server_case",
                    () -> new dev.jstech.computronics.item.ServerCaseItem(new Item.Properties()));

    public static final DeferredItem<dev.jstech.computronics.item.ServerItem> COMPUTE_SERVER =
            ITEMS.register("compute_server", () -> new dev.jstech.computronics.item.ServerItem(
                    new Item.Properties(),
                    dev.jstech.computronics.rack.RackChassis.COMPUTE_SERVER));

    public static net.minecraft.world.item.ItemStack defaultServer() {
        final net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(SERVER.get());
        final net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> hardware =
                net.minecraft.core.NonNullList.withSize(
                        dev.jstech.computronics.item.ServerHardwareHandler.SLOTS,
                        net.minecraft.world.item.ItemStack.EMPTY);
        hardware.set(dev.jstech.computronics.item.ServerHardwareHandler.MOBO,
                new net.minecraft.world.item.ItemStack(MOTHERBOARD_EEB_P.get()));
        hardware.set(dev.jstech.computronics.item.ServerHardwareHandler.CPU_START,
                new net.minecraft.world.item.ItemStack(CPU_SERVO_2620.get()));
        hardware.set(dev.jstech.computronics.item.ServerHardwareHandler.RAM_START,
                new net.minecraft.world.item.ItemStack(RAM_DDR3_8192.get()));
        hardware.set(dev.jstech.computronics.item.ServerHardwareHandler.PSU,
                new net.minecraft.world.item.ItemStack(PSU_650G.get()));
        stack.set(SERVER_HARDWARE.get(),
                net.minecraft.world.item.component.ItemContainerContents.fromItems(hardware));
        return stack;
    }

    /** A 2U storage server ready to run: the same board, CPU, RAM and supply as the default server. */
    public static net.minecraft.world.item.ItemStack defaultStorageServer() {
        final net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(STORAGE_SERVER.get());
        final net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> hardware =
                net.minecraft.core.NonNullList.withSize(
                        dev.jstech.computronics.item.ServerHardwareHandler.SLOTS,
                        net.minecraft.world.item.ItemStack.EMPTY);
        hardware.set(dev.jstech.computronics.item.ServerHardwareHandler.MOBO,
                new net.minecraft.world.item.ItemStack(MOTHERBOARD_EEB_P.get()));
        hardware.set(dev.jstech.computronics.item.ServerHardwareHandler.CPU_START,
                new net.minecraft.world.item.ItemStack(CPU_SERVO_2620.get()));
        hardware.set(dev.jstech.computronics.item.ServerHardwareHandler.RAM_START,
                new net.minecraft.world.item.ItemStack(RAM_DDR3_8192.get()));
        hardware.set(dev.jstech.computronics.item.ServerHardwareHandler.PSU,
                new net.minecraft.world.item.ItemStack(PSU_650G.get()));
        stack.set(SERVER_HARDWARE.get(),
                net.minecraft.world.item.component.ItemContainerContents.fromItems(hardware));
        return stack;
    }

    public static net.minecraft.world.item.ItemStack cpulessServer() {
        final net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(SERVER.get());
        final net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> hardware =
                net.minecraft.core.NonNullList.withSize(
                        dev.jstech.computronics.item.ServerHardwareHandler.SLOTS,
                        net.minecraft.world.item.ItemStack.EMPTY);
        hardware.set(dev.jstech.computronics.item.ServerHardwareHandler.MOBO,
                new net.minecraft.world.item.ItemStack(MOTHERBOARD_EEB_P.get()));
        hardware.set(dev.jstech.computronics.item.ServerHardwareHandler.RAM_START,
                new net.minecraft.world.item.ItemStack(RAM_DDR3_8192.get()));
        hardware.set(dev.jstech.computronics.item.ServerHardwareHandler.PSU,
                new net.minecraft.world.item.ItemStack(PSU_650G.get()));
        stack.set(SERVER_HARDWARE.get(),
                net.minecraft.world.item.component.ItemContainerContents.fromItems(hardware));
        return stack;
    }

    public static final DeferredItem<MotherboardItem> MOTHERBOARD_ATX_P = ITEMS.register(
            "motherboard_atx_p", () -> new MotherboardItem(new Item.Properties(),
                    new MotherboardSpec(dev.jstech.computronics.hardware.FormFactor.ATX,
                            HardwareEra.STANDARD, CpuSocket.AM3, 1,
                            Set.of(RamGeneration.DDR3), 4, PcieGeneration.PCIE_3_0, 4, 2, 4)));

    public static final DeferredItem<CpuItem> CPU_ASCENT_965 = ITEMS.register(
            "cpu_ascent_965", () -> new CpuItem(new Item.Properties(),
                    new CpuSpec(HardwareEra.STANDARD, CpuSocket.AM3, 4, 3400, 125, false)));

    // Mainframe

    private static BlockBehaviour.Properties mainframeProperties() {
        /*
         * The cabinet is one GeckoLib model drawn by the controller, so the twelve blocks render
         * nothing themselves: without noOcclusion they would still cull their neighbours' faces and
         * block light, leaving a machine-shaped hole in the world around the model.
         */
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .strength(3.5F)
                .sound(SoundType.METAL)
                .noOcclusion()
                .requiresCorrectToolForDrops();
    }

    public static final DeferredBlock<MainframeBlock> MAINFRAME = BLOCKS.register(
            "mainframe", () -> new MainframeBlock(mainframeProperties()));

    public static final DeferredItem<BlockItem> MAINFRAME_ITEM = ITEMS.register(
            "mainframe", () -> new dev.jstech.computronics.item.MainframeBlockItem(
                    MAINFRAME.get(), new Item.Properties(), "mainframe"));

    /*
     * Earlier-era Mainframes: the same orchestrator and block entity, differing only by era, accepted
     * MTX board and skin. Same 3x2x2 multiblock geometry and shared parts.
     */
    public static final DeferredBlock<dev.jstech.computronics.block.VintageMainframeBlock>
            VINTAGE_MAINFRAME = BLOCKS.register("vintage_mainframe",
                    () -> new dev.jstech.computronics.block.VintageMainframeBlock(mainframeProperties()));

    public static final DeferredItem<BlockItem> VINTAGE_MAINFRAME_ITEM = ITEMS.register(
            "vintage_mainframe", () -> new dev.jstech.computronics.item.MainframeBlockItem(
                    VINTAGE_MAINFRAME.get(), new Item.Properties(), "vintage_mainframe"));

    public static final DeferredBlock<dev.jstech.computronics.block.LegacyMainframeBlock>
            LEGACY_MAINFRAME = BLOCKS.register("legacy_mainframe",
                    () -> new dev.jstech.computronics.block.LegacyMainframeBlock(mainframeProperties()));

    public static final DeferredItem<BlockItem> LEGACY_MAINFRAME_ITEM = ITEMS.register(
            "legacy_mainframe", () -> new dev.jstech.computronics.item.MainframeBlockItem(
                    LEGACY_MAINFRAME.get(), new Item.Properties(), "legacy_mainframe"));

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

    /*
     * Earlier-era Personal Computers: the same machine and block entity, differing only by era, accepted
     * board and skin.
     */
    public static final DeferredBlock<dev.jstech.computronics.block.VintagePersonalComputerBlock>
            VINTAGE_PERSONAL_COMPUTER = BLOCKS.register("vintage_personal_computer",
                    () -> new dev.jstech.computronics.block.VintagePersonalComputerBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.COLOR_GRAY)
                                    .strength(2.0F)));

    public static final DeferredItem<BlockItem> VINTAGE_PERSONAL_COMPUTER_ITEM = ITEMS.register(
            "vintage_personal_computer",
            () -> new BlockItem(VINTAGE_PERSONAL_COMPUTER.get(), new Item.Properties()));

    public static final DeferredBlock<dev.jstech.computronics.block.LegacyPersonalComputerBlock>
            LEGACY_PERSONAL_COMPUTER = BLOCKS.register("legacy_personal_computer",
                    () -> new dev.jstech.computronics.block.LegacyPersonalComputerBlock(
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

    // Crafting Computer: an ATX computer that executes recipes once a Crafting Card is installed

    public static final DeferredBlock<CraftingComputerBlock> CRAFTING_COMPUTER = BLOCKS.register(
            "crafting_computer", () -> new CraftingComputerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(2.0F)));

    public static final DeferredItem<BlockItem> CRAFTING_COMPUTER_ITEM = ITEMS.register(
            "crafting_computer", () -> new BlockItem(CRAFTING_COMPUTER.get(), new Item.Properties()));

    /*
     * Earlier-era Crafting Computers: the same machine and block entity, differing only by era, accepted
     * board and skin.
     */
    public static final DeferredBlock<dev.jstech.computronics.block.VintageCraftingComputerBlock>
            VINTAGE_CRAFTING_COMPUTER = BLOCKS.register("vintage_crafting_computer",
                    () -> new dev.jstech.computronics.block.VintageCraftingComputerBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.COLOR_GRAY)
                                    .strength(2.0F)));

    public static final DeferredItem<BlockItem> VINTAGE_CRAFTING_COMPUTER_ITEM = ITEMS.register(
            "vintage_crafting_computer",
            () -> new BlockItem(VINTAGE_CRAFTING_COMPUTER.get(), new Item.Properties()));

    public static final DeferredBlock<dev.jstech.computronics.block.LegacyCraftingComputerBlock>
            LEGACY_CRAFTING_COMPUTER = BLOCKS.register("legacy_crafting_computer",
                    () -> new dev.jstech.computronics.block.LegacyCraftingComputerBlock(
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
            MenuType<dev.jstech.computronics.menu.CraftingComputerMenu>> CRAFTING_COMPUTER_MENU =
            MENUS.register("crafting_computer", () -> IMenuTypeExtension.create(
                    dev.jstech.computronics.menu.CraftingComputerMenu::fromNetwork));

    /*
     * Cluster Management Computer: a full computer that, with a Cluster Interface Card, drives every
     * supercomputer fabric and datacenter section on its network as one machine. Three eras.
     */
    public static final DeferredBlock<dev.jstech.computronics.block.ClusterManagementComputerBlock>
            CLUSTER_MANAGEMENT_COMPUTER = BLOCKS.register("cluster_management_computer",
                    () -> new dev.jstech.computronics.block.ClusterManagementComputerBlock(
                            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(2.0F)));
    public static final DeferredItem<BlockItem> CLUSTER_MANAGEMENT_COMPUTER_ITEM = ITEMS.register(
            "cluster_management_computer",
            () -> new BlockItem(CLUSTER_MANAGEMENT_COMPUTER.get(), new Item.Properties()));
    public static final DeferredBlock<dev.jstech.computronics.block.VintageClusterManagementComputerBlock>
            VINTAGE_CLUSTER_MANAGEMENT_COMPUTER = BLOCKS.register("vintage_cluster_management_computer",
                    () -> new dev.jstech.computronics.block.VintageClusterManagementComputerBlock(
                            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(2.0F)));
    public static final DeferredItem<BlockItem> VINTAGE_CLUSTER_MANAGEMENT_COMPUTER_ITEM = ITEMS.register(
            "vintage_cluster_management_computer",
            () -> new BlockItem(VINTAGE_CLUSTER_MANAGEMENT_COMPUTER.get(), new Item.Properties()));
    public static final DeferredBlock<dev.jstech.computronics.block.LegacyClusterManagementComputerBlock>
            LEGACY_CLUSTER_MANAGEMENT_COMPUTER = BLOCKS.register("legacy_cluster_management_computer",
                    () -> new dev.jstech.computronics.block.LegacyClusterManagementComputerBlock(
                            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(2.0F)));
    public static final DeferredItem<BlockItem> LEGACY_CLUSTER_MANAGEMENT_COMPUTER_ITEM = ITEMS.register(
            "legacy_cluster_management_computer",
            () -> new BlockItem(LEGACY_CLUSTER_MANAGEMENT_COMPUTER.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<dev.jstech.computronics.blockentity.ClusterManagementComputerBlockEntity>>
            CLUSTER_MANAGEMENT_COMPUTER_BE = BLOCK_ENTITIES.register("cluster_management_computer",
                    () -> BlockEntityType.Builder.of(
                            dev.jstech.computronics.blockentity.ClusterManagementComputerBlockEntity::new,
                            CLUSTER_MANAGEMENT_COMPUTER.get(), VINTAGE_CLUSTER_MANAGEMENT_COMPUTER.get(),
                            LEGACY_CLUSTER_MANAGEMENT_COMPUTER.get()).build(null));
    public static final DeferredHolder<MenuType<?>,
            MenuType<dev.jstech.computronics.menu.ClusterManagementComputerMenu>>
            CLUSTER_MANAGEMENT_COMPUTER_MENU = MENUS.register("cluster_management_computer",
                    () -> IMenuTypeExtension.create(
                            dev.jstech.computronics.menu.ClusterManagementComputerMenu::fromNetwork));

    /*
     * Supercomputer: the nodes are rack computers seated in Supercomputer Racks; the racks are tied
     * together by the high-compute fabric and uplinked to the data network by one HBW Interface.
     */

    public static final DeferredItem<dev.jstech.computronics.item.ServerItem> SUPERCOMPUTER_NODE =
            ITEMS.register("supercomputer_node", () -> new dev.jstech.computronics.item.ServerItem(
                    new Item.Properties(),
                    dev.jstech.computronics.rack.RackChassis.SUPERCOMPUTER_NODE));

    /** A node ready to run: board, CPU, RAM, supply, and the entry crafting co-processor in its slot. */
    public static net.minecraft.world.item.ItemStack defaultSupercomputerNode() {
        final net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(SUPERCOMPUTER_NODE.get());
        final net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> hardware =
                net.minecraft.core.NonNullList.withSize(
                        dev.jstech.computronics.item.ServerHardwareHandler.SLOTS,
                        net.minecraft.world.item.ItemStack.EMPTY);
        hardware.set(dev.jstech.computronics.item.ServerHardwareHandler.MOBO,
                new net.minecraft.world.item.ItemStack(MOTHERBOARD_EEB_P.get()));
        hardware.set(dev.jstech.computronics.item.ServerHardwareHandler.CPU_START,
                new net.minecraft.world.item.ItemStack(CPU_SERVO_2620.get()));
        hardware.set(dev.jstech.computronics.item.ServerHardwareHandler.RAM_START,
                new net.minecraft.world.item.ItemStack(RAM_DDR3_8192.get()));
        hardware.set(dev.jstech.computronics.item.ServerHardwareHandler.GPU_START,
                new net.minecraft.world.item.ItemStack(PHI_5100.get()));
        hardware.set(dev.jstech.computronics.item.ServerHardwareHandler.PSU,
                new net.minecraft.world.item.ItemStack(PSU_650G.get()));
        stack.set(SERVER_HARDWARE.get(),
                net.minecraft.world.item.component.ItemContainerContents.fromItems(hardware));
        return stack;
    }

    public static final DeferredBlock<dev.jstech.computronics.block.HbwInterfaceBlock>
            HBW_INTERFACE = BLOCKS.register("hbw_interface",
                    () -> new dev.jstech.computronics.block.HbwInterfaceBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.COLOR_GRAY)
                                    .strength(2.0F)
                                    .noOcclusion()));

    public static final DeferredItem<BlockItem> HBW_INTERFACE_ITEM = ITEMS.register(
            "hbw_interface", () -> new BlockItem(HBW_INTERFACE.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<dev.jstech.computronics.blockentity.HbwInterfaceBlockEntity>>
            HBW_INTERFACE_BE = BLOCK_ENTITIES.register("hbw_interface",
                    () -> BlockEntityType.Builder.of(
                            dev.jstech.computronics.blockentity.HbwInterfaceBlockEntity::new,
                            HBW_INTERFACE.get()).build(null));

    public static final DeferredItem<dev.jstech.computronics.item.PhiCoprocessorItem> PHI_5100 =
            ITEMS.register("phi_5100", () -> new dev.jstech.computronics.item.PhiCoprocessorItem(
                    new Item.Properties(), new dev.jstech.computronics.hardware.PhiCoprocessorSpec(
                            IndustrialTier.T3, 2, 60, 1050, 225)));

    public static final DeferredItem<dev.jstech.computronics.item.PhiCoprocessorItem> PHI_7120 =
            ITEMS.register("phi_7120", () -> new dev.jstech.computronics.item.PhiCoprocessorItem(
                    new Item.Properties(), new dev.jstech.computronics.hardware.PhiCoprocessorSpec(
                            IndustrialTier.T4, 3, 61, 1240, 250)));

    public static final DeferredItem<dev.jstech.computronics.item.PhiCoprocessorItem> PHI_7290 =
            ITEMS.register("phi_7290", () -> new dev.jstech.computronics.item.PhiCoprocessorItem(
                    new Item.Properties(), new dev.jstech.computronics.hardware.PhiCoprocessorSpec(
                            IndustrialTier.T4, 4, 72, 1500, 270)));

    public static final DeferredItem<dev.jstech.computronics.item.PhiCoprocessorItem> PHI_9000 =
            ITEMS.register("phi_9000", () -> new dev.jstech.computronics.item.PhiCoprocessorItem(
                    new Item.Properties(), new dev.jstech.computronics.hardware.PhiCoprocessorSpec(
                            IndustrialTier.T5, 6, 96, 1800, 300)));

    public static final DeferredHolder<MenuType<?>,
            MenuType<dev.jstech.computronics.menu.ServerRouterMenu>> SERVER_ROUTER_MENU =
            MENUS.register("server_router", () -> IMenuTypeExtension.create(
                    dev.jstech.computronics.menu.ServerRouterMenu::fromNetwork));

    public static void register(final IEventBus modEventBus) {
        /*
         * Force the per-era hardware catalog to load so its items register onto ITEMS before the
         * DeferredRegister is handed to the mod event bus below.
         */
        HardwareItems.init();
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        COMPONENTS.register(modEventBus);
        TRIGGERS.register(modEventBus);
    }
}
