/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.machine;

import dev.jstech.computronics.cannon.run.Halt;
import dev.jstech.computronics.cannon.run.Host;
import dev.jstech.computronics.cannon.run.Values;
import dev.jstech.computronics.program.cli.CliComputer;
import java.util.List;

/**
 * The machine's own drives, as a program reaches them.
 *
 * <p>It goes through the same door the shell does, so a path means the same thing to a program as it
 * does at the prompt and a file written by one is the file the other opens. Nothing here is free: a disk
 * is slower than adding two numbers, and the prices below are what says so.
 */
public final class HostFiles {

    /** What each of these is worth in instructions. Reading is dear; writing is dearer. */
    private static final int LOOK = 10;
    private static final int READ = 50;
    private static final int WRITE = 100;

    private HostFiles() {
    }

    /** Whether this is one of the calls handled here. */
    public static boolean handles(final String owner) {
        return "File".equals(owner);
    }

    /** Answers one of them against a real machine. */
    public static Host.Reply call(final CliComputer computer, final String member,
                                  final List<Object> arguments, final int line) {
        final String path = arguments.isEmpty() ? "" : String.valueOf(arguments.getFirst());
        return switch (member) {
            case "Exists" -> Host.Reply.of(computer.readFile(path).ok(), LOOK);
            case "Read" -> {
                final CliComputer.FsResult read = computer.readFile(path);
                if (!read.ok()) {
                    throw new Halt(Halt.Reason.NO_SUCH_MEMBER, line, read.message());
                }
                yield Host.Reply.of(read.message(), READ);
            }
            case "TryRead" -> {
                // The out parameter comes back beside the answer: found, and what was found.
                final CliComputer.FsResult read = computer.readFile(path);
                yield new Host.Reply(read.ok(), List.of(read.ok() ? read.message() : ""), READ);
            }
            case "Write" -> Host.Reply.of(
                    computer.writeFile(path, text(arguments)).ok(), WRITE);
            case "Append" -> {
                final CliComputer.FsResult had = computer.readFile(path);
                final String before = had.ok() ? had.message() : "";
                yield Host.Reply.of(computer.writeFile(path, before + text(arguments)).ok(), WRITE);
            }
            case "Delete" -> Host.Reply.of(computer.deleteFile(path).ok(), WRITE);
            case "MkDir" -> Host.Reply.of(computer.makeDir(path).ok(), WRITE);
            case "List" -> {
                final Values.ListValue names = new Values.ListValue();
                final CliComputer.FsResult listing = computer.listDisk(path);
                if (listing.ok()) {
                    for (final CliComputer.FsEntry entry : listing.entries()) {
                        names.items().add(entry.isDir() ? entry.name()
                                : entry.name() + (entry.ext().isEmpty() ? "" : "." + entry.ext()));
                    }
                }
                yield Host.Reply.of(names, READ);
            }
            default -> throw new Halt(Halt.Reason.NO_SUCH_MEMBER, line, "File has no " + member);
        };
    }

    /** The second argument of a write, which is the text to put there. */
    private static String text(final List<Object> arguments) {
        return arguments.size() < 2 ? "" : String.valueOf(arguments.get(1));
    }
}
