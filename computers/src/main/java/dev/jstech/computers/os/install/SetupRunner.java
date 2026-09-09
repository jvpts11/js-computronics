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
import dev.jstech.computers.operation.payload.SetupProgressPayload;
import dev.jstech.computers.os.IOsHost;
import dev.jstech.computers.os.ProgramKind;
import dev.jstech.computers.os.ProgramSpec;
import dev.jstech.computers.os.SoftwareHouse;
import dev.jstech.computers.os.media.MediaFormat;
import dev.jstech.computers.program.ComputerConsoleState;
import dev.jstech.computers.program.ServerCliComputer;
import dev.jstech.computers.program.cli.CliStyle;
import java.util.ArrayList;
import java.util.List;
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
 * the machine, and told to every window looking at it, so the Setup window on a desktop and the
 * progress line at a prompt are two views of one thing.
 */
public final class SetupRunner {

    /** Where a program comes from when no disc is involved. */
    public static final String SOURCE_NETWORK = "the Mirror";

    /** How often a running job is told to the desktops, in ticks. */
    private static final int PUSH_EVERY = 10;

    /** How much of the bar has to pass before the prompt gets another line. */
    private static final int PROMPT_STEP_PERMILLE = 250;

    private SetupRunner() {
    }

    /**
     * Starts installing (or removing) {@code spec} on {@code host}, or says why it cannot.
     *
     * @param medium the disc it comes from, or null for the network
     * @return the refusal, which was also shown at the machine's windows, or empty when the job began
     */
    public static Optional<String> begin(final IOsHost host, final ServerLevel level, final BlockPos pos,
                                         final ProgramSpec spec, @Nullable final MediaFormat medium,
                                         final boolean removing) {
        final ComputerConsoleState console = host.console();
        if (console == null) {
            return Optional.of("This computer cannot hold installed programs.");
        }
        if (console.setup() != null) {
            final String busy = "This computer is still setting up " + console.setup().name() + ".";
            return Optional.of(busy);
        }
        final Optional<String> refusal = SetupGate.refusal(host, spec, removing, true);
        if (refusal.isPresent()) {
            push(level, pos, refused(pos, spec, removing, refusal.get()));
            return refusal;
        }
        final String source = medium == null ? SOURCE_NETWORK : sourceName(medium);
        final int ticks = medium == null ? SetupTiming.networkTicks(spec.minDiskMb(), removing)
                : SetupTiming.ticks(spec.minDiskMb(), medium, removing);
        final SetupJob job = new SetupJob(spec.id().toString(), spec.displayName(),
                spec.houseOr(SoftwareHouse.MIDSOFT).name(), spec.minDiskMb(), source, removing, ticks);
        console.beginSetup(job);
        host.setChanged();
        push(level, pos, progress(pos, job, SetupProgressPayload.STATE_RUNNING, ""));
        promptLine(level, pos, (removing ? "Removing " : "Setting up ") + spec.displayName()
                + (medium == null ? " from " + SOURCE_NETWORK : " from " + source) + " ...", CliStyle.PLAIN);
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
        push(level, pos, progress(pos, job, SetupProgressPayload.STATE_CANCELLED, "Setup was cancelled."));
        promptLine(level, pos, "Setup cancelled. Nothing was " + (job.removing() ? "removed." : "installed."),
                CliStyle.ERROR);
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
            push(level, pos, progress(pos, job, SetupProgressPayload.STATE_DONE, ""));
            promptLine(level, pos, job.name() + (job.removing() ? " removed." : " installed."), CliStyle.OK);
            return;
        }
        if (job.ticksLeft() % PUSH_EVERY == 0) {
            push(level, pos, progress(pos, job, SetupProgressPayload.STATE_RUNNING, ""));
        }
        if (job.announce(PROMPT_STEP_PERMILLE)) {
            promptLine(level, pos, bar(job.permille()) + "  " + (job.permille() / 10) + "%", CliStyle.PLAIN);
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
        console.setInstalledVersion(id, ServerCliComputer.modVersion());
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

    /** A bar of hashes and dots for a prompt that cannot draw one, twenty cells wide. */
    private static String bar(final int permille) {
        final int cells = 20;
        final int filled = permille * cells / 1000;
        return "[" + "#".repeat(filled) + ".".repeat(cells - filled) + "]";
    }

    /** Every player at a desktop of that machine gets the job's state. */
    private static void push(final ServerLevel level, final BlockPos pos, final SetupProgressPayload payload) {
        for (final ServerPlayer player : level.players()) {
            if (player.containerMenu instanceof DesktopMenu desk && pos.equals(desk.hostPos())) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    /** Every player at that machine's prompt gets a line, since a prompt cannot show a window. */
    private static void promptLine(final ServerLevel level, final BlockPos pos, final String text,
                                   final CliStyle style) {
        final List<CommandOutputPayload.WireLine> wire = new ArrayList<>();
        wire.add(new CommandOutputPayload.WireLine(text, style.ordinal()));
        for (final ServerPlayer player : level.players()) {
            if (player.containerMenu instanceof CommandPromptMenu prompt && pos.equals(prompt.hostPos())) {
                PacketDistributor.sendToPlayer(player, new CommandOutputPayload(false, "", wire));
            }
        }
    }
}
