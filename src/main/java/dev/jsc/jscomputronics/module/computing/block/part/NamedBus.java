/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.block.part;

import dev.jsc.jscomputronics.common.network.NetworkSystem;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.module.computing.blockentity.DataCableBlockEntity;
import dev.jsc.jscomputronics.module.computing.storage.ExternalDataPort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves a named bus on a network so a query can address it by name (FROM/TO &lt;name&gt;). A bus name is
 * the external endpoint it exposes: its external port is the inventory its mounted face touches, so a query
 * can pull from or push to that inventory through the bus. Names match case-insensitively; the first match
 * on the network's cabling wins.
 */
public final class NamedBus {

    private NamedBus() {
    }

    /** A located bus: the cable hosting it and the face it is mounted on, enough to reach its external port. */
    public record Located(DataCableBlockEntity cable, Direction face) {

        public ExternalDataPort port() {
            return cable.neighborPort(face);
        }
    }

    /**
     * Finds the bus named {@code name} anywhere on the given network, or {@code null} if none matches. Scans
     * the network's cabling and checks every mounted bus part's name.
     */
    @Nullable
    public static Located find(final ServerLevel level, @Nullable final NetworkUuid network, final String name) {
        if (network == null || name == null || name.isBlank()) {
            return null;
        }
        for (final long encoded : NetworkSystem.get(level).connectivity().positionsOf(network)) {
            if (!(level.getBlockEntity(BlockPos.of(encoded)) instanceof DataCableBlockEntity cable)) {
                continue;
            }
            for (final Direction face : Direction.values()) {
                if (cable.getPart(face) instanceof AbstractBusPart bus && name.equalsIgnoreCase(bus.name())) {
                    return new Located(cable, face);
                }
            }
        }
        return null;
    }
}
