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
import dev.jstech.computronics.operation.MoveLabels;
import dev.jstech.computronics.program.cli.CliComputer;
import java.util.List;
import java.util.Locale;

/**
 * Asking the network to move things, from a program.
 *
 * <p>This is the one part of the API that changes the world rather than reading it, and it does so
 * through exactly the doors the Network Interactor and the prompt use. Nothing here reaches past them
 * into storage: whatever they check, a program is checked by too, now and whenever that changes.
 *
 * <p>Every row it puts in the network's log says which script asked, because a base can have a dozen
 * running at once and "a program did it" is not something a player can act on.
 *
 * <p>Asking is dear, and deliberately so. What a machine may ask for in a tick follows from the
 * processor running the script, and what the network will run at once follows from the Mainframe; a
 * program cannot outrun either by asking harder.
 */
public final class HostOperations {

    /** Reading what is in flight. */
    private static final int READ = 50;

    /** Asking for work. Far dearer than reading, because it is the network's time being spent. */
    private static final int SUBMIT = 200;

    private HostOperations() {
    }

    /** Whether this is one of the things done here. */
    public static boolean handles(final String owner) {
        return "Operations".equals(owner);
    }

    /**
     * Answers one of them.
     *
     * @param script the name of the program asking, for the row it leaves in the network's log
     */
    public static Host.Reply call(final CliComputer computer, final String script, final String member,
                                  final List<Object> arguments, final int line) {
        if (!computer.onNetwork()) {
            throw new Halt(Halt.Reason.NO_NETWORK, line, "this computer is not on a network");
        }
        final String origin = MoveLabels.cannon(script);
        return switch (member) {
            case "Pull" -> asked(computer.select(item(arguments), amount(arguments), origin));
            case "Push" -> asked(computer.insert(item(arguments), amount(arguments), origin));
            case "Craft" -> asked(computer.craft(item(arguments), amount(arguments), origin));
            case "Cancel" -> asked(computer.cancelOperation(item(arguments)));
            case "Reprioritise" -> asked(computer.repriorityOperation(item(arguments),
                    arguments.size() < 2 ? "" : String.valueOf(arguments.get(1))));
            case "List" -> {
                final Values.ListValue all = new Values.ListValue();
                for (final CliComputer.ActiveOp op : computer.activeOps()) {
                    all.items().add(shot(op));
                }
                yield Host.Reply.of(all, HostNetwork.priceOf(all.size()));
            }
            case "Get" -> {
                final String id = item(arguments);
                for (final CliComputer.ActiveOp op : computer.activeOps()) {
                    if (op.id().equalsIgnoreCase(id)) {
                        yield Host.Reply.of(shot(op), READ);
                    }
                }
                // An operation that has settled is no longer in flight, and saying so is the answer.
                yield Host.Reply.of(null, READ);
            }
            default -> throw new Halt(Halt.Reason.NO_SUCH_MEMBER, line, "Operations has no " + member);
        };
    }

    /**
     * What came of asking.
     *
     * <p>A refusal is not a mistake in the program: the network may have no Mainframe running, or
     * nothing that crafts the thing, and a script has to be able to carry on and try something else. So
     * it is answered, not thrown.
     */
    private static Host.Reply asked(final CliComputer.OpResult result) {
        final Values.Obj made = new Values.Obj("AskResult");
        made.set("Ok", result.ok());
        made.set("Message", result.message());
        return Host.Reply.of(made, SUBMIT);
    }

    private static Values.Obj shot(final CliComputer.ActiveOp op) {
        final Values.Obj made = new Values.Obj("OperationInfo");
        made.set("Id", op.id());
        made.set("Type", op.type().toLowerCase(Locale.ROOT));
        made.set("Item", op.item());
        made.set("Moved", op.progress());
        made.set("Requested", op.total());
        made.set("Status", op.status().toLowerCase(Locale.ROOT));
        made.set("Priority", op.priority().toLowerCase(Locale.ROOT));
        return made;
    }

    private static String item(final List<Object> arguments) {
        return arguments.isEmpty() ? "" : String.valueOf(arguments.getFirst());
    }

    private static long amount(final List<Object> arguments) {
        if (arguments.size() < 2) {
            return 0L;
        }
        return arguments.get(1) instanceof Number number ? number.longValue() : 0L;
    }
}
