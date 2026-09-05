/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.program.cli;

import java.util.List;

/**
 * Everything a command receives when it runs: the arguments after the command word, the computer to act on, the output buffer to write into, and the shell itself (so {@code help} can list its peers). Pure data — no Minecraft types — so a command is exercised in a unit test with a fake computer.
 */
public record CliContext(List<String> args, CliComputer computer, CliOutput out, CliShell shell) {

    /** The argument at {@code index}, or {@code ""} when there are fewer arguments than that. */
    public String arg(final int index) {
        return index >= 0 && index < args.size() ? args.get(index) : "";
    }

    public boolean hasArgs() {
        return !args.isEmpty();
    }

    public int argCount() {
        return args.size();
    }

    /**
     * Parses {@code args[index]} as a positive long quantity.
     *
     * @return the parsed amount, or {@code -1} when it is missing or not a positive whole number
     */
    public long longArg(final int index) {
        final String raw = arg(index).replace(",", "").replace("_", "");
        if (raw.isEmpty()) {
            return -1L;
        }
        try {
            final long value = Long.parseLong(raw);
            return value > 0L ? value : -1L;
        } catch (final NumberFormatException notANumber) {
            return -1L;
        }
    }

    /** The arguments from {@code index} onward joined with spaces — for free-text trailing operands. */
    public String rest(final int index) {
        if (index >= args.size()) {
            return "";
        }
        return String.join(" ", args.subList(index, args.size()));
    }
}
