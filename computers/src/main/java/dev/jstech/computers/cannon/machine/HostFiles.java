/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.cannon.machine;

import dev.jstech.computers.cannon.run.Halt;
import dev.jstech.computers.cannon.run.IHost;
import dev.jstech.computers.cannon.run.Values;
import dev.jstech.computers.program.cli.ICliComputer;
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
    private static final int LOOK = dev.jstech.computers.cannon.CannonCosts.GLANCE_NETWORK;
    private static final int READ = dev.jstech.computers.cannon.CannonCosts.READ;
    private static final int WRITE = dev.jstech.computers.cannon.CannonCosts.WRITE;

    private HostFiles() {
    }

    /** Whether this is one of the calls handled here. */
    public static boolean handles(final String owner) {
        return "File".equals(owner);
    }

    /** Answers one of them against a real machine. */
    public static IHost.Reply call(final ICliComputer computer, final String member,
                                  final List<Object> arguments, final int line) {
        final String path = arguments.isEmpty() ? "" : String.valueOf(arguments.getFirst());
        return switch (member) {
            case "Exists" -> IHost.Reply.of(computer.readFile(path).ok(), LOOK);
            case "Read" -> {
                final ICliComputer.FsResult read = computer.readFile(path);
                if (!read.ok()) {
                    throw new Halt(Halt.Reason.NO_SUCH_MEMBER, line, read.message());
                }
                yield IHost.Reply.of(read.message(), READ);
            }
            case "TryRead" -> {
                // The out parameter comes back beside the answer: found, and what was found.
                final ICliComputer.FsResult read = computer.readFile(path);
                yield new IHost.Reply(read.ok(), List.of(read.ok() ? read.message() : ""), READ);
            }
            case "Write" -> IHost.Reply.of(
                    computer.writeFile(path, text(arguments)).ok(), WRITE);
            case "Append" -> {
                final ICliComputer.FsResult had = computer.readFile(path);
                final String before = had.ok() ? had.message() : "";
                yield IHost.Reply.of(computer.writeFile(path, before + text(arguments)).ok(), WRITE);
            }
            case "Delete" -> IHost.Reply.of(computer.deleteFile(path).ok(), WRITE);
            case "MkDir" -> IHost.Reply.of(computer.makeDir(path).ok(), WRITE);
            case "List" -> {
                final Values.ListValue names = new Values.ListValue();
                final ICliComputer.FsResult listing = computer.listDisk(path);
                if (listing.ok()) {
                    for (final ICliComputer.FsEntry entry : listing.entries()) {
                        /*
                         * The name is the whole last segment of the path, extension included, so a name
                         * a program is handed is a name it can turn round and open. A folder ends in a
                         * slash, because a program walking a tree has to be able to tell which is which
                         * and asking it to try opening each one to find out would be a poor answer.
                         */
                        names.items().add(entry.isDir() ? entry.name() + "/" : entry.name());
                    }
                }
                yield IHost.Reply.of(names, READ);
            }
            default -> throw new Halt(Halt.Reason.NO_SUCH_MEMBER, line, "File has no " + member);
        };
    }

    /** The second argument of a write, which is the text to put there. */
    private static String text(final List<Object> arguments) {
        return arguments.size() < 2 ? "" : String.valueOf(arguments.get(1));
    }
}
