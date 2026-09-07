/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.rack;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * An item that can be mounted in a rack and occupies rack units: a server chassis, or one of the
 * rack units that serve the cabinet itself (a KVM switch, a UPS, a cooling unit). Rack units are
 * what makes a rack a puzzle: servers, storage, cooling, power and access all bid for the same
 * eight U.
 */
public interface IMountableRackUnit {

    /** How many rack units this item occupies when mounted. */
    int heightU();

    /** How many of the rack's front slots it cables as drive bays (zero for a non-computer unit). */
    default int driveSlots() {
        return 0;
    }

    /** How many of the rack's front slots it cables as gadget bays (zero for a non-computer unit). */
    default int gadgetSlots() {
        return 0;
    }

    /** The mountable unit a stack represents, or null when the item cannot be racked. */
    @Nullable
    static IMountableRackUnit of(final ItemStack stack) {
        return stack.getItem() instanceof IMountableRackUnit unit ? unit : null;
    }

    /** The occupancy of the stack mounted at {@code topU}, or null when it cannot be racked. */
    @Nullable
    static RackLayout.Unit unitAt(final ItemStack stack, final int topU) {
        final IMountableRackUnit unit = of(stack);
        return unit == null ? null
                : new RackLayout.Unit(topU, unit.heightU(), unit.driveSlots(), unit.gadgetSlots());
    }
}
