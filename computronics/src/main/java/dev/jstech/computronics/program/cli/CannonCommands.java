/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.program.cli;

import dev.jstech.computronics.cannon.CannonCompiler;
import dev.jstech.computronics.cannon.Diagnostic;
import dev.jstech.computronics.cannon.SourceFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The two verbs the Cannon toolchain brings to the prompt: one to compile a program, one to run it.
 *
 * <p>Neither exists until its package is installed, the way any other package's verbs do not. Both
 * are ordinary shell commands with no window of their own, because writing and running a program is
 * done where the files are.
 */
public final class CannonCommands {

    /** The id of the compiler package, as the Mirror serves it. */
    static final String COMPILER = "jsc:cannonc";

    /** The id of the runtime package. */
    static final String RUNTIME = "jsc:cannonrt";

    /** The extension a program is written in, and the one it is compiled to. */
    private static final String SOURCE = ".can";
    private static final String ASSEMBLY = ".asm";

    private CannonCommands() {
    }

    /** Both verbs, for the shell to register. */
    public static List<CliCommand> all() {
        return List.of(new Compile(), new Run());
    }

    /** Whether that package is installed on the computer. */
    static boolean installed(final CliComputer computer, final String id) {
        for (final CliComputer.ProgramInfo program : computer.programs()) {
            if (id.equalsIgnoreCase(program.id())) {
                return true;
            }
        }
        return false;
    }

    /** Compiles one or more source files into one assembly listing. */
    static final class Compile implements CliCommand {

        @Override
        public String name() {
            return "cannonc";
        }

        @Override
        public String summary() {
            return "compile a Cannon program into the assembly the runtime reads";
        }

        @Override
        public String usage() {
            return "<file" + SOURCE + "> [more" + SOURCE + " ...] [-o <out" + ASSEMBLY + ">]";
        }

        @Override
        public boolean available(final CliComputer computer) {
            return installed(computer, COMPILER);
        }

        @Override
        public void run(final CliContext ctx) {
            final List<String> paths = new ArrayList<>();
            String out = null;
            for (int i = 0; i < ctx.args().size(); i++) {
                final String arg = ctx.args().get(i);
                if ("-o".equals(arg)) {
                    i++;
                    out = i < ctx.args().size() ? ctx.args().get(i) : null;
                } else {
                    paths.add(arg);
                }
            }
            if (paths.isEmpty()) {
                ctx.out().error("usage: cannonc " + this.usage());
                return;
            }

            final List<SourceFile> sources = new ArrayList<>();
            for (final String path : paths) {
                final CliComputer.FsResult read = ctx.computer().readFile(path);
                if (!read.ok()) {
                    ctx.out().error("cannonc: " + read.message());
                    return;
                }
                sources.add(new SourceFile(leaf(path), read.message()));
            }

            final CannonCompiler.Result built = CannonCompiler.compile(sources);
            for (final Diagnostic diagnostic : built.diagnostics()) {
                if (diagnostic.isError()) {
                    ctx.out().error(diagnostic.format());
                } else {
                    ctx.out().dim(diagnostic.format());
                }
            }
            if (!built.ok()) {
                final long errors = built.diagnostics().stream().filter(Diagnostic::isError).count();
                ctx.out().error("cannonc: " + errors + (errors == 1 ? " error" : " errors")
                        + ", nothing was written");
                return;
            }
            final String target = out != null ? out : compiled(paths.getFirst());
            final CliComputer.FsResult written = ctx.computer().writeFile(target, built.assembly());
            if (!written.ok()) {
                ctx.out().error("cannonc: " + written.message());
                return;
            }
            ctx.out().ok("cannonc: wrote " + target);
        }

        /** The name a source file compiles to: the same name, with the assembly's extension. */
        private static String compiled(final String path) {
            final int dot = path.lastIndexOf('.');
            final int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            return dot > slash ? path.substring(0, dot) + ASSEMBLY : path + ASSEMBLY;
        }

        /** The file's own name, which is what a diagnostic quotes rather than the path to it. */
        private static String leaf(final String path) {
            final int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            return slash < 0 ? path : path.substring(slash + 1);
        }
    }

    /** Starts, stops and lists the Cannon programs running on this computer. */
    static final class Run implements CliCommand {

        @Override
        public String name() {
            return "cannon";
        }

        @Override
        public String summary() {
            return "run a compiled Cannon program, stop one, or list what is running";
        }

        @Override
        public String usage() {
            return "run <file" + ASSEMBLY + "> [--heap <n>M] | stop <id> | ps";
        }

        @Override
        public boolean available(final CliComputer computer) {
            return installed(computer, RUNTIME);
        }

        @Override
        public void run(final CliContext ctx) {
            switch (ctx.arg(0).toLowerCase(Locale.ROOT)) {
                case "run", "start" -> this.start(ctx);
                case "stop", "kill" -> this.stop(ctx);
                case "ps", "list", "" -> this.list(ctx);
                default -> ctx.out().error("usage: cannon " + this.usage());
            }
        }

        private void start(final CliContext ctx) {
            final String path = ctx.arg(1);
            if (path.isEmpty()) {
                ctx.out().error("usage: cannon run <file" + ASSEMBLY + "> [--heap <n>M]");
                return;
            }
            int heapMb = 0;
            for (int i = 2; i < ctx.args().size() - 1; i++) {
                if ("--heap".equals(ctx.args().get(i))) {
                    heapMb = megabytes(ctx.args().get(i + 1));
                }
            }
            final CliComputer.OpResult started = ctx.computer().startCannon(path, heapMb);
            if (started.ok()) {
                ctx.out().ok(started.message());
            } else {
                ctx.out().error(started.message());
            }
        }

        private void stop(final CliContext ctx) {
            final int id = whole(ctx.arg(1));
            if (id < 0) {
                ctx.out().error("usage: cannon stop <id>   (as listed by 'cannon ps')");
                return;
            }
            final CliComputer.OpResult stopped = ctx.computer().stopCannon(id);
            if (stopped.ok()) {
                ctx.out().ok(stopped.message());
            } else {
                ctx.out().error(stopped.message());
            }
        }

        private void list(final CliContext ctx) {
            final List<CliComputer.CannonProcess> running = ctx.computer().cannonProcesses();
            if (running.isEmpty()) {
                ctx.out().dim("no Cannon programs are running");
                return;
            }
            ctx.out().info("  id  name                 state      memory");
            for (final CliComputer.CannonProcess process : running) {
                ctx.out().line(String.format(Locale.ROOT, "  %-3d %-20s %-10s %s of %s",
                        process.id(), cut(process.name(), 20), cut(process.state(), 10),
                        kilobytes(process.heldBytes()), kilobytes(process.heapBytes())));
            }
        }

        /** "16M" and "16" both mean sixteen; anything else means the computer decides. */
        private static int megabytes(final String written) {
            final String text = written.toUpperCase(Locale.ROOT).replace("MB", "").replace("M", "");
            return Math.max(0, whole(text));
        }

        private static int whole(final String written) {
            try {
                return Integer.parseInt(written.trim());
            } catch (final NumberFormatException notANumber) {
                return -1;
            }
        }

        private static String kilobytes(final long bytes) {
            return bytes < 1024 ? bytes + " B" : (bytes / 1024) + " KB";
        }

        private static String cut(final String text, final int width) {
            return text.length() <= width ? text : text.substring(0, width - 1) + "~";
        }
    }
}
