/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.os;

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
    private static final Map<ResourceLocation, ProgramDef> PROGRAMS = new LinkedHashMap<>();

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

    static void registerProgram(ProgramDef def) {
        PROGRAMS.put(def.id(), def);
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
     * Returns the {@link ProgramDef} registered under {@code id}, or {@code null} if absent.
     */
    public static ProgramDef getProgram(ResourceLocation id) {
        return PROGRAMS.get(id);
    }

    /**
     * Whether the OS installed under {@code osId} satisfies the requirements of the program {@code progId}.
     *
     * <p>Null-safe by design: a program with no registered {@link ProgramDef} declares no requirement and is
     * always allowed (so existing/third-party programs are unaffected). A registered program, however, needs
     * a known installed OS that meets its minimum capability tier and era; an absent or unknown OS denies it.
     */
    public static boolean canHostRun(ResourceLocation osId, ResourceLocation progId) {
        final ProgramDef prog = getProgram(progId);
        if (prog == null) {
            return true;
        }
        final OsDef os = osId == null ? null : getOs(osId);
        if (os == null) {
            return false;
        }
        return OsGating.canRun(os.capability(), os.minEra(), prog.minCapability(), prog.minOsEra());
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
    public static Collection<ProgramDef> programs() {
        return Collections.unmodifiableCollection(PROGRAMS.values());
    }
}
