/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.program.cli;

import dev.jsc.jscomputronics.module.computing.program.install.LiveInstallState;

import java.util.List;

/**
 * The command set of a live installation medium (the Arch ISO, the Gentoo live CD): every verb of the manual
 * install sequence, each forwarded as its full line to the computer's {@link LiveInstallState}, which
 * validates the order and answers with the real tool's output. {@code clear} and {@code help} round it out.
 */
public final class LiveInstallCommands {

    private LiveInstallCommands() {
    }

    private static final String[] VERBS = {
            "lsblk", "mkfs.ext4", "mkfs", "mount", "pacstrap", "tar", "genfstab", "arch-chroot", "chroot",
            "emerge-webrsync", "emerge", "genkernel", "grub-install", "passwd", "exit", "reboot", "help"};

    public static List<CliCommand> all() {
        final List<CliCommand> out = new java.util.ArrayList<>();
        for (final String verb : VERBS) {
            out.add(new LiveVerb(verb));
        }
        out.add(new Clear());
        return out;
    }

    /** One install verb; the state machine decides what it does at this point of the sequence. */
    static final class LiveVerb implements CliCommand {
        private final String verb;

        LiveVerb(final String verb) {
            this.verb = verb;
        }

        @Override public String name() { return verb; }

        @Override public String summary() { return "live installer: " + verb; }

        @Override public void run(final CliContext ctx) {
            final String line = ctx.hasArgs() ? verb + " " + ctx.rest(0) : verb;
            final CliComputer.OpResult result = ctx.computer().liveRun(line);
            for (final String out : result.message().split("\n", -1)) {
                if (out.isEmpty()) {
                    continue;
                }
                if (result.ok()) {
                    ctx.out().line(out);
                } else {
                    ctx.out().error(out);
                }
            }
        }
    }

    static final class Clear implements CliCommand, CliShell.ClearMarker {
        @Override public String name() { return "clear"; }

        @Override public String summary() { return "clear the terminal"; }

        @Override public void run(final CliContext ctx) {
        }
    }
}
