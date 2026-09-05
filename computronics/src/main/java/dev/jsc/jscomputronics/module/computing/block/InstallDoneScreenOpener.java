/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.block;

import dev.jsc.jscomputronics.module.computing.os.FirmwareKind;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Reopens the client-only system installer at its "installation complete, reboot" beat. Mirrors
 * {@link PostScreenOpener}: the payload handler lives in common code, so the client registers the
 * actual screen-opening lambda here during client setup and the dedicated server never touches a
 * screen class.
 */
@FunctionalInterface
public interface InstallDoneScreenOpener {

    /**
     * Shows the finished installer's reboot prompt for the given computer on the given monitor.
     *
     * @param pos         the block position of the computer
     * @param monitorPos  the monitor the screen renders on
     * @param kind        the firmware variant matching the computer's hardware era
     * @param osName      the system that was installed
     * @param targetLabel the disk it was installed onto
     * @param targetSlot  the disk slot to boot on restart ({@code -1} = the default disk)
     * @param failure     empty when the system was installed; otherwise why the write was refused
     */
    void open(BlockPos pos, BlockPos monitorPos, FirmwareKind kind, String osName, String targetLabel,
              int targetSlot, String failure);

    final class Holder {

        private Holder() {
        }

        @Nullable
        private static InstallDoneScreenOpener instance;

        public static void set(final InstallDoneScreenOpener opener) {
            instance = opener;
        }

        public static void open(final BlockPos pos, final BlockPos monitorPos, final FirmwareKind kind,
                                final String osName, final String targetLabel, final int targetSlot,
                                final String failure) {
            if (instance != null) {
                instance.open(pos, monitorPos, kind, osName, targetLabel, targetSlot, failure);
            }
        }
    }
}
