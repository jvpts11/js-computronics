/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.program.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The command interpreter: it owns a set of {@link CliCommand}s, looks one up by name or alias, and runs it against a {@link CliComputer}, collecting the styled output. Pure logic with no Minecraft types, so the whole parse-and-dispatch path is unit-tested with a fake computer.
 */
public final class CliShell {

    private final Map<String, CliCommand> byName = new LinkedHashMap<>();
    private final Map<String, CliCommand> lookup = new LinkedHashMap<>();
    private final int width;

    public CliShell(final List<CliCommand> commands, final int width) {
        this.width = width;
        for (final CliCommand command : commands) {
            final String name = command.name().toLowerCase(Locale.ROOT);
            byName.put(name, command);
            lookup.put(name, command);
            for (final String alias : command.aliases()) {
                lookup.put(alias.toLowerCase(Locale.ROOT), command);
            }
        }
    }

    /** The commands this shell knows, in registration order — for {@code help} and tab completion. */
    public List<CliCommand> commands() {
        return List.copyOf(byName.values());
    }

    public CliCommand find(final String name) {
        return name == null ? null : lookup.get(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Interprets one command line against the given computer.
     *
     * @return the styled output plus whether the console should be cleared first
     */
    public Response run(final String line, final CliComputer computer) {
        final CliOutput out = new CliOutput(width);
        // A source build that finished in the background is announced before whatever the player typed
        // (an empty Enter included), the way a shell shows a finished job ahead of the next prompt.
        for (final String notice : computer.drainBuildNotices()) {
            out.ok(notice);
        }
        final List<String> tokens = CliTokenizer.tokenize(line);
        if (tokens.isEmpty()) {
            return new Response(out.lines(), false);
        }
        final String word = tokens.get(0);
        // A bare drive qualifier like "D:" switches the current drive (DOS-style), not a command.
        if (tokens.size() == 1 && word.length() == 2 && word.charAt(1) == ':'
                && Character.isLetter(word.charAt(0))) {
            final CliComputer.FsResult switched = computer.changeDrive(word.charAt(0));
            if (!switched.ok()) {
                out.error(switched.message());
            }
            return new Response(out.lines(), false);
        }
        final CliCommand command = find(word);
        // A command that is not available on this computer (another distribution's package manager, an
        // uninstalled program's verbs) does not exist here, exactly like an unknown word.
        if (command == null || !command.available(computer)) {
            out.error("command not found: " + word);
            out.dim("type 'help' to list commands");
            return new Response(out.lines(), false);
        }
        final List<String> args = new ArrayList<>(tokens.subList(1, tokens.size()));
        final CliContext context = new CliContext(args, computer, out, this);
        try {
            command.run(context);
        } catch (final RuntimeException unexpected) {
            // A command must not throw for ordinary errors; if one does anyway, the shell stays alive
            // and reports it rather than tearing down the session.
            out.error("error running '" + word + "': " + unexpected.getMessage());
        }
        final boolean clear = command instanceof ClearMarker;
        return new Response(out.lines(), clear);
    }

    /**
     * Implemented by the clear-screen command so the shell can tell the console to wipe its scrollback
     * after running it, without a magic line or a special style leaking into the output model.
     */
    public interface ClearMarker {
    }

    /** The result of one line: the output to print, and whether to clear the console before printing it. */
    public record Response(List<CliLine> lines, boolean clearScreen) {
    }
}
