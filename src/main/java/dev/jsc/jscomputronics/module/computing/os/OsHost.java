/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.os;

import dev.jsc.jscomputronics.common.tier.HardwareEra;
import dev.jsc.jscomputronics.common.uuid.NodeUuid;
import dev.jsc.jscomputronics.module.computing.program.ComputerConsoleState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A machine that can boot and run an operating system: the contract the whole OS stack — firmware,
 * POST, boot manager, terminals, desktops, and the CLI backend — talks to. Desk computers implement
 * it directly on their block entities; a rack implements it by delegating to the server mounted in
 * it, so every access route (a directly linked monitor, a KVM channel, ssh, remote control)
 * converges on one pipeline instead of duplicating it per machine shape.
 */
public interface OsHost extends dev.jsc.jscomputronics.common.peripheral.PeripheralOwner {

    /** Whether the machine is powered on with a valid build. */
    boolean isRunning();

    /**
     * Switches the machine on or off, as its own power control does. Shutting down from inside the
     * system has to reach this: a machine whose screen merely closed is still running, still on the
     * network, and still holding whatever was open.
     */
    void setPowered(boolean on);

    /** Whether the next session must run POST before handing over to the boot manager. */
    boolean needsPost();

    void setNeedsPost(boolean value);

    /** The value of {@link #pendingInstallSlot()} when no installation is waiting for its reboot. */
    int NO_PENDING_INSTALL = -2;

    /**
     * The disk slot a guided installer has just written a system to ({@code -1} for the default
     * disk), while the machine still sits in that installer waiting for the reboot that will boot
     * it; {@link #NO_PENDING_INSTALL} otherwise. A real machine does not become the new system the
     * moment the files are on the disk: until it restarts, the installer is what is running, so
     * leaving the monitor and coming back must find the installer's "reboot" prompt, not a booted
     * desktop. Any restart or power change clears it.
     */
    int pendingInstallSlot();

    void setPendingInstallSlot(int slot);

    /**
     * The desktop environment this machine actually booted, or {@code null} when it booted to a shell.
     * This is fixed at boot and does not follow later changes on disk: installing or removing a desktop
     * package changes what the NEXT boot will run, not what is running now. Without it, leaving the
     * monitor and coming back silently applied a change the machine was never restarted for.
     */
    @org.jetbrains.annotations.Nullable
    net.minecraft.resources.ResourceLocation bootedDesktopId();

    /** Fixes the desktop for this session. Called when POST hands over to the boot manager. */
    void setBootedDesktopId(@org.jetbrains.annotations.Nullable net.minecraft.resources.ResourceLocation id);

    /**
     * The program windows this machine has open, as the last player to leave its monitor left them.
     * Machine state, not viewer state: it persists with the machine and is cleared by a restart or a
     * shutdown, exactly like the windows on a real desktop.
     */
    java.util.List<OpenWindow> openWindows();

    void setOpenWindows(java.util.List<OpenWindow> windows);

    /** The RAM buffer of the current build, in items. */
    long ramBuffer();

    /** The best CPU clock in MHz across installed CPUs, or 0 with no valid build. */
    int maxCpuMhz();

    /** The total VRAM in MB across installed GPUs, or 0 with no valid build. */
    int totalVramMb();

    /** Free space on the system disk in MB. */
    long systemDiskFreeMb();

    /** How many disk slots this machine wires up. */
    int diskSlots();

    /** The disk stack in the given 0-based disk slot, or EMPTY. */
    ItemStack diskInSlot(int slot);

    /** The disk the firmware boots (the preferred slot when it holds a system), or EMPTY. */
    ItemStack systemDisk();

    /** The firmware's preferred boot disk slot, or {@code -1} for "the first disk with a system". */
    int bootDiskSlot();

    /** Sets the firmware's preferred boot disk slot ({@code -1} = the first disk with a system). */
    void setBootDiskSlot(int slot);

    /** The disk slot a guided OS install targets by default. */
    int defaultInstallSlot();

    /** Erases everything the disk in {@code slot} carries; returns whether anything was formatted. */
    boolean formatDisk(int slot);

    /** Installs the given OS onto {@code preferredSlot} (or the default target); true on success. */
    boolean installOs(ResourceLocation osId, int preferredSlot);

    /** Whether a bootable system disk is present. */
    boolean hasOs();

    @Nullable
    ResourceLocation installedOsId();

    @Nullable
    OsDef installedOs();

    /** The desktop environment this machine boots into, or null for a TTY-only or network OS. */
    @Nullable
    ResourceLocation installedDesktopId();

    /**
     * Whether this machine still has something to run: the installed OS, or a live-install session
     * whose medium is still present. Implementations drop a dead live session as a side effect.
     */
    boolean validateOsSession();

    /** The per-machine console state: history, installed programs, settings. */
    ComputerConsoleState console();

    /** The machine's node identity (assigned on first use). */
    NodeUuid nodeUuid();

    /** The network this machine currently belongs to, or null when unlinked. */
    @Nullable
    dev.jsc.jscomputronics.common.uuid.NetworkUuid networkUuid();

    /** The player-given machine name, or an empty string. */
    String customName();

    /** Renames the machine (an empty name clears it). */
    void setCustomName(String name);

    /** How many CPUs the current build carries. */
    int installedCpus();

    /** The installed disk stacks, in slot order. */
    java.util.List<ItemStack> diskStacks();

    /** Free space on the system disk in internal data-weight units. */
    long systemDiskFreeWeight();

    /** The disk footprint the installed OS reserves, in item-equivalents. */
    long reservedByOs();

    /** Installs the given OS onto the default target slot; true on success. */
    boolean installOs(ResourceLocation osId);

    /** Marks the machine's persistent state dirty after a mutation. */
    void setChanged();

    /** The hardware era of the installed motherboard, or null when no board is present. */
    @Nullable
    HardwareEra installedEra();

    /** The hardware era the GUI should wear (a fixed-era chassis wins over the board). */
    @Nullable
    HardwareEra displayEra();
}
