/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.program.cli;

import java.util.List;

/**
 * A single shell command. Add-ons implement this and register it with {@link CliCommands#register} (or through the {@code RegisterCliCommandsEvent}) to extend the Command Prompt with their own verbs — the command set is deliberately open.
 */
public interface CliCommand {

    /** The primary word that invokes this command (lower-case, no spaces). */
    String name();

    /** Alternative words that also invoke it; empty by default. */
    default List<String> aliases() {
        return List.of();
    }

    /** A one-line description shown by {@code help}. */
    String summary();

    /** The argument syntax shown by {@code help <name>}, e.g. {@code "<quantity> <item>"}. */
    default String usage() {
        return "";
    }

    /** Runs the command, writing its result into {@code context.out()}. Must not throw for ordinary errors. */
    void run(CliContext context);

    /**
     * Whether this command is available on a given computer — used to hide a program's commands until that
     * program is installed there. Built-ins are always available; a program's verbs override this.
     */
    default boolean available(final CliComputer computer) {
        return true;
    }
}
