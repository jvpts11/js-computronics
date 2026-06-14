/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.registry;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.industrial.IndustrialModule;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
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

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, JsComputronics.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> INDUSTRIAL =
            CREATIVE_MODE_TABS.register("industrial", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.jsc.industrial"))
                    .icon(() -> new ItemStack(IndustrialModule.MACERATOR_ITEM.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(IndustrialModule.MACERATOR_ITEM.get());
                        output.accept(IndustrialModule.COAL_GENERATOR_ITEM.get());
                        output.accept(IndustrialModule.IRON_DUST.get());
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
                        output.accept(ComputingModule.DATACENTER_STATION_ITEM.get());
                        output.accept(ComputingModule.MAINFRAME_ITEM.get());
                        output.accept(ComputingModule.MONITOR_ITEM.get());
                        output.accept(ComputingModule.VINTAGE_MONITOR_ITEM.get());
                        output.accept(ComputingModule.LEGACY_MONITOR_ITEM.get());
                        output.accept(ComputingModule.TANK_ITEM.get());
                        output.accept(ComputingModule.PERSONAL_COMPUTER_ITEM.get());
                        output.accept(ComputingModule.VINTAGE_PERSONAL_COMPUTER_ITEM.get());
                        output.accept(ComputingModule.LEGACY_PERSONAL_COMPUTER_ITEM.get());
                        output.accept(ComputingModule.CRAFTING_COMPUTER_ITEM.get());
                        output.accept(ComputingModule.SUPERCOMPUTER_NODE_ITEM.get());
                        output.accept(ComputingModule.HPC_CABLE_ITEM.get());
                        output.accept(ComputingModule.HBW_INTERFACE_ITEM.get());
                        output.accept(ComputingModule.SUPERCOMPUTER_CONSOLE_ITEM.get());
                        output.accept(ComputingModule.PATTERN_ENCODER_ITEM.get());
                        output.accept(ComputingModule.PATTERN_READER_ITEM.get());
                        output.accept(ComputingModule.SERVER_RACK_ITEM.get());
                        output.accept(ComputingModule.IMPORT_BUS_ITEM.get());
                        output.accept(ComputingModule.EXPORT_BUS_ITEM.get());
                        // Server items.
                        output.accept(ComputingModule.SERVER_CASE.get());
                        // An empty Server: the player assembles it by right-clicking.
                        output.accept(new ItemStack(ComputingModule.SERVER.get()));
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
                        output.accept(ComputingModule.PHI_5100.get());
                        output.accept(ComputingModule.PHI_7120.get());
                        output.accept(ComputingModule.PHI_7290.get());
                        output.accept(ComputingModule.PHI_9000.get());
                        output.accept(ComputingModule.PATTERN_DISC.get());
                        output.accept(ComputingModule.PATTERN_DISC_RW.get());
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
