/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * The version each program is at, as a package manager or an About box would say it.
 *
 * <p>A version is part of a program's face: the package line says {@code screenfetch 3.9.1}, not the
 * build number of the mod it happens to ship in. The numbers here are written by hand, one per program,
 * and a program without one is at 1.0, which is what a first release is.
 */
public final class ProgramVersions {

    /** What a program with no version of its own is at. */
    public static final String DEFAULT = "1.0";

    private static final Map<String, String> BY_PATH = Map.ofEntries(
            Map.entry("minesweeper", "5.1"),
            Map.entry("nms", "19.3"),
            Map.entry("iqlengine", "16.0"),
            Map.entry("crafting_manager", "3.2"),
            Map.entry("pattern_studio", "2.0"),
            Map.entry("cluster_manager", "1.4"),
            Map.entry("storage_insights", "2.1"),
            Map.entry("craft_planner", "1.8"),
            Map.entry("automation_engine", "4.0"),
            Map.entry("automation_manager", "4.0"),
            Map.entry("predictive_cache", "1.2"),
            Map.entry("load_balancer", "1.0"),
            Map.entry("integrity_monitor", "2.3"),
            Map.entry("remote_control", "1.1"),
            Map.entry("mirror", "2.5"),
            Map.entry("screenfetch", "3.9.1"),
            Map.entry("cannonc", "1.0"),
            Map.entry("cannonrt", "1.0"),
            Map.entry("virtual_studio", "17.0"),
            Map.entry("virtual_studio_code", "1.85"),
            Map.entry("exposure", "4.2"),
            Map.entry("vim", "9.1"),
            Map.entry("emacs", "29.2"),
            Map.entry("kde_plasma", "5.27"),
            Map.entry("gnome", "45.2"),
            Map.entry("cinnamon", "6.0"),
            Map.entry("command_prompt", "6.1"),
            Map.entry("files", "6.1"),
            Map.entry("editor", "6.1"),
            Map.entry("calculator", "6.1"),
            Map.entry("settings", "6.1"),
            Map.entry("system_monitor", "6.1"),
            Map.entry("task_manager", "6.1"),
            Map.entry("network_manager", "2.0"),
            Map.entry("network", "2.0"),
            Map.entry("this_pc", "6.1"),
            Map.entry("disks", "6.1"));

    private ProgramVersions() {
    }

    /** The version of the program with that registry id. */
    public static String of(final ResourceLocation id) {
        return id == null ? DEFAULT : BY_PATH.getOrDefault(id.getPath(), DEFAULT);
    }

    /** The same, from the id written out ({@code jsc:minesweeper}) or the bare path. */
    public static String of(final String id) {
        if (id == null) {
            return DEFAULT;
        }
        final int colon = id.indexOf(':');
        return BY_PATH.getOrDefault(colon >= 0 ? id.substring(colon + 1) : id, DEFAULT);
    }
}
