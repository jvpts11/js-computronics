/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.peripheral;

import java.util.List;
import java.util.Set;

/**
 * An {@link IPeripheralOwner} that keeps its linked endpoints in a backing set, so the
 * computing-side block entities share one implementation of the owner contract instead of
 * each repeating it. Only the capacity ({@link #maxEndpoints()}) stays per-owner, since it
 * depends on the installed hardware.
 */
public interface IPeripheralOwnerSupport extends IPeripheralOwner {

    /** The live, mutable set of linked endpoint positions backing this owner. */
    Set<Long> peripheralEndpoints();

    /** Marks the owner dirty after its endpoint set changes (typically {@code setChanged()}). */
    void markPeripheralChange();

    @Override
    default PeripheralCableType cableType() {
        return PeripheralCableType.COMPUTING;
    }

    @Override
    default List<Long> linkedEndpoints() {
        return List.copyOf(peripheralEndpoints());
    }

    @Override
    default void onEndpointLinked(final long endpointPos) {
        if (peripheralEndpoints().add(endpointPos)) {
            markPeripheralChange();
        }
    }

    @Override
    default void onEndpointUnlinked(final long endpointPos) {
        if (peripheralEndpoints().remove(endpointPos)) {
            markPeripheralChange();
        }
    }
}
