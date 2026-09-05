/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.registry;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jstech.core.material.MaterialForm;
import dev.jstech.core.material.MaterialItems;
import dev.jstech.core.material.ModMaterial;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.os.media.MediaItem;
import dev.jsc.jscomputronics.module.computing.os.media.MediaKind;
import dev.jsc.jscomputronics.module.industrial.IndustrialModule;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Creative-mode tabs for J's Computronics — one tab per logical module, so the catalog stays organized as it grows.
 */
public final class JscCreativeModeTabs {

    private JscCreativeModeTabs() {
    }

    /** The blank medium item of a physical format, to stamp an installer onto. */
    private static net.minecraft.world.item.Item mediumFor(
            final dev.jsc.jscomputronics.module.computing.os.media.MediaFormat format) {
        return switch (format) {
            case FLOPPY -> ComputingModule.FLOPPY_DISK.get();
            case CD -> ComputingModule.CD_ROM.get();
            case DVD -> ComputingModule.DVD_ROM.get();
            case USB -> ComputingModule.USB_FLASH_DRIVE.get();
        };
    }

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, JsComputronics.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> INDUSTRIAL =
            CREATIVE_MODE_TABS.register("industrial", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.jsc.industrial"))
                    .icon(() -> new ItemStack(IndustrialModule.MACERATOR_ITEM.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(IndustrialModule.MACERATOR_ITEM.get());
                        output.accept(IndustrialModule.ELECTRIC_FURNACE_ITEM.get());
                        output.accept(IndustrialModule.COMPRESSOR_ITEM.get());
                        output.accept(IndustrialModule.COAL_GENERATOR_ITEM.get());
                        output.accept(MaterialItems.get(ModMaterial.IRON, MaterialForm.DUST).get());
                        output.accept(MaterialItems.get(ModMaterial.IRON, MaterialForm.PLATE).get());
                        output.accept(MaterialItems.get(ModMaterial.COPPER, MaterialForm.PLATE).get());
                    })
                    .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> COMPUTING =
            CREATIVE_MODE_TABS.register("computing", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.jsc.computing"))
                    .icon(() -> new ItemStack(ComputingModule.MAINFRAME_ITEM.get()))
                    .displayItems((parameters, output) -> {
                        // Blocks & network infrastructure.
                        output.accept(ComputingModule.ETHERNET_CABLE_ITEM.get());
                        output.accept(ComputingModule.HBW_CABLE_ITEM.get());
                        output.accept(ComputingModule.PERIPHERAL_CABLE_ITEM.get());
                        output.accept(ComputingModule.PERSONAL_ROUTER_ITEM.get());
                        output.accept(ComputingModule.SERVER_ROUTER_ITEM.get());
                        output.accept(ComputingModule.MAINFRAME_ITEM.get());
                        output.accept(ComputingModule.VINTAGE_MAINFRAME_ITEM.get());
                        output.accept(ComputingModule.LEGACY_MAINFRAME_ITEM.get());
                        output.accept(ComputingModule.MONITOR_ITEM.get());
                        output.accept(ComputingModule.VINTAGE_MONITOR_ITEM.get());
                        output.accept(ComputingModule.LEGACY_MONITOR_ITEM.get());
                        output.accept(ComputingModule.TANK_ITEM.get());
                        output.accept(ComputingModule.PERSONAL_COMPUTER_ITEM.get());
                        output.accept(ComputingModule.VINTAGE_PERSONAL_COMPUTER_ITEM.get());
                        output.accept(ComputingModule.LEGACY_PERSONAL_COMPUTER_ITEM.get());
                        output.accept(ComputingModule.CRAFTING_COMPUTER_ITEM.get());
                        output.accept(ComputingModule.VINTAGE_CRAFTING_COMPUTER_ITEM.get());
                        output.accept(ComputingModule.LEGACY_CRAFTING_COMPUTER_ITEM.get());
                        output.accept(ComputingModule.CLUSTER_MANAGEMENT_COMPUTER_ITEM.get());
                        output.accept(ComputingModule.VINTAGE_CLUSTER_MANAGEMENT_COMPUTER_ITEM.get());
                        output.accept(ComputingModule.LEGACY_CLUSTER_MANAGEMENT_COMPUTER_ITEM.get());
                        output.accept(new ItemStack(ComputingModule.SUPERCOMPUTER_NODE.get()));
                        output.accept(ComputingModule.HPC_CABLE_ITEM.get());
                        output.accept(ComputingModule.CRAFTING_CABLE_ITEM.get());
                        output.accept(ComputingModule.CRAFTING_SWITCH_ITEM.get());
                        output.accept(ComputingModule.HBW_INTERFACE_ITEM.get());
                        output.accept(ComputingModule.PATTERN_ENCODER_ITEM.get());
                        output.accept(ComputingModule.LEGACY_PATTERN_ENCODER_ITEM.get());
                        output.accept(ComputingModule.VINTAGE_PATTERN_ENCODER_ITEM.get());
                        output.accept(ComputingModule.FLOPPY_DRIVE_ITEM.get());
                        output.accept(ComputingModule.CD_DRIVE_ITEM.get());
                        output.accept(ComputingModule.DVD_DRIVE_ITEM.get());
                        output.accept(ComputingModule.DOCK_STATION_ITEM.get());
                        // Blank typed media (one item per physical format).
                        output.accept(ComputingModule.FLOPPY_DISK.get());
                        output.accept(ComputingModule.CD_ROM.get());
                        output.accept(ComputingModule.CD_RW.get());
                        output.accept(ComputingModule.DVD_ROM.get());
                        output.accept(ComputingModule.DVD_RW.get());
                        output.accept(ComputingModule.USB_FLASH_DRIVE.get());
                        // Pre-stamped OS installers, one per registered OS, straight from the single registry.
                        // The medium follows the software's era through one rule (InstallMedia): Vintage on
                        // a floppy, Legacy on a CD, a Standard system on a bootable flash drive.
                        for (final dev.jsc.jscomputronics.module.computing.os.OsDef os
                                : dev.jsc.jscomputronics.module.computing.os.OsBootstrap.builtinOses()) {
                            final ItemStack disc = new ItemStack(mediumFor(
                                    dev.jsc.jscomputronics.module.computing.os.media.InstallMedia.forSystem(os.minEra())));
                            MediaItem.setKind(disc, MediaKind.OS_INSTALL);
                            MediaItem.setPayload(disc, os.id());
                            output.accept(disc);
                        }
                        // Program installers, one per installable program, on the medium of the generation
                        // the program was written in: a Standard application on a DVD, a Standard service on
                        // a flash drive. Size never decides, so a small server daemon is no longer a floppy.
                        for (final dev.jsc.jscomputronics.module.computing.os.ProgramSpec program
                                : dev.jsc.jscomputronics.module.computing.os.OsBootstrap.builtinPrograms()) {
                            if (!program.installable()) {
                                continue;
                            }
                            final ItemStack disc = new ItemStack(mediumFor(
                                    dev.jsc.jscomputronics.module.computing.os.media.InstallMedia.forProgram(
                                            program.era(), program.kind())));
                            MediaItem.setKind(disc, MediaKind.PROGRAM_INSTALL);
                            MediaItem.setPayload(disc, program.id());
                            output.accept(disc);
                        }
                        output.accept(ComputingModule.VINTAGE_SERVER_RACK_ITEM.get());
                        output.accept(ComputingModule.LEGACY_SERVER_RACK_ITEM.get());
                        output.accept(ComputingModule.SERVER_RACK_ITEM.get());
                        output.accept(ComputingModule.SUPERCOMPUTER_RACK_ITEM.get());
                        output.accept(ComputingModule.IMPORT_BUS_ITEM.get());
                        output.accept(ComputingModule.EXPORT_BUS_ITEM.get());
                        output.accept(ComputingModule.INPUT_BUS_ITEM.get());
                        output.accept(ComputingModule.RECEIVING_BUS_ITEM.get());
                        // Server items.
                        output.accept(ComputingModule.VINTAGE_SERVER_CASE.get());
                        output.accept(new ItemStack(ComputingModule.VINTAGE_SERVER.get()));
                        output.accept(ComputingModule.LEGACY_SERVER_CASE.get());
                        output.accept(new ItemStack(ComputingModule.LEGACY_SERVER.get()));
                        output.accept(ComputingModule.SERVER_CASE.get());
                        // An empty Server: the player assembles it by right-clicking.
                        output.accept(new ItemStack(ComputingModule.SERVER.get()));
                        output.accept(ComputingModule.STORAGE_SERVER_CASE.get());
                        output.accept(new ItemStack(ComputingModule.STORAGE_SERVER.get()));
                        output.accept(ComputingModule.COMPUTE_SERVER_CASE.get());
                        output.accept(new ItemStack(ComputingModule.COMPUTE_SERVER.get()));
                        // Bay gadgets for the rack's front-panel gadget slots.
                        output.accept(ComputingModule.RAID_CONTROLLER.get());
                        output.accept(ComputingModule.CACHE_CARD.get());
                        // Rack units: equipment that shares the cabinet's rack-unit budget.
                        output.accept(ComputingModule.KVM_SWITCH.get());
                        output.accept(ComputingModule.RACK_UPS.get());
                        output.accept(ComputingModule.COOLING_UNIT.get());
                        // Hardware components.
                        output.accept(ComputingModule.MOTHERBOARD_MTX_P.get());
                        output.accept(ComputingModule.MOTHERBOARD_ATX_P.get());
                        output.accept(ComputingModule.MOTHERBOARD_EEB_P.get());
                        output.accept(ComputingModule.CPU_SERVO_2620.get());
                        output.accept(ComputingModule.CPU_SERVO_2690.get());
                        output.accept(ComputingModule.CPU_SERVO_2699.get());
                        output.accept(ComputingModule.CPU_ASCENT_965.get());
                        output.accept(ComputingModule.RAM_DDR3_8192.get());
                        output.accept(ComputingModule.GPU_HD_7970.get());
                        output.accept(ComputingModule.CRAFTING_CARD_T2.get());
                        output.accept(ComputingModule.CRAFTING_CARD_T3.get());
                        output.accept(ComputingModule.SERIAL_CONSOLE_CARD.get());
                        output.accept(ComputingModule.MANAGEMENT_NIC.get());
                        output.accept(ComputingModule.FABRIC_HOST_ADAPTER.get());
                        output.accept(ComputingModule.PHI_5100.get());
                        output.accept(ComputingModule.PHI_7120.get());
                        output.accept(ComputingModule.PHI_7290.get());
                        output.accept(ComputingModule.PHI_9000.get());
                        output.accept(ComputingModule.PSU_650G.get());
                        // Disks (every tier × size).
                        for (final ComputingModule.DiskEntry disk : ComputingModule.DISKS) {
                            output.accept(disk.item().get());
                        }
                        // Per-era hardware catalog, ordered Vintage to Singularity so the progression
                        // reads cleanly in the tab.
                        for (final net.minecraft.world.item.Item hardware
                                : dev.jsc.jscomputronics.module.computing.HardwareItems.creativeOrder()) {
                            output.accept(hardware);
                        }
                    })
                    .build());

    public static void register(final IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
