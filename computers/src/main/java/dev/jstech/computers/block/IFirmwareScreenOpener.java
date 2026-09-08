/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.block;

import dev.jstech.computers.os.FirmwareKind;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Callback that opens the firmware setup screen on the client.
 *
 * <p>This interface lives in common code so the block classes can reference it without importing
 * any client-only class. The client-side implementation is registered in
 * {@link dev.jstech.computers.client.ComputingClientSetup} during the
 * {@link net.neoforged.neoforge.client.event.RegisterMenuScreensEvent}; until then (on dedicated
 * servers) the holder remains {@code null} and any call is silently skipped.
 */
@FunctionalInterface
public interface IFirmwareScreenOpener {

    /**
     * Opens the firmware setup screen for the given computer.
     *
     * @param pos         the block position of the computer
     * @param kind        the firmware variant matching the computer's hardware era
     * @param machineName the localised display name of the machine
     */
    void open(BlockPos pos, BlockPos monitorPos, FirmwareKind kind, String machineName);

    // ─── Static holder ────────────────────────────────────────────────────────

    final class Holder {

        private Holder() {
        }

        @Nullable
        private static IFirmwareScreenOpener instance;

        /** Registers the client-side opener. Called from the client event subscriber. */
        public static void set(final IFirmwareScreenOpener opener) {
            instance = opener;
        }

        /** Opens the firmware screen if the client-side opener is registered; no-op otherwise. */
        public static void open(final BlockPos pos, final BlockPos monitorPos, final FirmwareKind kind,
                                final String machineName) {
            if (instance != null) {
                instance.open(pos, monitorPos, kind, machineName);
            }
        }
    }
}
