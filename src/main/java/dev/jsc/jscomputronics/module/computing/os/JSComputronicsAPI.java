/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.os;

/**
 * Public API surface for third-party addons to extend J's Computronics with custom kernels,
 * operating systems, and programs.
 *
 * <p>Registrations must be submitted during the NeoForge {@code FMLCommonSetupEvent} phase (or any
 * earlier mod-loading phase). Entries registered after that point are not guaranteed to be visible
 * to the mod's boot and gating logic.
 *
 * <p>The built-in kernels and OSes shipped with the mod register through this same API, so addon
 * authors have a proven registration path from day one.
 */
public final class JSComputronicsAPI {

    private JSComputronicsAPI() {}

    /**
     * Registers a kernel so it can be referenced by {@link OsDef#kernelId()} entries and looked up
     * via {@link OsRegistry#getKernel}.
     *
     * @param def the kernel descriptor to register; must not be {@code null}
     */
    public static void registerKernel(KernelDef def) {
        OsRegistry.registerKernel(def);
    }

    /**
     * Registers an operating system so it appears in {@link OsRegistry} and can be installed on
     * computers.
     *
     * @param def the OS descriptor to register; must not be {@code null}
     */
    public static void registerOS(OsDef def) {
        OsRegistry.registerOs(def);
    }

    /**
     * Registers a program so it appears in {@link OsRegistry} and can be launched on a compatible
     * OS.
     *
     * @param def the program descriptor to register; must not be {@code null}
     */
    public static void registerProgram(ProgramSpec def) {
        OsRegistry.registerProgram(def);
    }

    /**
     * Registers a desktop environment so a Linux computer can install it as a package and boot into its
     * chrome, or an OS can bundle it.
     *
     * @param def the desktop environment descriptor to register; must not be {@code null}
     */
    public static void registerDesktopEnvironment(DesktopEnvironmentDef def) {
        OsRegistry.registerDesktop(def);
    }
}
