/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The registered {@link ChemicalBridge}s. With no bridge (no chemical mod present) every query answers "no
 * such chemical", so chemical keys are inert data the network can still store, move and display by id.
 */
public final class ChemicalBridges {

    private static final List<ChemicalBridge> BRIDGES = new CopyOnWriteArrayList<>();
    private static final int DEFAULT_TINT = 0xFF8FA3B7;

    private ChemicalBridges() {
    }

    public static void register(final ChemicalBridge bridge) {
        BRIDGES.add(bridge);
    }

    public static boolean anyRegistered() {
        return !BRIDGES.isEmpty();
    }

    /** The first bridge that offers a chemical port on the block at {@code pos}. */
    public static Optional<ChemicalPort> portFor(final Level level, final BlockPos pos, @Nullable final Direction side) {
        for (final ChemicalBridge bridge : BRIDGES) {
            final Optional<ChemicalPort> port = bridge.portFor(level, pos, side);
            if (port.isPresent()) {
                return port;
            }
        }
        return Optional.empty();
    }

    public static boolean exists(final ResourceLocation chemical) {
        for (final ChemicalBridge bridge : BRIDGES) {
            if (bridge.exists(chemical)) {
                return true;
            }
        }
        return false;
    }

    public static Component displayName(final ResourceLocation chemical) {
        for (final ChemicalBridge bridge : BRIDGES) {
            if (bridge.exists(chemical)) {
                return bridge.displayName(chemical);
            }
        }
        return Component.literal(chemical.toString());
    }

    public static int tint(final ResourceLocation chemical) {
        for (final ChemicalBridge bridge : BRIDGES) {
            if (bridge.exists(chemical)) {
                return bridge.tint(chemical);
            }
        }
        return DEFAULT_TINT;
    }
}
