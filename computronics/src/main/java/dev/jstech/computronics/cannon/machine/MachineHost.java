/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.machine;

import dev.jstech.computronics.cannon.run.Host;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The clock a program running on a real machine reads.
 *
 * <p>It asks the machine for its world every time rather than holding on to one, because a block entity
 * is read out of a save before it is placed in a world and its programs come back with it.
 */
public record MachineHost(BlockEntity machine) implements Host {

    /** The length of a Minecraft day in ticks. */
    private static final long DAY = 24_000L;

    @Override
    public long tick() {
        final Level level = this.machine.getLevel();
        return level == null ? 0 : level.getGameTime();
    }

    @Override
    public long dayTime() {
        final Level level = this.machine.getLevel();
        return level == null ? 0 : level.getDayTime() % DAY;
    }

    @Override
    public long day() {
        final Level level = this.machine.getLevel();
        return level == null ? 0 : level.getDayTime() / DAY;
    }

    @Override
    public boolean provides(final String owner) {
        return HostFiles.handles(owner) || HostComputer.handles(owner) || HostNetwork.handles(owner);
    }

    @Override
    public Reply call(final String owner, final String member, final java.util.List<Object> arguments,
                      final int line) {
        final dev.jstech.computronics.program.cli.CliComputer computer = this.asComputer();
        if (computer == null) {
            throw new dev.jstech.computronics.cannon.run.Halt(
                    dev.jstech.computronics.cannon.run.Halt.Reason.NO_SUCH_MEMBER, line,
                    "this machine cannot reach " + owner);
        }
        if (HostFiles.handles(owner)) {
            return HostFiles.call(computer, member, arguments, line);
        }
        if (HostComputer.handles(owner)
                && this.machine instanceof dev.jstech.computronics.blockentity
                        .AbstractComputerBlockEntity self) {
            return HostComputer.call(self, computer, member, line);
        }
        if (HostNetwork.handles(owner)) {
            return HostNetwork.call(computer, member, arguments, line);
        }
        throw new dev.jstech.computronics.cannon.run.Halt(
                dev.jstech.computronics.cannon.run.Halt.Reason.NO_SUCH_MEMBER, line,
                "this machine cannot reach " + owner);
    }

    /**
     * The machine as the shell sees it, which is how a program reaches its drives and its network.
     *
     * <p>Null when this block entity is not one a person could sit at, or when it has been read out of a
     * save and not yet placed in a world.
     */
    @org.jetbrains.annotations.Nullable
    private dev.jstech.computronics.program.cli.CliComputer asComputer() {
        if (this.machine instanceof dev.jstech.computronics.terminal.ComputerTerminalHost terminal
                && this.machine.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            return new dev.jstech.computronics.program.ServerCliComputer(terminal, level);
        }
        return null;
    }
}
