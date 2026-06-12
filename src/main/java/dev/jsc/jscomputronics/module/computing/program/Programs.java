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

    private static final Map<ResourceLocation, Program> REGISTERED = new LinkedHashMap<>();

    static {
        register(new Program(COMMAND_PROMPT, "cmd", "program.jsc.command_prompt", true));
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
