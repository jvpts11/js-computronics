/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.machine;

import dev.jstech.computronics.cannon.run.Halt;
import dev.jstech.computronics.cannon.run.IHost;
import dev.jstech.computronics.cannon.run.Values;
import dev.jstech.computronics.program.cli.ICliComputer;
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
    private static final int GLANCE = dev.jstech.computronics.cannon.CannonCosts.GLANCE_NETWORK;
    private static final int READ = dev.jstech.computronics.cannon.CannonCosts.READ;

    /**
     * How many rows one call may gather.
     *
     * <p>It is not a cap on the answer, which is why it is set far past any real network: a program that
     * asks what a hundred thousand kinds of thing there are is handed all of them and pays for all of
     * them, in instructions and in memory. If the list does not fit, the program runs out of memory and
     * says so, which is the right answer and the one that tells the player to put more in the machine.
     */
    private static final int EVERYTHING = 1_000_000;

    private HostNetwork() {
    }

    /** Whether this is one of the things read here. */
    public static boolean handles(final String owner) {
        return "Network".equals(owner);
    }

    /** Answers one of them. */
    public static IHost.Reply call(final ICliComputer computer, final String member,
                                  final List<Object> arguments, final int line) {
        if ("Online".equals(member)) {
            return IHost.Reply.of(computer.onNetwork(), GLANCE);
        }
        if ("Current".equals(member)) {
            return IHost.Reply.of(computer.onNetwork() ? computer.networkId() : null, GLANCE);
        }
        if (!computer.onNetwork()) {
            throw new Halt(Halt.Reason.NO_NETWORK, line, "this computer is not on a network");
        }
        return switch (member) {
            case "Capacity" -> IHost.Reply.of(computer.networkUse().capacity(), READ);
            case "Used" -> IHost.Reply.of(computer.networkUse().stored(), READ);
            case "Total" -> IHost.Reply.of(total(computer, name(arguments)), READ);
            case "Types" -> rows(types(computer));
            case "Find" -> rows(find(computer, name(arguments)));
            case "Servers" -> rows(servers(computer));
            default -> throw new Halt(Halt.Reason.NO_SUCH_MEMBER, line, "Network has no " + member);
        };
    }

    /** A list, priced by how long it is. */
    private static IHost.Reply rows(final Values.ListValue all) {
        return IHost.Reply.of(all, priceOf(all.size()));
    }

    /**
     * What gathering that many rows is worth.
     *
     * <p>Asking for one thing and asking for a hundred thousand are not the same question, and charging
     * the same for both would let a program sweep the whole network every tick for nothing.
     */
    public static int priceOf(final int rows) {
        return READ + Math.max(0, rows);
    }

    /** How much of that the whole network holds, counting every server that has any. */
    private static long total(final ICliComputer computer, final String item) {
        long sum = 0;
        for (final ICliComputer.Holding holding : computer.find(item)) {
            sum += holding.quantity();
        }
        return sum;
    }

    private static Values.ListValue types(final ICliComputer computer) {
        final Values.ListValue all = new Values.ListValue();
        for (final ICliComputer.StoredItem item : computer.query(null, "", EVERYTHING)) {
            all.items().add(item.name());
        }
        return all;
    }

    private static Values.ListValue find(final ICliComputer computer, final String item) {
        final Values.ListValue all = new Values.ListValue();
        for (final ICliComputer.Holding holding : computer.find(item)) {
            final Values.Obj made = new Values.Obj("HoldingInfo");
            made.set("Server", holding.server());
            made.set("Quantity", holding.quantity());
            all.items().add(made);
        }
        return all;
    }

    private static Values.ListValue servers(final ICliComputer computer) {
        final Values.ListValue all = new Values.ListValue();
        for (final ICliComputer.ServerUse row : computer.servers()) {
            final Values.Obj made = new Values.Obj("ServerInfo");
            made.set("Name", row.name());
            made.set("Stored", row.stored());
            made.set("Capacity", row.capacity());
            all.items().add(made);
        }
        return all;
    }

    /** The item a call was asked about. */
    private static String name(final List<Object> arguments) {
        return arguments.isEmpty() ? "" : String.valueOf(arguments.getFirst());
    }
}
