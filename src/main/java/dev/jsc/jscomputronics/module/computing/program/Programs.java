/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.program;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.module.computing.os.OsRegistry;
import dev.jsc.jscomputronics.module.computing.os.ProgramSpec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Well-known program ids and a thin CLI-facing view over the single program registry
 * ({@link OsRegistry}). The descriptors themselves — platforms, hardware minimums, host scope, display
 * name — live on {@link ProgramSpec} and are registered once through the addon API, so there is no longer
 * a second, parallel program store here. These constants are just the ids the mod's own code refers to.
 */
public final class Programs {

    /** The Command Prompt: the CLI that every computer ships with. */
    public static final ResourceLocation COMMAND_PROMPT =
            ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "command_prompt");

    /** Network Management Studio: an SSMS-style operations console, installed by the player. */
    public static final ResourceLocation NMS =
            ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "nms");

    /** The IQL Engine: a background service installed on the Mainframe (the network's "SQL Server"). */
    public static final ResourceLocation IQL_ENGINE =
            ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "iqlengine");

    /**
     * The Crafting Manager: the desktop app that moves {@code .craft} recipe files between removable
     * media and a Crafting Computer's recipe store. It installs only on a Crafting Computer and needs a
     * Crafting Card to run its actions.
     */
    public static final ResourceLocation CRAFTING_MANAGER =
            ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "crafting_manager");

    /** Cluster Manager: the Cluster Management Computer's front for every cluster on its network. */
    public static final ResourceLocation CLUSTER_MANAGER =
            ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "cluster_manager");

    /** Minesweeper: the classic game, installed by the player like any other add-on. */
    public static final ResourceLocation MINESWEEPER =
            ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "minesweeper");

    /** Storage Insights: a dashboard over the network's contents (totals, top types, low stock). */
    public static final ResourceLocation STORAGE_INSIGHTS =
            ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "storage_insights");

    /** Craft Planner: previews a craft's stages and ingredient bill before the player commits to it. */
    public static final ResourceLocation CRAFT_PLANNER =
            ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "craft_planner");

    /** The Automation Engine: a Mainframe service (like the IQL Engine) that runs the saved jobs. */
    public static final ResourceLocation AUTOMATION_ENGINE =
            ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "automation_engine");

    /** The Automation Manager: the desktop front-end that creates and manages jobs for the Engine. */
    public static final ResourceLocation AUTOMATION_MANAGER =
            ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "automation_manager");

    private Programs() {
    }

    /** The descriptor registered under {@code id}, or {@code null} when none is. */
    public static ProgramSpec get(final ResourceLocation id) {
        return id == null ? null : OsRegistry.getProgram(id);
    }

    /** Every registered program descriptor. */
    public static List<ProgramSpec> all() {
        return List.copyOf(OsRegistry.programs());
    }

    /** The programs every computer ships with (no install step). */
    public static List<ProgramSpec> installed() {
        final List<ProgramSpec> out = new ArrayList<>();
        for (final ProgramSpec program : OsRegistry.programs()) {
            if (program.preinstalled()) {
                out.add(program);
            }
        }
        return out;
    }
}
