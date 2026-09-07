/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.block;

import dev.jstech.computronics.os.FirmwareKind;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Opens the client-only power-on self-test screen. Mirrors {@link IFirmwareScreenOpener}: the payload
 * handler lives in common code, so the client registers the actual screen-opening lambda here during
 * client setup and the dedicated server never touches a screen class.
 */
@FunctionalInterface
public interface IPostScreenOpener {

    /**
     * Plays the power-on self-test for the given computer on the given monitor.
     *
     * @param pos         the block position of the computer
     * @param monitorPos  the monitor the sequence renders on
     * @param kind        the firmware variant matching the computer's hardware era
     * @param machineName the localised display name of the machine
     */
    void open(BlockPos pos, BlockPos monitorPos, FirmwareKind kind, String machineName);

    final class Holder {

        private Holder() {
        }

        @Nullable
        private static IPostScreenOpener instance;

        public static void set(final IPostScreenOpener opener) {
            instance = opener;
        }

        public static void open(final BlockPos pos, final BlockPos monitorPos, final FirmwareKind kind,
                                final String machineName) {
            if (instance != null) {
                instance.open(pos, monitorPos, kind, machineName);
            }
        }
    }
}
