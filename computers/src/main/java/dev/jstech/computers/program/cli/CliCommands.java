/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.program.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * The open registry of shell commands. The mod's built-ins are present by default; an add-on adds its own verbs by calling {@link #register} from its setup (or by listening for the registration event the mod fires during common setup). Minecraft-free, so the registry and a shell built from it run in plain unit tests.
 */
public final class CliCommands {

    private static final List<ICliCommand> EXTRA = new ArrayList<>();

    private CliCommands() {
    }

    /**
     * Adds a command to the Command Prompt. Safe to call from any mod's setup; later registrations
     * appear after the built-ins. A name or alias that collides with an existing command shadows it
     * only on lookup ties by registration order, so add-ons should namespace unusual verbs.
     */
    public static synchronized void register(final ICliCommand command) {
        EXTRA.add(command);
    }

    /** Every command the prompt knows: the built-ins first, then anything add-ons registered. */
    public static synchronized List<ICliCommand> all() {
        final List<ICliCommand> commands = new ArrayList<>(BuiltinCommands.all());
        commands.addAll(EXTRA);
        return commands;
    }

    /**
     * The command set for a shell family: the DOS verbs (plus registered extras) for {@code DOS}, or the
     * shared network/program verbs plus the POSIX file verbs (plus extras) for {@code POSIX}.
     */
    public static synchronized List<ICliCommand> commandsFor(
            final dev.jstech.computers.os.ShellFamily family) {
        if (family == dev.jstech.computers.os.ShellFamily.POSIX) {
            final List<ICliCommand> commands = new ArrayList<>(BuiltinCommands.shared());
            commands.addAll(PosixCommands.all());
            commands.addAll(EXTRA);
            return commands;
        }
        return all();
    }

    /** A shell speaking the given family's command set. */
    public static CliShell newShell(final dev.jstech.computers.os.ShellFamily family,
                                    final int width) {
        return new CliShell(commandsFor(family), width);
    }

    /** The shell for a computer: the live installer's verbs while a live medium is booted, else its OS family's. */
    public static CliShell shellFor(final ICliComputer computer, final int width) {
        if (computer.liveInstall() != null) {
            return new CliShell(LiveInstallCommands.all(), width);
        }
        return newShell(computer.shellFamily(), width);
    }

    /** The command list a computer's terminal offers for completion: live verbs, or its family's set. */
    public static List<ICliCommand> commandsFor(final dev.jstech.computers.os.ShellFamily family,
                                               final boolean live) {
        return live ? LiveInstallCommands.all() : commandsFor(family);
    }

    /** A fresh shell over the current command set, sized to a console {@code width} in characters. */
    public static CliShell newShell(final int width) {
        return new CliShell(all(), width);
    }
}
