/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.program;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.module.computing.program.cli.CliComputer;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The open registry of programs. The Command Prompt is built in and pre-installed on every computer; an add-on registers its own program with {@link #register} from its setup. Listing is the only thing the registry does here — how a program opens is wired on the client.
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
     * media and a Crafting Computer's recipe store. It installs only on a Crafting Computer (the
     * install is rejected elsewhere) and needs a Crafting Card to actually run its actions.
     */
    public static final ResourceLocation CRAFTING_MANAGER =
            ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "crafting_manager");

    private static final Map<ResourceLocation, Program> REGISTERED = new LinkedHashMap<>();

    static {
        register(new Program(COMMAND_PROMPT, "cmd", "program.jsc.command_prompt", true));
        register(new Program(NMS, "nms", "program.jsc.nms", false));
        register(new Program(IQL_ENGINE, "iqlengine", "program.jsc.iqlengine", false));
        register(new Program(CRAFTING_MANAGER, "craftmgr", "program.jsc.crafting_manager", false));
    }

    private Programs() {
    }

    /** Adds a program. Safe to call from any mod's setup; a duplicate id is ignored. */
    public static synchronized void register(final Program program) {
        REGISTERED.putIfAbsent(program.id(), program);
    }

    public static synchronized Program get(final ResourceLocation id) {
        return REGISTERED.get(id);
    }

    public static synchronized List<Program> all() {
        return List.copyOf(REGISTERED.values());
    }

    /** The programs every computer ships with. */
    public static synchronized List<Program> installed() {
        final List<Program> out = new ArrayList<>();
        for (final Program program : REGISTERED.values()) {
            if (program.preinstalled()) {
                out.add(program);
            }
        }
        return out;
    }

    /** The pre-installed programs as the Minecraft-free rows the {@code programs} command prints. */
    public static List<CliComputer.ProgramInfo> installedInfo() {
        final List<CliComputer.ProgramInfo> out = new ArrayList<>();
        for (final Program program : installed()) {
            out.add(new CliComputer.ProgramInfo(program.commandName(), program.id().toString()));
        }
        return out;
    }
}
