/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.menu;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.terminal.IComputerTerminalHost;
import dev.jstech.core.tier.HardwareEra;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A slotless menu for the Command Prompt. It holds no inventory — the console is driven entirely by command payloads — but being a real menu lets the server validate that the player has this prompt open for this host before running a typed line, exactly as the graphical terminal does.
 */
public class CommandPromptMenu extends AbstractContainerMenu {

    private final BlockPos monitorPos;
    private final BlockPos hostPos;
    @Nullable
    private final HardwareEra era;
    private final ContainerLevelAccess access;
    // A POSIX (Linux) terminal: the shell id ("" for a DOS-family OS), the host name and the OS label, so the
    // client can draw the login banner and the initial prompt before the first server round-trip.
    private final String shellId;
    private final String hostname;
    private final String osLabel;

    public CommandPromptMenu(final int containerId, final Inventory playerInventory,
                             final BlockPos monitorPos, final BlockPos hostPos,
                             @Nullable final HardwareEra era) {
        this(containerId, playerInventory, monitorPos, hostPos, era, "", "", "");
    }

    public CommandPromptMenu(final int containerId, final Inventory playerInventory,
                             final BlockPos monitorPos, final BlockPos hostPos,
                             @Nullable final HardwareEra era, final String shellId, final String hostname,
                             final String osLabel) {
        this(ComputingModule.COMMAND_PROMPT_MENU.get(), containerId, playerInventory, monitorPos, hostPos,
                era, shellId, hostname, osLabel);
    }

    /**
     * Base constructor for the per-platform terminal menus (the MC-DOS terminal and the Linux TTY carry the
     * same data but open their own screens, so each gets its own menu type over this shared plumbing).
     */
    protected CommandPromptMenu(final net.minecraft.world.inventory.MenuType<?> type, final int containerId,
                                final Inventory playerInventory, final BlockPos monitorPos, final BlockPos hostPos,
                                @Nullable final HardwareEra era, final String shellId, final String hostname,
                                final String osLabel) {
        super(type, containerId);
        this.monitorPos = monitorPos;
        this.hostPos = hostPos;
        this.era = era;
        this.shellId = shellId == null ? "" : shellId;
        this.hostname = hostname == null ? "" : hostname;
        this.osLabel = osLabel == null ? "" : osLabel;
        this.access = ContainerLevelAccess.create(playerInventory.player.level(), hostPos);
    }

    public static CommandPromptMenu fromNetwork(final int containerId, final Inventory playerInventory,
                                                final RegistryFriendlyByteBuf buf) {
        final OpenData data = readOpenBuffer(buf);
        return new CommandPromptMenu(containerId, playerInventory, data.monitor(), data.host(), data.era(),
                data.shellId(), data.hostname(), data.osLabel());
    }

    /** The shared open-buffer contents, so each terminal menu's {@code fromNetwork} reads them the same way. */
    protected record OpenData(BlockPos monitor, BlockPos host, @Nullable HardwareEra era, String shellId,
                              String hostname, String osLabel) {
    }

    protected static OpenData readOpenBuffer(final RegistryFriendlyByteBuf buf) {
        final BlockPos monitor = buf.readBlockPos();
        final BlockPos host = buf.readBlockPos();
        final int eraOrdinal = buf.readVarInt();
        final HardwareEra era = eraOrdinal >= 0 && eraOrdinal < HardwareEra.values().length
                ? HardwareEra.values()[eraOrdinal] : null;
        final String shellId = buf.readUtf(16);
        final String hostname = buf.readUtf(48);
        final String osLabel = buf.readUtf(48);
        return new OpenData(monitor, host, era, shellId, hostname, osLabel);
    }

    /** Writes the open buffer the client reconstructs from: the two positions plus the host era ordinal (-1 if none). */
    public static void writeOpenBuffer(final RegistryFriendlyByteBuf buf, final BlockPos monitorPos,
                                       final BlockPos hostPos, @Nullable final HardwareEra era) {
        writeOpenBuffer(buf, monitorPos, hostPos, era, "", "", "");
    }

    /** The full open buffer, including the POSIX shell details (empty strings for a DOS-family OS). */
    public static void writeOpenBuffer(final RegistryFriendlyByteBuf buf, final BlockPos monitorPos,
                                       final BlockPos hostPos, @Nullable final HardwareEra era,
                                       final String shellId, final String hostname, final String osLabel) {
        buf.writeBlockPos(monitorPos);
        buf.writeBlockPos(hostPos);
        buf.writeVarInt(era == null ? -1 : era.ordinal());
        buf.writeUtf(shellId == null ? "" : shellId, 16);
        buf.writeUtf(hostname == null ? "" : hostname, 48);
        buf.writeUtf(osLabel == null ? "" : osLabel, 48);
    }

    /** Whether the host runs a POSIX-family (Linux) OS, so the terminal wears a login banner and a bash prompt. */
    public boolean posixShell() {
        return !shellId.isEmpty();
    }

    /** The installed shell id ({@code bash}, {@code zsh}), or {@code ""} for a DOS-family OS. */
    public String shellId() {
        return shellId;
    }

    /** The POSIX host name, or {@code ""} for a DOS-family OS. */
    public String hostname() {
        return hostname;
    }

    /** The installed OS display name, or {@code ""} when unknown. */
    public String osLabel() {
        return osLabel;
    }

    public BlockPos monitorPos() {
        return monitorPos;
    }

    public BlockPos hostPos() {
        return hostPos;
    }

    /** The host computer's board-derived hardware era, captured when the prompt was opened; {@code null} for none. */
    @Nullable
    public HardwareEra hardwareEra() {
        return era;
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(final Player player) {
        // The player sits at the MONITOR, not at the machine: reach is measured there, so a remote
        // session over a screen keeps working however far the machine itself is. The machine still
        // has to be alive and shown by that screen.
        return access.evaluate((level, pos) -> {
            if (!(level.getBlockEntity(pos) instanceof IComputerTerminalHost) || !sessionAlive(level, pos)) {
                return false;
            }
            if (!(level.getBlockEntity(monitorPos)
                    instanceof dev.jstech.computronics.blockentity.MonitorBlockEntity monitor)) {
                // No monitor block (a firmware-opened prompt): fall back to standing at the machine.
                return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
            }
            return monitor.shows(pos)
                    && player.distanceToSqr(monitorPos.getX() + 0.5, monitorPos.getY() + 0.5,
                            monitorPos.getZ() + 0.5) <= 64.0;
        }, true);
    }

    /**
     * The console dies with its machine: powering the computer off, pulling the system disk, or ejecting a
     * live installer's medium closes the terminal on the next tick (the monitor then shows the firmware —
     * or nothing — on its next use).
     */
    static boolean sessionAlive(final net.minecraft.world.level.Level level, final BlockPos pos) {
        if (level.getBlockEntity(pos)
                instanceof dev.jstech.computronics.blockentity.AbstractComputerBlockEntity computer) {
            return computer.isRunning() && computer.validateOsSession();
        }
        return true;
    }
}
