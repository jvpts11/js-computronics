/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os.boot;

import dev.jstech.computers.blockentity.AbstractComputerBlockEntity;
import dev.jstech.computers.os.OsCapability;
import dev.jstech.computers.os.OsDef;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Determines the boot target for a computer, given its OS state.
 *
 * <p>The pure {@link #targetFor(boolean, OsCapability)} overload has no Minecraft dependency and is
 * covered by JUnit. The binding helper {@link #targetForComputer(BlockEntity)} reads from the live
 * block entity and is tested by GameTest.
 */
public final class BootController {

    private BootController() {}

    /**
     * The display target that should be opened when a player interacts with a computer.
     *
     * <p>Ordered from lowest capability (firmware-only) to highest (full desktop).
     */
    public enum BootTarget {
        /** No OS is installed; the firmware setup screen is shown. */
        FIRMWARE,
        /** A CLI-only OS (e.g. MC-DOS); opens the Command Prompt as the sole shell. */
        TERMINAL_ONLY,
        /** A network-GUI OS (e.g. Network OS); opens the Network Interactor tabbed screen. */
        NETWORK_GUI,
        /** A full-desktop OS; opens the graphical desktop environment. */
        FULL_DESKTOP
    }

    // Pure logic (no MC imports, safe for JUnit)

    /**
     * Returns the boot target for a computer with the given OS state.
     *
     * <p>When {@code hasOs} is {@code false} the computer shows firmware regardless of any
     * {@code capability} value; callers must pass a consistent pair.
     *
     * @param hasOs      whether a valid OS is installed on the computer
     * @param capability the capability tier of the installed OS, or {@code null} when no OS is installed
     * @return the appropriate {@link BootTarget}
     */
    public static BootTarget targetFor(final boolean hasOs, @Nullable final OsCapability capability) {
        if (!hasOs || capability == null) {
            return BootTarget.FIRMWARE;
        }
        return switch (capability) {
            case TERMINAL_ONLY -> BootTarget.TERMINAL_ONLY;
            case NETWORK_GUI   -> BootTarget.NETWORK_GUI;
            case FULL_DESKTOP  -> BootTarget.FULL_DESKTOP;
        };
    }

    // Binding helpers (read MC types, use from server/game logic only)

    /**
     * Returns the boot target derived from a fully loaded {@link OsDef}.
     * Convenience overload used by binding code that already holds the def.
     *
     * @param hasOs      whether a valid OS is installed
     * @param installedOs the OS definition, or {@code null} when no OS is installed
     * @return the appropriate {@link BootTarget}
     */
    public static BootTarget targetFor(final boolean hasOs, @Nullable final OsDef installedOs) {
        return targetFor(hasOs, installedOs != null ? installedOs.capability() : null);
    }

    /**
     * Returns the boot target for the given block entity.
     *
     * <p>When the entity hosts an operating system the result is derived from its installed OS
     * state. Any other entity type (or {@code null}) returns {@link BootTarget#FIRMWARE} as a
     * safe default.
     *
     * @param be the block entity to inspect; may be {@code null}
     * @return the appropriate {@link BootTarget}
     */
    public static BootTarget targetForComputer(@Nullable final BlockEntity be) {
        if (!(be instanceof dev.jstech.computers.os.IOsHost computer)) {
            return BootTarget.FIRMWARE;
        }
        /*
         * A booted live installation medium (the manual Arch / Gentoo install) runs its own shell in the
         * terminal until the sequence completes, whatever is or is not on the disks.
         */
        if (computer.console() != null && computer.console().liveInstall() != null) {
            return BootTarget.TERMINAL_ONLY;
        }
        final OsDef def = computer.installedOs();
        /*
         * A TTY-only OS boots a desktop only when THIS session booted one. The disk may already carry a
         * newly installed desktop package, but a running machine does not grow a graphical session on
         * its own; that waits for the next restart.
         */
        if (def != null && computer.hasOs() && computer.bootedDesktopId() != null) {
            return BootTarget.FULL_DESKTOP;
        }
        return targetFor(computer.hasOs(), def != null ? def.capability() : null);
    }
}
