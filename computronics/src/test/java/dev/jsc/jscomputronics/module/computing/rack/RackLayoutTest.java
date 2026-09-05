/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.rack;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RackLayoutTest {

    private RackLayout rack;

    @BeforeEach
    void setUp() {
        rack = new RackLayout(8);
    }

    private static RackLayout.Unit of(final RackChassis chassis, final int topU) {
        return new RackLayout.Unit(topU, chassis.heightU(), chassis.driveSlots(), chassis.gadgetSlots());
    }

    @Test
    void canPlace_rejectsOverlapAndOutOfBounds() {
        final List<RackLayout.Unit> mounted = List.of(of(RackChassis.STORAGE_SERVER, 0));
        assertFalse(rack.canPlace(0, 1, mounted), "row 0 is taken by the 2U storage chassis");
        assertFalse(rack.canPlace(1, 2, mounted), "row 1 is the storage chassis' second row");
        assertTrue(rack.canPlace(2, 2, mounted), "rows 2-3 are free");
        assertFalse(rack.canPlace(7, 2, mounted), "a 2U unit does not fit in the last row");
        assertFalse(rack.canPlace(-1, 1, mounted), "negative rows are outside the rack");
    }

    @Test
    void firstFit_findsTheTopmostGap() {
        final List<RackLayout.Unit> mounted = List.of(
                of(RackChassis.SERVER, 0),
                of(RackChassis.STORAGE_SERVER, 2));
        assertEquals(1, rack.firstFit(1, mounted), "the 1U gap between the units is used first");
        assertEquals(4, rack.firstFit(2, mounted), "a 2U unit skips the 1U gap");
    }

    @Test
    void firstFit_returnsMinusOneWhenFull() {
        final List<RackLayout.Unit> mounted = List.of(
                of(RackChassis.STORAGE_SERVER, 0),
                of(RackChassis.STORAGE_SERVER, 2),
                of(RackChassis.STORAGE_SERVER, 4),
                of(RackChassis.STORAGE_SERVER, 6));
        assertEquals(-1, rack.firstFit(1, mounted));
        assertEquals(8, RackLayout.usedU(mounted));
    }

    @Test
    void roleAt_serverChassisWiresThreeDrivesOneGadgetOneBlocked() {
        final List<RackLayout.Unit> mounted = List.of(of(RackChassis.SERVER, 3));
        assertEquals(RackLayout.SlotRole.DRIVE, rack.roleAt(3, 0, mounted));
        assertEquals(RackLayout.SlotRole.DRIVE, rack.roleAt(3, 1, mounted));
        assertEquals(RackLayout.SlotRole.DRIVE, rack.roleAt(3, 2, mounted));
        assertEquals(RackLayout.SlotRole.GADGET, rack.roleAt(3, 3, mounted));
        assertEquals(RackLayout.SlotRole.BLOCKED_BUDGET, rack.roleAt(3, 4, mounted));
    }

    @Test
    void roleAt_storageChassisFillsItsTenSlotsExactly() {
        final List<RackLayout.Unit> mounted = List.of(of(RackChassis.STORAGE_SERVER, 0));
        for (int index = 0; index < RackLayout.SLOTS_PER_U; index++) {
            assertEquals(RackLayout.SlotRole.DRIVE, rack.roleAt(0, index, mounted),
                    "first row is all drives");
        }
        assertEquals(RackLayout.SlotRole.DRIVE, rack.roleAt(1, 0, mounted));
        assertEquals(RackLayout.SlotRole.DRIVE, rack.roleAt(1, 1, mounted));
        assertEquals(RackLayout.SlotRole.DRIVE, rack.roleAt(1, 2, mounted));
        assertEquals(RackLayout.SlotRole.GADGET, rack.roleAt(1, 3, mounted));
        assertEquals(RackLayout.SlotRole.GADGET, rack.roleAt(1, 4, mounted));
    }

    @Test
    void roleAt_computeChassisBlocksEverythingPastItsTwoSlots() {
        final List<RackLayout.Unit> mounted = List.of(of(RackChassis.COMPUTE_SERVER, 6));
        assertEquals(RackLayout.SlotRole.DRIVE, rack.roleAt(6, 0, mounted));
        assertEquals(RackLayout.SlotRole.GADGET, rack.roleAt(6, 1, mounted));
        for (int index = 2; index < RackLayout.SLOTS_PER_U; index++) {
            assertEquals(RackLayout.SlotRole.BLOCKED_BUDGET, rack.roleAt(6, index, mounted));
        }
        for (int index = 0; index < RackLayout.SLOTS_PER_U; index++) {
            assertEquals(RackLayout.SlotRole.BLOCKED_BUDGET, rack.roleAt(7, index, mounted),
                    "the compute chassis' second row is all cooling/accelerator bulk");
        }
    }

    @Test
    void roleAt_rowsWithoutAUnitAreBlockedForThatReason() {
        assertEquals(RackLayout.SlotRole.BLOCKED_NO_UNIT, rack.roleAt(5, 2, List.of()));
        assertEquals(RackLayout.SlotRole.BLOCKED_NO_UNIT, rack.roleAt(9, 0, List.of()),
                "rows outside the rack read as unoccupied");
    }

    @Test
    void constructor_rejectsANonPositiveCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new RackLayout(0));
    }
}
