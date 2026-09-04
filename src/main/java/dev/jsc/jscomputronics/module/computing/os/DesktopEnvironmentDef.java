/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.os;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * A desktop environment: the graphical shell a computer runs on top of its OS. The Frames editions bundle
 * their own (the id doubles as the OS id, so every Frames look and icon set keeps its key); a Linux
 * distribution boots to a TTY until one is installed as a package (KDE Plasma, GNOME, Cinnamon).
 *
 * <p>A desktop environment brings its chrome ({@link PanelStyle}), its skin and wallpaper (looked up by
 * {@link #id()} on the client), the built-in programs it bundles, and the native names those programs
 * show under it (Files is Dolphin on KDE and Nautilus on GNOME). Registered through
 * {@link JSComputronicsAPI}, so an add-on can ship its own.
 *
 * @param id               unique registry key (e.g. {@code jsc:kde_plasma}); Frames use their OS id
 * @param displayName      the human name
 * @param panelStyle       the chrome family the desktop screen draws
 * @param bundledPrograms  the pre-installed program ids this desktop shows launchers for, in rail order
 * @param nativeNames      per-program display-name overrides under this desktop (missing = the program's own)
 */
public record DesktopEnvironmentDef(
        ResourceLocation id,
        String displayName,
        PanelStyle panelStyle,
        List<ResourceLocation> bundledPrograms,
        Map<ResourceLocation, String> nativeNames
) {

    public DesktopEnvironmentDef {
        if (displayName == null || displayName.isBlank()) {
            displayName = id.getPath();
        }
        bundledPrograms = List.copyOf(bundledPrograms);
        nativeNames = Map.copyOf(nativeNames);
    }

    /** The name a program shows under this desktop: its native name here, else its own display name. */
    public String nameOf(final ProgramSpec program) {
        return nativeNames.getOrDefault(program.id(), program.displayName());
    }

    /** Whether this desktop shows a launcher for the given pre-installed program. */
    public boolean bundles(final ResourceLocation programId) {
        return bundledPrograms.contains(programId);
    }
}
