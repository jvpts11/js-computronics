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
import java.util.Locale;

/**
 * The machine that orchestrates the network, as a program on it can read it.
 *
 * <p>What is here is what the Mainframe already keeps about the work it has been doing: how many of
 * each kind of Operation it ran in the last hour, how long they waited, how long they took, and how
 * often they came up short. A script watching for a base that is falling behind reads exactly this.
 */
public final class HostMainframe {

    private static final int GLANCE = 10;
    private static final int READ = 50;

    private HostMainframe() {
    }

    /** Whether this is one of the things read here. */
    public static boolean handles(final String owner) {
        return "Mainframe".equals(owner);
    }

    /** Answers one of them. */
    public static Host.Reply call(final CliComputer computer, final String member,
                                  final List<Object> arguments, final int line) {
        if ("Online".equals(member)) {
            return Host.Reply.of(computer.onNetwork() && computer.network().mainframePresent(), GLANCE);
        }
        if (!computer.onNetwork()) {
            throw new Halt(Halt.Reason.NO_NETWORK, line, "this computer is not on a network");
        }
        return switch (member) {
            case "PeakToday" -> Host.Reply.of(computer.peakOperationsToday(), GLANCE);
            case "Stats" -> Host.Reply.of(stats(computer, kind(arguments)), READ);
            case "Work" -> {
                final Values.ListValue all = new Values.ListValue();
                for (final CliComputer.OperationStat stat : computer.operationStats()) {
                    all.items().add(shot(stat));
                }
                yield Host.Reply.of(all, HostNetwork.priceOf(all.size()));
            }
            default -> throw new Halt(Halt.Reason.NO_SUCH_MEMBER, line, "Mainframe has no " + member);
        };
    }

    /**
     * What the network did with that kind of Operation in the last hour.
     *
     * <p>A kind it has not run reads as zeroes rather than as nothing, so a script can add up and
     * compare without asking first whether there is anything to add up.
     */
    private static Values.Obj stats(final CliComputer computer, final String kind) {
        for (final CliComputer.OperationStat stat : computer.operationStats()) {
            if (stat.type().equalsIgnoreCase(kind)) {
                return shot(stat);
            }
        }
        return shot(new CliComputer.OperationStat(kind, 0, 0, 0, 0, 0L));
    }

    private static Values.Obj shot(final CliComputer.OperationStat stat) {
        final Values.Obj made = new Values.Obj("WorkStat");
        made.set("Type", stat.type().toLowerCase(Locale.ROOT));
        made.set("Count", stat.count());
        made.set("AverageWait", stat.averageWait());
        made.set("AverageRun", stat.averageRun());
        made.set("ShortfallPercent", stat.shortfallPercent());
        made.set("Moved", stat.moved());
        return made;
    }

    /** The kind of Operation a call was asked about. */
    private static String kind(final List<Object> arguments) {
        return arguments.isEmpty() ? "" : String.valueOf(arguments.getFirst());
    }
}
