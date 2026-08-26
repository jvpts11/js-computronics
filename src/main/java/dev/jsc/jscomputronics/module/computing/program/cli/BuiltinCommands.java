/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.program.cli;

import dev.jsc.jscomputronics.module.computing.program.iql.IqlCondition;
import dev.jsc.jscomputronics.module.computing.program.iql.IqlOperation;
import dev.jsc.jscomputronics.module.computing.program.iql.IqlParseResult;
import dev.jsc.jscomputronics.module.computing.program.iql.IqlParser;
import dev.jsc.jscomputronics.module.computing.program.iql.IqlVerb;

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
                new Find(),
                new Lock(),
                new Unlock(),
                new Locks(),
                new Ops(),
                new Operation(),
                new Devices(),
                new ProgramsList(),
                new Install(),
                new Store(),
                new IqlEngineCommand(),
                new Services(),
                new Maint("analyze", "analyze"),
                new Maint("reindex", "reindex"),
                new Maint("vacuum", "vacuum"),
                new Dir(),
                new Type(),
                new Del(),
                new Write(),
                new Run());
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
                if (command.available(ctx.computer())) {
                    ctx.out().row("  " + command.name(), command.summary());
                }
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

    static final class Lock implements CliCommand {
        @Override public String name() {
            return "lock";
        }

        @Override public List<String> aliases() {
            return List.of("hold");
        }

        @Override public String summary() {
            return "hold an item so concurrent operations wait";
        }

        @Override public String usage() {
            return "<item> | <quantity> <item>";
        }

        @Override public void run(final CliContext ctx) {
            if (!ctx.hasArgs()) {
                ctx.out().error("usage: lock " + usage());
                return;
            }
            if (!ctx.computer().onNetwork()) {
                ctx.out().error("not on a network");
                return;
            }
            // "lock <quantity> <item>" reserves an amount; "lock <item>" holds everything available.
            final long qty = ctx.longArg(0);
            final String item = qty > 0L && ctx.argCount() >= 2 ? ctx.rest(1) : ctx.rest(0);
            final CliComputer.OpResult result = ctx.computer().lock(item, qty > 0L ? qty : 0L);
            ctx.out().styled(result.message(), result.ok() ? CliStyle.OK : CliStyle.ERROR);
        }
    }

    static final class Unlock implements CliCommand {
        @Override public String name() {
            return "unlock";
        }

        @Override public List<String> aliases() {
            return List.of("release");
        }

        @Override public String summary() {
            return "release a held item";
        }

        @Override public String usage() {
            return "<item>";
        }

        @Override public void run(final CliContext ctx) {
            if (!ctx.hasArgs()) {
                ctx.out().error("usage: unlock <item>");
                return;
            }
            final CliComputer.OpResult result = ctx.computer().unlock(ctx.rest(0));
            ctx.out().styled(result.message(), result.ok() ? CliStyle.OK : CliStyle.ERROR);
        }
    }

    static final class Locks implements CliCommand {
        @Override public String name() {
            return "locks";
        }

        @Override public List<String> aliases() {
            return List.of("holds");
        }

        @Override public String summary() {
            return "list held item types";
        }

        @Override public void run(final CliContext ctx) {
            final List<CliComputer.StoredItem> held = ctx.computer().locks();
            if (held.isEmpty()) {
                ctx.out().dim("no items are locked");
                return;
            }
            for (final CliComputer.StoredItem row : held) {
                ctx.out().row(row.name(), group(row.quantity()));
            }
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

    static final class Operation implements CliCommand {
        private static final int QUERY_LIMIT = 64;

        @Override public String name() {
            return "operation";
        }

        @Override public List<String> aliases() {
            return List.of("op", "sql");
        }

        @Override public String summary() {
            return "run an IQL statement on the network";
        }

        @Override public String usage() {
            return "<statement>";
        }

        @Override public void run(final CliContext ctx) {
            if (!ctx.hasArgs()) {
                ctx.out().error("usage: operation <statement>   e.g. operation SELECT 64 Cobblestone");
                return;
            }
            final IqlParseResult parsed = IqlParser.tryParse(ctx.rest(0));
            if (!parsed.ok()) {
                ctx.out().error("syntax: " + parsed.error());
                return;
            }
            final IqlOperation op = parsed.operation();
            if (op.verb() == IqlVerb.QUERY || op.verb() == IqlVerb.COUNT) {
                if (!ctx.computer().onNetwork()) {
                    ctx.out().error("not on a network");
                    return;
                }
                final int limit = op.limit() > 0 ? op.limit() : QUERY_LIMIT;
                final List<CliComputer.StoredItem> items = ctx.computer().queryObject(op.item(),
                        op.where(), "", limit);
                if (items.isEmpty()) {
                    ctx.out().dim("no rows");
                    return;
                }
                for (final CliComputer.StoredItem item : items) {
                    ctx.out().row(item.detail().isEmpty() ? item.name() : item.name() + " · " + item.detail(),
                            group(item.quantity()));
                }
                return;
            }
            final CliComputer.OpResult result = ctx.computer().execute(op);
            ctx.out().styled(result.message(), result.ok() ? CliStyle.OK : CliStyle.ERROR);
        }
    }

    static final class Install implements CliCommand {
        @Override public String name() {
            return "install";
        }

        @Override public String summary() {
            return "install a program on this computer";
        }

        @Override public String usage() {
            return "<program-id>";
        }

        @Override public void run(final CliContext ctx) {
            if (!ctx.hasArgs()) {
                ctx.out().error("usage: install <program-id>   (see 'programs')");
                return;
            }
            final CliComputer.OpResult result = ctx.computer().install(ctx.arg(0));
            ctx.out().styled(result.message(), result.ok() ? CliStyle.OK : CliStyle.ERROR);
        }
    }

    static final class Store implements CliCommand {
        @Override public String name() {
            return "store";
        }

        @Override public List<String> aliases() {
            return List.of("available");
        }

        @Override public String summary() {
            return "list programs you can install on this computer";
        }

        @Override public void run(final CliContext ctx) {
            boolean any = false;
            for (final dev.jsc.jscomputronics.module.computing.program.Program program
                    : dev.jsc.jscomputronics.module.computing.program.Programs.all()) {
                if (program.preinstalled()) {
                    continue;
                }
                ctx.out().row("  " + program.commandName(), "install " + program.commandName());
                any = true;
            }
            if (!any) {
                ctx.out().dim("nothing else to install");
            }
        }
    }

    static final class IqlEngineCommand implements CliCommand {
        @Override public String name() {
            return "iqlengine";
        }

        @Override public List<String> aliases() {
            return List.of("engine");
        }

        @Override public String summary() {
            return "start/stop the network's IQL Engine service";
        }

        @Override public String usage() {
            return "start|stop|status";
        }

        @Override public boolean available(final CliComputer computer) {
            return computer.iqlEngineInstalled(); // shown only after 'install iqlengine'
        }

        @Override public void run(final CliContext ctx) {
            final CliComputer.OpResult result = ctx.computer().engineControl(ctx.hasArgs() ? ctx.arg(0) : "status");
            ctx.out().styled(result.message(), result.ok() ? CliStyle.OK : CliStyle.ERROR);
        }
    }

    static final class Services implements CliCommand {
        @Override public String name() {
            return "services";
        }

        @Override public List<String> aliases() {
            return List.of("ps");
        }

        @Override public String summary() {
            return "list the network's services and their state";
        }

        @Override public boolean available(final CliComputer computer) {
            return computer.iqlEngineInstalled();
        }

        @Override public void run(final CliContext ctx) {
            final List<CliComputer.ServiceStatus> services = ctx.computer().services();
            if (services.isEmpty()) {
                ctx.out().dim("no services");
                return;
            }
            for (final CliComputer.ServiceStatus service : services) {
                ctx.out().row("  " + service.name(), service.state());
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

    // --- filesystem -------------------------------------------------------------------------------

    /**
     * Lists the files on the system disk. Each entry shows the file name, its size in mB-equivalents,
     * and a {@code [RO]} marker for read-only {@code .dat} projection entries.
     */
    static final class Dir implements CliCommand {
        @Override public String name() { return "dir"; }

        @Override public List<String> aliases() { return List.of("ls"); }

        @Override public String summary() { return "list files on the system disk"; }

        @Override public String usage() { return "[directory]"; }

        @Override public void run(final CliContext ctx) {
            final String dir = ctx.hasArgs() ? ctx.arg(0) : "";
            final CliComputer.FsResult result = ctx.computer().listDisk(dir);
            if (!result.ok()) {
                ctx.out().error(result.message());
                return;
            }
            final List<CliComputer.FsEntry> entries = result.entries();
            if (entries.isEmpty()) {
                ctx.out().dim("(no files)");
                return;
            }
            for (final CliComputer.FsEntry entry : entries) {
                final String label = entry.path() + "." + entry.ext()
                        + (entry.readOnly() ? "  [RO]" : "");
                ctx.out().row(label, entry.weightMbEq() + " mB");
            }
            ctx.out().blank();
            ctx.out().dim(entries.size() + (entries.size() == 1 ? " file" : " files"));
        }
    }

    /**
     * Prints the content of a file on the system disk to the console.
     * Refuses to open {@code .dat} (read-only storage projections).
     */
    static final class Type implements CliCommand {
        @Override public String name() { return "type"; }

        @Override public List<String> aliases() { return List.of("cat"); }

        @Override public String summary() { return "print the content of a file"; }

        @Override public String usage() { return "<file>"; }

        @Override public void run(final CliContext ctx) {
            if (!ctx.hasArgs()) {
                ctx.out().error("usage: type <file>");
                return;
            }
            final CliComputer.FsResult result = ctx.computer().readFile(ctx.arg(0));
            if (!result.ok()) {
                ctx.out().error(result.message());
                return;
            }
            // Print each line of the file content as a plain output line.
            final String content = result.message();
            if (content.isEmpty()) {
                ctx.out().dim("(empty file)");
                return;
            }
            for (final String line : content.split("\n", -1)) {
                ctx.out().line(line);
            }
        }
    }

    /**
     * Deletes a file from the system disk. Refuses to delete {@code .dat} storage projections;
     * use the Network Interactor to move items out of disk storage.
     */
    static final class Del implements CliCommand {
        @Override public String name() { return "del"; }

        @Override public List<String> aliases() { return List.of("rm"); }

        @Override public String summary() { return "delete a file from the system disk"; }

        @Override public String usage() { return "<file>"; }

        @Override public void run(final CliContext ctx) {
            if (!ctx.hasArgs()) {
                ctx.out().error("usage: del <file>");
                return;
            }
            final CliComputer.FsResult result = ctx.computer().deleteFile(ctx.arg(0));
            if (!result.ok()) {
                ctx.out().error(result.message());
                return;
            }
            ctx.out().styled(result.message(), CliStyle.OK);
        }
    }

    /**
     * Creates or overwrites a file on the system disk with the given text. The file type is inferred
     * from the extension; non-editable types ({@code .dat}, {@code .log}) are refused.
     */
    static final class Write implements CliCommand {
        @Override public String name() { return "write"; }

        @Override public List<String> aliases() { return List.of("save"); }

        @Override public String summary() { return "create or overwrite a file on the system disk"; }

        @Override public String usage() { return "<file> <text...>"; }

        @Override public void run(final CliContext ctx) {
            if (ctx.argCount() < 1) {
                ctx.out().error("usage: write <file> <text...>");
                return;
            }
            final CliComputer.FsResult result = ctx.computer().writeFile(ctx.arg(0), ctx.rest(1));
            if (!result.ok()) {
                ctx.out().error(result.message());
                return;
            }
            ctx.out().styled(result.message(), CliStyle.OK);
        }
    }

    /**
     * Reads a {@code .iql} file from the system disk and executes it as an IQL statement, routing
     * through the same dispatch path as the {@code operation} command.
     */
    static final class Run implements CliCommand {
        @Override public String name() { return "run"; }

        @Override public String summary() { return "execute an .iql script from the system disk"; }

        @Override public String usage() { return "<file.iql>"; }

        @Override public void run(final CliContext ctx) {
            if (!ctx.hasArgs()) {
                ctx.out().error("usage: run <file.iql>");
                return;
            }
            final CliComputer.FsResult result = ctx.computer().runScript(ctx.arg(0));
            if (!result.ok()) {
                ctx.out().error(result.message());
                return;
            }
            // Forward the underlying OpResult style: OK in green, fail in red.
            final CliComputer.OpResult op = result.opResult();
            if (op != null) {
                ctx.out().styled(op.message(), op.ok() ? CliStyle.OK : CliStyle.ERROR);
            } else {
                ctx.out().styled(result.message(), CliStyle.OK);
            }
        }
    }
}
