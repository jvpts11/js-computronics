/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.program.cli;

import java.util.List;
import java.util.Locale;

/**
 * The shell verbs that ship with the mod. Each is a small, self-contained {@link CliCommand}; add-ons add their own the same way. They talk only to the {@link CliComputer} facade, so the whole set is exercised in unit tests against a fake computer.
 */
public final class BuiltinCommands {

    private BuiltinCommands() {
    }

    /** Every built-in command, in the order they appear in {@code help}. */
    public static List<CliCommand> all() {
        return List.of(
                new Help(),
                new Clear(),
                new Echo(),
                new Version(),
                new Whoami(),
                new Status(),
                new Net(),
                new Query(),
                new Find(),
                new Select(),
                new Insert(),
                new Craft(),
                new Ops(),
                new Devices(),
                new ProgramsList(),
                new Maint("analyze", "analyze"),
                new Maint("reindex", "reindex"),
                new Maint("vacuum", "vacuum"));
    }

    private static String group(final long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    // --- meta -------------------------------------------------------------------------------------

    static final class Help implements CliCommand {
        @Override public String name() {
            return "help";
        }

        @Override public List<String> aliases() {
            return List.of("?", "man", "commands");
        }

        @Override public String summary() {
            return "list commands, or show how one is used";
        }

        @Override public String usage() {
            return "[command]";
        }

        @Override public void run(final CliContext ctx) {
            if (ctx.hasArgs()) {
                final CliCommand command = ctx.shell().find(ctx.arg(0));
                if (command == null) {
                    ctx.out().error("no such command: " + ctx.arg(0));
                    return;
                }
                ctx.out().accent(command.name() + (command.usage().isEmpty() ? "" : " " + command.usage()));
                ctx.out().dim("  " + command.summary());
                if (!command.aliases().isEmpty()) {
                    ctx.out().dim("  aliases: " + String.join(", ", command.aliases()));
                }
                return;
            }
            ctx.out().header("commands");
            for (final CliCommand command : ctx.shell().commands()) {
                ctx.out().row("  " + command.name(), command.summary());
            }
            ctx.out().blank();
            ctx.out().dim("'help <command>' for details");
        }
    }

    static final class Clear implements CliCommand, CliShell.ClearMarker {
        @Override public String name() {
            return "clear";
        }

        @Override public List<String> aliases() {
            return List.of("cls");
        }

        @Override public String summary() {
            return "clear the console";
        }

        @Override public void run(final CliContext ctx) {
            // The shell clears the scrollback because this command is a ClearMarker; nothing to print.
        }
    }

    static final class Echo implements CliCommand {
        @Override public String name() {
            return "echo";
        }

        @Override public String summary() {
            return "print the given text";
        }

        @Override public String usage() {
            return "<text>";
        }

        @Override public void run(final CliContext ctx) {
            ctx.out().line(ctx.rest(0));
        }
    }

    static final class Version implements CliCommand {
        @Override public String name() {
            return "version";
        }

        @Override public List<String> aliases() {
            return List.of("ver");
        }

        @Override public String summary() {
            return "show the shell version";
        }

        @Override public void run(final CliContext ctx) {
            ctx.out().accent("J's Computronics Shell v1.0");
        }
    }

    // --- this computer ----------------------------------------------------------------------------

    static final class Whoami implements CliCommand {
        @Override public String name() {
            return "whoami";
        }

        @Override public String summary() {
            return "show this computer's name and id";
        }

        @Override public void run(final CliContext ctx) {
            final CliComputer c = ctx.computer();
            ctx.out().row("name", c.name().isEmpty() ? "(unnamed)" : c.name());
            ctx.out().row("type", c.type());
            ctx.out().row("node", c.nodeId());
        }
    }

    static final class Status implements CliCommand {
        @Override public String name() {
            return "status";
        }

        @Override public List<String> aliases() {
            return List.of("stat");
        }

        @Override public String summary() {
            return "show power, cpu, ram and link";
        }

        @Override public void run(final CliContext ctx) {
            final CliComputer c = ctx.computer();
            ctx.out().styled(c.running() ? "ONLINE" : "OFFLINE", c.running() ? CliStyle.OK : CliStyle.ERROR);
            ctx.out().row("cpu", group(c.cpuCapacity()) + " it/t");
            ctx.out().row("ram", group(c.ramBuffer()) + " it");
            ctx.out().row("network", c.onNetwork() ? "linked (" + c.networkId() + ")" : "--");
        }
    }

    static final class Net implements CliCommand {
        @Override public String name() {
            return "net";
        }

        @Override public List<String> aliases() {
            return List.of("network");
        }

        @Override public String summary() {
            return "summarise the network";
        }

        @Override public void run(final CliContext ctx) {
            final CliComputer.NetSummary n = ctx.computer().network();
            if (!n.linked()) {
                ctx.out().error("not on a network");
                return;
            }
            ctx.out().row("network", ctx.computer().networkId());
            ctx.out().row("mainframe", n.mainframePresent() ? "present" : "MISSING");
            ctx.out().row("servers", group(n.servers()));
            ctx.out().row("computers", group(n.personalComputers()));
            ctx.out().row("subframes", group(n.subframes()));
            ctx.out().row("indexed types", group(n.indexedTypes()));
        }
    }

    static final class Devices implements CliCommand {
        @Override public String name() {
            return "devices";
        }

        @Override public List<String> aliases() {
            return List.of("dev", "peripherals");
        }

        @Override public String summary() {
            return "list linked peripherals";
        }

        @Override public void run(final CliContext ctx) {
            final List<String> devices = ctx.computer().peripherals();
            if (devices.isEmpty()) {
                ctx.out().dim("no peripherals linked");
                return;
            }
            for (final String device : devices) {
                ctx.out().line("  " + device);
            }
        }
    }

    static final class ProgramsList implements CliCommand {
        @Override public String name() {
            return "programs";
        }

        @Override public List<String> aliases() {
            return List.of("apps");
        }

        @Override public String summary() {
            return "list installed programs";
        }

        @Override public void run(final CliContext ctx) {
            final List<CliComputer.ProgramInfo> programs = ctx.computer().programs();
            if (programs.isEmpty()) {
                ctx.out().dim("no programs installed");
                return;
            }
            for (final CliComputer.ProgramInfo program : programs) {
                ctx.out().row("  " + program.name(), program.id());
            }
        }
    }

    // --- storage ----------------------------------------------------------------------------------

    static final class Query implements CliCommand {
        private static final int LIMIT = 64;

        @Override public String name() {
            return "query";
        }

        @Override public List<String> aliases() {
            return List.of("ls", "q");
        }

        @Override public String summary() {
            return "list what the network holds";
        }

        @Override public String usage() {
            return "[name filter]";
        }

        @Override public void run(final CliContext ctx) {
            if (!ctx.computer().onNetwork()) {
                ctx.out().error("not on a network");
                return;
            }
            final List<CliComputer.StoredItem> items = ctx.computer().query(ctx.rest(0), LIMIT);
            if (items.isEmpty()) {
                ctx.out().dim(ctx.hasArgs() ? "nothing matches '" + ctx.rest(0) + "'" : "the network is empty");
                return;
            }
            for (final CliComputer.StoredItem item : items) {
                ctx.out().row(item.name(), group(item.quantity()));
            }
        }
    }

    static final class Find implements CliCommand {
        @Override public String name() {
            return "find";
        }

        @Override public List<String> aliases() {
            return List.of("locate");
        }

        @Override public String summary() {
            return "show which servers hold an item";
        }

        @Override public String usage() {
            return "<item>";
        }

        @Override public void run(final CliContext ctx) {
            if (!ctx.hasArgs()) {
                ctx.out().error("usage: find <item>");
                return;
            }
            final List<CliComputer.Holding> holdings = ctx.computer().find(ctx.rest(0));
            if (holdings.isEmpty()) {
                ctx.out().dim("no server holds '" + ctx.rest(0) + "'");
                return;
            }
            for (final CliComputer.Holding holding : holdings) {
                ctx.out().row(holding.server(), group(holding.quantity()));
            }
        }
    }

    // --- operations -------------------------------------------------------------------------------

    /** Shared body for the quantity-then-item verbs (select / insert / craft). */
    private abstract static class QuantityItem implements CliCommand {
        @Override public String usage() {
            return "<quantity> <item>";
        }

        @Override public void run(final CliContext ctx) {
            final long qty = ctx.longArg(0);
            if (qty < 0L || ctx.argCount() < 2) {
                ctx.out().error("usage: " + name() + " " + usage());
                return;
            }
            if (!ctx.computer().onNetwork()) {
                ctx.out().error("not on a network");
                return;
            }
            final CliComputer.OpResult result = act(ctx.computer(), ctx.rest(1), qty);
            ctx.out().styled(result.message(), result.ok() ? CliStyle.OK : CliStyle.ERROR);
        }

        abstract CliComputer.OpResult act(CliComputer computer, String item, long quantity);
    }

    static final class Select extends QuantityItem {
        @Override public String name() {
            return "select";
        }

        @Override public List<String> aliases() {
            return List.of("get", "pull");
        }

        @Override public String summary() {
            return "pull items from the network to this computer";
        }

        @Override CliComputer.OpResult act(final CliComputer c, final String item, final long qty) {
            return c.select(item, qty);
        }
    }

    static final class Insert extends QuantityItem {
        @Override public String name() {
            return "insert";
        }

        @Override public List<String> aliases() {
            return List.of("push", "put");
        }

        @Override public String summary() {
            return "push items from this computer into the network";
        }

        @Override CliComputer.OpResult act(final CliComputer c, final String item, final long qty) {
            return c.insert(item, qty);
        }
    }

    static final class Craft extends QuantityItem {
        @Override public String name() {
            return "craft";
        }

        @Override public List<String> aliases() {
            return List.of("make");
        }

        @Override public String summary() {
            return "ask the network to craft an item";
        }

        @Override CliComputer.OpResult act(final CliComputer c, final String item, final long qty) {
            return c.craft(item, qty);
        }
    }

    static final class Ops implements CliCommand {
        @Override public String name() {
            return "ops";
        }

        @Override public List<String> aliases() {
            return List.of("jobs", "ps");
        }

        @Override public String summary() {
            return "list operations in flight";
        }

        @Override public void run(final CliContext ctx) {
            final List<CliComputer.ActiveOp> ops = ctx.computer().activeOps();
            if (ops.isEmpty()) {
                ctx.out().dim("no operations running");
                return;
            }
            for (final CliComputer.ActiveOp op : ops) {
                final String head = op.type() + " " + op.item();
                final String tail = op.status() + " " + group(op.progress()) + "/" + group(op.total());
                ctx.out().row(head, tail);
            }
        }
    }

    static final class Maint implements CliCommand {
        private final String verb;
        private final String action;

        Maint(final String verb, final String action) {
            this.verb = verb;
            this.action = action;
        }

        @Override public String name() {
            return verb;
        }

        @Override public String summary() {
            return "mainframe: " + verb + " the storage index";
        }

        @Override public void run(final CliContext ctx) {
            final CliComputer.OpResult result = ctx.computer().maintenance(action);
            ctx.out().styled(result.message(), result.ok() ? CliStyle.OK : CliStyle.ERROR);
        }
    }
}
