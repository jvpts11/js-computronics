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
 * The data network, as a program on one of its computers can read it.
 *
 * <p>Every read here comes from the index the network already keeps, so none of it submits an Operation
 * or waits on anything: a program asks what the network holds and is told, in the same tick. It is still
 * far dearer than the program's own arithmetic, because a network is a great many machines and the index
 * is what stands in for asking each of them.
 *
 * <p>A machine with no cable in it is not on a network, and says so rather than pretending: {@code
 * Online} is false and {@code Current} is nothing. Asking anything else of it stops the program, which is
 * the honest answer to a question that has none.
 */
public final class HostNetwork {

    /** Reading the index. The network is not the program's own memory, and the price says so. */
    private static final int GLANCE = 10;
    private static final int READ = 50;

    /**
     * The most rows one call hands back.
     *
     * <p>A big network holds tens of thousands of kinds of thing, and a program that asked for all of
     * them would be handed a list that does not fit in any heap it is likely to have. The cap keeps the
     * answer to a size a script can actually work with; the ones with the most in them come first.
     */
    public static final int MOST_ROWS = 1024;

    private HostNetwork() {
    }

    /** Whether this is one of the things read here. */
    public static boolean handles(final String owner) {
        return "Network".equals(owner);
    }

    /** Answers one of them. */
    public static Host.Reply call(final CliComputer computer, final String member,
                                  final List<Object> arguments, final int line) {
        if ("Online".equals(member)) {
            return Host.Reply.of(computer.onNetwork(), GLANCE);
        }
        if ("Current".equals(member)) {
            return Host.Reply.of(computer.onNetwork() ? computer.networkId() : null, GLANCE);
        }
        if (!computer.onNetwork()) {
            throw new Halt(Halt.Reason.NO_NETWORK, line, "this computer is not on a network");
        }
        return switch (member) {
            case "Total" -> Host.Reply.of(total(computer, name(arguments)), READ);
            case "Types" -> Host.Reply.of(types(computer), READ);
            case "Find" -> Host.Reply.of(find(computer, name(arguments)), READ);
            case "Servers" -> Host.Reply.of(servers(computer), READ);
            default -> throw new Halt(Halt.Reason.NO_SUCH_MEMBER, line, "Network has no " + member);
        };
    }

    /** How much of that the whole network holds, counting every server that has any. */
    private static long total(final CliComputer computer, final String item) {
        long sum = 0;
        for (final CliComputer.Holding holding : computer.find(item)) {
            sum += holding.quantity();
        }
        return sum;
    }

    private static Values.ListValue types(final CliComputer computer) {
        final Values.ListValue all = new Values.ListValue();
        for (final CliComputer.StoredItem item : computer.query(null, "", MOST_ROWS)) {
            all.items().add(item.name());
        }
        return all;
    }

    private static Values.ListValue find(final CliComputer computer, final String item) {
        final Values.ListValue all = new Values.ListValue();
        for (final CliComputer.Holding holding : computer.find(item)) {
            final Values.Obj made = new Values.Obj("HoldingInfo");
            made.set("Server", holding.server());
            made.set("Quantity", holding.quantity());
            all.items().add(made);
        }
        return all;
    }

    private static Values.ListValue servers(final CliComputer computer) {
        final Values.ListValue all = new Values.ListValue();
        for (final CliComputer.StoredItem row : computer.queryObject("servers", null, "", MOST_ROWS)) {
            final Values.Obj made = new Values.Obj("ServerInfo");
            made.set("Name", row.name());
            made.set("Stored", row.quantity());
            all.items().add(made);
        }
        return all;
    }

    /** The item a call was asked about. */
    private static String name(final List<Object> arguments) {
        return arguments.isEmpty() ? "" : String.valueOf(arguments.getFirst());
    }
}
