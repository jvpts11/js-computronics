/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics;

import dev.jstech.computronics.block.PeripheralCableBlock;
import dev.jstech.computronics.blockentity.MainframePartBlockEntity;
import dev.jstech.core.peripheral.PeripheralCableType;
import dev.jstech.core.peripheral.IPeripheralEndpoint;
import dev.jstech.core.peripheral.PeripheralLinkValidator;
import dev.jstech.core.peripheral.IPeripheralOwner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * World adapter that wires the pure {@link PeripheralLinkValidator} to live blocks and BlockEntities, plus owner discovery from an endpoint.
 */
public final class PeripheralLinks {

    private PeripheralLinks() {
    }

    public static PeripheralLinkValidator validator(final ServerLevel level) {
        return new PeripheralLinkValidator(
                pos -> cableTypeAt(level, pos),
                pos -> ownerAt(level, pos),
                pos -> endpointAt(level, pos),
                PeripheralLinks::neighbors);
    }

    private static Optional<PeripheralCableType> cableTypeAt(final ServerLevel level, final long pos) {
        return level.getBlockState(BlockPos.of(pos)).getBlock() instanceof PeripheralCableBlock cable
                ? Optional.of(cable.peripheralType()) : Optional.empty();
    }

    private static Optional<IPeripheralOwner> ownerAt(final ServerLevel level, final long pos) {
        return level.getBlockEntity(BlockPos.of(pos)) instanceof IPeripheralOwner owner
                ? Optional.of(owner) : Optional.empty();
    }

    private static OptionalLong resolveOwnerPos(final ServerLevel level, final long pos,
                                                final PeripheralCableType type) {
        final BlockEntity be = level.getBlockEntity(BlockPos.of(pos));
        if (be instanceof IPeripheralOwner owner && owner.cableType() == type) {
            return OptionalLong.of(pos);
        }
        if (be instanceof MainframePartBlockEntity part && part.controllerPos() != null
                && level.getBlockEntity(part.controllerPos()) instanceof IPeripheralOwner owner
                && owner.cableType() == type) {
            return OptionalLong.of(part.controllerPos().asLong());
        }
        // A rack cabinet spans several blocks; a cable touching any part links to its controller.
        if (be instanceof dev.jstech.computronics.blockentity.ServerRackPartBlockEntity part
                && part.controllerPos() != null
                && level.getBlockEntity(part.controllerPos()) instanceof IPeripheralOwner owner
                && owner.cableType() == type) {
            return OptionalLong.of(part.controllerPos().asLong());
        }
        return OptionalLong.empty();
    }

    private static Optional<IPeripheralEndpoint> endpointAt(final ServerLevel level, final long pos) {
        return level.getBlockEntity(BlockPos.of(pos)) instanceof IPeripheralEndpoint endpoint
                ? Optional.of(endpoint) : Optional.empty();
    }

    private static List<Long> neighbors(final long pos) {
        final BlockPos p = BlockPos.of(pos);
        final List<Long> result = new ArrayList<>(6);
        for (final Direction direction : Direction.values()) {
            result.add(p.relative(direction).asLong());
        }
        return result;
    }

    public static OptionalLong discoverOwner(final ServerLevel level, final long endpointPos) {
        final PeripheralCableType type = PeripheralCableType.COMPUTING;
        final int max = type.maxLength();
        final Set<Long> visited = new HashSet<>();
        final Deque<long[]> queue = new ArrayDeque<>();
        visited.add(endpointPos);
        for (final long neighbor : neighbors(endpointPos)) {
            if (!visited.add(neighbor)) {
                continue;
            }
            final OptionalLong owner = resolveOwnerPos(level, neighbor, type);
            if (owner.isPresent()) {
                return owner;
            }
            if (cableTypeAt(level, neighbor).filter(t -> t == type).isPresent()) {
                queue.addLast(new long[]{neighbor, 1L});
            }
        }
        while (!queue.isEmpty()) {
            final long[] current = queue.pollFirst();
            final int distance = (int) current[1];
            for (final long neighbor : neighbors(current[0])) {
                if (!visited.add(neighbor)) {
                    continue;
                }
                final OptionalLong owner = resolveOwnerPos(level, neighbor, type);
                if (owner.isPresent()) {
                    return owner;
                }
                if (distance < max && cableTypeAt(level, neighbor).filter(t -> t == type).isPresent()) {
                    queue.addLast(new long[]{neighbor, distance + 1L});
                }
            }
        }
        return OptionalLong.empty();
    }
}
