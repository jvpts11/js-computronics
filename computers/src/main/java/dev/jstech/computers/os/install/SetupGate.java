/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os.install;

import dev.jstech.computers.blockentity.ClusterManagementComputerBlockEntity;
import dev.jstech.computers.blockentity.CraftingComputerBlockEntity;
import dev.jstech.computers.blockentity.MainframeBlockEntity;
import dev.jstech.computers.blockentity.RackUnitHost;
import dev.jstech.computers.blockentity.ServerRackBlockEntity;
import dev.jstech.computers.os.IOsHost;
import dev.jstech.computers.os.OsDef;
import dev.jstech.computers.os.OsGating;
import dev.jstech.computers.os.OsRegistry;
import dev.jstech.computers.os.Platform;
import dev.jstech.computers.os.ProgramSpec;
import dev.jstech.computers.program.ComputerConsoleState;
import dev.jstech.core.tier.HardwareEra;
import java.util.Locale;
import java.util.Optional;

/**
 * Whether a machine can take a program, and if not, why, in words the Setup window can show.
 *
 * <p>Every way of installing a program (the disc's setup, This PC's button, the prompt, the package
 * manager) used to keep its own copy of these checks, and each said no in its own words, or in none.
 * They are asked here, once, in the order a real setup asks them, and the first thing wrong is the
 * answer.
 */
public final class SetupGate {

    private SetupGate() {
    }

    /**
     * Why the machine refuses to install (or remove) {@code spec}, or empty when it may go ahead.
     *
     * @param hasMedium whether the program's disc is in a linked drive, or it comes over the network
     */
    /**
     * Whether {@code spec} is on the machine already.
     *
     * <p>A Mainframe's services are flags on the Mainframe: one switched on with its own verb, as the
     * Mirror is with {@code mirror install}, is installed whether or not the console lists it, and
     * removing it has to be allowed on the strength of the flag.
     */
    private static boolean installed(final IOsHost host, final ComputerConsoleState console, final ProgramSpec spec) {
        if (console.isInstalled(spec.id().toString())) {
            return true;
        }
        if (host instanceof MainframeBlockEntity mainframe) {
            return switch (spec.id().getPath()) {
                case "iqlengine" -> mainframe.isIqlEngineInstalled();
                case "automation_engine" -> mainframe.isAutomationEngineInstalled();
                case "mirror" -> mainframe.isMirrorInstalled();
                default -> false;
            };
        }
        return false;
    }

    public static Optional<String> refusal(final IOsHost host, final ProgramSpec spec, final boolean removing,
                                           final boolean hasMedium) {
        final ComputerConsoleState console = host.console();
        if (console == null) {
            return Optional.of("This computer cannot hold installed programs.");
        }
        final String name = spec.displayName();
        if (removing) {
            return installed(host, console, spec) ? Optional.empty()
                    : Optional.of(name + " is not installed.");
        }
        if (spec.preinstalled()) {
            return Optional.of(name + " comes with every system. There is nothing to install.");
        }
        if (installed(host, console, spec)) {
            return Optional.of(name + " is already installed.");
        }
        final HardwareEra era = host.displayEra();
        if (spec.minEra() != HardwareEra.VINTAGE && era != null && !OsGating.canInstall(spec.minEra(), era)) {
            return Optional.of(name + " needs " + eraName(spec.minEra()) + " hardware or newer. This computer is "
                    + eraName(era) + ".");
        }
        final OsDef os = host.installedOs();
        if (os == null) {
            return Optional.of(name + " needs an operating system to install on.");
        }
        if (!spec.platforms().contains(os.platform())) {
            return Optional.of(name + " runs on " + platforms(spec) + ", not on " + os.platform().label() + ".");
        }
        final int rank = OsRegistry.osVersionRank(os.id());
        if (rank != 0 && rank < spec.minOsRank()) {
            return Optional.of(name + " needs " + systemOfRank(spec.minOsRank()) + " or newer. This computer runs "
                    + os.displayName() + ".");
        }
        final Optional<String> scope = hostScope(host, spec);
        if (scope.isPresent()) {
            return scope;
        }
        if (host.maxCpuMhz() < spec.minCpuMhz()) {
            return Optional.of(name + " needs a " + spec.minCpuMhz() + " MHz processor. This computer has "
                    + host.maxCpuMhz() + " MHz.");
        }
        if (host.totalVramMb() < spec.minVramMb()) {
            return Optional.of(name + " needs " + spec.minVramMb() + " MB of video memory. This computer has "
                    + host.totalVramMb() + " MB.");
        }
        if (host.systemDiskFreeMb() < spec.minDiskMb()) {
            return Optional.of(name + " needs " + spec.minDiskMb() + " MB free on the disk. There are "
                    + host.systemDiskFreeMb() + " MB.");
        }
        if (!hasMedium) {
            return Optional.of("Put the " + name + " disc in a drive linked to this computer.");
        }
        // The registry's own word is the last one, in case a rule lives there and nowhere above.
        if (!OsRegistry.canInstallProgram(os.id(), spec.id(), host.maxCpuMhz(), host.totalVramMb(),
                host.systemDiskFreeMb())) {
            return Optional.of(name + " cannot install on this computer's system or hardware.");
        }
        return Optional.empty();
    }

    /** A program bound to one kind of machine refuses every other kind by name. */
    private static Optional<String> hostScope(final IOsHost host, final ProgramSpec spec) {
        final boolean allowed = switch (spec.hostScope()) {
            case ANY -> true;
            case MAINFRAME -> host instanceof MainframeBlockEntity;
            case CRAFTING_COMPUTER -> host instanceof CraftingComputerBlockEntity;
            case SERVER -> host instanceof ServerRackBlockEntity || host instanceof RackUnitHost;
            case CLUSTER_MANAGEMENT_COMPUTER -> host instanceof ClusterManagementComputerBlockEntity;
        };
        if (allowed) {
            return Optional.empty();
        }
        final String where = switch (spec.hostScope()) {
            case MAINFRAME -> "a Mainframe";
            case CRAFTING_COMPUTER -> "a Crafting Computer";
            case SERVER -> "a server in a rack";
            case CLUSTER_MANAGEMENT_COMPUTER -> "a Cluster Management Computer";
            default -> "this computer";
        };
        return Optional.of(spec.displayName() + " only installs on " + where + ".");
    }

    private static String platforms(final ProgramSpec spec) {
        final StringBuilder out = new StringBuilder();
        for (final Platform platform : spec.platforms()) {
            if (!out.isEmpty()) {
                out.append(" and ");
            }
            out.append(platform.label());
        }
        return out.isEmpty() ? "nothing" : out.toString();
    }

    /** The name of the oldest system of that rank, which is what "or newer" is measured from. */
    private static String systemOfRank(final int rank) {
        for (final OsDef os : OsRegistry.oses()) {
            if (OsRegistry.osVersionRank(os.id()) == rank) {
                return os.displayName();
            }
        }
        return "a newer system";
    }

    /** An era's name as a word: "Legacy", not "LEGACY". */
    public static String eraName(final HardwareEra era) {
        final String raw = era.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }
}
