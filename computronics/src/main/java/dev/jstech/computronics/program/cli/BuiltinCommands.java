/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.program.cli;

import dev.jstech.computronics.program.iql.IIqlCondition;
import dev.jstech.computronics.program.iql.IqlOperation;
import dev.jstech.computronics.program.iql.IqlParseResult;
import dev.jstech.computronics.program.iql.IqlParser;
import dev.jstech.computronics.program.iql.IqlVerb;

import java.util.List;
import java.util.Locale;

/**
 * The shell verbs that ship with the mod. Each is a small, self-contained {@link ICliCommand}; add-ons add their own the same way. They talk only to the {@link ICliComputer} facade, so the whole set is exercised in unit tests against a fake computer.
 */
public final class BuiltinCommands {

    private BuiltinCommands() {
    }

    /** Every built-in command, in the order they appear in {@code help}. */
    /** The DOS-only verbs: everything else in {@link #all()} is shared with the POSIX shell. */
    private static final java.util.Set<String> DOS_ONLY = java.util.Set.of(
            "cls", "dir", "cd", "type", "del", "write", "run", "mkdir", "rmdir", "copy", "move", "ren",
            // The POSIX shell formats with mkfs and removes packages through its package manager.
            "format", "uninstall",
            // pckmgr is the Frames package manager: a Linux distribution keeps apt/dnf/pacman/emerge,
            // and offering both on the same shell would be two doors to one room.
            "pckmgr");

    /** The verbs both shell families share (network, programs, config, maintenance); no DOS file verbs. */
    public static List<ICliCommand> shared() {
        final List<ICliCommand> out = new java.util.ArrayList<>();
        for (final ICliCommand command : all()) {
            if (!DOS_ONLY.contains(command.name())) {
                out.add(command);
            }
        }
        return out;
    }

    public static List<ICliCommand> all() {
        final List<ICliCommand> out = new java.util.ArrayList<>(base());
        // The toolchain's verbs come from the toolchain, so adding one there is enough to have it. Two
        // lists of the same commands is two lists that eventually disagree, and the one that loses is
        // always the one a player types into.
        out.addAll(CannonCommands.all());
        return List.copyOf(out);
    }

    private static List<ICliCommand> base() {
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
                new Cancel(),
                new Stats(),
                new Operation(),
                new Devices(),
                new Ssh(),
                new Exit(),
                new Pckmgr(),
                new ProgramsList(),
                new Install(),
                new Store(),
                new IqlEngineCommand(),
                new Services(),
                new Maint("analyze", "analyze"),
                new Maint("reindex", "reindex"),
                new Maint("vacuum", "vacuum"),
                new Config(),
                new Reboot(),
                new ClusterCommand(),
                new MirrorCommand(),
                new Uninstall(),
                new Format(),
                new Dir(),
                new Cd(),
                new Type(),
                new Del(),
                new Write(),
                new Run(),
                new Mkdir(),
                new Rmdir(),
                new Copy(),
                new Move(),
                new Ren());
    }

    private static String group(final long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    // --- meta -------------------------------------------------------------------------------------

    static final class Help implements ICliCommand {
        @Override public String name() {
            return "help";
        }

        @Override public List<String> aliases() {
            return List.of("?", "commands");
        }

        @Override public String summary() {
            return "list commands, or show how one is used";
        }

        @Override public String usage() {
            return "[command]";
        }

        @Override public void run(final CliContext ctx) {
            if (ctx.hasArgs()) {
                final ICliCommand command = ctx.shell().find(ctx.arg(0));
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
            for (final ICliCommand command : ctx.shell().commands()) {
                if (command.available(ctx.computer())) {
                    ctx.out().row("  " + command.name(), command.summary());
                }
            }
            ctx.out().blank();
            ctx.out().dim("'help <command>' for details");
        }
    }

    static final class Clear implements ICliCommand, CliShell.IClearMarker {
        @Override public String name() {
            return "cls";
        }

        @Override public String summary() {
            return "clear the console";
        }

        @Override public void run(final CliContext ctx) {
            // The shell clears the scrollback because this command is a ClearMarker; nothing to print.
        }
    }

    static final class Echo implements ICliCommand {
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

    static final class Version implements ICliCommand {
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

    static final class Whoami implements ICliCommand {
        @Override public String name() {
            return "whoami";
        }

        @Override public String summary() {
            return "show this computer's name and id";
        }

        @Override public void run(final CliContext ctx) {
            final ICliComputer c = ctx.computer();
            ctx.out().row("name", c.name().isEmpty() ? "(unnamed)" : c.name());
            ctx.out().row("type", c.type());
            ctx.out().row("node", c.nodeId());
        }
    }

    static final class Status implements ICliCommand {
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
            final ICliComputer c = ctx.computer();
            ctx.out().styled(c.running() ? "ONLINE" : "OFFLINE", c.running() ? CliStyle.OK : CliStyle.ERROR);
            ctx.out().row("cpu", group(c.cpuCapacity()) + " it/t");
            ctx.out().row("ram", group(c.ramBuffer()) + " it");
            ctx.out().row("network", c.onNetwork() ? "linked (" + c.networkId() + ")" : "--");
        }
    }

    /**
     * A remote shell on another machine of the same network — the route that makes a headless rack
     * server administrable from any terminal. {@code ssh} with no argument lists what is reachable;
     * {@code exit} on a connected session comes back to the local shell.
     */
    static final class Ssh implements ICliCommand {
        @Override public String name() {
            return "ssh";
        }

        @Override public String summary() {
            return "open a shell on another computer of this network";
        }

        @Override public String usage() {
            return "ssh [hostname]";
        }

        @Override public void run(final CliContext ctx) {
            final ICliComputer computer = ctx.computer();
            if (!ctx.hasArgs()) {
                final List<ICliComputer.RemoteHost> hosts = computer.reachableHosts();
                if (hosts.isEmpty()) {
                    ctx.out().error("ssh: no other computers reachable on this network");
                    return;
                }
                ctx.out().line("Reachable hosts:");
                for (final ICliComputer.RemoteHost host : hosts) {
                    // Name what the player can actually type: the host name, the machine's own name
                    // and its node id all address it.
                    final StringBuilder detail = new StringBuilder();
                    if (!host.name().isEmpty() && !host.name().equalsIgnoreCase(host.hostname())) {
                        detail.append('"').append(host.name()).append("\"  ");
                    }
                    detail.append("node ").append(host.nodeId()).append("  ").append(host.type());
                    if (!host.os().isEmpty()) {
                        detail.append("  ").append(host.os());
                    }
                    if (!host.running()) {
                        detail.append("  (offline)");
                    }
                    ctx.out().row("  " + host.hostname(), detail.toString());
                }
                ctx.out().line("");
                ctx.out().line("ssh <host name | machine name | node | os> to connect; exit to come back.");
                return;
            }
            final ICliComputer.OpResult result = computer.sshConnect(ctx.arg(0));
            if (result.ok()) {
                ctx.out().ok(result.message());
            } else {
                ctx.out().error(result.message());
            }
        }
    }

    /**
     * The package manager every Frames edition ships with: one verb set over the network Mirror, so a
     * player who never touches a Linux distribution still installs, removes, searches and updates
     * software the same way. Linux distributions keep their own managers (apt, dnf, pacman, emerge);
     * this is the Frames-side equivalent, and it speaks to the same Mirror.
     */
    static final class Pckmgr implements ICliCommand {
        @Override public String name() {
            return "pckmgr";
        }

        @Override public String summary() {
            return "install, remove, search and update packages from the network mirror";
        }

        @Override public String usage() {
            return "pckmgr install|remove|search|list|update [name]";
        }

        @Override public void run(final CliContext ctx) {
            final ICliComputer computer = ctx.computer();
            final String verb = ctx.hasArgs() ? ctx.arg(0).toLowerCase(Locale.ROOT) : "";
            switch (verb) {
                case "install" -> requireName(ctx, computer::packageInstall);
                case "remove", "uninstall" -> requireName(ctx, computer::packageRemove);
                case "update", "upgrade" -> report(ctx, computer.packageUpdate());
                // search always looks at the whole shelf; list shows this computer's packages unless
                // --available asks for everything the mirror offers.
                case "search" -> listPackages(ctx, ctx.argCount() > 1 ? ctx.arg(1) : "", false);
                case "list" -> {
                    final String flag = ctx.argCount() > 1 ? ctx.arg(1) : "";
                    // A mistyped flag must say so: silently listing something else is how a typo
                    // becomes "the feature is broken".
                    if (!flag.isEmpty() && !flag.equalsIgnoreCase("--available")) {
                        ctx.out().error("pckmgr list: unknown option " + flag + " (did you mean --available?)");
                    } else {
                        listPackages(ctx, "", flag.isEmpty());
                    }
                }
                default -> {
                    ctx.out().error("usage: " + usage());
                    ctx.out().line("  install <name>   fetch and set up a package");
                    ctx.out().line("  remove <name>    uninstall a package");
                    ctx.out().line("  search [text]    find packages the mirror offers");
                    ctx.out().line("  list [--available]  installed packages, or everything on offer");
                    ctx.out().line("  update           bring installed packages to the current build");
                }
            }
        }

        private static void requireName(final CliContext ctx,
                                        final java.util.function.Function<String, ICliComputer.OpResult> action) {
            if (ctx.argCount() < 2) {
                ctx.out().error("pckmgr: this verb needs a package name");
                return;
            }
            report(ctx, action.apply(ctx.arg(1)));
        }

        private static void report(final CliContext ctx, final ICliComputer.OpResult result) {
            if (result.ok()) {
                ctx.out().ok(result.message());
            } else {
                ctx.out().error(result.message());
            }
        }

        /**
         * Lists packages from the mirror, marking what this computer already has.
         *
         * @param filter        matches name or description; empty matches everything
         * @param onlyInstalled true for plain {@code list} (this computer's packages), false for
         *                      {@code search} and {@code list --available} (the whole shelf)
         */
        private static void listPackages(final CliContext ctx, final String filter,
                                         final boolean onlyInstalled) {
            final List<ICliComputer.PackageInfo> packages = ctx.computer().packagesAvailable();
            if (packages.isEmpty()) {
                ctx.out().error("could not resolve mirror:// - no package source reachable");
                return;
            }
            final String needle = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
            int shown = 0;
            for (final ICliComputer.PackageInfo info : packages) {
                if (onlyInstalled && !info.installed()) {
                    continue;
                }
                if (!needle.isEmpty() && !info.name().toLowerCase(Locale.ROOT).contains(needle)
                        && !info.description().toLowerCase(Locale.ROOT).contains(needle)) {
                    continue;
                }
                // Something another player wrote says so. Whether to install it is then an informed
                // choice rather than a guess about where it came from.
                final String state = info.building() ? "building"
                        : info.installed() ? "installed"
                                : info.community() ? "community" : "available";
                ctx.out().row("  " + info.name() + "  [" + state + "]", info.description());
                shown++;
            }
            if (shown == 0) {
                ctx.out().line(onlyInstalled ? "No packages installed."
                        : needle.isEmpty() ? "The mirror offers nothing for this computer."
                                : "No package matches " + filter + ".");
            }
        }
    }

    /** Leaves a remote shell. With no session open there is nothing to leave but the window. */
    static final class Exit implements ICliCommand {
        @Override public String name() {
            return "exit";
        }

        @Override public List<String> aliases() {
            return List.of("logout");
        }

        @Override public String summary() {
            return "close the remote shell and return to this computer";
        }

        @Override public void run(final CliContext ctx) {
            final ICliComputer.OpResult result = ctx.computer().sshDisconnect();
            if (result.ok()) {
                ctx.out().ok(result.message());
            } else {
                ctx.out().error(result.message());
            }
        }
    }

    static final class Net implements ICliCommand {
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
            final ICliComputer.NetSummary n = ctx.computer().network();
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

    static final class Devices implements ICliCommand {
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

    static final class ProgramsList implements ICliCommand {
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
            final List<ICliComputer.ProgramInfo> programs = ctx.computer().programs();
            if (programs.isEmpty()) {
                ctx.out().dim("no programs installed");
                return;
            }
            for (final ICliComputer.ProgramInfo program : programs) {
                ctx.out().row("  " + program.name(), program.id());
            }
        }
    }

    // --- storage ----------------------------------------------------------------------------------

    static final class Find implements ICliCommand {
        @Override public String name() {
            return "find";
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
            final List<ICliComputer.Holding> holdings = ctx.computer().find(ctx.rest(0));
            if (holdings.isEmpty()) {
                ctx.out().dim("no server holds '" + ctx.rest(0) + "'");
                return;
            }
            for (final ICliComputer.Holding holding : holdings) {
                ctx.out().row(holding.server(), group(holding.quantity()));
            }
        }
    }

    // --- operations -------------------------------------------------------------------------------

    static final class Lock implements ICliCommand {
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
            final ICliComputer.OpResult result = ctx.computer().lock(item, qty > 0L ? qty : 0L);
            ctx.out().styled(result.message(), result.ok() ? CliStyle.OK : CliStyle.ERROR);
        }
    }

    static final class Unlock implements ICliCommand {
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
            final ICliComputer.OpResult result = ctx.computer().unlock(ctx.rest(0));
            ctx.out().styled(result.message(), result.ok() ? CliStyle.OK : CliStyle.ERROR);
        }
    }

    static final class Locks implements ICliCommand {
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
            final List<ICliComputer.StoredItem> held = ctx.computer().locks();
            if (held.isEmpty()) {
                ctx.out().dim("no items are locked");
                return;
            }
            for (final ICliComputer.StoredItem row : held) {
                ctx.out().row(row.name(), group(row.quantity()));
            }
        }
    }

    static final class Ops implements ICliCommand {
        @Override public String name() {
            return "ops";
        }

        @Override public List<String> aliases() {
            return List.of("jobs");
        }

        @Override public String summary() {
            return "list operations in flight";
        }

        @Override public void run(final CliContext ctx) {
            final List<ICliComputer.ActiveOp> ops = ctx.computer().activeOps();
            if (ops.isEmpty()) {
                ctx.out().dim("no operations running");
                return;
            }
            for (final ICliComputer.ActiveOp op : ops) {
                final String head = op.id() + "  " + op.type() + " " + op.item();
                final String tail = op.priority() + " " + op.status() + " " + group(op.progress()) + "/"
                        + group(op.total());
                ctx.out().row(head, tail);
            }
        }
    }

    static final class Stats implements ICliCommand {
        @Override public String name() {
            return "stats";
        }

        @Override public List<String> aliases() {
            return List.of("statistics");
        }

        @Override public String summary() {
            return "the network's operations over the last hour";
        }

        @Override public void run(final CliContext ctx) {
            if (!ctx.computer().onNetwork()) {
                ctx.out().error("not on a network");
                return;
            }
            final List<ICliComputer.OperationStat> stats = ctx.computer().operationStats();
            ctx.out().dim("peak " + ctx.computer().peakOperationsToday() + " in flight today");
            if (stats.isEmpty()) {
                ctx.out().dim("no operations settled in the last hour");
                return;
            }
            for (final ICliComputer.OperationStat stat : stats) {
                final String tail = stat.count() + " ops  wait " + ticks(stat.averageWait()) + "  run "
                        + ticks(stat.averageRun()) + "  fail " + stat.shortfallPercent() + "%  moved "
                        + group(stat.moved());
                ctx.out().row(stat.type(), tail);
            }
        }

        private static String ticks(final int ticks) {
            return ticks >= 1200 ? (ticks / 20) + "s" : ticks + "t";
        }
    }

    static final class Cancel implements ICliCommand {
        @Override public String name() {
            return "cancel";
        }

        @Override public List<String> aliases() {
            return List.of("kill");
        }

        @Override public String summary() {
            return "stop an operation in flight";
        }

        @Override public String usage() {
            return "<id>";
        }

        @Override public void run(final CliContext ctx) {
            if (!ctx.hasArgs()) {
                ctx.out().error("usage: cancel <id>   (the id column of 'ops')");
                return;
            }
            if (!ctx.computer().onNetwork()) {
                ctx.out().error("not on a network");
                return;
            }
            final ICliComputer.OpResult result = ctx.computer().cancelOperation(ctx.arg(0));
            ctx.out().styled(result.message(), result.ok() ? CliStyle.OK : CliStyle.ERROR);
        }
    }

    static final class Operation implements ICliCommand {
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
                final List<ICliComputer.StoredItem> items = ctx.computer().queryObject(op.item(),
                        op.where(), "", limit);
                if (items.isEmpty()) {
                    ctx.out().dim("no rows");
                    return;
                }
                for (final ICliComputer.StoredItem item : items) {
                    ctx.out().row(item.detail().isEmpty() ? item.name() : item.name() + " · " + item.detail(),
                            group(item.quantity()));
                }
                return;
            }
            final ICliComputer.OpResult result = ctx.computer().execute(op);
            ctx.out().styled(result.message(), result.ok() ? CliStyle.OK : CliStyle.ERROR);
        }
    }

    static final class Install implements ICliCommand {
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
            final ICliComputer.OpResult result = ctx.computer().install(ctx.arg(0));
            ctx.out().styled(result.message(), result.ok() ? CliStyle.OK : CliStyle.ERROR);
        }
    }

    static final class Uninstall implements ICliCommand {
        @Override public String name() {
            return "uninstall";
        }

        @Override public String summary() {
            return "remove an installed program from this computer";
        }

        @Override public String usage() {
            return "<program-id>";
        }

        @Override public void run(final CliContext ctx) {
            if (!ctx.hasArgs()) {
                ctx.out().error("usage: uninstall <program-id>   (see 'programs')");
                return;
            }
            final ICliComputer.OpResult result = ctx.computer().packageRemove(ctx.arg(0));
            ctx.out().styled(result.message(), result.ok() ? CliStyle.OK : CliStyle.ERROR);
        }
    }

    static final class Format implements ICliCommand {
        @Override public String name() {
            return "format";
        }

        @Override public String summary() {
            return "erase everything on a drive";
        }

        @Override public String usage() {
            return "<drive>: [/y]";
        }

        @Override public void run(final CliContext ctx) {
            if (!ctx.hasArgs()) {
                ctx.out().error("usage: format <drive>: [/y]");
                return;
            }
            final String arg = ctx.arg(0).toUpperCase(Locale.ROOT);
            if (arg.isEmpty() || !Character.isLetter(arg.charAt(0))) {
                ctx.out().error("format: invalid drive: " + ctx.arg(0));
                return;
            }
            final char drive = arg.charAt(0);
            // The real format asks before destroying a volume; a stateless shell asks for the /y flag.
            final boolean confirmed = ctx.argCount() > 1 && ctx.arg(1).equalsIgnoreCase("/y");
            if (!confirmed) {
                ctx.out().styled("WARNING: ALL DATA ON DRIVE " + drive + ": WILL BE LOST!", CliStyle.ERROR);
                ctx.out().dim("Run 'format " + drive + ": /y' to proceed.");
                return;
            }
            final ICliComputer.OpResult result = ctx.computer().formatDrive(drive);
            for (final String line : result.message().split("\n", -1)) {
                ctx.out().styled(line, result.ok() ? CliStyle.OK : CliStyle.ERROR);
            }
        }
    }

    static final class Store implements ICliCommand {
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
            for (final dev.jstech.computronics.os.ProgramSpec program
                    : dev.jstech.computronics.program.Programs.all()) {
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

    static final class IqlEngineCommand implements ICliCommand {
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

        @Override public boolean available(final ICliComputer computer) {
            return computer.iqlEngineInstalled(); // shown only after 'install iqlengine'
        }

        @Override public void run(final CliContext ctx) {
            final ICliComputer.OpResult result = ctx.computer().engineControl(ctx.hasArgs() ? ctx.arg(0) : "status");
            ctx.out().styled(result.message(), result.ok() ? CliStyle.OK : CliStyle.ERROR);
        }
    }

    static final class Services implements ICliCommand {
        @Override public String name() {
            return "services";
        }

        @Override public String summary() {
            return "list the network's services and their state";
        }

        @Override public boolean available(final ICliComputer computer) {
            return computer.iqlEngineInstalled();
        }

        @Override public void run(final CliContext ctx) {
            final List<ICliComputer.ServiceStatus> services = ctx.computer().services();
            if (services.isEmpty()) {
                ctx.out().dim("no services");
                return;
            }
            for (final ICliComputer.ServiceStatus service : services) {
                ctx.out().row("  " + service.name(), service.state());
            }
        }
    }

    static final class Maint implements ICliCommand {
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
            final ICliComputer.OpResult result = ctx.computer().maintenance(action);
            ctx.out().styled(result.message(), result.ok() ? CliStyle.OK : CliStyle.ERROR);
        }
    }

    // --- filesystem -------------------------------------------------------------------------------

    /**
     * Lists the files on the system disk. Each entry shows the file name, its size in mB-equivalents,
     * and a {@code [RO]} marker for read-only {@code .dat} projection entries.
     */
    /**
     * Restarts the computer. With {@code --firmware} the restart lands in the firmware setup (the boot
     * manager) instead of the installed OS, which is how the player reaches it once a system is installed.
     */
    /** Installs or reports the Mirror, the Mainframe's package repository the Linux package managers use. */
    static final class MirrorCommand implements ICliCommand {
        @Override public String name() { return "mirror"; }

        @Override public String summary() { return "install or check the Mirror package service on the Mainframe"; }

        @Override public String usage() { return "install|status"; }

        @Override public void run(final CliContext ctx) {
            final ICliComputer.OpResult result = ctx.computer().mirrorControl(ctx.hasArgs() ? ctx.arg(0) : "status");
            if (result.ok()) {
                ctx.out().ok(result.message());
            } else {
                ctx.out().error(result.message());
            }
        }
    }

    static final class Reboot implements ICliCommand {
        @Override public String name() { return "reboot"; }

        @Override public List<String> aliases() { return List.of("restart"); }

        @Override public String summary() { return "restart the computer (--firmware: into the firmware setup)"; }

        @Override public String usage() { return "[--firmware]"; }

        @Override public void run(final CliContext ctx) {
            final boolean firmware = ctx.hasArgs() && ctx.arg(0).equals("--firmware");
            if (firmware) {
                ctx.out().dim("Restarting into the firmware setup ...");
                ctx.computer().requestFirmwareReboot();
            } else {
                ctx.out().dim("The system is going down for reboot NOW!");
                ctx.computer().requestReboot();
            }
        }
    }

    static final class Dir implements ICliCommand {
        @Override public String name() { return "dir"; }

        @Override public String summary() { return "list the contents of a directory"; }

        @Override public String usage() { return "[directory]"; }

        @Override public void run(final CliContext ctx) {
            final String dir = ctx.hasArgs() ? ctx.rest(0) : "";
            final ICliComputer.FsResult result = ctx.computer().listDisk(dir);
            if (!result.ok()) {
                ctx.out().error(result.message());
                return;
            }
            ctx.out().accent(" Directory of "
                    + DosPath.resolve(ctx.computer().currentLocation(), dir).dosPath());
            ctx.out().blank();
            final List<ICliComputer.FsEntry> entries = result.entries();
            if (entries.isEmpty()) {
                ctx.out().dim("File Not Found");
                return;
            }
            int dirs = 0;
            int files = 0;
            long bytes = 0L;
            for (final ICliComputer.FsEntry entry : entries) {
                final String stamp = formatStamp(entry.modified());
                if (entry.isDir()) {
                    dirs++;
                    ctx.out().row(entry.name() + "  <DIR>", stamp);
                } else {
                    files++;
                    bytes += entry.weightMbEq();
                    final String label = entry.name() + (entry.readOnly() ? "  [RO]" : "");
                    ctx.out().row(label, group(entry.weightMbEq()) + " mB   " + stamp);
                }
            }
            ctx.out().blank();
            ctx.out().dim(group(files) + " File(s), " + group(dirs) + " Dir(s), "
                    + group(bytes) + " mB");
        }
    }

    /**
     * Formats a file's world-time stamp (total ticks) as an in-game day and clock, e.g.
     * {@code "Day 12  08:15"}. A stamp of {@code 0} (unknown, e.g. a virtual .dat projection or a
     * file written before timestamps existed) renders as a short placeholder.
     */
    private static String formatStamp(final long ticks) {
        if (ticks <= 0L) {
            return "  --  ";
        }
        final long day = ticks / 24_000L;
        final long timeOfDay = ticks % 24_000L;
        // Minecraft tick 0 is 06:00; each in-game hour is 1000 ticks.
        final long hour = ((timeOfDay / 1000L) + 6L) % 24L;
        final long minute = (timeOfDay % 1000L) * 60L / 1000L;
        return String.format(Locale.ROOT, "Day %d  %02d:%02d", day, hour, minute);
    }

    /**
     * Prints the content of a file on the system disk to the console.
     * Refuses to open {@code .dat} (read-only storage projections).
     */
    static final class Type implements ICliCommand {
        @Override public String name() { return "type"; }

        @Override public String summary() { return "print the content of a file"; }

        @Override public String usage() { return "<file>"; }

        @Override public void run(final CliContext ctx) {
            if (!ctx.hasArgs()) {
                ctx.out().error("usage: type <file>");
                return;
            }
            final ICliComputer.FsResult result = ctx.computer().readFile(ctx.arg(0));
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
    static final class Del implements ICliCommand {
        @Override public String name() { return "del"; }

        @Override public List<String> aliases() { return List.of("erase"); }

        @Override public String summary() { return "delete a file from the system disk"; }

        @Override public String usage() { return "<file>"; }

        @Override public void run(final CliContext ctx) {
            if (!ctx.hasArgs()) {
                ctx.out().error("usage: del <file>");
                return;
            }
            final ICliComputer.FsResult result = ctx.computer().deleteFile(ctx.arg(0));
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
    static final class Write implements ICliCommand {
        @Override public String name() { return "write"; }

        @Override public List<String> aliases() { return List.of("save"); }

        @Override public String summary() { return "create or overwrite a file on the system disk"; }

        @Override public String usage() { return "<file> <text...>"; }

        @Override public void run(final CliContext ctx) {
            if (ctx.argCount() < 1) {
                ctx.out().error("usage: write <file> <text...>");
                return;
            }
            final ICliComputer.FsResult result = ctx.computer().writeFile(ctx.arg(0), ctx.rest(1));
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
    static final class Run implements ICliCommand {
        @Override public String name() { return "run"; }

        @Override public String summary() { return "execute an .iql script from the system disk"; }

        @Override public String usage() { return "<file.iql>"; }

        @Override public void run(final CliContext ctx) {
            if (!ctx.hasArgs()) {
                ctx.out().error("usage: run <file.iql>");
                return;
            }
            final ICliComputer.FsResult result = ctx.computer().runScript(ctx.arg(0));
            if (!result.ok()) {
                ctx.out().error(result.message());
                return;
            }
            // Forward the underlying OpResult style: OK in green, fail in red.
            final ICliComputer.OpResult op = result.opResult();
            if (op != null) {
                ctx.out().styled(op.message(), op.ok() ? CliStyle.OK : CliStyle.ERROR);
            } else {
                ctx.out().styled(result.message(), CliStyle.OK);
            }
        }
    }

    /**
     * Shows or changes the current directory. With no argument it prints the current path (DOS
     * behaviour); with a path it changes to that directory relative to the current one.
     */
    static final class Cd implements ICliCommand {
        @Override public String name() { return "cd"; }

        @Override public List<String> aliases() { return List.of("chdir"); }

        @Override public String summary() { return "show or change the current directory"; }

        @Override public String usage() { return "[directory]"; }

        @Override public void run(final CliContext ctx) {
            if (!ctx.hasArgs()) {
                ctx.out().line(ctx.computer().currentLocation().dosPath());
                return;
            }
            final ICliComputer.FsResult result = ctx.computer().changeDir(ctx.rest(0));
            if (!result.ok()) {
                ctx.out().error(result.message());
            }
        }
    }

    /** Creates a directory on the current drive. */
    static final class Mkdir implements ICliCommand {
        @Override public String name() { return "mkdir"; }

        @Override public List<String> aliases() { return List.of("md"); }

        @Override public String summary() { return "create a directory"; }

        @Override public String usage() { return "<directory>"; }

        @Override public void run(final CliContext ctx) {
            if (!ctx.hasArgs()) {
                ctx.out().error("usage: mkdir <directory>");
                return;
            }
            final ICliComputer.FsResult result = ctx.computer().makeDir(ctx.rest(0));
            if (!result.ok()) {
                ctx.out().error(result.message());
            }
        }
    }

    /** Removes an empty directory from the current drive. */
    static final class Rmdir implements ICliCommand {
        @Override public String name() { return "rmdir"; }

        @Override public List<String> aliases() { return List.of("rd"); }

        @Override public String summary() { return "remove an empty directory"; }

        @Override public String usage() { return "<directory>"; }

        @Override public void run(final CliContext ctx) {
            if (!ctx.hasArgs()) {
                ctx.out().error("usage: rmdir <directory>");
                return;
            }
            final ICliComputer.FsResult result = ctx.computer().removeDir(ctx.rest(0));
            if (!result.ok()) {
                ctx.out().error(result.message());
            }
        }
    }

    /** Copies a file (or directory subtree) to a new location. */
    static final class Copy implements ICliCommand {
        @Override public String name() { return "copy"; }

        @Override public String summary() { return "copy a file to another location"; }

        @Override public String usage() { return "<source> <destination>"; }

        @Override public void run(final CliContext ctx) {
            if (ctx.argCount() < 2) {
                ctx.out().error("usage: copy <source> <destination>");
                return;
            }
            final ICliComputer.FsResult result = ctx.computer().copyPath(ctx.arg(0), ctx.arg(1));
            if (!result.ok()) {
                ctx.out().error(result.message());
                return;
            }
            ctx.out().styled(result.message(), CliStyle.OK);
        }
    }

    /** Moves a file (or directory subtree) into another directory. */
    static final class Move implements ICliCommand {
        @Override public String name() { return "move"; }

        @Override public String summary() { return "move a file into another directory"; }

        @Override public String usage() { return "<source> <directory>"; }

        @Override public void run(final CliContext ctx) {
            if (ctx.argCount() < 2) {
                ctx.out().error("usage: move <source> <directory>");
                return;
            }
            final ICliComputer.FsResult result = ctx.computer().movePath(ctx.arg(0), ctx.arg(1));
            if (!result.ok()) {
                ctx.out().error(result.message());
                return;
            }
            ctx.out().styled(result.message(), CliStyle.OK);
        }
    }

    /** Shows or changes this computer's settings — the MC-DOS front-end for the Settings app. */
    static final class Config implements ICliCommand {
        @Override public String name() { return "config"; }

        @Override public String summary() { return "show or change this computer's settings"; }

        @Override public String usage() { return "[key] [value]"; }

        @Override public void run(final CliContext ctx) {
            if (!ctx.hasArgs()) {
                final List<String> lines = ctx.computer().configSummary();
                if (lines.isEmpty()) {
                    ctx.out().error("this computer has no settings store");
                    return;
                }
                ctx.out().header("settings");
                for (final String line : lines) {
                    ctx.out().line(line);
                }
                ctx.out().dim("'config <key> <value>' to change one");
                return;
            }
            if (ctx.argCount() < 2) {
                ctx.out().error("usage: config <key> <value>  (or 'config' to list)");
                return;
            }
            final ICliComputer.OpResult result = ctx.computer().setConfig(ctx.arg(0), ctx.rest(1));
            ctx.out().styled(result.message(), result.ok() ? CliStyle.OK : CliStyle.ERROR);
        }
    }

    /** Renames a file or directory in place. */
    static final class Ren implements ICliCommand {
        @Override public String name() { return "ren"; }

        @Override public List<String> aliases() { return List.of("rename"); }

        @Override public String summary() { return "rename a file or directory"; }

        @Override public String usage() { return "<file> <new name>"; }

        @Override public void run(final CliContext ctx) {
            if (ctx.argCount() < 2) {
                ctx.out().error("usage: ren <file> <new name>");
                return;
            }
            final ICliComputer.FsResult result = ctx.computer().renamePath(ctx.arg(0), ctx.arg(1));
            if (!result.ok()) {
                ctx.out().error(result.message());
            }
        }
    }
}
