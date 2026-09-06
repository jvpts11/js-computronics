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
}
