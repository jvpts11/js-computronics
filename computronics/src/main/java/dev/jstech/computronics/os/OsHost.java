/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.os;

import dev.jstech.computronics.program.ComputerConsoleState;
import dev.jstech.core.tier.HardwareEra;
import dev.jstech.core.uuid.NodeUuid;
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
public interface OsHost extends dev.jstech.core.peripheral.PeripheralOwner {

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
    dev.jstech.core.uuid.NetworkUuid networkUuid();

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

    /**
     * The machine's recipe workbench: the drafts the Pattern Studio edits, kept with the machine so they survive
     * the window and the session. Null on a host that has no room for one (a machine that is not seated).
     */
    @Nullable
    default dev.jstech.computronics.crafting.PatternWorkbench studio() {
        return null;
    }

    /** The installed RAM in megabytes: the modules' marketed sizes, read off the buffer items they stage. */
    default int ramTotalMb() {
        return (int) Math.min(Integer.MAX_VALUE, ramBuffer() * RamLedger.MB_PER_BUFFER_ITEM);
    }

    /**
     * This machine's memory ledger: the running system's own share, the desktop package it booted, the
     * services installed on it and the windows it has open, each weighed under the installed system. A
     * machine that is off or has no system holds nothing.
     */
    default RamLedger ramLedger() {
        final RamLedger ledger = new RamLedger(ramTotalMb());
        final OsDef os = installedOs();
        if (os == null || !isRunning()) {
            return ledger;
        }
        ledger.add(os.displayName(), os.ramMb(), RamLedger.Kind.SYSTEM);
        final ResourceLocation desktopId = bootedDesktopId();
        final DesktopEnvironmentDef desktop = desktopId != null ? OsRegistry.getDesktop(desktopId) : null;
        if (desktopId != null && !desktopId.equals(os.id())) {
            // A desktop package on a Linux system; a Frames desktop is the system itself and is counted above.
            final ProgramSpec pack = OsRegistry.getProgram(desktopId);
            if (pack != null) {
                ledger.add(desktop != null ? desktop.displayName() : pack.displayName(), pack.ramMbOn(os),
                        RamLedger.Kind.DESKTOP);
            }
        }
        final dev.jstech.computronics.program.ComputerConsoleState console = console();
        if (console != null) {
            for (final ProgramSpec spec : OsRegistry.programs()) {
                if (spec.kind() == ProgramKind.SERVICE && console.isInstalled(spec.id().getPath())) {
                    ledger.add(spec.displayName(), spec.ramMbOn(os), RamLedger.Kind.SERVICE);
                }
            }
        }
        for (final OpenWindow window : openWindows()) {
            ledger.add(window.key(), windowRamMb(window.key(), os, desktop), RamLedger.Kind.WINDOW);
        }
        return ledger;
    }

    /** Everything the session holds before the player opens a window: the system, its desktop, its services. */
    default int ramReservedMb() {
        final RamLedger ledger = ramLedger();
        return ledger.usedMb() - ledger.usedMb(RamLedger.Kind.WINDOW);
    }

    /**
     * The leading windows of {@code windows} that fit beside everything else this machine holds, in order;
     * the first past the budget and everything after it are dropped, so the oldest windows survive.
     */
    default java.util.List<OpenWindow> windowsWithinBudget(final java.util.List<OpenWindow> windows) {
        final OsDef os = installedOs();
        if (os == null) {
            return windows;
        }
        final ResourceLocation desktopId = bootedDesktopId();
        final DesktopEnvironmentDef desktop = desktopId != null ? OsRegistry.getDesktop(desktopId) : null;
        return RamLedger.withinBudget(windows, window -> windowRamMb(window.key(), os, desktop),
                ramTotalMb() - ramReservedMb());
    }

    /**
     * The megabytes a window opened under {@code key} holds: its program's weight under {@code os}, found by
     * the label the desktop gives the program, else by the program's own name; a window no program answers to
     * weighs what a bundled program of that system does.
     */
    static int windowRamMb(final String key, final OsDef os, @Nullable final DesktopEnvironmentDef desktop) {
        ProgramSpec spec = desktop != null ? desktop.programFor(key) : null;
        if (spec == null) {
            for (final ProgramSpec candidate : OsRegistry.programs()) {
                if (candidate.displayName().equals(key)) {
                    spec = candidate;
                    break;
                }
            }
        }
        return spec != null ? spec.ramMbOn(os) : RamLedger.bundledWeightMb(os.ramMb());
    }
}
