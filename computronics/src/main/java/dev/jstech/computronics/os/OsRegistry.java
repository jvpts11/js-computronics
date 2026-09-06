/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.os;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static registry for all kernels, operating systems, and programs known to the mod.
 *
 * <p>Entries are registered during the mod common-setup phase via {@link JSComputronicsAPI}. The
 * maps are insertion-ordered so iteration order is deterministic for display and testing purposes.
 *
 * <p>This class is not thread-safe; all registrations must occur on the mod-loading thread before
 * any game tick accesses the maps.
 */
public final class OsRegistry {

    private static final Map<ResourceLocation, KernelDef> KERNELS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, OsDef> OSES = new LinkedHashMap<>();
    private static final Map<ResourceLocation, ProgramSpec> PROGRAMS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, DesktopEnvironmentDef> DESKTOPS = new LinkedHashMap<>();

    private OsRegistry() {}

    // -------------------------------------------------------------------------
    // Registration (called by JSComputronicsAPI)
    // -------------------------------------------------------------------------

    static void registerKernel(KernelDef def) {
        KERNELS.put(def.id(), def);
    }

    static void registerOs(OsDef def) {
        OSES.put(def.id(), def);
    }

    static void registerProgram(ProgramSpec def) {
        PROGRAMS.put(def.id(), def);
    }

    static void registerDesktop(DesktopEnvironmentDef def) {
        DESKTOPS.put(def.id(), def);
    }

    /** Returns the {@link DesktopEnvironmentDef} registered under {@code id}, or {@code null} if absent. */
    public static DesktopEnvironmentDef getDesktop(ResourceLocation id) {
        return id == null ? null : DESKTOPS.get(id);
    }

    /** Returns an unmodifiable view of all registered desktop environments. */
    public static Collection<DesktopEnvironmentDef> desktops() {
        return Collections.unmodifiableCollection(DESKTOPS.values());
    }

    // -------------------------------------------------------------------------
    // Lookup
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link KernelDef} registered under {@code id}, or {@code null} if absent.
     */
    public static KernelDef getKernel(ResourceLocation id) {
        return KERNELS.get(id);
    }

    /**
     * Returns the {@link OsDef} registered under {@code id}, or {@code null} if absent.
     */
    public static OsDef getOs(ResourceLocation id) {
        return OSES.get(id);
    }

    /**
     * Returns the {@link ProgramSpec} registered under {@code id}, or {@code null} if absent.
     */
    public static ProgramSpec getProgram(ResourceLocation id) {
        return PROGRAMS.get(id);
    }

    /**
     * Whether the OS installed under {@code osId} lets the program {@code progId} <em>run</em> on a
     * computer with the given best CPU clock and total VRAM.
     *
     * <p>Null-safe by design: a program with no registered {@link ProgramSpec} declares no requirement
     * and is always allowed (so existing/third-party programs are unaffected). A registered program
     * needs a known installed OS whose platform it supports and a computer that meets its CPU/VRAM
     * minimums; an absent or unknown OS denies it.
     *
     * @param osId   the installed OS id, or {@code null} when none is installed
     * @param progId the program id
     * @param cpuMhz the computer's best CPU clock in MHz
     * @param vramMb the computer's total VRAM in MB
     */
    public static boolean canRunProgram(ResourceLocation osId, ResourceLocation progId,
                                        int cpuMhz, int vramMb) {
        final ProgramSpec prog = getProgram(progId);
        if (prog == null) {
            return true;
        }
        final OsDef os = osId == null ? null : getOs(osId);
        if (os == null) {
            return false;
        }
        // The version rank gate only bites WITHIN the Frames family (ranks 1/2/3); a non-Frames OS (rank 0)
        // is decided by the platform gate alone, so a headless service still runs on an MC-DOS Mainframe.
        final int rank = osVersionRank(osId);
        return (rank == 0 || rank >= prog.minOsRank())
                && OsGating.canRunProgram(os.platform(), prog.platforms(), cpuMhz, vramMb,
                prog.minCpuMhz(), prog.minVramMb());
    }

    /**
     * The version rank of a Frames OS, so a program can require a newer version within the same platform:
     * {@code frames_95 = 1}, {@code frames_xp = 2}, {@code frames_11 = 3}; everything else (and no OS) is 0.
     * A pure {@code osId}-to-rank map, safe to call on either side without the registry being populated.
     */
    public static int osVersionRank(final ResourceLocation osId) {
        if (osId == null) {
            return 0;
        }
        return switch (osId.getPath()) {
            case "frames_95" -> 1;
            case "frames_xp" -> 2;
            case "frames_11" -> 3;
            default -> 0;
        };
    }

    /**
     * Whether the program {@code progId} may be <em>installed</em> on the OS under {@code osId} on a
     * computer with the given hardware: it must satisfy {@link #canRunProgram} and the system disk
     * must have at least the program's disk footprint free.
     *
     * @param osId       the installed OS id, or {@code null} when none is installed
     * @param progId     the program id
     * @param cpuMhz     the computer's best CPU clock in MHz
     * @param vramMb     the computer's total VRAM in MB
     * @param freeDiskMb the free space on the system disk in MB
     */
    public static boolean canInstallProgram(ResourceLocation osId, ResourceLocation progId,
                                            int cpuMhz, int vramMb, long freeDiskMb) {
        final ProgramSpec prog = getProgram(progId);
        if (prog == null) {
            return true;
        }
        final OsDef os = osId == null ? null : getOs(osId);
        if (os == null) {
            return false;
        }
        final int rank = osVersionRank(osId);
        return (rank == 0 || rank >= prog.minOsRank())
                && OsGating.canInstallProgram(os.platform(), prog.platforms(), cpuMhz, vramMb, freeDiskMb,
                prog.minCpuMhz(), prog.minVramMb(), prog.minDiskMb());
    }

    // -------------------------------------------------------------------------
    // Listing
    // -------------------------------------------------------------------------

    /** Returns an unmodifiable view of all registered kernels. */
    public static Collection<KernelDef> kernels() {
        return Collections.unmodifiableCollection(KERNELS.values());
    }

    /** Returns an unmodifiable view of all registered operating systems. */
    public static Collection<OsDef> oses() {
        return Collections.unmodifiableCollection(OSES.values());
    }

    /** Returns an unmodifiable view of all registered programs. */
    public static Collection<ProgramSpec> programs() {
        return Collections.unmodifiableCollection(PROGRAMS.values());
    }
}
