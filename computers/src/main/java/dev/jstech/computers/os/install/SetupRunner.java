/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os.install;

import dev.jstech.computers.blockentity.MainframeBlockEntity;
import dev.jstech.computers.menu.CommandPromptMenu;
import dev.jstech.computers.menu.DesktopMenu;
import dev.jstech.computers.operation.payload.CommandOutputPayload;
import dev.jstech.computers.operation.payload.DesktopShellOutputPayload;
import dev.jstech.computers.operation.payload.SetupProgressPayload;
import dev.jstech.computers.os.IOsHost;
import dev.jstech.computers.os.OsDef;
import dev.jstech.computers.os.Platform;
import dev.jstech.computers.os.ProgramKind;
import dev.jstech.computers.os.ProgramSpec;
import dev.jstech.computers.os.ProgramVersions;
import dev.jstech.computers.os.SoftwareHouse;
import dev.jstech.computers.os.media.MediaFormat;
import dev.jstech.computers.program.ComputerConsoleState;
import dev.jstech.computers.program.cli.CliStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Installing a program as something a machine does over time, rather than a flag that flips.
 *
 * <p>Every way of starting an install ends up here: the disc's setup program, This PC's button, the
 * prompt's {@code install}, the package manager. The job is held by the machine's console, ticked by
 * the machine, and told to every window looking at it. On Frames that is a Setup window on the desktop
 * and a bar at the prompt; on Linux it is the terminal alone, drawn the way the package manager that
 * was asked draws it, since no Linux ever put up a Setup window for {@code apt}.
 */
public final class SetupRunner {

    /** Where a program comes from when no disc is involved. */
    public static final String SOURCE_NETWORK = "the Mirror";

    /** How often a running job is told to the windows and the prompt, in ticks. */
    private static final int PUSH_EVERY = 10;

    private SetupRunner() {
    }

    /**
     * Starts installing (or removing) {@code spec} on {@code host}, asked for the plain way: a disc's
     * setup program, or the Frames package manager when there is no disc.
     */
    public static Optional<String> begin(final IOsHost host, final ServerLevel level, final BlockPos pos,
                                         final ProgramSpec spec, @Nullable final MediaFormat medium,
                                         final boolean removing) {
        return begin(host, level, pos, spec, medium, removing,
                medium == null ? SetupJob.VIA_PCKMGR : SetupJob.VIA_SETUP);
    }

    /**
     * Starts installing (or removing) {@code spec} on {@code host}, or says why it cannot.
     *
     * @param medium the disc it comes from, or null for the network
     * @param via    how it was asked for: a setup program, the install verb, or a package manager's word
     * @return the refusal, which was also shown at the machine's windows, or empty when the job began
     */
    public static Optional<String> begin(final IOsHost host, final ServerLevel level, final BlockPos pos,
                                         final ProgramSpec spec, @Nullable final MediaFormat medium,
                                         final boolean removing, final String via) {
        final ComputerConsoleState console = host.console();
        if (console == null) {
            return Optional.of("This computer cannot hold installed programs.");
        }
        if (console.setup() != null) {
            return Optional.of("This computer is still setting up " + console.setup().name() + ".");
        }
        final Optional<String> refusal = SetupGate.refusal(host, spec, removing, true);
        if (refusal.isPresent()) {
            pushWindow(host, level, pos, refused(pos, spec, removing, refusal.get()));
            return refusal;
        }
        final String source = medium == null ? SOURCE_NETWORK : sourceName(medium);
        // A floppy is a floppy, but a newer machine unpacks what it carries that much faster.
        final int factor = SetupTiming.eraFactor(host.displayEra());
        final int ticks = medium == null ? SetupTiming.networkTicks(spec.minDiskMb(), removing, factor)
                : SetupTiming.ticks(spec.minDiskMb(), medium, removing, factor);
        final SetupJob job = new SetupJob(spec.id().toString(), spec.displayName(),
                spec.houseOr(SoftwareHouse.MIDSOFT).name(), spec.minDiskMb(), source, removing, ticks, via,
                spec.commandName());
        console.beginSetup(job);
        host.setChanged();
        pushWindow(host, level, pos, progress(pos, job, SetupProgressPayload.STATE_RUNNING, ""));
        // Whoever asked has already said what began; the bar follows on the next tick.
        return Optional.empty();
    }

    /** Stops the job, leaving the machine as it was. */
    public static void cancel(final IOsHost host, final ServerLevel level, final BlockPos pos) {
        final ComputerConsoleState console = host.console();
        final SetupJob job = console == null ? null : console.setup();
        if (job == null) {
            return;
        }
        console.clearSetup();
        host.setChanged();
        pushWindow(host, level, pos, progress(pos, job, SetupProgressPayload.STATE_CANCELLED, "Setup was cancelled."));
        promptLines(level, pos, List.of(line("Setup cancelled. Nothing was "
                + (job.removing() ? "removed." : "installed."), CliStyle.ERROR)), false);
    }

    /** One tick of whatever the machine is setting up, if anything. */
    public static void tick(final IOsHost host, final ServerLevel level, final BlockPos pos) {
        final ComputerConsoleState console = host.console();
        final SetupJob job = console == null ? null : console.setup();
        if (job == null) {
            return;
        }
        final boolean last = job.tick();
        if (last) {
            console.clearSetup();
            finish(host, console, job);
            host.setChanged();
            pushWindow(host, level, pos, progress(pos, job, SetupProgressPayload.STATE_DONE, ""));
            // The bar reaches its end where it stands, then the lines that say what was done follow it.
            promptLines(level, pos, List.of(line(bar(job), CliStyle.PLAIN)), !job.drawBar());
            promptLines(level, pos, finished(job), false);
            return;
        }
        if (job.ticksLeft() % PUSH_EVERY == 0) {
            pushWindow(host, level, pos, progress(pos, job, SetupProgressPayload.STATE_RUNNING, ""));
            // The first drawing takes a line of its own; every one after it grows over that line.
            final boolean first = job.drawBar();
            promptLines(level, pos, List.of(line(bar(job), CliStyle.PLAIN)), !first);
        }
    }

    /**
     * The install itself, on the last tick.
     *
     * <p>The Mainframe's services are flags on the Mainframe as well as entries in its console; they
     * used to be flipped by each install path separately, which is how a Mirror installed from its disc
     * once ended up listed but not serving.
     */
    private static void finish(final IOsHost host, final ComputerConsoleState console, final SetupJob job) {
        final String id = job.programId();
        final String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        if (job.removing()) {
            console.uninstall(id);
            if (host instanceof MainframeBlockEntity mainframe) {
                switch (path) {
                    case "iqlengine" -> mainframe.uninstallIqlEngine();
                    case "automation_engine" -> mainframe.uninstallAutomationEngine();
                    case "mirror" -> mainframe.uninstallMirror();
                    default -> { }
                }
            }
            return;
        }
        if (host instanceof MainframeBlockEntity mainframe) {
            switch (path) {
                case "iqlengine" -> mainframe.installIqlEngine();
                case "automation_engine" -> mainframe.installAutomationEngine();
                case "mirror" -> mainframe.installMirror();
                default -> { }
            }
        }
        console.install(id);
        console.setInstalledVersion(id, ProgramVersions.of(id));
    }

    /** Whether {@code spec} is a service a Mainframe switches on, which install and remove both special-case. */
    public static boolean isMainframeService(final ProgramSpec spec) {
        return spec.kind() == ProgramKind.SERVICE;
    }

    private static String sourceName(final MediaFormat medium) {
        return switch (medium) {
            case FLOPPY -> "floppy";
            case CD -> "CD";
            case DVD -> "DVD";
            case USB -> "USB drive";
        };
    }

    /* What the prompt prints, in the dialect of whatever was asked */

    /**
     * The bar as the thing that was asked draws it.
     *
     * <p>{@code apt} keeps a percentage in front of a long bar, {@code dnf} counts megabytes against a
     * rate, {@code pacman} does both on one line, and a setup program from a disc draws a plain bar.
     * Whichever it is, the line is the one line, redrawn.
     */
    static String bar(final SetupJob job) {
        final int pct = job.permille() / 10;
        final String pkg = job.packageName();
        final String ver = ProgramVersions.of(job.programId());
        return switch (job.via()) {
            case "apt" -> "Progress: [" + pad3(pct) + "%] [" + cells(job.permille(), 40, '#', '.') + "]";
            case "dnf" -> pkg + "-" + ver + "  " + rate(job) + " MB/s | " + doneMb(job) + " MB  " + clock(job);
            case "pacman" -> " " + pkg + "-" + ver + "  " + job.sizeMb() + ".0 MiB  " + rate(job) + " MiB/s "
                    + clock(job) + " [" + cells(job.permille(), 20, '#', '-') + "] " + pad3(pct) + "%";
            case SetupJob.VIA_PCKMGR -> (job.removing() ? "Removing " : "Downloading ") + pkg + " " + ver
                    + "  [" + cells(job.permille(), 20, '#', '.') + "]  " + pct + "%";
            default -> "[" + cells(job.permille(), 20, '#', '.') + "]  " + pct + "%";
        };
    }

    /** What follows the bar once it is full: the manager's own closing lines. */
    static List<CommandOutputPayload.WireLine> finished(final SetupJob job) {
        final String pkg = job.packageName();
        final String ver = ProgramVersions.of(job.programId());
        final List<CommandOutputPayload.WireLine> out = new ArrayList<>();
        switch (job.via()) {
            case "apt" -> {
                if (job.removing()) {
                    out.add(line("Removing " + pkg + " (" + ver + ") ...", CliStyle.PLAIN));
                    out.add(line("Processing triggers for " + pkg + " ...", CliStyle.OK));
                } else {
                    out.add(line("Fetched " + job.sizeMb() + " MB in " + seconds(job) + "s (" + rate(job) + " MB/s)",
                            CliStyle.PLAIN));
                    out.add(line("Selecting previously unselected package " + pkg + ".", CliStyle.PLAIN));
                    out.add(line("Unpacking " + pkg + " (" + ver + ") ...", CliStyle.PLAIN));
                    out.add(line("Setting up " + pkg + " (" + ver + ") ...", CliStyle.OK));
                }
            }
            case "dnf" -> {
                out.add(line("Running transaction", CliStyle.PLAIN));
                out.add(line("  " + (job.removing() ? "Erasing          : " : "Installing       : ") + pkg + "-" + ver
                        + "   1/1", CliStyle.PLAIN));
                out.add(line((job.removing() ? "Removed:" : "Installed:"), CliStyle.PLAIN));
                out.add(line("  " + pkg + "-" + ver, CliStyle.PLAIN));
                out.add(line("Complete!", CliStyle.OK));
            }
            case "pacman" -> {
                if (job.removing()) {
                    out.add(line("(1/1) removing " + pkg, CliStyle.OK));
                } else {
                    out.add(line("(1/1) checking keys in keyring", CliStyle.PLAIN));
                    out.add(line("(1/1) checking package integrity", CliStyle.PLAIN));
                    out.add(line("(1/1) installing " + pkg, CliStyle.OK));
                }
            }
            case SetupJob.VIA_PCKMGR -> out.add(line(job.name() + " " + ver
                    + (job.removing() ? " removed." : " installed."), CliStyle.OK));
            default -> out.add(line(job.name() + (job.removing() ? " removed." : " installed."), CliStyle.OK));
        }
        return out;
    }

    private static String cells(final int permille, final int width, final char full, final char empty) {
        final int filled = permille * width / 1000;
        return String.valueOf(full).repeat(filled) + String.valueOf(empty).repeat(width - filled);
    }

    private static String pad3(final int pct) {
        return pct < 10 ? "  " + pct : pct < 100 ? " " + pct : String.valueOf(pct);
    }

    /** Megabytes a second, from the job's own length: what it carries over how long it takes. */
    private static String rate(final SetupJob job) {
        final double seconds = job.ticksTotal() / (double) SetupTiming.TICKS_PER_SECOND;
        return String.format(Locale.ROOT, "%.1f", Math.max(0.1, job.sizeMb() / Math.max(0.05, seconds)));
    }

    private static String doneMb(final SetupJob job) {
        return String.format(Locale.ROOT, "%.1f", job.sizeMb() * job.permille() / 1000.0);
    }

    private static int seconds(final SetupJob job) {
        return Math.max(1, job.ticksTotal() / SetupTiming.TICKS_PER_SECOND);
    }

    private static String clock(final SetupJob job) {
        final int gone = job.ticksDone() / SetupTiming.TICKS_PER_SECOND;
        return String.format(Locale.ROOT, "%02d:%02d", gone / 60, gone % 60);
    }

    private static CommandOutputPayload.WireLine line(final String text, final CliStyle style) {
        return new CommandOutputPayload.WireLine(text, style.ordinal());
    }

    /* Who gets told */

    private static SetupProgressPayload progress(final BlockPos pos, final SetupJob job, final int state,
                                                 final String message) {
        return new SetupProgressPayload(pos, job.programId(), job.name(), job.house(), job.sizeMb(), job.source(),
                job.permille(), job.phase(), state, message, job.removing());
    }

    private static SetupProgressPayload refused(final BlockPos pos, final ProgramSpec spec, final boolean removing,
                                                final String message) {
        return new SetupProgressPayload(pos, spec.id().toString(), spec.displayName(),
                spec.houseOr(SoftwareHouse.MIDSOFT).name(), spec.minDiskMb(), "", 0, "",
                SetupProgressPayload.STATE_REFUSED, message, removing);
    }

    /**
     * Every player at a Frames desktop of that machine gets the job's state.
     *
     * <p>Only Frames: a Linux desktop installs from its terminal and never put up a Setup window for a
     * package manager, so on Linux the terminal is the whole of it.
     */
    private static void pushWindow(final IOsHost host, final ServerLevel level, final BlockPos pos,
                                   final SetupProgressPayload payload) {
        final OsDef os = host.installedOs();
        if (os == null || os.platform() != Platform.FRAMES) {
            return;
        }
        for (final ServerPlayer player : level.players()) {
            if (player.containerMenu instanceof DesktopMenu desk && pos.equals(desk.hostPos())) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    /**
     * Every prompt and every terminal window on that machine gets the lines, over the last line printed
     * when {@code replaceLast} says so, which is how a bar grows in place.
     */
    private static void promptLines(final ServerLevel level, final BlockPos pos,
                                    final List<CommandOutputPayload.WireLine> lines, final boolean replaceLast) {
        final List<DesktopShellOutputPayload.WireLine> desktop = new ArrayList<>(lines.size());
        for (final CommandOutputPayload.WireLine each : lines) {
            desktop.add(new DesktopShellOutputPayload.WireLine(each.text(), each.style()));
        }
        for (final ServerPlayer player : level.players()) {
            if (player.containerMenu instanceof CommandPromptMenu prompt && pos.equals(prompt.hostPos())) {
                PacketDistributor.sendToPlayer(player, new CommandOutputPayload(false, "", lines, "", "", replaceLast));
            } else if (player.containerMenu instanceof DesktopMenu desk && pos.equals(desk.hostPos())) {
                PacketDistributor.sendToPlayer(player, DesktopShellOutputPayload.informational(desktop, replaceLast));
            }
        }
    }
}
