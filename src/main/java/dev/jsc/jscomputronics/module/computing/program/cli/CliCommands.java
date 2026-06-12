/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.program.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * The open registry of shell commands. The mod's built-ins are present by default; an add-on adds its own verbs by calling {@link #register} from its setup (or by listening for the registration event the mod fires during common setup). Minecraft-free, so the registry and a shell built from it run in plain unit tests.
 */
public final class CliCommands {

    private static final List<CliCommand> EXTRA = new ArrayList<>();

    private CliCommands() {
    }

    /**
     * Adds a command to the Command Prompt. Safe to call from any mod's setup; later registrations
     * appear after the built-ins. A name or alias that collides with an existing command shadows it
     * only on lookup ties by registration order, so add-ons should namespace unusual verbs.
     */
    public static synchronized void register(final CliCommand command) {
        EXTRA.add(command);
    }

    /** Every command the prompt knows: the built-ins first, then anything add-ons registered. */
    public static synchronized List<CliCommand> all() {
        final List<CliCommand> commands = new ArrayList<>(BuiltinCommands.all());
        commands.addAll(EXTRA);
        return commands;
    }

    /** A fresh shell over the current command set, sized to a console {@code width} in characters. */
    public static CliShell newShell(final int width) {
        return new CliShell(all(), width);
    }
}
