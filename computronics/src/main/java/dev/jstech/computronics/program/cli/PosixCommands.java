/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.program.cli;

import java.util.List;
import java.util.Locale;

/**
 * The POSIX command set: what a shell on a {@code ShellFamily.POSIX} kernel (Linux) speaks. Every file verb
 * converts its path arguments through {@link PosixPath#toDos} and then calls the same filesystem facade the
 * DOS shell uses, so the two families share one storage model and differ only in syntax and presentation.
 * The network and program verbs are shared with the DOS set (see {@link BuiltinCommands#shared()}).
 */
public final class PosixCommands {

    private PosixCommands() {
    }

    /** The POSIX-only commands (the shared verbs are added by {@link CliCommands}). */
    public static List<CliCommand> all() {
        return List.of(
                new Ls(),
                new Pwd(),
                new Cd(),
                new Cat(),
                new Rm(),
                new Mkdir(),
                new Rmdir(),
                new Cp(),
                new Mv(),
                new Touch(),
                new Write(),
                new Run(),
                new Clear(),
                new Man(),
                new Uname(),
                new Hostname(),
                new Df(),
                new Mkfs(),
                new Screenfetch(),
                // One package manager per distribution family; each is only available on the OS that ships it.
                new PackageManagerCommand(dev.jstech.computronics.os.PackageManagerKind.APT),
                new PackageManagerCommand(dev.jstech.computronics.os.PackageManagerKind.DNF),
                new PackageManagerCommand(dev.jstech.computronics.os.PackageManagerKind.PACMAN),
                new PackageManagerCommand(dev.jstech.computronics.os.PackageManagerKind.EMERGE));
    }

    /**
     * A distribution's package manager ({@code apt}, {@code dnf}, {@code pacman}, {@code emerge}), speaking its
     * own flags but all resolving packages against the network's Mirror service. Only the manager the installed
     * OS ships is available, so {@code apt} does not exist on Arch and {@code pacman} does not exist on Ubuntu.
     */
    static final class PackageManagerCommand implements CliCommand {
        private final dev.jstech.computronics.os.PackageManagerKind kind;

        PackageManagerCommand(final dev.jstech.computronics.os.PackageManagerKind kind) {
            this.kind = kind;
        }

        @Override public String name() { return kind.command(); }

        @Override public String summary() { return "install and remove packages from the network mirror"; }

        // Removal is listed here on purpose: a verb the shell accepts but never advertises may as well
        // not exist, since the only way to find it is to already know it.
        @Override public String usage() {
            return switch (kind) {
                case PACMAN -> "-S <package> | -R <package> | -Ss [term] | -Q | -Syu";
                case EMERGE -> "<package> | --unmerge <package> | --search [term] | --sync | --status";
                default -> "install <package> | remove <package> | search [term] | list | update";
            };
        }

        @Override public boolean available(final CliComputer computer) {
            return computer.packageManager() == kind;
        }

        @Override public void run(final CliContext ctx) {
            final String verb = ctx.hasArgs() ? ctx.arg(0) : "";
            final String arg = ctx.argCount() > 1 ? ctx.rest(1) : "";
            switch (kind) {
                case PACMAN -> {
                    switch (verb) {
                        case "-S" -> install(ctx, arg);
                        case "-Ss" -> search(ctx, arg);
                        case "-Q" -> installed(ctx);
                        case "-Syu", "-Sy" -> sync(ctx);
                        case "-R", "-Rs", "-Rns" -> remove(ctx, arg);
                        default -> ctx.out().error("usage: pacman " + usage());
                    }
                }
                case EMERGE -> {
                    switch (verb) {
                        case "--search", "-s" -> search(ctx, arg);
                        case "--sync" -> sync(ctx);
                        case "--status" -> builds(ctx);
                        case "" -> ctx.out().error("usage: emerge " + usage());
                        case "--ask", "-a" -> install(ctx, arg);
                        case "--unmerge", "-C", "--depclean", "-c" -> remove(ctx, arg);
                        default -> install(ctx, ctx.rest(0));
                    }
                }
                default -> {
                    switch (verb) {
                        case "install" -> install(ctx, arg);
                        case "search" -> search(ctx, arg);
                        case "list" -> installed(ctx);
                        case "update", "upgrade" -> sync(ctx);
                        case "remove", "purge", "erase" -> remove(ctx, arg);
                        default -> ctx.out().error("usage: " + kind.command() + " " + usage());
                    }
                }
            }
        }

        private void sync(final CliContext ctx) {
            if (!ctx.computer().mirrorReachable()) {
                ctx.out().error("Err: could not resolve mirror://");
                ctx.out().dim("  This computer is not on a network whose Mainframe runs the Mirror service.");
                return;
            }
            ctx.out().dim(kind == dev.jstech.computronics.os.PackageManagerKind.PACMAN
                    ? ":: Synchronizing package databases (mirror://mainframe) ... done"
                    : "Reading package lists from mirror://mainframe ... Done");
        }

        private void install(final CliContext ctx, final String pkg) {
            if (pkg.isBlank()) {
                ctx.out().error("usage: " + kind.command() + " " + usage());
                return;
            }
            final String name = pkg.trim().split("\\s+")[0];
            ctx.out().dim("Resolving mirror://mainframe ...");
            final CliComputer.OpResult result = ctx.computer().packageInstall(name);
            if (result.ok()) {
                ctx.out().ok(result.message());
            } else {
                ctx.out().error("E: " + result.message());
            }
        }

        private void remove(final CliContext ctx, final String pkg) {
            if (pkg.isBlank()) {
                ctx.out().error("usage: " + kind.command() + " " + usage());
                return;
            }
            final String name = pkg.trim().split("\\s+")[0];
            final CliComputer.OpResult result = ctx.computer().packageRemove(name);
            if (result.ok()) {
                ctx.out().ok(result.message());
            } else {
                ctx.out().error("E: " + result.message());
            }
        }

        private void search(final CliContext ctx, final String term) {
            if (!ctx.computer().mirrorReachable()) {
                ctx.out().error("Err: could not resolve mirror://");
                return;
            }
            final String needle = term.trim().toLowerCase(Locale.ROOT);
            boolean any = false;
            for (final CliComputer.PackageInfo p : ctx.computer().packagesAvailable()) {
                if (!needle.isEmpty() && !p.name().contains(needle)
                        && !p.description().toLowerCase(Locale.ROOT).contains(needle)) {
                    continue;
                }
                any = true;
                ctx.out().row(p.name() + (p.installed() ? "  [installed]"
                                : p.building() ? "  [building]"
                                        : p.community() ? "  [community]" : ""),
                        p.description());
            }
            if (!any) {
                ctx.out().dim("no packages match '" + needle + "'");
            }
        }

        private void installed(final CliContext ctx) {
            boolean any = false;
            for (final CliComputer.PackageInfo p : ctx.computer().packagesAvailable()) {
                if (p.installed()) {
                    any = true;
                    ctx.out().row(p.name(), p.description());
                }
            }
            if (!any) {
                ctx.out().dim(ctx.computer().mirrorReachable() ? "no packages installed" : "could not resolve mirror://");
            }
        }

        private void builds(final CliContext ctx) {
            final java.util.Map<String, Long> builds = ctx.computer().buildsRemaining();
            if (builds.isEmpty()) {
                ctx.out().dim("no builds in progress");
                return;
            }
            builds.forEach((id, ticks) -> {
                // Show the package name the way it was typed (the id path), not the registry namespace.
                final String name = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
                ctx.out().row(">>> " + name, ticks <= 0 ? "done" : "compiling ... " + (ticks / 20) + "s left");
            });
            ctx.out().dim("(the shell announces each build when it finishes)");
        }
    }

    static final class Mkfs implements CliCommand {
        @Override public String name() { return "mkfs.ext4"; }

        @Override public java.util.List<String> aliases() { return java.util.List.of("mkfs"); }

        @Override public String summary() { return "build a filesystem on a device (erases it)"; }

        @Override public String usage() { return "/dev/<device>"; }

        @Override public void run(final CliContext ctx) {
            if (!ctx.hasArgs()) {
                ctx.out().error("usage: mkfs.ext4 /dev/<device>   (see df for the devices)");
                return;
            }
            final String raw = ctx.arg(0);
            final String device = raw.startsWith("/dev/") ? raw.substring(5) : raw;
            // Resolve the device name back to its drive: "sda" also matches the "sda1" system partition.
            for (final CliComputer.MountInfo mount : ctx.computer().mounts()) {
                if (mount.device().equals(device) || mount.device().startsWith(device)) {
                    final CliComputer.OpResult result = ctx.computer().formatDrive(mount.drive());
                    if (result.ok()) {
                        ctx.out().dim("mke2fs 1.47 (JSC)");
                        ctx.out().ok("Creating filesystem on /dev/" + device + " ... done");
                    } else {
                        ctx.out().error("mkfs.ext4: " + result.message());
                    }
                    return;
                }
            }
            ctx.out().error("mkfs.ext4: cannot open /dev/" + device + ": No such device");
        }
    }

    static final class Screenfetch implements CliCommand {

        // One small ASCII mark per distribution, printed beside the system readout.
        private static final java.util.Map<String, String[]> LOGOS = java.util.Map.of(
                "ubuntu", new String[]{
                        "                          ./+o+-",
                        "                  yyyyy- -yyyyyy+",
                        "               ://+//////-yyyyyyo",
                        "           .++ .:/++++++/-.+sss/`",
                        "         .:++o:  /++++++++/:--:/-",
                        "        o:+o+:++.`..```.-/oo+++++/",
                        "       .:+o:+o/.          `+sssoo+/",
                        "  .++/+:+oo+o:`             /sssooo.",
                        " /+++//+:`oo+o               /::--:.",
                        " \\+/+o+++`o++o               ++////.",
                        "  .++.o+++oo+:`             /dddhhh.",
                        "       .+.o+oo:.          `oddhhhh+",
                        "        \\+.++o+o``-````.:ohdhhhhh+",
                        "         `:o+++ `ohhhhhhhhyo++os:",
                        "           .o:`.syhhhhhhh/.oo++o`",
                        "               /osyyyyyyo++ooo+++/",
                        "              ````` +oo+++o\\:",
                        "                          `oo++."},
                "debian", new String[]{
                        "       _,met$$$$$gg.",
                        "    ,g$$$$$$$$$$$$$$$P.",
                        "  ,g$$P\"\"       \"\"\"Y$$.\".",
                        " ,$$P'              `$$$.",
                        "',$$P       ,ggs.     `$$b:",
                        "`d$$'     ,$P\"'   .    $$$",
                        " $$P      d$'     ,    $$P",
                        " $$:      $$.   -    ,d$$'",
                        " $$;      Y$b._   _,d$P'",
                        " Y$$.    `.`\"Y$$$$P\"'",
                        " `$$b      \"-.__",
                        "  `Y$$",
                        "   `Y$$.",
                        "     `$$b.",
                        "       `Y$$b.",
                        "          `\"Y$b._",
                        "              `\"\"\""},
                "fedora", new String[]{
                        "          /:-------------:\\",
                        "       :-------------------::",
                        "     :-----------/shhOHbmp---:\\",
                        "   /-----------omMMMNNNMMD  ---:",
                        "  :-----------sMMMMNMNMP.    ---:",
                        " :-----------:MMMdP-------    ---\\",
                        ",------------:MMMd--------    ---:",
                        ":------------:MMMd-------    .---:",
                        ":----    oNMMMMMMMMMNho     .----:",
                        ":--     .+shhhMMMmhhy++   .------/",
                        ":-    -------:MMMd--------------:",
                        ":-   --------/MMMd-------------;",
                        ":-    ------/hMMMy------------:",
                        ":-- :dMNdhhdNMMNo------------;",
                        ":---:sdNMMMMNds:------------:",
                        ":------:://:-------------::",
                        ":---------------------://"},
                "arch", new String[]{
                        "                   -`",
                        "                  .o+`",
                        "                 `ooo/",
                        "                `+oooo:",
                        "               `+oooooo:",
                        "               -+oooooo+:",
                        "             `/:-:++oooo+:",
                        "            `/++++/+++++++:",
                        "           `/++++++++++++++:",
                        "          `/+++ooooooooooooo/`",
                        "         ./ooosssso++osssssso+`",
                        "        .oossssso-````/ossssss+`",
                        "       -osssssso.      :ssssssso.",
                        "      :osssssss/        osssso+++.",
                        "     /ossssssss/        +ssssooo/-",
                        "   `/ossssso+/:-        -:/+osssso+-",
                        "  `+sso+:-`                 `.-/+oso:",
                        " `++:.                           `-/+/",
                        " .`                                 `/"},
                "gentoo", new String[]{
                        "         -/oyddmdhs+:.",
                        "     -odNMMMMMMMMNNmhy+-`",
                        "   -yNMMMMMMMMMMMNNNmmdhy+-",
                        " `omMMMMMMMMMMMMNmdmmmmddhhy/`",
                        " omMMMMMMMMMMMNhhyyyohmdddhhhdo`",
                        ".ydMMMMMMMMMMdhs++so/smdddhhhhdm+`",
                        " oyhdmNMMMMMMMNdyooydmddddhhhhyhNd.",
                        "  :oyhhdNNMMMMMMMNNNmmdddhhhhhyymMh",
                        "    .:+sydNMMMMMNNNmmmdddhhhhhhmMmy",
                        "       /mMMMMMMNNNmmmdddhhhhhmMNhs:",
                        "    `oNMMMMMMMNNNmmmddddhhdmMNhs+`",
                        "  `sNMMMMMMMMNNNmmmdddddmNMmhs/.",
                        " /NMMMMMMMMNNNNmmmdddmNMNdso:`",
                        "+MMMMMMMNNNNNmmmmdmNMNdso/-",
                        "yMMNNNNNNNmmmmmNNMmhs+/-`",
                        "/hMMNNNNNNNNMNdhs++/-`",
                        "`/ohdmmddhys+++/:.`",
                        "  `-//////:--."});

        private static final String[] DEFAULT_LOGO = {
                "  .--------.  ",
                "  |  .--.  |  ",
                "  |  |  |  |  ",
                "  |  '--'  |  ",
                "  |  JSC   |  ",
                "  '--------'  "};

        // Each distribution paints in its brand color, the way real fetch tools color their logo block.
        private static final java.util.Map<String, dev.jstech.computronics.program.cli.CliStyle>
                COLORS = java.util.Map.of(
                        "ubuntu", dev.jstech.computronics.program.cli.CliStyle.ORANGE,
                        "debian", dev.jstech.computronics.program.cli.CliStyle.MAGENTA,
                        "fedora", dev.jstech.computronics.program.cli.CliStyle.BLUE,
                        "arch", dev.jstech.computronics.program.cli.CliStyle.CYAN,
                        "gentoo", dev.jstech.computronics.program.cli.CliStyle.PURPLE);

        @Override public String name() { return "screenfetch"; }

        @Override public java.util.List<String> aliases() { return java.util.List.of("neofetch"); }

        @Override public String summary() { return "show the system logo and information"; }

        @Override public String usage() { return ""; }

        @Override public boolean available(final CliComputer computer) {
            // A package the Mirror serves, not a built-in: 'command not found' until it is installed —
            // the classic first thing to apt install on a fresh system.
            return computer.hasProgram(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("jsc", "screenfetch"));
        }

        @Override public void run(final CliContext ctx) {
            final CliComputer.SystemInfo info = ctx.computer().systemInfo();
            if (info == null) {
                ctx.out().error("screenfetch: no operating system installed");
                return;
            }
            final String[] logo = LOGOS.getOrDefault(info.distroId(), DEFAULT_LOGO);
            final dev.jstech.computronics.program.cli.CliStyle color = COLORS.getOrDefault(
                    info.distroId(), dev.jstech.computronics.program.cli.CliStyle.ACCENT);
            final java.util.List<String> lines = new java.util.ArrayList<>();
            final String user = "player@" + info.hostname();
            lines.add(user);
            lines.add("-".repeat(user.length()));
            lines.add("OS: " + info.os() + " x86_64");
            lines.add("Kernel: " + info.kernel());
            lines.add("Uptime: " + uptime(info.uptimeTicks()));
            lines.add("Packages: " + info.packages() + " (" + ctx.computer().packageManager().command() + ")");
            lines.add("Shell: " + info.shell());
            lines.add("DE: " + info.desktop());
            lines.add("CPU: " + info.cpu());
            lines.add("RAM: " + info.ramMb() + " MB");
            lines.add("Disk: " + info.diskUsedMb() + " MB / " + info.diskTotalMb() + " MB");
            final int rows = Math.max(logo.length, lines.size());
            // The info column starts two spaces past the widest logo row (the arts differ in width).
            int logoW = 0;
            for (final String row : logo) {
                logoW = Math.max(logoW, row.length());
            }
            logoW += 2;
            for (int i = 0; i < rows; i++) {
                final String left = i < logo.length ? logo[i] : "";
                final String right = i < lines.size() ? lines.get(i) : "";
                final String padded = left + " ".repeat(Math.max(0, logoW - left.length()));
                ctx.out().styled((padded + right).stripTrailing(), color);
            }
        }

        /** The world's uptime as {@code Xd Xh Xm} (in-game time — the machine has been part of it). */
        private static String uptime(final long ticks) {
            final long minutes = ticks / (20L * 60L);
            final long days = minutes / (60 * 24);
            final long hours = (minutes / 60) % 24;
            final long mins = minutes % 60;
            if (days > 0) {
                return days + "d " + hours + "h " + mins + "m";
            }
            return hours > 0 ? hours + "h " + mins + "m" : mins + "m";
        }
    }

    private static String dos(final String posixPath) {
        return PosixPath.toDos(posixPath);
    }

    static final class Ls implements CliCommand {
        @Override public String name() { return "ls"; }

        @Override public String summary() { return "list directory contents"; }

        @Override public String usage() { return "[-l] [directory]"; }

        @Override public void run(final CliContext ctx) {
            boolean longFormat = false;
            String dir = "";
            for (int i = 0; i < ctx.argCount(); i++) {
                final String a = ctx.arg(i);
                if (a.equals("-l") || a.equals("-la") || a.equals("-al")) {
                    longFormat = true;
                } else {
                    dir = a;
                }
            }
            final CliComputer.FsResult result = ctx.computer().listDisk(dos(dir));
            if (!result.ok()) {
                ctx.out().error("ls: " + result.message());
                return;
            }
            final List<CliComputer.FsEntry> entries = result.entries();
            if (entries.isEmpty()) {
                return;
            }
            if (longFormat) {
                for (final CliComputer.FsEntry e : entries) {
                    final String mode = (e.isDir() ? "d" : "-") + (e.readOnly() ? "r--r--r--" : "rw-r--r--");
                    ctx.out().row(mode + "  " + e.name() + (e.isDir() ? "/" : ""),
                            e.isDir() ? "" : String.format(Locale.ROOT, "%,d mB", e.weightMbEq()));
                }
                return;
            }
            final StringBuilder line = new StringBuilder();
            for (final CliComputer.FsEntry e : entries) {
                if (line.length() > 0) {
                    line.append("  ");
                }
                line.append(e.name()).append(e.isDir() ? "/" : "");
            }
            ctx.out().line(line.toString());
        }
    }

    static final class Pwd implements CliCommand {
        @Override public String name() { return "pwd"; }

        @Override public String summary() { return "print the current directory"; }

        @Override public void run(final CliContext ctx) {
            ctx.out().line(PosixPath.render(ctx.computer().currentLocation()));
        }
    }

    static final class Cd implements CliCommand {
        @Override public String name() { return "cd"; }

        @Override public String summary() { return "change the current directory (home when no argument)"; }

        @Override public String usage() { return "[directory]"; }

        @Override public void run(final CliContext ctx) {
            final String target = ctx.hasArgs() ? ctx.rest(0) : "~";
            final CliComputer.FsResult result = ctx.computer().changeDir(dos(target));
            if (!result.ok()) {
                ctx.out().error("cd: " + result.message());
            }
        }
    }

    static final class Cat implements CliCommand {
        @Override public String name() { return "cat"; }

        @Override public String summary() { return "print the content of a file"; }

        @Override public String usage() { return "<file>"; }

        @Override public void run(final CliContext ctx) {
            if (!ctx.hasArgs()) {
                ctx.out().error("usage: cat <file>");
                return;
            }
            final CliComputer.FsResult result = ctx.computer().readFile(dos(ctx.arg(0)));
            if (!result.ok()) {
                ctx.out().error("cat: " + result.message());
                return;
            }
            for (final String line : result.message().split("\n", -1)) {
                ctx.out().line(line);
            }
        }
    }

    static final class Rm implements CliCommand {
        @Override public String name() { return "rm"; }

        @Override public String summary() { return "remove a file"; }

        @Override public String usage() { return "<file>"; }

        @Override public void run(final CliContext ctx) {
            if (!ctx.hasArgs()) {
                ctx.out().error("usage: rm <file>");
                return;
            }
            final CliComputer.FsResult result = ctx.computer().deleteFile(dos(ctx.arg(0)));
            if (!result.ok()) {
                ctx.out().error("rm: " + result.message());
            }
        }
    }

    static final class Mkdir implements CliCommand {
        @Override public String name() { return "mkdir"; }

        @Override public String summary() { return "create a directory"; }

        @Override public String usage() { return "<directory>"; }

        @Override public void run(final CliContext ctx) {
            if (!ctx.hasArgs()) {
                ctx.out().error("usage: mkdir <directory>");
                return;
            }
            final CliComputer.FsResult result = ctx.computer().makeDir(dos(ctx.rest(0)));
            if (!result.ok()) {
                ctx.out().error("mkdir: " + result.message());
            }
        }
    }

    static final class Rmdir implements CliCommand {
        @Override public String name() { return "rmdir"; }

        @Override public String summary() { return "remove an empty directory"; }

        @Override public String usage() { return "<directory>"; }

        @Override public void run(final CliContext ctx) {
            if (!ctx.hasArgs()) {
                ctx.out().error("usage: rmdir <directory>");
                return;
            }
            final CliComputer.FsResult result = ctx.computer().removeDir(dos(ctx.rest(0)));
            if (!result.ok()) {
                ctx.out().error("rmdir: " + result.message());
            }
        }
    }

    static final class Cp implements CliCommand {
        @Override public String name() { return "cp"; }

        @Override public String summary() { return "copy a file to another location"; }

        @Override public String usage() { return "<source> <destination>"; }

        @Override public void run(final CliContext ctx) {
            if (ctx.argCount() < 2) {
                ctx.out().error("usage: cp <source> <destination>");
                return;
            }
            final CliComputer.FsResult result = ctx.computer().copyPath(dos(ctx.arg(0)), dos(ctx.arg(1)));
            if (!result.ok()) {
                ctx.out().error("cp: " + result.message());
            }
        }
    }

    static final class Mv implements CliCommand {
        @Override public String name() { return "mv"; }

        @Override public String summary() { return "move a file into a directory, or rename it"; }

        @Override public String usage() { return "<source> <directory|new-name>"; }

        @Override public void run(final CliContext ctx) {
            if (ctx.argCount() < 2) {
                ctx.out().error("usage: mv <source> <directory|new-name>");
                return;
            }
            final String dest = ctx.arg(1);
            // A bare new name (no slash, no path form) is a rename; anything else moves into a directory.
            final boolean rename = !dest.contains("/") && !dest.startsWith("~") && !dest.equals(".") && !dest.equals("..");
            final CliComputer.FsResult result = rename
                    ? ctx.computer().renamePath(dos(ctx.arg(0)), dest)
                    : ctx.computer().movePath(dos(ctx.arg(0)), dos(dest));
            if (!result.ok()) {
                ctx.out().error("mv: " + result.message());
            }
        }
    }

    static final class Touch implements CliCommand {
        @Override public String name() { return "touch"; }

        @Override public String summary() { return "create an empty file"; }

        @Override public String usage() { return "<file>"; }

        @Override public void run(final CliContext ctx) {
            if (!ctx.hasArgs()) {
                ctx.out().error("usage: touch <file>");
                return;
            }
            final CliComputer.FsResult result = ctx.computer().writeFile(dos(ctx.arg(0)), "");
            if (!result.ok()) {
                ctx.out().error("touch: " + result.message());
            }
        }
    }

    static final class Write implements CliCommand {
        @Override public String name() { return "write"; }

        @Override public String summary() { return "create or overwrite a file with the given text"; }

        @Override public String usage() { return "<file> <text...>"; }

        @Override public void run(final CliContext ctx) {
            if (ctx.argCount() < 2) {
                ctx.out().error("usage: write <file> <text...>");
                return;
            }
            final CliComputer.FsResult result = ctx.computer().writeFile(dos(ctx.arg(0)), ctx.rest(1));
            if (!result.ok()) {
                ctx.out().error("write: " + result.message());
            } else if (!result.message().isEmpty()) {
                ctx.out().ok(result.message());
            }
        }
    }

    static final class Run implements CliCommand {
        @Override public String name() { return "run"; }

        @Override public String summary() { return "execute an .iql script"; }

        @Override public String usage() { return "<file.iql>"; }

        @Override public void run(final CliContext ctx) {
            if (!ctx.hasArgs()) {
                ctx.out().error("usage: run <file.iql>");
                return;
            }
            final CliComputer.FsResult result = ctx.computer().runScript(dos(ctx.arg(0)));
            if (!result.ok()) {
                ctx.out().error("run: " + result.message());
                return;
            }
            if (result.opResult() != null) {
                if (result.opResult().ok()) {
                    ctx.out().ok(result.opResult().message());
                } else {
                    ctx.out().error(result.opResult().message());
                }
            } else if (!result.message().isEmpty()) {
                ctx.out().line(result.message());
            }
        }
    }

    static final class Clear implements CliCommand, CliShell.ClearMarker {
        @Override public String name() { return "clear"; }

        @Override public String summary() { return "clear the terminal"; }

        @Override public void run(final CliContext ctx) {
            // The shell clears the scrollback because this command is a ClearMarker; nothing to print.
        }
    }

    static final class Man implements CliCommand {
        @Override public String name() { return "man"; }

        @Override public String summary() { return "show the manual entry for a command"; }

        @Override public String usage() { return "<command>"; }

        @Override public void run(final CliContext ctx) {
            if (!ctx.hasArgs()) {
                ctx.out().error("What manual page do you want?");
                return;
            }
            final CliCommand command = ctx.shell().find(ctx.arg(0));
            if (command == null) {
                ctx.out().error("No manual entry for " + ctx.arg(0));
                return;
            }
            ctx.out().accent(command.name().toUpperCase(Locale.ROOT) + "(1)");
            ctx.out().line("NAME");
            ctx.out().line("    " + command.name() + " - " + command.summary());
            if (!command.usage().isEmpty()) {
                ctx.out().line("SYNOPSIS");
                ctx.out().line("    " + command.name() + " " + command.usage());
            }
        }
    }

    static final class Uname implements CliCommand {
        @Override public String name() { return "uname"; }

        @Override public String summary() { return "print system information"; }

        @Override public String usage() { return "[-a]"; }

        @Override public void run(final CliContext ctx) {
            if (ctx.hasArgs() && ctx.arg(0).equals("-a")) {
                ctx.out().line("Linux " + ctx.computer().hostname() + " 6.8-jsc #1 SMP x86_64 GNU/Linux");
            } else {
                ctx.out().line("Linux");
            }
        }
    }

    static final class Hostname implements CliCommand {
        @Override public String name() { return "hostname"; }

        @Override public String summary() { return "print this computer's host name"; }

        @Override public void run(final CliContext ctx) {
            ctx.out().line(ctx.computer().hostname());
        }
    }

    static final class Df implements CliCommand {
        @Override public String name() { return "df"; }

        @Override public String summary() { return "report filesystem space usage"; }

        @Override public void run(final CliContext ctx) {
            final List<CliComputer.MountInfo> mounts = ctx.computer().mounts();
            if (mounts.isEmpty()) {
                ctx.out().error("df: no filesystems mounted");
                return;
            }
            ctx.out().row("Filesystem       Size   Used   Avail  Use%", "Mounted on");
            for (final CliComputer.MountInfo m : mounts) {
                final String mount = PosixPath.render(DosPath.Location.root(m.drive()));
                if (!m.ready()) {
                    ctx.out().row(String.format(Locale.ROOT, "/dev/%s%s", m.device(), "  (no medium)"), mount);
                    continue;
                }
                final long used = Math.max(0L, m.capacityMbEq() - m.freeMbEq());
                final int pct = m.capacityMbEq() <= 0 ? 0 : (int) (used * 100 / m.capacityMbEq());
                ctx.out().row(String.format(Locale.ROOT, "/dev/%-8s %6s %6s %6s %3d%%", m.device(),
                        size(m.capacityMbEq()), size(used), size(m.freeMbEq()), pct), mount);
            }
        }

        private static String size(final long mbEq) {
            if (mbEq >= 1_000_000L) {
                return String.format(Locale.ROOT, "%.1fT", mbEq / 1_000_000.0);
            }
            if (mbEq >= 1_000L) {
                return String.format(Locale.ROOT, "%.1fG", mbEq / 1_000.0);
            }
            return mbEq + "M";
        }
    }
}
