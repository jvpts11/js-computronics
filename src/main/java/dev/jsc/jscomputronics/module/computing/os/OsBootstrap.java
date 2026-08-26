/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.os;

import dev.jsc.jscomputronics.common.tier.HardwareEra;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.Optional;

/**
 * Registers the built-in kernels and operating systems during the common-setup phase.
 *
 * <p>All registrations go through {@link JSComputronicsAPI} so the built-in entries exercise the
 * same public addon path that third-party developers use.
 */
@EventBusSubscriber(modid = "jsc", bus = EventBusSubscriber.Bus.MOD)
public final class OsBootstrap {

    private OsBootstrap() {}

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(OsBootstrap::registerBuiltins);
    }

    // -------------------------------------------------------------------------
    // Built-in registrations
    // -------------------------------------------------------------------------

    static void registerBuiltins() {
        registerKernels();
        registerOses();
        registerPrograms();
    }

    private static void registerKernels() {
        // Single-task / batch DOS kernel with a flat filesystem.
        JSComputronicsAPI.registerKernel(new KernelDef(
                rl("dos"),
                SchedulerKind.NONE,
                FilesystemKind.FLAT
        ));

        // Cooperative multitasking kernel with a hierarchical filesystem (Win9x-like).
        JSComputronicsAPI.registerKernel(new KernelDef(
                rl("win9x"),
                SchedulerKind.COOPERATIVE,
                FilesystemKind.HIERARCHICAL
        ));

        // Preemptive multitasking kernel with a hierarchical filesystem (NT-like; covers WinXP and Win11).
        JSComputronicsAPI.registerKernel(new KernelDef(
                rl("nt"),
                SchedulerKind.PREEMPTIVE,
                FilesystemKind.HIERARCHICAL
        ));

        // Minimal network kernel with no scheduler and no filesystem; used by the Network OS.
        JSComputronicsAPI.registerKernel(new KernelDef(
                rl("net_min"),
                SchedulerKind.NONE,
                FilesystemKind.NONE
        ));
    }

    private static void registerOses() {
        // MC-DOS: terminal-only CLI shell available from the Vintage era.
        JSComputronicsAPI.registerOS(new OsDef(
                rl("mc_dos"),
                OsCapability.TERMINAL_ONLY,
                HardwareEra.VINTAGE,
                rl("dos"),
                4,
                Optional.empty()
        ));

        // MC-NET: full-screen network GUI (the rewrapped network interactor), from the Vintage era.
        // The id stays mc_net for back-compat; the display name is MC-NET (translation os.jsc.mc_net).
        JSComputronicsAPI.registerOS(new OsDef(
                rl("mc_net"),
                OsCapability.NETWORK_GUI,
                HardwareEra.VINTAGE,
                rl("net_min"),
                8,
                Optional.empty()
        ));

        // Panes 95: graphical desktop OS, cooperative Win9x-like kernel, from the Legacy era.
        JSComputronicsAPI.registerOS(new OsDef(
                rl("panes_95"),
                OsCapability.FULL_DESKTOP,
                HardwareEra.LEGACY,
                rl("win9x"),
                32,
                Optional.empty()
        ));

        // Panes XP: graphical desktop OS, preemptive NT kernel, also from the Legacy era.
        JSComputronicsAPI.registerOS(new OsDef(
                rl("panes_xp"),
                OsCapability.FULL_DESKTOP,
                HardwareEra.LEGACY,
                rl("nt"),
                64,
                Optional.empty()
        ));

        // Panes 11: graphical desktop OS, preemptive NT kernel, from the Standard era.
        JSComputronicsAPI.registerOS(new OsDef(
                rl("panes_11"),
                OsCapability.FULL_DESKTOP,
                HardwareEra.STANDARD,
                rl("nt"),
                128,
                Optional.empty()
        ));

        // OS case (c): PDA/Tablet/Smartphone portables ship with a factory mobile OS.
        // Those item/block types do not exist yet; when they are added the mobile OS should be
        // registered here and set as the installedOsId on item creation.
        // TODO(os): register the mobile OS and wire it into the portable items once they exist.
    }

    private static void registerPrograms() {
        // The Network Management Studio is a foreground graphical app: it only runs on a full desktop OS
        // (Panes 95+), so it is gated to FULL_DESKTOP from the Legacy era. It cannot install or launch on
        // MC-DOS (terminal-only) or MC-NET (network GUI).
        JSComputronicsAPI.registerProgram(new ProgramDef(
                rl("nms"),
                OsCapability.FULL_DESKTOP,
                HardwareEra.LEGACY,
                ProgramKind.APP
        ));

        // The IQL Engine is a headless service that runs on the network's Mainframe; any OS tier can host
        // it (it has no graphical surface of its own — the NMS is its client).
        JSComputronicsAPI.registerProgram(new ProgramDef(
                rl("iqlengine"),
                OsCapability.TERMINAL_ONLY,
                HardwareEra.VINTAGE,
                ProgramKind.SERVICE
        ));

        // The Crafting Manager is a foreground graphical app, like the NMS: it needs a full desktop OS
        // (Panes 95+, Legacy era). A second gate, applied at install time, restricts it to Crafting
        // Computers; a Crafting Card is required at run time for its actions to do anything.
        JSComputronicsAPI.registerProgram(new ProgramDef(
                rl("crafting_manager"),
                OsCapability.FULL_DESKTOP,
                HardwareEra.LEGACY,
                ProgramKind.APP
        ));
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("jsc", path);
    }
}
