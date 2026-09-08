/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.rack;

import java.util.List;

/**
 * Pure geometry of a rack measured in rack units: which vertical spans are free, and what role each of
 * the rack's front-panel hotswap slots plays given what is mounted. The front slots belong to the RACK
 * (5 per rack unit); a mounted chassis claims the slots of the rows it occupies, row-major, up to its
 * drive and gadget budgets; every remaining slot is blocked, and each blocked slot knows why, so the
 * GUI can always explain itself. Minecraft-free on purpose: the fitting and claiming rules are unit
 * tested here and consumed by both the block entity and the screen.
 */
public final class RackLayout {

    /** Front-panel hotswap slots per rack unit. */
    public static final int SLOTS_PER_U = 5;

    /** A mounted unit's occupancy and front-slot budgets ({@code 0/0} for non-computer units). */
    public record Unit(int topU, int heightU, int driveSlots, int gadgetSlots) {

        /** Whether this unit occupies the given rack-unit row. */
        public boolean occupies(final int uRow) {
            return uRow >= topU && uRow < topU + heightU;
        }
    }

    /** What one front-panel slot is, and, when blocked, why. */
    public enum SlotRole {
        /** Cabled as a drive bay by the chassis in this row. */
        DRIVE,
        /** Cabled as a gadget bay (RAID controller, cache card, ...). */
        GADGET,
        /** No unit occupies this row, so the slot has nothing to cable it. */
        BLOCKED_NO_UNIT,
        /** A unit occupies the row but its chassis does not cable this many slots. */
        BLOCKED_BUDGET
    }

    private final int capacityU;

    public RackLayout(final int capacityU) {
        if (capacityU <= 0) {
            throw new IllegalArgumentException("a rack needs at least one rack unit, got " + capacityU);
        }
        this.capacityU = capacityU;
    }

    public int capacityU() {
        return capacityU;
    }

    /** Whether a unit of the given height fits at {@code topU} without leaving the rack or overlapping. */
    public boolean canPlace(final int topU, final int heightU, final List<Unit> mounted) {
        if (topU < 0 || heightU <= 0 || topU + heightU > capacityU) {
            return false;
        }
        for (final Unit unit : mounted) {
            for (int row = topU; row < topU + heightU; row++) {
                if (unit.occupies(row)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The topmost row where a unit of the given height fits, or {@code -1} when the rack is full. */
    public int firstFit(final int heightU, final List<Unit> mounted) {
        for (int topU = 0; topU + heightU <= capacityU; topU++) {
            if (canPlace(topU, heightU, mounted)) {
                return topU;
            }
        }
        return -1;
    }

    /** The unit occupying the given row, or null. */
    public static Unit unitAt(final int uRow, final List<Unit> mounted) {
        for (final Unit unit : mounted) {
            if (unit.occupies(uRow)) {
                return unit;
            }
        }
        return null;
    }

    /**
     * The role of the front slot at ({@code uRow}, {@code index}): the unit occupying the row claims its
     * rows' slots row-major: drives first, then gadgets, the rest blocked by budget.
     */
    public SlotRole roleAt(final int uRow, final int index, final List<Unit> mounted) {
        if (uRow < 0 || uRow >= capacityU || index < 0 || index >= SLOTS_PER_U) {
            return SlotRole.BLOCKED_NO_UNIT;
        }
        final Unit unit = unitAt(uRow, mounted);
        if (unit == null) {
            return SlotRole.BLOCKED_NO_UNIT;
        }
        final int ordinal = (uRow - unit.topU()) * SLOTS_PER_U + index;
        if (ordinal < unit.driveSlots()) {
            return SlotRole.DRIVE;
        }
        if (ordinal < unit.driveSlots() + unit.gadgetSlots()) {
            return SlotRole.GADGET;
        }
        return SlotRole.BLOCKED_BUDGET;
    }

    /** How many rack units are currently occupied. */
    public static int usedU(final List<Unit> mounted) {
        int used = 0;
        for (final Unit unit : mounted) {
            used += unit.heightU();
        }
        return used;
    }
}
